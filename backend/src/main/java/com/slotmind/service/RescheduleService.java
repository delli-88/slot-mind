package com.slotmind.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotmind.api.SuggestionResponse;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentRepository;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.AttentionReason;
import com.slotmind.domain.Provider;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotRepository;
import com.slotmind.domain.SuggestionRepository;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.scheduling.SchedulingStrategy.Recommendation;
import com.slotmind.scheduling.SchedulingStrategy.SchedulingRequest;
import com.slotmind.scheduling.StrategyRegistry;

/**
 * Plans one suggestion: short read transaction (snapshot) -> strategy with NO transaction open (the AI strategy
 * makes a network call) -> short write transaction. Used by the on-demand endpoint and, from S4, by the
 * asynchronous loop.
 */
@Service
public class RescheduleService {

	private static final Logger log = LoggerFactory.getLogger(RescheduleService.class);

	private final TransactionTemplate readTx;

	private final TransactionTemplate writeTx;

	private final AppointmentRepository appointments;

	private final SlotRepository slots;

	private final SuggestionRepository suggestions;

	private final SnapshotBuilder snapshots;

	private final StrategyRegistry registry;

	private final Clock clock;

	public RescheduleService(PlatformTransactionManager tm, AppointmentRepository appointments, SlotRepository slots,
			SuggestionRepository suggestions, SnapshotBuilder snapshots, StrategyRegistry registry, Clock clock) {
		this.readTx = new TransactionTemplate(tm);
		this.readTx.setReadOnly(true);
		this.writeTx = new TransactionTemplate(tm);
		this.appointments = appointments;
		this.slots = slots;
		this.suggestions = suggestions;
		this.snapshots = snapshots;
		this.registry = registry;
		this.clock = clock;
	}

	/**
	 * @param strategyName null uses the configured strategy
	 * @return the stored PENDING suggestion, or empty when no candidate slot exists
	 * @throws ConflictException when the appointment cannot take a suggestion or one is already pending
	 */
	public Optional<SuggestionResponse> plan(String appointmentId, TriggerReason trigger, String strategyName) {
		SchedulingRequest request = readTx.execute(status -> snapshot(appointmentId, trigger));

		List<Recommendation> ranked;
		try {
			ranked = registry.resolve(strategyName).recommend(request);
		}
		catch (RuntimeException ex) {
			log.error("Strategy failed for {}", appointmentId, ex);
			writeTx.executeWithoutResult(status -> flag(appointmentId, AttentionReason.ENGINE_ERROR));
			throw ex;
		}

		try {
			return Optional.ofNullable(writeTx.execute(status -> persist(appointmentId, trigger, ranked)));
		}
		catch (DataIntegrityViolationException ex) {
			// UNIQUE pending_key: another request planned the same (appointment, trigger) first
			throw new ConflictException("A pending suggestion already exists", "PENDING_SUGGESTION_EXISTS", null);
		}
	}

	private SchedulingRequest snapshot(String appointmentId, TriggerReason trigger) {
		Appointment a = appointments.findById(appointmentId)
				.orElseThrow(() -> new NotFoundException("Appointment " + appointmentId + " not found"));
		if (a.getStatus() != AppointmentStatus.CONFIRMED && a.getStatus() != AppointmentStatus.RESCHEDULE_PENDING) {
			throw new ConflictException("Appointment cannot take a suggestion", "ILLEGAL_TRANSITION",
					"appointment is " + a.getStatus());
		}
		suggestions.findFirstByAppointmentIdAndStatus(appointmentId, SuggestionStatus.PENDING).ifPresent(s -> {
			throw new ConflictException("A pending suggestion already exists", "PENDING_SUGGESTION_EXISTS",
					s.getId());
		});
		return snapshots.build(a, trigger);
	}

	private SuggestionResponse persist(String appointmentId, TriggerReason trigger, List<Recommendation> ranked) {
		Appointment a = appointments.findById(appointmentId).orElseThrow();
		if (ranked.isEmpty()) {
			if (a.getStatus() == AppointmentStatus.RESCHEDULE_PENDING) {
				a.needsAttention(AttentionReason.NO_CANDIDATE_SLOT);
			}
			return null;
		}
		Recommendation best = ranked.get(0);
		Slot slot = slots.findById(best.slotId()).orElseThrow();
		Provider alternate = best.alternateProviderId() == null ? null : slot.getProvider();
		RescheduleSuggestion s = new RescheduleSuggestion(Ids.next("SUG"), a, slot, alternate, best.confidence(),
				best.reasoning(), best.source(), best.fallbackCause(), trigger, clock.instant());
		if (a.getStatus() == AppointmentStatus.CONFIRMED) {
			a.markReschedulePending();
		}
		a.clearAttention();
		suggestions.saveAndFlush(s);
		return SuggestionResponse.from(s);
	}

	private void flag(String appointmentId, AttentionReason reason) {
		appointments.findById(appointmentId).ifPresent(a -> {
			if (a.getStatus() == AppointmentStatus.RESCHEDULE_PENDING) {
				a.needsAttention(reason);
			}
		});
	}

}

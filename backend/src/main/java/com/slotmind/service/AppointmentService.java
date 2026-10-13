package com.slotmind.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotmind.api.AppointmentRequest;
import com.slotmind.api.AppointmentResponse;
import com.slotmind.api.PageResponse;
import com.slotmind.api.SuggestionResponse;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentRepository;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderRepository;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotRepository;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.SuggestionRepository;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;

@Service
public class AppointmentService {

	private final AppointmentRepository appointments;

	private final ProviderRepository providers;

	private final SlotRepository slots;

	private final SuggestionRepository suggestions;

	private final RescheduleService reschedule;

	private final Clock clock;

	public AppointmentService(AppointmentRepository appointments, ProviderRepository providers, SlotRepository slots,
			SuggestionRepository suggestions, RescheduleService reschedule, Clock clock) {
		this.appointments = appointments;
		this.providers = providers;
		this.slots = slots;
		this.suggestions = suggestions;
		this.reschedule = reschedule;
		this.clock = clock;
	}

	@Transactional
	public AppointmentResponse book(AppointmentRequest req) {
		Provider provider = providers.findById(req.providerId())
				.orElseThrow(() -> new NotFoundException("Provider " + req.providerId() + " not found"));
		Slot slot = slots.findById(req.slotId())
				.orElseThrow(() -> new NotFoundException("Slot " + req.slotId() + " not found"));
		if (!slot.getProvider().getId().equals(provider.getId())) {
			throw new BadRequestException("Validation failed",
					List.of("slotId: " + slot.getId() + " does not belong to provider " + provider.getId()));
		}
		if (provider.getStatus() == ProviderStatus.OFFLINE) {
			throw new ConflictException("Provider is offline", "PROVIDER_OFFLINE", provider.getId() + " is OFFLINE");
		}
		if (slot.getStatus() != SlotStatus.OPEN) {
			throw new ConflictException("Slot is not available", "SLOT_NOT_OPEN",
					slot.getId() + " is " + slot.getStatus());
		}
		if (!slot.getStartTime().isAfter(clock.instant())) {
			throw new ConflictException("Slot is not available", "SLOT_NOT_OPEN", slot.getId() + " is in the past");
		}
		slot.book();
		Appointment a = new Appointment(Ids.next("APT"), req.patientName().trim(), req.type(), provider, slot,
				clock.instant());
		slots.saveAndFlush(slot);
		appointments.saveAndFlush(a);
		return AppointmentResponse.from(a, null);
	}

	@Transactional(readOnly = true)
	public PageResponse<AppointmentResponse> list(AppointmentStatus status, Integer page, Integer size) {
		Pageable pageable = Paging.of(page, size, Sort.by("slot.startTime", "id"));
		Specification<Appointment> spec = (root, query, cb) -> status == null ? cb.conjunction()
				: cb.equal(root.get("status"), status);
		Page<Appointment> result = appointments.findAll(spec, pageable);
		List<String> ids = result.getContent().stream().map(Appointment::getId).toList();
		Map<String, RescheduleSuggestion> pending = ids.isEmpty() ? Map.of()
				: suggestions.findByAppointmentIdInAndStatus(ids, SuggestionStatus.PENDING).stream()
						.collect(Collectors.toMap(s -> s.getAppointment().getId(), Function.identity(), (a, b) -> a));
		List<AppointmentResponse> items = result.getContent().stream()
				.map(a -> AppointmentResponse.from(a, pending.get(a.getId()))).toList();
		return PageResponse.of(result, items);
	}

	@Transactional(readOnly = true)
	public AppointmentResponse get(String id) {
		Appointment a = appointments.findById(id)
				.orElseThrow(() -> new NotFoundException("Appointment " + id + " not found"));
		RescheduleSuggestion pending = suggestions.findFirstByAppointmentIdAndStatus(id, SuggestionStatus.PENDING)
				.orElse(null);
		return AppointmentResponse.from(a, pending);
	}

	/** On-demand suggestion. Deliberately not transactional: the planner manages its own short transactions. */
	public SuggestionResponse suggest(String id, String strategyParam) {
		String strategy = StrategyParam.validate(strategyParam);
		return reschedule.plan(id, TriggerReason.INITIAL, strategy).orElseThrow(
				() -> new UnprocessableException("No suitable slot is available", "NO_CANDIDATE_SLOT"));
	}

}

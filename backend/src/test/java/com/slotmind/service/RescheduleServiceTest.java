package com.slotmind.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.slotmind.Fixtures;
import com.slotmind.IntegrationTest;
import com.slotmind.api.SuggestionResponse;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentRepository;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.AttentionReason;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.Specialty;
import com.slotmind.domain.SuggestionRepository;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;

@IntegrationTest
class RescheduleServiceTest {

	@Autowired
	RescheduleService reschedule;

	@Autowired
	Fixtures fx;

	@Autowired
	AppointmentRepository appointments;

	@Autowired
	SuggestionRepository suggestions;

	@Test
	void twoStrandedAppointmentsBothGetTheSameOpenSlot() {
		// Same-provider slot (tier 1) so other tests' open slots in this shared database cannot outrank it.
		Provider home = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot target = fx.openSlot(home, 150);
		Appointment a1 = fx.appointment(home, AppointmentType.ROUTINE, 60);
		Appointment a2 = fx.appointment(home, AppointmentType.ROUTINE, 120);
		a1.markReschedulePending();
		a2.markReschedulePending();
		appointments.save(a1);
		appointments.save(a2);

		SuggestionResponse s1 = reschedule.plan(a1.getId(), TriggerReason.SLOT_CANCELLED, "rule").orElseThrow();
		SuggestionResponse s2 = reschedule.plan(a2.getId(), TriggerReason.SLOT_CANCELLED, "rule").orElseThrow();

		assertThat(s1.recommendedSlot().id()).isEqualTo(target.getId());
		assertThat(s2.recommendedSlot().id()).isEqualTo(target.getId());
		assertThat(s1.status()).isEqualTo(SuggestionStatus.PENDING);
		assertThat(s1.confidence()).isEqualTo(0.9);
		assertThat(s1.alternateProvider()).isNull();
		assertThat(s1.triggerReason()).isEqualTo(TriggerReason.SLOT_CANCELLED);
	}

	@Test
	void anotherProvidersSlotIsOfferedWithAnAlternateProvider() {
		// SPECIALIST, so this open slot cannot leak into the PEDIATRIC no-candidate test below.
		Provider offline = fx.provider(Specialty.SPECIALIST, ProviderStatus.OFFLINE);
		Provider other = fx.provider(Specialty.SPECIALIST, ProviderStatus.AVAILABLE);
		Slot target = fx.openSlot(other, 150);
		Appointment a = fx.appointment(offline, AppointmentType.ROUTINE, 60);
		a.markReschedulePending();
		appointments.save(a);

		SuggestionResponse s = reschedule.plan(a.getId(), TriggerReason.PROVIDER_OFFLINE, "rule").orElseThrow();

		assertThat(s.recommendedSlot().id()).isEqualTo(target.getId());
		assertThat(s.alternateProvider().id()).isEqualTo(other.getId());
		assertThat(s.confidence()).isEqualTo(0.7);
	}

	@Test
	void secondPlanForTheSameAppointmentIsRejectedAsDuplicate() {
		Provider offline = fx.provider(Specialty.GENERAL, ProviderStatus.OFFLINE);
		fx.openSlot(fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE), 150);
		Appointment a = fx.appointment(offline, AppointmentType.ROUTINE, 60);
		reschedule.plan(a.getId(), TriggerReason.PROVIDER_OFFLINE, "rule").orElseThrow();

		assertThatThrownBy(() -> reschedule.plan(a.getId(), TriggerReason.PROVIDER_OFFLINE, "rule"))
				.isInstanceOfSatisfying(ConflictException.class,
						e -> assertThat(e.getDetail()).startsWith("PENDING_SUGGESTION_EXISTS"));
		assertThat(suggestions.findFirstByAppointmentIdAndStatus(a.getId(), SuggestionStatus.PENDING)).isPresent();
	}

	@Test
	void noCandidateFlagsAStrandedAppointmentForManualHandling() {
		Provider pediatric = fx.provider(Specialty.PEDIATRIC, ProviderStatus.OFFLINE);
		Appointment a = fx.appointment(pediatric, AppointmentType.ROUTINE, 60);
		a.markReschedulePending();
		appointments.save(a);

		Optional<SuggestionResponse> result = reschedule.plan(a.getId(), TriggerReason.PROVIDER_OFFLINE, "rule");

		assertThat(result).isEmpty();
		Appointment reloaded = appointments.findById(a.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
		assertThat(reloaded.getAttentionReason()).isEqualTo(AttentionReason.NO_CANDIDATE_SLOT);
	}

	@Test
	void rejectedSlotIsNotSuggestedAgain() {
		Provider home = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot first = fx.openSlot(home, 100);
		Slot second = fx.openSlot(home, 200);
		Appointment a = fx.appointment(home, AppointmentType.ROUTINE, 60);

		SuggestionResponse s = reschedule.plan(a.getId(), TriggerReason.SLOT_CANCELLED, "rule").orElseThrow();
		assertThat(s.recommendedSlot().id()).isEqualTo(first.getId());
		var entity = suggestions.findById(s.id()).orElseThrow();
		entity.reject(java.time.Instant.now());
		suggestions.save(entity);

		SuggestionResponse next = reschedule.plan(a.getId(), TriggerReason.SLOT_CANCELLED, "rule").orElseThrow();
		assertThat(next.recommendedSlot().id()).isEqualTo(second.getId());
	}

	@Test
	void decidedOrUnknownAppointmentsAreRefused() {
		assertThatThrownBy(() -> reschedule.plan("APT-999", TriggerReason.INITIAL, "rule"))
				.isInstanceOf(NotFoundException.class);
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Appointment a = fx.appointment(p, AppointmentType.ROUTINE, 60);
		a.complete();
		appointments.save(a);
		assertThatThrownBy(() -> reschedule.plan(a.getId(), TriggerReason.INITIAL, "rule"))
				.isInstanceOf(ConflictException.class);
	}

}

package com.slotmind.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.slotmind.IntegrationTest;

@IntegrationTest
class ConstraintTest {

	private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

	@Autowired
	ProviderRepository providers;

	@Autowired
	SlotRepository slots;

	@Autowired
	AppointmentRepository appointments;

	@Autowired
	SuggestionRepository suggestions;

	private Provider provider(String id) {
		return providers.save(new Provider(id, "Dr " + id, Specialty.GENERAL, ProviderStatus.AVAILABLE));
	}

	private Slot slot(String id, Provider p) {
		return slots.save(new Slot(id, p, NOW.plusSeconds(3600), 30));
	}

	@Test
	void twoAppointmentsCannotShareASlot() {
		Provider p = provider("PRV-C1");
		Slot s = slot("SLT-C1", p);
		appointments.saveAndFlush(new Appointment("APT-C1", "A", AppointmentType.ROUTINE, p, s, NOW));
		assertThatThrownBy(() -> appointments
				.saveAndFlush(new Appointment("APT-C2", "B", AppointmentType.ROUTINE, p, s, NOW)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void secondPendingSuggestionForSameAppointmentAndTriggerViolates() {
		Provider p = provider("PRV-C2");
		Slot own = slot("SLT-C3", p);
		Slot target = slot("SLT-C4", p);
		Appointment a = appointments
				.save(new Appointment("APT-C3", "A", AppointmentType.ROUTINE, p, own, NOW));
		suggestions.saveAndFlush(suggestion("SUG-C1", a, target));
		assertThatThrownBy(() -> suggestions.saveAndFlush(suggestion("SUG-C2", a, target)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void decidedSuggestionFreesThePendingKey() {
		Provider p = provider("PRV-C3");
		Slot own = slot("SLT-C5", p);
		Slot target = slot("SLT-C6", p);
		Appointment a = appointments
				.save(new Appointment("APT-C4", "A", AppointmentType.ROUTINE, p, own, NOW));
		RescheduleSuggestion first = suggestions.saveAndFlush(suggestion("SUG-C3", a, target));
		first.reject(NOW);
		suggestions.saveAndFlush(first);
		assertThat(first.getPendingKey()).isNull();
		suggestions.saveAndFlush(suggestion("SUG-C4", a, target));
	}

	@Test
	void appointmentLifecycleRejectsIllegalMoves() {
		Provider p = provider("PRV-C4");
		Slot own = slot("SLT-C7", p);
		Slot other = slot("SLT-C8", p);
		Appointment a = new Appointment("APT-C5", "A", AppointmentType.ROUTINE, p, own, NOW);
		assertThatThrownBy(() -> a.reschedule(other)).isInstanceOf(IllegalTransitionException.class);
		a.markReschedulePending();
		assertThatThrownBy(a::markReschedulePending).isInstanceOf(IllegalTransitionException.class);
		assertThatThrownBy(a::complete).isInstanceOf(IllegalTransitionException.class);
		a.reschedule(other);
		assertThat(a.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
		assertThat(a.getSlot()).isSameAs(other);
		a.complete();
		assertThatThrownBy(a::complete).isInstanceOf(IllegalTransitionException.class);
	}

	@Test
	void suggestionCannotBeDecidedTwice() {
		Provider p = provider("PRV-C5");
		Slot own = slot("SLT-C9", p);
		Slot target = slot("SLT-CA", p);
		Appointment a = new Appointment("APT-C6", "A", AppointmentType.ROUTINE, p, own, NOW);
		RescheduleSuggestion s = suggestion("SUG-C5", a, target);
		s.accept(NOW);
		assertThatThrownBy(() -> s.reject(NOW)).isInstanceOf(IllegalTransitionException.class);
		assertThatThrownBy(() -> s.accept(NOW)).isInstanceOf(IllegalTransitionException.class);
	}

	private RescheduleSuggestion suggestion(String id, Appointment a, Slot target) {
		return new RescheduleSuggestion(id, a, target, null, 0.9, "same provider", SuggestionSource.RULES, null,
				TriggerReason.PROVIDER_OFFLINE, NOW);
	}

}

package com.slotmind;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentRepository;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderRepository;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotRepository;
import com.slotmind.domain.Specialty;
import com.slotmind.domain.SuggestionRepository;
import com.slotmind.domain.SuggestionSource;
import com.slotmind.domain.TriggerReason;

/** Builds uniquely-named test data so tests in one context never collide. */
@Component
public class Fixtures {

	private final AtomicInteger counter = new AtomicInteger();

	private final ProviderRepository providers;

	private final SlotRepository slots;

	private final AppointmentRepository appointments;

	private final SuggestionRepository suggestions;

	private final Clock clock;

	public Fixtures(ProviderRepository providers, SlotRepository slots, AppointmentRepository appointments,
			SuggestionRepository suggestions, Clock clock) {
		this.providers = providers;
		this.slots = slots;
		this.appointments = appointments;
		this.suggestions = suggestions;
		this.clock = clock;
	}

	public Provider provider(Specialty specialty, ProviderStatus status) {
		int n = counter.incrementAndGet();
		return providers.save(new Provider("PRV-F" + n, "Dr. Fixture " + n, specialty, status));
	}

	/** An OPEN slot starting the given number of minutes from now. */
	public Slot openSlot(Provider provider, int minutesFromNow) {
		int n = counter.incrementAndGet();
		return slots.save(new Slot("SLT-F" + n, provider, clock.instant().plusSeconds(minutesFromNow * 60L), 30));
	}

	/** A CONFIRMED appointment on a fresh slot of the given provider (slot is BOOKED). */
	public Appointment appointment(Provider provider, AppointmentType type, int minutesFromNow) {
		Slot slot = openSlot(provider, minutesFromNow);
		return appointmentOn(provider, slot, type);
	}

	public Appointment appointmentOn(Provider provider, Slot slot, AppointmentType type) {
		int n = counter.incrementAndGet();
		slot.book();
		slots.save(slot);
		return appointments.save(new Appointment("APT-F" + n, "Patient " + n, type, provider, slot, clock.instant()));
	}

	/** Moves the appointment to RESCHEDULE_PENDING and stores a PENDING suggestion pointing at {@code target}. */
	public RescheduleSuggestion pendingSuggestion(Appointment appointment, Slot target) {
		if (appointment.getStatus() == com.slotmind.domain.AppointmentStatus.CONFIRMED) {
			appointment.markReschedulePending();
			appointments.save(appointment);
		}
		int n = counter.incrementAndGet();
		Provider alternate = target.getProvider().getId().equals(appointment.getProvider().getId()) ? null
				: target.getProvider();
		return suggestions.save(new RescheduleSuggestion("SUG-F" + n, appointment, target, alternate, 0.8,
				"fixture", SuggestionSource.RULES, null, TriggerReason.PROVIDER_OFFLINE, clock.instant()));
	}

}

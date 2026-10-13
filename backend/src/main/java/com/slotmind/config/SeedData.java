package com.slotmind.config;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentRepository;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderRepository;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotRepository;
import com.slotmind.domain.Specialty;

/** Loads the Addendum A demo data with clock-relative slot times; skipped when providers already exist. */
@Component
@ConditionalOnProperty(name = "slotmind.seed.enabled", havingValue = "true", matchIfMissing = true)
public class SeedData implements ApplicationRunner {

	private final ProviderRepository providers;

	private final SlotRepository slots;

	private final AppointmentRepository appointments;

	private final Clock clock;

	public SeedData(ProviderRepository providers, SlotRepository slots, AppointmentRepository appointments,
			Clock clock) {
		this.providers = providers;
		this.slots = slots;
		this.appointments = appointments;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (providers.count() > 0) {
			return;
		}
		Instant now = clock.instant();

		Provider p1 = providers.save(new Provider("PRV-001", "Dr. Sarah Chen", Specialty.GENERAL, ProviderStatus.BUSY));
		Provider p2 = providers.save(
				new Provider("PRV-002", "Dr. James Okonkwo", Specialty.SPECIALIST, ProviderStatus.AVAILABLE));
		Provider p3 = providers.save(
				new Provider("PRV-003", "Dr. Priya Sharma", Specialty.PEDIATRIC, ProviderStatus.BUSY));
		Provider p4 = providers.save(
				new Provider("PRV-004", "Dr. Rahul Verma", Specialty.GENERAL, ProviderStatus.AVAILABLE));
		Provider p5 = providers.save(
				new Provider("PRV-005", "Dr. Ananya Iyer", Specialty.SPECIALIST, ProviderStatus.BUSY));

		Slot s1 = slot("SLT-001", p1, now, 60, 30, true);
		Slot s2 = slot("SLT-002", p1, now, 120, 30, true);
		Slot s3 = slot("SLT-003", p3, now, 90, 45, true);
		slot("SLT-004", p2, now, 180, 30, false);
		slot("SLT-005", p4, now, 150, 30, false);
		Slot s6 = slot("SLT-006", p5, now, 240, 60, true);

		appointments.save(new Appointment("APT-001", "John Martinez", AppointmentType.ROUTINE, p1, s1, now));
		appointments.save(new Appointment("APT-002", "Emily Watson", AppointmentType.FOLLOW_UP, p1, s2, now));
		appointments.save(new Appointment("APT-003", "Aisha Patel", AppointmentType.URGENT, p3, s3, now));
		appointments.save(new Appointment("APT-004", "Robert Kim", AppointmentType.ROUTINE, p5, s6, now));
	}

	private Slot slot(String id, Provider provider, Instant now, int offsetMinutes, int duration, boolean booked) {
		Slot s = new Slot(id, provider, now.plus(offsetMinutes, ChronoUnit.MINUTES), duration);
		if (booked) {
			s.book();
		}
		return slots.save(s);
	}

}

package com.slotmind.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.slotmind.IntegrationTest;
import com.slotmind.domain.AppointmentRepository;
import com.slotmind.domain.ProviderRepository;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotRepository;
import com.slotmind.domain.SlotStatus;

@IntegrationTest
@TestPropertySource(properties = "slotmind.seed.enabled=true")
class SeedDataTest {

	@Autowired
	ProviderRepository providers;

	@Autowired
	SlotRepository slots;

	@Autowired
	AppointmentRepository appointments;

	@Autowired
	Clock clock;

	@Test
	void loadsAddendumAData() {
		assertThat(providers.count()).isEqualTo(5);
		assertThat(slots.count()).isEqualTo(6);
		assertThat(appointments.count()).isEqualTo(4);
	}

	@Test
	void slotTimesAreRelativeToNow() {
		Slot s5 = slots.findById("SLT-005").orElseThrow();
		long minutes = Duration.between(Instant.now(clock), s5.getStartTime()).toMinutes();
		assertThat(minutes).isBetween(140L, 150L);
		assertThat(s5.getStatus()).isEqualTo(SlotStatus.OPEN);
		assertThat(slots.findById("SLT-001").orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
		assertThat(slots.findById("SLT-004").orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
	}

}

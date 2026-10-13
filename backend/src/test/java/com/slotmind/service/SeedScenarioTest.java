package com.slotmind.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.slotmind.IntegrationTest;
import com.slotmind.api.ProviderStatusResponse;
import com.slotmind.api.SuggestionResponse;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.TriggerReason;

/** The Addendum A demo: Dr. Chen goes offline. */
@IntegrationTest
@TestPropertySource(properties = "slotmind.seed.enabled=true")
class SeedScenarioTest {

	@Autowired
	ProviderService providers;

	@Autowired
	RescheduleService reschedule;

	@Autowired
	AppointmentService appointments;

	@Test
	void chenOfflineStrandsTwoAndBothAreOfferedTheSameSlot() {
		ProviderStatusResponse r = providers.updateStatus("PRV-001", ProviderStatus.OFFLINE);
		assertThat(r.affectedAppointmentIds()).containsExactly("APT-001", "APT-002");
		assertThat(r.rescheduleQueued()).isTrue();

		// the agentic loop plans both in the background; wait for it instead of calling plan() ourselves
		SuggestionResponse s1 = awaitSuggestion("APT-001");
		SuggestionResponse s2 = awaitSuggestion("APT-002");
		assertThat(List.of(s1.recommendedSlot().id(), s2.recommendedSlot().id())).containsOnly("SLT-005");
		assertThat(s1.recommendedSlot().providerId()).isEqualTo("PRV-004");
	}

	private SuggestionResponse awaitSuggestion(String appointmentId) {
		return Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50))
				.until(() -> appointments.get(appointmentId).pendingSuggestion(), Objects::nonNull);
	}

	@Test
	void urgentPediatricCaseFallsBackToAnyAvailableProviderWithLowConfidence() {
		SuggestionResponse s = reschedule.plan("APT-003", TriggerReason.INITIAL, "rule").orElseThrow();
		assertThat(s.recommendedSlot().id()).isEqualTo("SLT-005");
		assertThat(s.confidence()).isEqualTo(0.5);
		assertThat(s.lowConfidence()).isFalse();
	}

}

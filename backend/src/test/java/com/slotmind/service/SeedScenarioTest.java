package com.slotmind.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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

	@Test
	void chenOfflineStrandsTwoAndBothAreOfferedTheSameSlot() {
		ProviderStatusResponse r = providers.updateStatus("PRV-001", ProviderStatus.OFFLINE);
		assertThat(r.affectedAppointmentIds()).containsExactly("APT-001", "APT-002");
		assertThat(r.rescheduleQueued()).isTrue();

		SuggestionResponse s1 = reschedule.plan("APT-001", TriggerReason.PROVIDER_OFFLINE, "rule").orElseThrow();
		SuggestionResponse s2 = reschedule.plan("APT-002", TriggerReason.PROVIDER_OFFLINE, "rule").orElseThrow();
		assertThat(List.of(s1.recommendedSlot().id(), s2.recommendedSlot().id())).containsOnly("SLT-005");
		assertThat(s1.recommendedSlot().providerId()).isEqualTo("PRV-004");
	}

	@Test
	void urgentPediatricCaseFallsBackToAnyAvailableProviderWithLowConfidence() {
		SuggestionResponse s = reschedule.plan("APT-003", TriggerReason.INITIAL, "rule").orElseThrow();
		assertThat(s.recommendedSlot().id()).isEqualTo("SLT-005");
		assertThat(s.confidence()).isEqualTo(0.5);
		assertThat(s.lowConfidence()).isFalse();
	}

}

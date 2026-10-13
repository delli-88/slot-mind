package com.slotmind.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.Specialty;
import com.slotmind.domain.SuggestionSource;
import com.slotmind.domain.TriggerReason;
import com.slotmind.scheduling.SchedulingStrategy.AppointmentInfo;
import com.slotmind.scheduling.SchedulingStrategy.ProviderInfo;
import com.slotmind.scheduling.SchedulingStrategy.Recommendation;
import com.slotmind.scheduling.SchedulingStrategy.SchedulingRequest;
import com.slotmind.scheduling.SchedulingStrategy.SlotInfo;

class RuleBasedStrategyTest {

	private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

	private final RuleBasedStrategy strategy = new RuleBasedStrategy();

	private static final ProviderInfo HOME = new ProviderInfo("P1", "Dr One", Specialty.GENERAL, ProviderStatus.OFFLINE);

	private static final ProviderInfo GENERAL = new ProviderInfo("P2", "Dr Two", Specialty.GENERAL,
			ProviderStatus.AVAILABLE);

	private static final ProviderInfo SPECIALIST = new ProviderInfo("P3", "Dr Three", Specialty.SPECIALIST,
			ProviderStatus.AVAILABLE);

	private static SlotInfo slot(String id, String providerId, int minutes) {
		return new SlotInfo(id, providerId, NOW.plusSeconds(minutes * 60L), 30, SlotStatus.OPEN);
	}

	private SchedulingRequest request(AppointmentType type, List<SlotInfo> slots, Set<String> excluded,
			ProviderInfo... providers) {
		AppointmentInfo appt = new AppointmentInfo("APT-1", type, "P1", "SLT-OWN", NOW.plusSeconds(3600));
		return new SchedulingRequest(appt, TriggerReason.PROVIDER_OFFLINE, slots, List.of(providers), excluded, NOW);
	}

	@Test
	void sameSpecialtyBeatsEarlierOtherSpecialty() {
		List<Recommendation> r = strategy.recommend(request(AppointmentType.URGENT,
				List.of(slot("A", "P3", 10), slot("B", "P2", 120)), Set.of(), HOME, GENERAL, SPECIALIST));
		assertThat(r).extracting(Recommendation::slotId).containsExactly("B", "A");
		assertThat(r.get(0).confidence()).isEqualTo(0.7);
		assertThat(r.get(1).confidence()).isEqualTo(0.5);
		assertThat(r.get(0).alternateProviderId()).isEqualTo("P2");
		assertThat(r.get(0).source()).isEqualTo(SuggestionSource.RULES);
		assertThat(r.get(0).fallbackCause()).isNull();
	}

	@Test
	void nonUrgentGetsNothingOutsideItsSpecialty() {
		assertThat(strategy.recommend(request(AppointmentType.ROUTINE, List.of(slot("A", "P3", 10)), Set.of(),
				HOME, GENERAL, SPECIALIST))).isEmpty();
	}

	@Test
	void sameProviderIsTierOneWithNoAlternate() {
		ProviderInfo home = new ProviderInfo("P1", "Dr One", Specialty.GENERAL, ProviderStatus.AVAILABLE);
		List<Recommendation> r = strategy.recommend(request(AppointmentType.ROUTINE,
				List.of(slot("A", "P2", 10), slot("B", "P1", 200)), Set.of(), home, GENERAL));
		assertThat(r.get(0).slotId()).isEqualTo("B");
		assertThat(r.get(0).confidence()).isEqualTo(0.9);
		assertThat(r.get(0).alternateProviderId()).isNull();
	}

	@Test
	void skipsExcludedOwnPastBookedAndOfflineProviderSlots() {
		SlotInfo booked = new SlotInfo("BOOKED", "P2", NOW.plusSeconds(600), 30, SlotStatus.BOOKED);
		SlotInfo past = slot("PAST", "P2", -5);
		SlotInfo own = slot("SLT-OWN", "P2", 30);
		SlotInfo offlineProvider = slot("OFF", "P1", 40);
		SlotInfo unknownProvider = slot("UNKNOWN", "P9", 40);
		SlotInfo good = slot("GOOD", "P2", 50);
		List<Recommendation> r = strategy.recommend(request(AppointmentType.URGENT,
				List.of(booked, past, own, offlineProvider, unknownProvider, slot("REJECTED", "P2", 20), good),
				Set.of("REJECTED"), HOME, GENERAL));
		assertThat(r).extracting(Recommendation::slotId).containsExactly("GOOD");
	}

	@Test
	void tiesBreakOnStartTimeThenSlotId() {
		List<Recommendation> r = strategy.recommend(request(AppointmentType.ROUTINE,
				List.of(slot("B", "P2", 60), slot("A", "P2", 60), slot("C", "P2", 30)), Set.of(), HOME, GENERAL));
		assertThat(r).extracting(Recommendation::slotId).containsExactly("C", "A", "B");
	}

	@Test
	void emptyWhenNoSlots() {
		assertThat(strategy.recommend(request(AppointmentType.URGENT, List.of(), Set.of(), HOME, GENERAL))).isEmpty();
	}

	@Test
	void reasoningNamesTheProviderAndIsShortEnoughToStore() {
		Recommendation r = strategy.recommend(request(AppointmentType.ROUTINE, List.of(slot("A", "P2", 60)),
				Set.of(), HOME, GENERAL)).get(0);
		assertThat(r.reasoning()).contains("Dr Two").hasSizeLessThanOrEqualTo(500);
	}

}

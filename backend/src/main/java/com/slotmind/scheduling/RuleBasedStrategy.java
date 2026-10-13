package com.slotmind.scheduling;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.SuggestionSource;

/**
 * Deterministic ranking, also the fallback for the AI strategy (DESIGN 1.4). Tier 1: same provider (0.9).
 * Tier 2: same specialty (0.7). Tier 3, URGENT only: any non-offline provider (0.5). Ties break on start time,
 * then slot id.
 */
@Component
public class RuleBasedStrategy implements SchedulingStrategy {

	public static final String NAME = "rule";

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm 'UTC'", Locale.ENGLISH)
			.withZone(ZoneOffset.UTC);

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public List<Recommendation> recommend(SchedulingRequest request) {
		AppointmentInfo appt = request.appointment();
		Map<String, ProviderInfo> providers = new HashMap<>();
		request.providers().forEach(p -> providers.put(p.id(), p));
		ProviderInfo home = providers.get(appt.providerId());

		List<Ranked> ranked = new ArrayList<>();
		for (SlotInfo slot : request.slots()) {
			if (slot.status() != SlotStatus.OPEN || !slot.startTime().isAfter(request.now())
					|| slot.id().equals(appt.slotId()) || request.excludedSlotIds().contains(slot.id())) {
				continue;
			}
			ProviderInfo provider = providers.get(slot.providerId());
			if (provider == null || provider.status() == ProviderStatus.OFFLINE) {
				continue;
			}
			if (provider.id().equals(appt.providerId())) {
				ranked.add(new Ranked(1, 0.9, slot, provider, "the same provider"));
			}
			else if (home != null && provider.specialty() == home.specialty()) {
				ranked.add(new Ranked(2, 0.7, slot, provider, "the same specialty"));
			}
			else if (appt.type() == AppointmentType.URGENT) {
				ranked.add(new Ranked(3, 0.5, slot, provider, "an urgent-case fallback"));
			}
		}
		ranked.sort(Comparator.comparingInt(Ranked::tier).thenComparing(r -> r.slot().startTime())
				.thenComparing(r -> r.slot().id()));
		return ranked.stream().map(r -> toRecommendation(r, appt)).toList();
	}

	private Recommendation toRecommendation(Ranked r, AppointmentInfo appt) {
		boolean sameProvider = r.provider().id().equals(appt.providerId());
		String reasoning = "Earliest open slot with " + r.provider().name() + " (" + r.provider().specialty()
				+ ") at " + WHEN.format(r.slot().startTime()) + ", chosen as " + r.why() + ".";
		return new Recommendation(r.slot().id(), r.provider().id(), sameProvider ? null : r.provider().id(),
				r.confidence(), reasoning, SuggestionSource.RULES, null);
	}

	private record Ranked(int tier, double confidence, SlotInfo slot, ProviderInfo provider, String why) {
	}

}

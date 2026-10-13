package com.slotmind.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.ProviderRepository;
import com.slotmind.domain.SlotRepository;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.SuggestionRepository;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.scheduling.SchedulingStrategy.AppointmentInfo;
import com.slotmind.scheduling.SchedulingStrategy.ProviderInfo;
import com.slotmind.scheduling.SchedulingStrategy.SchedulingRequest;
import com.slotmind.scheduling.SchedulingStrategy.SlotInfo;

/** Copies what a strategy needs into immutable records. Call inside a read-only transaction. */
@Component
class SnapshotBuilder {

	private final ProviderRepository providers;

	private final SlotRepository slots;

	private final SuggestionRepository suggestions;

	private final Clock clock;

	SnapshotBuilder(ProviderRepository providers, SlotRepository slots, SuggestionRepository suggestions,
			Clock clock) {
		this.providers = providers;
		this.slots = slots;
		this.suggestions = suggestions;
		this.clock = clock;
	}

	SchedulingRequest build(Appointment a, TriggerReason trigger) {
		Instant now = clock.instant();
		AppointmentInfo appt = new AppointmentInfo(a.getId(), a.getType(), a.getProvider().getId(),
				a.getSlot().getId(), a.getSlot().getStartTime());
		List<ProviderInfo> providerInfos = providers.findAll().stream()
				.map(p -> new ProviderInfo(p.getId(), p.getName(), p.getSpecialty(), p.getStatus())).toList();
		List<SlotInfo> slotInfos = slots.findByStatusAndStartTimeAfter(SlotStatus.OPEN, now).stream()
				.map(s -> new SlotInfo(s.getId(), s.getProvider().getId(), s.getStartTime(), s.getDurationMinutes(),
						s.getStatus()))
				.toList();
		Set<String> excluded = new HashSet<>(suggestions.recommendedSlotIds(a.getId(), SuggestionStatus.REJECTED));
		return new SchedulingRequest(appt, trigger, slotInfos, providerInfos, excluded, now);
	}

}

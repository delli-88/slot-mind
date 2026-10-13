package com.slotmind.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.Specialty;
import com.slotmind.domain.TriggerReason;
import com.slotmind.scheduling.SchedulingStrategy.AppointmentInfo;
import com.slotmind.scheduling.SchedulingStrategy.ProviderInfo;
import com.slotmind.scheduling.SchedulingStrategy.SchedulingRequest;
import com.slotmind.scheduling.SchedulingStrategy.SlotInfo;

/**
 * Pure request fixtures for the AI-layer tests. Home provider PRV-H (GENERAL) owns appointment APT-X on SLT-OLD.
 * Offered: SLT-A (PRV-A, GENERAL, +100 min), SLT-B (PRV-B, GENERAL, +200 min). Never offered because ineligible:
 * SLT-BOOKED (booked), SLT-OFF (PRV-OFF is offline), SLT-PED (PEDIATRIC, wrong specialty for a non-urgent case).
 */
final class Requests {

	static final Instant NOW = Instant.parse("2026-10-13T09:00:00Z");

	private Requests() {
	}

	static SchedulingRequest offline(AppointmentType type) {
		return build(type, TriggerReason.PROVIDER_OFFLINE, ProviderStatus.OFFLINE, true);
	}

	static SchedulingRequest cancelled(AppointmentType type) {
		return build(type, TriggerReason.SLOT_CANCELLED, ProviderStatus.AVAILABLE, true);
	}

	static SchedulingRequest noSlots() {
		return build(AppointmentType.ROUTINE, TriggerReason.PROVIDER_OFFLINE, ProviderStatus.OFFLINE, false);
	}

	static SchedulingRequest build(AppointmentType type, TriggerReason trigger, ProviderStatus homeStatus,
			boolean withOpenSlots) {
		AppointmentInfo appt = new AppointmentInfo("APT-X", type, "PRV-H", "SLT-OLD", NOW.plus(Duration.ofMinutes(60)));
		List<ProviderInfo> providers = List.of(new ProviderInfo("PRV-H", "Dr. Home", Specialty.GENERAL, homeStatus),
				new ProviderInfo("PRV-A", "Dr. Alpha", Specialty.GENERAL, ProviderStatus.AVAILABLE),
				new ProviderInfo("PRV-B", "Dr. Beta", Specialty.GENERAL, ProviderStatus.BUSY),
				new ProviderInfo("PRV-OFF", "Dr. Gone", Specialty.GENERAL, ProviderStatus.OFFLINE),
				new ProviderInfo("PRV-PED", "Dr. Kid", Specialty.PEDIATRIC, ProviderStatus.AVAILABLE));
		List<SlotInfo> slots = withOpenSlots ? List.of(slot("SLT-A", "PRV-A", 100, SlotStatus.OPEN),
				slot("SLT-B", "PRV-B", 200, SlotStatus.OPEN), slot("SLT-BOOKED", "PRV-A", 50, SlotStatus.BOOKED),
				slot("SLT-OFF", "PRV-OFF", 60, SlotStatus.OPEN), slot("SLT-PED", "PRV-PED", 70, SlotStatus.OPEN))
				: List.of();
		return new SchedulingRequest(appt, trigger, slots, providers, Set.of(), NOW);
	}

	static SlotInfo slot(String id, String providerId, int minutesFromNow, SlotStatus status) {
		return new SlotInfo(id, providerId, NOW.plus(Duration.ofMinutes(minutesFromNow)), 30, status);
	}

	static String json(String slotId, String alt, Object confidence, String reasoning) {
		return "{\"slotId\":" + (slotId == null ? "null" : "\"" + slotId + "\"") + ",\"alternateProviderId\":"
				+ (alt == null ? "null" : "\"" + alt + "\"") + ",\"confidence\":" + confidence + ",\"reasoning\":\""
				+ reasoning + "\"}";
	}

}

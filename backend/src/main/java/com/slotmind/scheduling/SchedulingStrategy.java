package com.slotmind.scheduling;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.Specialty;
import com.slotmind.domain.SuggestionSource;
import com.slotmind.domain.TriggerReason;

/**
 * Seam 1: ranks replacement slots for one stranded appointment. Implementations are pure functions of an
 * immutable request (no JPA types, no database), so they can be unit-tested directly. Adding a strategy means
 * adding one {@code @Component}.
 */
public interface SchedulingStrategy {

	/** Registry key, for example "rule" or "ai". */
	String name();

	/** Best first. An empty list means no candidate exists. */
	List<Recommendation> recommend(SchedulingRequest request);

	record AppointmentInfo(String id, AppointmentType type, String providerId, String slotId, Instant startTime) {
	}

	record ProviderInfo(String id, String name, Specialty specialty, ProviderStatus status) {
	}

	record SlotInfo(String id, String providerId, Instant startTime, int durationMinutes, SlotStatus status) {
	}

	record SchedulingRequest(AppointmentInfo appointment, TriggerReason trigger, List<SlotInfo> slots,
			List<ProviderInfo> providers, Set<String> excludedSlotIds, Instant now) {
	}

	/** {@code alternateProviderId} is null when the slot belongs to the appointment's own provider. */
	record Recommendation(String slotId, String providerId, String alternateProviderId, double confidence,
			String reasoning, SuggestionSource source, String fallbackCause) {
	}

}

package com.slotmind.api;

import java.time.Instant;

import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;

public record SlotResponse(String id, String providerId, String providerName, Instant startTime,
		int durationMinutes, SlotStatus status) {

	public static SlotResponse from(Slot s) {
		return new SlotResponse(s.getId(), s.getProvider().getId(), s.getProvider().getName(), s.getStartTime(),
				s.getDurationMinutes(), s.getStatus());
	}

}

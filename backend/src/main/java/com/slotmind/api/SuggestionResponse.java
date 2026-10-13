package com.slotmind.api;

import java.time.Instant;

import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.SuggestionSource;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;

public record SuggestionResponse(String id, String appointmentId, SlotResponse recommendedSlot,
		ProviderResponse alternateProvider, double confidence, boolean lowConfidence, String reasoning,
		SuggestionSource source, String fallbackCause, TriggerReason triggerReason, SuggestionStatus status,
		Instant createdAt, Instant decidedAt) {

	/** Below this the UI flags the suggestion for closer review. */
	public static final double LOW_CONFIDENCE = 0.5;

	public static SuggestionResponse from(RescheduleSuggestion s) {
		return new SuggestionResponse(s.getId(), s.getAppointment().getId(), SlotResponse.from(s.getRecommendedSlot()),
				s.getAlternateProvider() == null ? null : ProviderResponse.from(s.getAlternateProvider()),
				s.getConfidence(), s.getConfidence() < LOW_CONFIDENCE, s.getReasoning(), s.getSource(),
				s.getFallbackCause(), s.getTriggerReason(), s.getStatus(), s.getCreatedAt(), s.getDecidedAt());
	}

}

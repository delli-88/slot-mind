package com.slotmind.api;

import java.time.Instant;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.AttentionReason;
import com.slotmind.domain.RescheduleSuggestion;

public record AppointmentResponse(String id, String patientName, AppointmentType type, AppointmentStatus status,
		ProviderResponse provider, SlotResponse slot, AttentionReason attentionReason,
		SuggestionResponse pendingSuggestion, Instant createdAt) {

	public static AppointmentResponse from(Appointment a, RescheduleSuggestion pending) {
		return new AppointmentResponse(a.getId(), a.getPatientName(), a.getType(), a.getStatus(),
				ProviderResponse.from(a.getProvider()), SlotResponse.from(a.getSlot()), a.getAttentionReason(),
				pending == null ? null : SuggestionResponse.from(pending), a.getCreatedAt());
	}

}

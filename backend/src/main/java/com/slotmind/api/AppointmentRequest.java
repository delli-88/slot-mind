package com.slotmind.api;

import com.slotmind.domain.AppointmentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppointmentRequest(@NotBlank @Size(max = 100) String patientName, @NotNull AppointmentType type,
		@NotBlank String providerId, @NotBlank String slotId) {
}

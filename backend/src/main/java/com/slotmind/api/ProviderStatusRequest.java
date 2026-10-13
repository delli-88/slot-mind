package com.slotmind.api;

import com.slotmind.domain.ProviderStatus;

import jakarta.validation.constraints.NotNull;

public record ProviderStatusRequest(@NotNull ProviderStatus status) {
}

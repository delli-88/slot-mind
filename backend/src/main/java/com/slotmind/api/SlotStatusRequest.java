package com.slotmind.api;

import com.slotmind.domain.SlotStatus;

import jakarta.validation.constraints.NotNull;

public record SlotStatusRequest(@NotNull SlotStatus status) {
}

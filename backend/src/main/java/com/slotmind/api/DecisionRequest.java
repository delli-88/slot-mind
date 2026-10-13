package com.slotmind.api;

import com.slotmind.domain.SuggestionStatus;

import jakarta.validation.constraints.NotNull;

public record DecisionRequest(@NotNull SuggestionStatus status) {
}

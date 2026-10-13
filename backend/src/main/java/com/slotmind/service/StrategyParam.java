package com.slotmind.service;

import java.util.List;

/** Validates the optional {@code ?strategy=} query value. */
public final class StrategyParam {

	private StrategyParam() {
	}

	/** Returns null when absent (use the configured strategy). */
	public static String validate(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String v = raw.trim().toLowerCase();
		if (!v.equals("ai") && !v.equals("rule")) {
			throw new BadRequestException("Unknown strategy", List.of("strategy: must be one of ai, rule"));
		}
		return v;
	}

}

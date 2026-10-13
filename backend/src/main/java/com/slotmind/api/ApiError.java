package com.slotmind.api;

import java.time.Instant;
import java.util.List;

/** Uniform error body for every non-2xx response (DESIGN section 5). */
public record ApiError(Instant timestamp, int status, String error, String message, String path,
		List<String> details) {
}

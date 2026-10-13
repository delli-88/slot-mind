package com.slotmind.ai;

/** A failed model call, already classified so the caller can fall back with a precise cause. */
public class LlmException extends RuntimeException {

	private final FallbackCause cause;

	public LlmException(FallbackCause cause, String message, Throwable root) {
		super(message, root);
		this.cause = cause;
	}

	public FallbackCause getFallbackCause() {
		return cause;
	}

}

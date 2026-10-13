package com.slotmind.service;

/** 422: the request is well formed but cannot be satisfied (for example NO_CANDIDATE_SLOT). */
public class UnprocessableException extends RuntimeException {

	private final String code;

	public UnprocessableException(String message, String code) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}

}

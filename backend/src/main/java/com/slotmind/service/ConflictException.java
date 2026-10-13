package com.slotmind.service;

/** 409. {@code code} is a stable machine-readable value such as SLOT_NOT_OPEN, placed first in details. */
public class ConflictException extends RuntimeException {

	private final String code;

	private final String detail;

	public ConflictException(String message, String code, String detail) {
		super(message);
		this.code = code;
		this.detail = detail;
	}

	public String getCode() {
		return code;
	}

	/** "CODE: explanation", or just the code when there is nothing to add. */
	public String getDetail() {
		return detail == null || detail.isBlank() ? code : code + ": " + detail;
	}

}

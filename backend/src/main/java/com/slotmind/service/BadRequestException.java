package com.slotmind.service;

import java.util.List;

/** 400 raised by services for rule violations that Bean Validation cannot express. */
public class BadRequestException extends RuntimeException {

	private final List<String> details;

	public BadRequestException(String message, List<String> details) {
		super(message);
		this.details = details;
	}

	public List<String> getDetails() {
		return details;
	}

}

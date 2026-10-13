package com.slotmind.domain;

/** Thrown by entity transition methods; the API layer maps it to 409 ILLEGAL_TRANSITION. */
public class IllegalTransitionException extends RuntimeException {

	public IllegalTransitionException(String message) {
		super(message);
	}

}

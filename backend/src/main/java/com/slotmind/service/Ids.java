package com.slotmind.service;

import java.util.UUID;

public final class Ids {

	private Ids() {
	}

	/** PREFIX-XXXXXXXX (8 upper-case hex chars). */
	public static String next(String prefix) {
		return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
	}

}

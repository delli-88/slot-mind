package com.slotmind.ai;

/** Why an AI suggestion was replaced by the rule-based one. Stored as a string on the suggestion. */
public enum FallbackCause {

	TIMEOUT, GATEWAY_ERROR, PARSE_ERROR, INVALID_SLOT, INVALID_PROVIDER, SLOT_NOT_OPEN, MODEL_DECLINED, NO_API_KEY,
	REFUSED

}

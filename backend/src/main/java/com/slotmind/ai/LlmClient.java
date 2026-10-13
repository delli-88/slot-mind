package com.slotmind.ai;

import java.time.Duration;

/** Seam 2: one blocking model call. The real implementation is {@link LLMGateway}; tests use a fake. */
public interface LlmClient {

	/**
	 * @return the model's text reply
	 * @throws LlmException with TIMEOUT, GATEWAY_ERROR, REFUSED or NO_API_KEY
	 */
	String callLLM(String system, String user, Duration timeout);

	default String callLLM(String prompt) {
		return callLLM("", prompt, Duration.ofSeconds(8));
	}

}

package com.slotmind.ai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the real Claude gateway with a scripted fake. Pair with {@code llm.api.key=test-key} so the AI path runs. */
@TestConfiguration
public class FakeLlmConfig {

	@Bean
	@Primary
	FakeLlmClient fakeLlm() {
		return new FakeLlmClient();
	}

}

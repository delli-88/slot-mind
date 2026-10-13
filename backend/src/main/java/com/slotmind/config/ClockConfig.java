package com.slotmind.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

	/** Single time source so tests and seed data can reason about "now". */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}

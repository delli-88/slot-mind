package com.slotmind.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class StrategyRegistryTest {

	private static SchedulingStrategy named(String name) {
		return new SchedulingStrategy() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public List<Recommendation> recommend(SchedulingRequest request) {
				return List.of();
			}
		};
	}

	private final SchedulingStrategy rule = named("rule");

	private final SchedulingStrategy ai = named("ai");

	@Test
	void picksByNameAndByConfiguration() {
		StrategyRegistry registry = new StrategyRegistry(List.of(rule, ai), "ai");
		assertThat(registry.resolve("rule")).isSameAs(rule);
		assertThat(registry.resolve("ai")).isSameAs(ai);
		assertThat(registry.resolve(null)).isSameAs(ai);
		assertThat(registry.active()).isSameAs(ai);
	}

	@Test
	void configuredStrategyChangesWithoutCodeChanges() {
		assertThat(new StrategyRegistry(List.of(rule, ai), "rule").active()).isSameAs(rule);
	}

	@Test
	void unregisteredNameDegradesToRules() {
		StrategyRegistry registry = new StrategyRegistry(List.of(rule), "ai");
		assertThat(registry.active()).isSameAs(rule);
		assertThat(registry.resolve("nonsense")).isSameAs(rule);
	}

}

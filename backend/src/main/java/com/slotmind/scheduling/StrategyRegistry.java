package com.slotmind.scheduling;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Collects every strategy bean by name; the same registry serves the async loop and on-demand calls. */
@Component
public class StrategyRegistry {

	private static final Logger log = LoggerFactory.getLogger(StrategyRegistry.class);

	private final Map<String, SchedulingStrategy> byName;

	private final String configured;

	public StrategyRegistry(List<SchedulingStrategy> strategies,
			@Value("${slotmind.scheduling.strategy:ai}") String configured) {
		this.byName = strategies.stream().collect(Collectors.toMap(SchedulingStrategy::name, Function.identity()));
		this.configured = configured;
	}

	/** The strategy named by configuration. */
	public SchedulingStrategy active() {
		return resolve(configured);
	}

	/** Null means "use the configured one". A name that is not registered degrades to the rules, never fails. */
	public SchedulingStrategy resolve(String name) {
		String wanted = name == null ? configured : name;
		SchedulingStrategy found = byName.get(wanted);
		if (found == null) {
			log.warn("Strategy '{}' is not registered, using '{}'", wanted, RuleBasedStrategy.NAME);
			return byName.get(RuleBasedStrategy.NAME);
		}
		return found;
	}

}

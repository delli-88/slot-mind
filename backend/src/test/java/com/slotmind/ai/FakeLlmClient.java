package com.slotmind.ai;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/** Scripted replies (a String is returned, a RuntimeException is thrown) and a record of every prompt sent. */
public class FakeLlmClient implements LlmClient {

	private final Queue<Object> script = new ArrayDeque<>();

	public final List<String> systems = new ArrayList<>();

	public final List<String> users = new ArrayList<>();

	public final List<Duration> timeouts = new ArrayList<>();

	public FakeLlmClient reply(String text) {
		script.add(text);
		return this;
	}

	public FakeLlmClient fail(RuntimeException ex) {
		script.add(ex);
		return this;
	}

	public int calls() {
		return users.size();
	}

	@Override
	public String callLLM(String system, String user, Duration timeout) {
		systems.add(system);
		users.add(user);
		timeouts.add(timeout);
		Object next = script.poll();
		if (next == null) {
			throw new IllegalStateException("FakeLlmClient: no scripted reply left");
		}
		if (next instanceof RuntimeException ex) {
			throw ex;
		}
		return (String) next;
	}

}

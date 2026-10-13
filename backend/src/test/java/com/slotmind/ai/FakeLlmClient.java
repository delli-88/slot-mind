package com.slotmind.ai;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

/**
 * Scripted replies (a String is returned, a RuntimeException is thrown) and a record of every prompt sent. Safe to
 * use from the async planner thread: the script and the records are guarded by the instance lock.
 */
public class FakeLlmClient implements LlmClient {

	private final Queue<Object> script = new ArrayDeque<>();

	public final List<String> systems = Collections.synchronizedList(new ArrayList<>());

	public final List<String> users = Collections.synchronizedList(new ArrayList<>());

	public final List<Duration> timeouts = Collections.synchronizedList(new ArrayList<>());

	/** Simulates a slow model; applied before every reply. */
	public volatile long delayMillis;

	public synchronized FakeLlmClient reply(String text) {
		script.add(text);
		return this;
	}

	public synchronized FakeLlmClient fail(RuntimeException ex) {
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
		if (delayMillis > 0) {
			try {
				Thread.sleep(delayMillis);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
		Object next;
		synchronized (this) {
			next = script.poll();
		}
		if (next == null) {
			throw new IllegalStateException("FakeLlmClient: no scripted reply left");
		}
		if (next instanceof RuntimeException ex) {
			throw ex;
		}
		return (String) next;
	}

}

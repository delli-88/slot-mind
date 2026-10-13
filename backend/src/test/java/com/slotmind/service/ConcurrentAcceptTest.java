package com.slotmind.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.slotmind.Fixtures;
import com.slotmind.IntegrationTest;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.Specialty;

/** I1/I3 under real parallelism: many threads race for one slot, exactly one wins, the rest get 409. */
@IntegrationTest
class ConcurrentAcceptTest {

	private static final int THREADS = 6;

	@Autowired
	MockMvc mvc;

	@Autowired
	Fixtures fx;

	/** Runs every call on its own thread, released together, and returns the HTTP statuses. */
	private List<Integer> race(List<Callable<Integer>> calls) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(calls.size());
		try {
			CountDownLatch go = new CountDownLatch(1);
			List<Future<Integer>> futures = new ArrayList<>();
			for (Callable<Integer> call : calls) {
				futures.add(pool.submit(() -> {
					go.await();
					return call.call();
				}));
			}
			go.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> f : futures) {
				statuses.add(f.get(20, TimeUnit.SECONDS));
			}
			return statuses;
		}
		finally {
			pool.shutdownNow();
		}
	}

	private int accept(String suggestionId) throws Exception {
		return mvc.perform(patch("/suggestions/" + suggestionId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ACCEPTED\"}")).andReturn().getResponse().getStatus();
	}

	@RepeatedTest(5)
	void differentSuggestionsForOneSlotHaveExactlyOneWinner() throws Exception {
		Provider from = fx.provider(Specialty.GENERAL, ProviderStatus.OFFLINE);
		Provider to = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot target = fx.openSlot(to, 90);
		List<Callable<Integer>> calls = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			Appointment apt = fx.appointment(from, AppointmentType.ROUTINE, 60 + i);
			RescheduleSuggestion s = fx.pendingSuggestion(apt, target);
			calls.add(() -> accept(s.getId()));
		}

		List<Integer> statuses = race(calls);

		assertThat(statuses).filteredOn(c -> c == 200).hasSize(1);
		assertThat(statuses).filteredOn(c -> c == 409).hasSize(THREADS - 1);
	}

	@RepeatedTest(5)
	void theSameSuggestionAcceptedTwiceAtOnceSucceedsOnce() throws Exception {
		Provider from = fx.provider(Specialty.GENERAL, ProviderStatus.OFFLINE);
		Provider to = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Appointment apt = fx.appointment(from, AppointmentType.ROUTINE, 60);
		RescheduleSuggestion s = fx.pendingSuggestion(apt, fx.openSlot(to, 90));
		List<Callable<Integer>> calls = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			calls.add(() -> accept(s.getId()));
		}

		List<Integer> statuses = race(calls);

		assertThat(statuses).filteredOn(c -> c == 200).hasSize(1);
		assertThat(statuses).filteredOn(c -> c == 409).hasSize(THREADS - 1);
	}

	@RepeatedTest(5)
	void manyBookingsOfOneSlotHaveExactlyOneWinner() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot slot = fx.openSlot(p, 60);
		List<Callable<Integer>> calls = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			String body = "{\"patientName\":\"Racer " + i + "\",\"type\":\"ROUTINE\",\"providerId\":\"" + p.getId()
					+ "\",\"slotId\":\"" + slot.getId() + "\"}";
			calls.add(() -> mvc.perform(post("/appointments").contentType(MediaType.APPLICATION_JSON).content(body))
					.andReturn().getResponse().getStatus());
		}

		List<Integer> statuses = race(calls);

		assertThat(statuses).filteredOn(c -> c == 201).hasSize(1);
		assertThat(statuses).filteredOn(c -> c == 409).hasSize(THREADS - 1);
	}

}

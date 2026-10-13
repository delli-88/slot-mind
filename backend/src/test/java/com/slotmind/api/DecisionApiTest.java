package com.slotmind.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.slotmind.Fixtures;
import com.slotmind.IntegrationTest;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentRepository;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderRepository;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotRepository;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.Specialty;
import com.slotmind.domain.SuggestionRepository;
import com.slotmind.domain.SuggestionStatus;

@IntegrationTest
class DecisionApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	Fixtures fx;

	@Autowired
	AppointmentRepository appointments;

	@Autowired
	SlotRepository slots;

	@Autowired
	SuggestionRepository suggestions;

	@Autowired
	ProviderRepository providers;

	private ResultActions decide(String id, String status) throws Exception {
		return mvc.perform(patch("/suggestions/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"" + status + "\"}"));
	}

	/** An appointment on an offline provider with a PENDING suggestion pointing at an open slot. */
	private record World(Appointment appointment, Slot oldSlot, Slot target, RescheduleSuggestion suggestion) {
	}

	private World world(Slot sharedTarget) {
		Provider offline = fx.provider(Specialty.GENERAL, ProviderStatus.OFFLINE);
		Slot target = sharedTarget != null ? sharedTarget
				: fx.openSlot(fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE), 150);
		Appointment a = fx.appointment(offline, AppointmentType.ROUTINE, 60);
		RescheduleSuggestion s = fx.pendingSuggestion(a, target);
		return new World(a, a.getSlot(), target, s);
	}

	@Test
	void acceptMovesAppointmentAndSwapsSlotsAtomically() throws Exception {
		World w = world(null);
		decide(w.suggestion().getId(), "ACCEPTED").andExpect(status().isOk())
				.andExpect(jsonPath("$.suggestion.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.suggestion.decidedAt").exists())
				.andExpect(jsonPath("$.appointment.status").value("RESCHEDULED"))
				.andExpect(jsonPath("$.appointment.slot.id").value(w.target().getId()))
				.andExpect(jsonPath("$.appointment.provider.id").value(w.target().getProvider().getId()))
				.andExpect(jsonPath("$.appointment.pendingSuggestion").doesNotExist());
		assertThat(slots.findById(w.oldSlot().getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
		assertThat(slots.findById(w.target().getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
	}

	@Test
	void rejectKeepsAppointmentPendingAndTouchesNoSlots() throws Exception {
		World w = world(null);
		decide(w.suggestion().getId(), "REJECTED").andExpect(status().isOk())
				.andExpect(jsonPath("$.suggestion.status").value("REJECTED"))
				.andExpect(jsonPath("$.appointment.status").value("RESCHEDULE_PENDING"));
		assertThat(slots.findById(w.oldSlot().getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
		assertThat(slots.findById(w.target().getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
	}

	@Test
	void decidingTwiceIs409() throws Exception {
		World w = world(null);
		decide(w.suggestion().getId(), "ACCEPTED").andExpect(status().isOk());
		decide(w.suggestion().getId(), "ACCEPTED").andExpect(status().isConflict())
				.andExpect(jsonPath("$.details[0]").value(startsWith("SUGGESTION_NOT_PENDING")));
		decide(w.suggestion().getId(), "REJECTED").andExpect(status().isConflict());
	}

	@Test
	void slotTakenSinceIs409AndNothingChanges() throws Exception {
		World w = world(null);
		fx.appointmentOn(w.target().getProvider(), w.target(), AppointmentType.ROUTINE);
		decide(w.suggestion().getId(), "ACCEPTED").andExpect(status().isConflict())
				.andExpect(jsonPath("$.details[0]").value(startsWith("SLOT_NOT_OPEN")));
		assertThat(appointments.findById(w.appointment().getId()).orElseThrow().getStatus())
				.isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
		assertThat(suggestions.findById(w.suggestion().getId()).orElseThrow().getStatus())
				.isEqualTo(SuggestionStatus.PENDING);
		assertThat(slots.findById(w.oldSlot().getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
	}

	@Test
	void targetProviderWentOfflineIs409() throws Exception {
		World w = world(null);
		Provider target = providers.findById(w.target().getProvider().getId()).orElseThrow();
		target.changeStatus(ProviderStatus.OFFLINE);
		providers.save(target);
		decide(w.suggestion().getId(), "ACCEPTED").andExpect(status().isConflict())
				.andExpect(jsonPath("$.details[0]").value(startsWith("PROVIDER_OFFLINE")));
	}

	@Test
	void appointmentNoLongerPendingIs409() throws Exception {
		World w = world(null);
		Appointment a = appointments.findById(w.appointment().getId()).orElseThrow();
		a.reschedule(w.oldSlot());
		appointments.save(a);
		decide(w.suggestion().getId(), "ACCEPTED").andExpect(status().isConflict())
				.andExpect(jsonPath("$.details[0]").value(startsWith("APPOINTMENT_NOT_PENDING")));
	}

	@Test
	void secondAppointmentOfferedTheSameSlotLosesWithA409() throws Exception {
		Slot shared = fx.openSlot(fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE), 150);
		World first = world(shared);
		World second = world(shared);
		decide(first.suggestion().getId(), "ACCEPTED").andExpect(status().isOk());
		decide(second.suggestion().getId(), "ACCEPTED").andExpect(status().isConflict())
				.andExpect(jsonPath("$.details[0]").value(startsWith("SLOT_NOT_OPEN")));
		assertThat(appointments.findById(second.appointment().getId()).orElseThrow().getStatus())
				.isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
	}

	@Test
	void invalidBodyAndUnknownSuggestion() throws Exception {
		World w = world(null);
		decide(w.suggestion().getId(), "PENDING").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details[0]").value("status: must be ACCEPTED or REJECTED"));
		decide(w.suggestion().getId(), "MAYBE").andExpect(status().isBadRequest());
		mvc.perform(patch("/suggestions/" + w.suggestion().getId()).contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isBadRequest());
		decide("SUG-999", "ACCEPTED").andExpect(status().isNotFound());
	}

	@Test
	void concurrentAcceptsOfOneSlotLetExactlyOneWin() throws Exception {
		int contenders = 6;
		Slot shared = fx.openSlot(fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE), 150);
		List<World> worlds = new ArrayList<>();
		for (int i = 0; i < contenders; i++) {
			worlds.add(world(shared));
		}
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(contenders);
		List<Future<Integer>> results = new ArrayList<>();
		for (World w : worlds) {
			results.add(pool.submit(() -> {
				start.await();
				return decide(w.suggestion().getId(), "ACCEPTED").andReturn().getResponse().getStatus();
			}));
		}
		start.countDown();
		List<Integer> codes = new ArrayList<>();
		for (Future<Integer> f : results) {
			codes.add(f.get());
		}
		pool.shutdown();

		assertThat(codes).filteredOn(c -> c == 200).hasSize(1);
		assertThat(codes).filteredOn(c -> c == 409).hasSize(contenders - 1);
		assertThat(slots.findById(shared.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
		long rescheduled = worlds.stream().map(w -> appointments.findById(w.appointment().getId()).orElseThrow())
				.filter(a -> a.getStatus() == AppointmentStatus.RESCHEDULED).count();
		assertThat(rescheduled).isEqualTo(1);
	}

}

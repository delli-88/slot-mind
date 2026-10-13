package com.slotmind.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.Fixtures;
import com.slotmind.IntegrationTest;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.Specialty;

@IntegrationTest
class ListAppointmentsTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	Fixtures fx;

	@Autowired
	ObjectMapper json;

	@Test
	void statusFilterReturnsOnlyThatStatus() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Appointment confirmed = fx.appointment(p, AppointmentType.ROUTINE, 60);
		Appointment pending = fx.appointment(p, AppointmentType.ROUTINE, 90);
		Slot target = fx.openSlot(p, 120);
		fx.pendingSuggestion(pending, target);

		mvc.perform(get("/appointments").param("status", "RESCHEDULE_PENDING")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].status", everyItem(is("RESCHEDULE_PENDING"))))
				.andExpect(jsonPath("$.items[*].id", hasItem(pending.getId())));
		mvc.perform(get("/appointments").param("status", "CONFIRMED"))
				.andExpect(jsonPath("$.items[*].id", hasItem(confirmed.getId())));
	}

	@Test
	void embedsThePendingSuggestion() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Appointment a = fx.appointment(p, AppointmentType.ROUTINE, 60);
		Slot target = fx.openSlot(fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE), 120);
		RescheduleSuggestion s = fx.pendingSuggestion(a, target);

		JsonNode item = find(a.getId());
		org.assertj.core.api.Assertions.assertThat(item.at("/pendingSuggestion/id").asText()).isEqualTo(s.getId());
		org.assertj.core.api.Assertions.assertThat(item.at("/pendingSuggestion/recommendedSlot/id").asText())
				.isEqualTo(target.getId());
		org.assertj.core.api.Assertions.assertThat(item.at("/pendingSuggestion/alternateProvider/id").asText())
				.isEqualTo(target.getProvider().getId());
	}

	@Test
	void sortedBySlotStartAndSizeIsCapped() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		fx.appointment(p, AppointmentType.ROUTINE, 300);
		fx.appointment(p, AppointmentType.ROUTINE, 100);
		fx.appointment(p, AppointmentType.ROUTINE, 200);

		String body = mvc.perform(get("/appointments").param("size", "500")).andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(50)).andReturn().getResponse().getContentAsString();
		Instant previous = Instant.MIN;
		for (JsonNode item : json.readTree(body).get("items")) {
			Instant t = Instant.parse(item.at("/slot/startTime").asText());
			org.assertj.core.api.Assertions.assertThat(t).isAfterOrEqualTo(previous);
			previous = t;
		}
	}

	@Test
	void defaultsAreFirstPageOf20() throws Exception {
		mvc.perform(get("/appointments")).andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void badParametersAre400() throws Exception {
		mvc.perform(get("/appointments").param("status", "NOPE")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details[0]").value(
						"status: must be one of CONFIRMED, RESCHEDULE_PENDING, RESCHEDULED, COMPLETED"));
		mvc.perform(get("/appointments").param("size", "0")).andExpect(status().isBadRequest());
		mvc.perform(get("/appointments").param("page", "-1")).andExpect(status().isBadRequest());
	}

	@Test
	void getByIdAndUnknownId() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Appointment a = fx.appointment(p, AppointmentType.URGENT, 60);
		mvc.perform(get("/appointments/" + a.getId())).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(a.getId())).andExpect(jsonPath("$.type").value("URGENT"));
		mvc.perform(get("/appointments/APT-999")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Appointment APT-999 not found"));
	}

	private JsonNode find(String id) throws Exception {
		String body = mvc.perform(get("/appointments").param("size", "50").param("status", "RESCHEDULE_PENDING"))
				.andReturn().getResponse().getContentAsString();
		for (JsonNode n : json.readTree(body).get("items")) {
			if (n.get("id").asText().equals(id)) {
				return n;
			}
		}
		throw new AssertionError(id + " not in first page");
	}

}

package com.slotmind.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Specialty;

@IntegrationTest
class ProviderStatusTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	Fixtures fx;

	@Autowired
	AppointmentRepository appointments;

	private ResultActions set(String id, String json) throws Exception {
		return mvc.perform(patch("/providers/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	@Test
	void offlineStrandsConfirmedAppointmentsOnly() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Appointment a1 = fx.appointment(p, AppointmentType.ROUTINE, 60);
		Appointment a2 = fx.appointment(p, AppointmentType.ROUTINE, 90);
		Appointment done = fx.appointment(p, AppointmentType.ROUTINE, 120);
		done.complete();
		appointments.save(done);

		String[] expected = { a1.getId(), a2.getId() };
		java.util.Arrays.sort(expected);
		set(p.getId(), "{\"status\":\"OFFLINE\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.provider.status").value("OFFLINE"))
				.andExpect(jsonPath("$.affectedAppointmentIds[0]").value(expected[0]))
				.andExpect(jsonPath("$.affectedAppointmentIds[1]").value(expected[1]))
				.andExpect(jsonPath("$.affectedAppointmentIds.length()").value(2))
				.andExpect(jsonPath("$.rescheduleQueued").value(true));

		assertThat(appointments.findById(a1.getId()).orElseThrow().getStatus())
				.isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
		assertThat(appointments.findById(done.getId()).orElseThrow().getStatus())
				.isEqualTo(AppointmentStatus.COMPLETED);
	}

	@Test
	void sameStatusIsANoOp() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.OFFLINE);
		fx.appointment(p, AppointmentType.ROUTINE, 60);
		set(p.getId(), "{\"status\":\"OFFLINE\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.affectedAppointmentIds.length()").value(0))
				.andExpect(jsonPath("$.rescheduleQueued").value(false));
	}

	@Test
	void backOnlineDoesNotTouchAppointments() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Appointment a = fx.appointment(p, AppointmentType.ROUTINE, 60);
		set(p.getId(), "{\"status\":\"BUSY\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.provider.status").value("BUSY"))
				.andExpect(jsonPath("$.rescheduleQueued").value(false));
		assertThat(appointments.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
	}

	@Test
	void badInputIs400AndUnknownProviderIs404() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		set(p.getId(), "{}").andExpect(status().isBadRequest());
		set(p.getId(), "{\"status\":\"SLEEPING\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details[0]").value("status: must be one of AVAILABLE, BUSY, OFFLINE"));
		set("PRV-999", "{\"status\":\"OFFLINE\"}").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Provider PRV-999 not found"));
	}

}

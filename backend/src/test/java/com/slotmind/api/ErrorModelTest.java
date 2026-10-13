package com.slotmind.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.slotmind.IntegrationTest;

@IntegrationTest
class ErrorModelTest {

	@Autowired
	MockMvc mvc;

	@Test
	void validationErrorListsFields() throws Exception {
		mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.path").value("/probe/body"))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.details[0]").value("name: must not be blank"));
	}

	@Test
	void malformedJsonIs400() throws Exception {
		mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{nope"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.details").isArray());
	}

	@Test
	void badEnumParamNamesTheAllowedValues() throws Exception {
		mvc.perform(get("/probe/enum").param("specialty", "WIZARD"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details[0]").value("specialty: must be one of GENERAL, SPECIALIST, PEDIATRIC"));
	}

	@Test
	void notFoundIs404() throws Exception {
		mvc.perform(get("/probe/not-found")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Slot SLT-999 not found"))
				.andExpect(jsonPath("$.details").isEmpty());
	}

	@Test
	void unknownRouteIs404InTheSameShape() throws Exception {
		mvc.perform(get("/does-not-exist")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.path").value("/does-not-exist"));
	}

	@Test
	void conflictCarriesStableCode() throws Exception {
		mvc.perform(get("/probe/conflict")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.details[0]").value("SLOT_NOT_OPEN: SLT-001 is BOOKED"));
	}

	@Test
	void illegalTransitionIs409() throws Exception {
		mvc.perform(get("/probe/transition")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.details[0]").value(containsString("ILLEGAL_TRANSITION")));
	}

	@Test
	void unprocessableIs422() throws Exception {
		mvc.perform(get("/probe/unprocessable")).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.details[0]").value("NO_CANDIDATE_SLOT"));
	}

	@Test
	void unexpectedErrorHidesInternals() throws Exception {
		mvc.perform(get("/probe/boom")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value("Unexpected error"))
				.andExpect(content().string(not(containsString("secret internal detail"))));
	}

}

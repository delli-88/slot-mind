package com.slotmind;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import java.time.Duration;
import java.util.function.Predicate;

import org.awaitility.Awaitility;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Helpers for tests of the asynchronous loop: poll the public API the way the UI does. */
public final class LoopSupport {

	private LoopSupport() {
	}

	public static JsonNode appointment(MockMvc mvc, ObjectMapper json, String id) throws Exception {
		return json.readTree(mvc.perform(get("/appointments/" + id)).andReturn().getResponse().getContentAsString());
	}

	/** Polls GET /appointments/{id} until the condition holds (10 s at most) and returns that appointment. */
	public static JsonNode waitFor(MockMvc mvc, ObjectMapper json, String id, Predicate<JsonNode> condition) {
		JsonNode[] last = new JsonNode[1];
		Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).until(() -> {
			last[0] = appointment(mvc, json, id);
			return condition.test(last[0]);
		});
		return last[0];
	}

	public static JsonNode waitForSuggestion(MockMvc mvc, ObjectMapper json, String id) {
		return waitFor(mvc, json, id, a -> !a.path("pendingSuggestion").isMissingNode() && !a.path("pendingSuggestion").isNull());
	}

	public static ResultActions setProvider(MockMvc mvc, String providerId, String status) throws Exception {
		return mvc.perform(patch("/providers/" + providerId + "/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"" + status + "\"}"));
	}

}

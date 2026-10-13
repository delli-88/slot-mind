package com.slotmind.ai;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;

/**
 * The only class that talks to Claude (official anthropic-java SDK). No sampling parameters (current models reject
 * them), no retries (the caller owns the time budget), a hard per-request timeout, and the key is never logged.
 */
@Component
public class LLMGateway implements LlmClient {

	private static final Logger log = LoggerFactory.getLogger(LLMGateway.class);

	private final LlmProperties props;

	private volatile AnthropicClient client;

	public LLMGateway(LlmProperties props) {
		this.props = props;
		if (!props.hasApiKey()) {
			log.warn("LLM_API_KEY is not set: AI suggestions will fall back to the rules (NO_API_KEY)");
		}
	}

	@Override
	public String callLLM(String system, String user, Duration timeout) {
		if (!props.hasApiKey()) {
			throw new LlmException(FallbackCause.NO_API_KEY, "LLM_API_KEY is not set", null);
		}
		MessageCreateParams params = MessageCreateParams.builder().model(props.model()).maxTokens(props.maxTokens())
				.system(system).addUserMessage(user)
				.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(props.effort())).build()).build();
		Message reply;
		try {
			reply = client().messages().create(params, RequestOptions.builder().timeout(timeout).build());
		}
		catch (AnthropicIoException ex) {
			throw new LlmException(isTimeout(ex) ? FallbackCause.TIMEOUT : FallbackCause.GATEWAY_ERROR,
					"model call failed: " + ex.getClass().getSimpleName(), ex);
		}
		catch (AnthropicException ex) {
			throw new LlmException(FallbackCause.GATEWAY_ERROR, "model call failed: " + ex.getClass().getSimpleName(),
					ex);
		}
		if (reply.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
			throw new LlmException(FallbackCause.REFUSED, "model refused", null);
		}
		if (reply.stopReason().map(StopReason.MAX_TOKENS::equals).orElse(false)) {
			throw new LlmException(FallbackCause.PARSE_ERROR, "reply cut off at max_tokens", null);
		}
		StringBuilder text = new StringBuilder();
		reply.content().forEach(block -> block.text().ifPresent(t -> text.append(t.text())));
		return text.toString();
	}

	private static boolean isTimeout(Throwable ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t instanceof SocketTimeoutException || t instanceof InterruptedIOException) {
				return true;
			}
		}
		return false;
	}

	private AnthropicClient client() {
		AnthropicClient c = client;
		if (c == null) {
			synchronized (this) {
				if (client == null) {
					client = AnthropicOkHttpClient.builder().apiKey(props.apiKey()).maxRetries(0)
							.timeout(props.timeout()).build();
				}
				c = client;
			}
		}
		return c;
	}

}

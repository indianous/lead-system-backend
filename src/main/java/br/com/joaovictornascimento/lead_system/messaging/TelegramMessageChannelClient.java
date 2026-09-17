package br.com.joaovictornascimento.lead_system.messaging;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envia mensagens via Telegram Bot API — POST bot{token}/sendMessage. Sem suporte a
 * confirmação de entrega/leitura: a Bot API não expõe esses eventos (diferente do WhatsApp),
 * mensagens enviadas ficam em SENT (ou FAILED) e nunca avançam para DELIVERED/READ.
 */
@Component
public class TelegramMessageChannelClient implements MessageChannelClient {

	private final RestClient restClient;

	private final String botToken;

	public TelegramMessageChannelClient(@Value("${app.messaging.telegram.api-base-url}") String apiBaseUrl,
			@Value("${app.messaging.telegram.bot-token}") String botToken) {
		this.botToken = botToken;
		this.restClient = RestClient.builder()
			.baseUrl(apiBaseUrl)
			.requestFactory(new SimpleClientHttpRequestFactory())
			.build();
	}

	@Override
	@SuppressWarnings("unchecked")
	public SendMessageResult send(String externalThreadId, String content) {
		try {
			Map<String, Object> response = restClient.post()
				.uri("/bot{token}/sendMessage", botToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("chat_id", externalThreadId, "text", content))
				.retrieve()
				.body(Map.class);

			boolean ok = response != null && Boolean.TRUE.equals(response.get("ok"));
			if (!ok) {
				return SendMessageResult.failure(String.valueOf(response));
			}
			Map<String, Object> result = (Map<String, Object>) response.get("result");
			String messageId = result != null && result.get("message_id") != null
					? String.valueOf(result.get("message_id")) : null;
			return SendMessageResult.success(messageId);
		}
		catch (RestClientResponseException e) {
			return SendMessageResult.failure(e.getResponseBodyAsString());
		}
		catch (RestClientException e) {
			return SendMessageResult.failure(e.getMessage());
		}
	}

}

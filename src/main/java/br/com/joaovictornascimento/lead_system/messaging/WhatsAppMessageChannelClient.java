package br.com.joaovictornascimento.lead_system.messaging;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envia mensagens via WhatsApp Business Platform (Cloud API) — POST
 * {phone-number-id}/messages. Sem suporte a template message (fora da janela de 24h a Meta
 * rejeita a chamada; ver plano da Etapa 8) — mensagem vira FAILED nesse caso.
 */
@Component
public class WhatsAppMessageChannelClient implements MessageChannelClient {

	private final RestClient restClient;

	private final String phoneNumberId;

	public WhatsAppMessageChannelClient(@Value("${app.messaging.whatsapp.api-base-url}") String apiBaseUrl,
			@Value("${app.messaging.whatsapp.phone-number-id}") String phoneNumberId,
			@Value("${app.messaging.whatsapp.access-token}") String accessToken) {
		this.phoneNumberId = phoneNumberId;
		this.restClient = RestClient.builder()
			.baseUrl(apiBaseUrl)
			.requestFactory(new SimpleClientHttpRequestFactory())
			.defaultHeader("Authorization", "Bearer " + accessToken)
			.build();
	}

	@Override
	@SuppressWarnings("unchecked")
	public SendMessageResult send(String externalThreadId, String content) {
		try {
			Map<String, Object> response = restClient.post()
				.uri("/{phoneNumberId}/messages", phoneNumberId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("messaging_product", "whatsapp", "to", externalThreadId, "type", "text", "text",
						Map.of("body", content)))
				.retrieve()
				.body(Map.class);

			List<Map<String, Object>> messages = response != null ? (List<Map<String, Object>>) response.get("messages")
					: null;
			String externalMessageId = messages != null && !messages.isEmpty() ? (String) messages.get(0).get("id")
					: null;
			return SendMessageResult.success(externalMessageId);
		}
		catch (RestClientResponseException e) {
			return SendMessageResult.failure(e.getResponseBodyAsString());
		}
		catch (RestClientException e) {
			return SendMessageResult.failure(e.getMessage());
		}
	}

}

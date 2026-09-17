package br.com.joaovictornascimento.lead_system.messaging;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;

@WireMockTest
class TelegramMessageChannelClientTest {

	@Test
	void sendsMessageAndReturnsExternalMessageId(WireMockRuntimeInfo wireMock) {
		stubFor(post(urlPathEqualTo("/bottest-bot-token/sendMessage")).willReturn(aResponse().withStatus(200)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"ok\":true,\"result\":{\"message_id\":42}}")));

		TelegramMessageChannelClient client = new TelegramMessageChannelClient(wireMock.getHttpBaseUrl(),
				"test-bot-token");

		SendMessageResult result = client.send("987654321", "Olá!");

		assertThat(result.success()).isTrue();
		assertThat(result.externalMessageId()).isEqualTo("42");
	}

	@Test
	void mapsNotOkResponseToFailure(WireMockRuntimeInfo wireMock) {
		stubFor(post(urlPathEqualTo("/bottest-bot-token/sendMessage")).willReturn(aResponse().withStatus(200)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"ok\":false,\"description\":\"Forbidden: bot was blocked by the user\"}")));

		TelegramMessageChannelClient client = new TelegramMessageChannelClient(wireMock.getHttpBaseUrl(),
				"test-bot-token");

		SendMessageResult result = client.send("987654321", "Olá!");

		assertThat(result.success()).isFalse();
	}

}

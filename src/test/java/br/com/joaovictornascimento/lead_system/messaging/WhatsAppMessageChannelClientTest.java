package br.com.joaovictornascimento.lead_system.messaging;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;

@WireMockTest
class WhatsAppMessageChannelClientTest {

	@Test
	void sendsMessageAndReturnsExternalMessageId(WireMockRuntimeInfo wireMock) {
		stubFor(post(urlPathEqualTo("/123456789/messages")).willReturn(aResponse().withStatus(200)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"messages\":[{\"id\":\"wamid.ABC123\"}]}")));

		WhatsAppMessageChannelClient client = new WhatsAppMessageChannelClient(wireMock.getHttpBaseUrl(), "123456789",
				"test-access-token");

		SendMessageResult result = client.send("5511999999999", "Olá!");

		assertThat(result.success()).isTrue();
		assertThat(result.externalMessageId()).isEqualTo("wamid.ABC123");
		verify(postRequestedFor(urlPathEqualTo("/123456789/messages"))
			.withHeader("Authorization", equalTo("Bearer test-access-token")));
	}

	@Test
	void mapsHttpErrorToFailure(WireMockRuntimeInfo wireMock) {
		stubFor(post(urlPathEqualTo("/123456789/messages")).willReturn(aResponse().withStatus(400)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\":{\"message\":\"outside 24h window\"}}")));

		WhatsAppMessageChannelClient client = new WhatsAppMessageChannelClient(wireMock.getHttpBaseUrl(), "123456789",
				"test-access-token");

		SendMessageResult result = client.send("5511999999999", "Olá!");

		assertThat(result.success()).isFalse();
		assertThat(result.errorMessage()).contains("outside 24h window");
	}

}

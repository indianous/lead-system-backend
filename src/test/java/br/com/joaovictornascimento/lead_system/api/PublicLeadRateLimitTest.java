package br.com.joaovictornascimento.lead_system.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.joaovictornascimento.lead_system.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Classe isolada com um limite baixo (via {@code @TestPropertySource}), para não compartilhar o
 * balde de rate limiting com {@link PublicLeadControllerTest} nem com o resto da suíte — Spring
 * Boot Test cria um contexto (e um RateLimitFilter novo) separado por ter propriedades diferentes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = { "app.public-api.rate-limit.capacity=2", "app.public-api.rate-limit.refill-tokens=2",
		"app.public-api.rate-limit.refill-duration-seconds=60" })
class PublicLeadRateLimitTest {

	@Autowired
	private MockMvc mockMvc;

	@Value("${app.public-api.key}")
	private String apiKey;

	private static final String VALID_PAYLOAD = "{\"name\":\"Cliente do site\",\"phone\":\"11999999999\"}";

	@Test
	void exceedingTheConfiguredLimitReturnsTooManyRequests() throws Exception {
		mockMvc
			.perform(post("/api/public/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.content(VALID_PAYLOAD))
			.andExpect(status().isCreated());

		mockMvc
			.perform(post("/api/public/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.content(VALID_PAYLOAD))
			.andExpect(status().isCreated());

		mockMvc
			.perform(post("/api/public/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.content(VALID_PAYLOAD))
			.andExpect(status().isTooManyRequests());
	}

}

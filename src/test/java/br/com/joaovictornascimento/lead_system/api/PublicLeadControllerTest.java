package br.com.joaovictornascimento.lead_system.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.joaovictornascimento.lead_system.config.TestcontainersConfiguration;
import br.com.joaovictornascimento.lead_system.domain.CaptureMethod;
import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.FunnelStatusHistory;
import br.com.joaovictornascimento.lead_system.domain.FunnelStatusHistoryRepository;
import br.com.joaovictornascimento.lead_system.domain.Lead;
import br.com.joaovictornascimento.lead_system.domain.LeadRepository;
import br.com.joaovictornascimento.lead_system.domain.LeadType;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PublicLeadControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private LeadRepository leadRepository;

	@Autowired
	private FunnelStatusHistoryRepository funnelStatusHistoryRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${app.public-api.key}")
	private String apiKey;

	@Value("${app.public-api.default-assignee-email}")
	private String defaultAssigneeEmail;

	private static String publicLeadJson(String name, String phone, String email) {
		return "{\"name\":\"%s\",\"phone\":%s,\"email\":%s,\"initialMessage\":\"Quero um site\"}"
			.formatted(name, phone == null ? "null" : "\"" + phone + "\"", email == null ? "null" : "\"" + email + "\"");
	}

	@Test
	void createPublicLeadWithoutApiKeyReturnsUnauthorized() throws Exception {
		mockMvc
			.perform(post("/api/public/leads").contentType("application/json")
				.content(publicLeadJson("Cliente do site", "11999999999", null)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createPublicLeadWithWrongApiKeyReturnsUnauthorized() throws Exception {
		mockMvc
			.perform(post("/api/public/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, "Bearer chave-errada")
				.content(publicLeadJson("Cliente do site", "11999999999", null)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createPublicLeadWithBlankNameReturnsBadRequest() throws Exception {
		mockMvc
			.perform(post("/api/public/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.content(publicLeadJson("", "11999999999", null)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createPublicLeadWithoutPhoneOrEmailReturnsBadRequest() throws Exception {
		mockMvc
			.perform(post("/api/public/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.content(publicLeadJson("Cliente do site", null, null)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createPublicLeadWithValidApiKeyAndPayloadReturnsCreated() throws Exception {
		User defaultAssignee = userRepository.findByEmail(defaultAssigneeEmail).orElseThrow();

		String responseJson = mockMvc
			.perform(post("/api/public/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.content(publicLeadJson("Cliente do site", "11999999999", "cliente@example.com")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").exists())
			.andExpect(jsonPath("$.createdAt").exists())
			.andExpect(jsonPath("$.assignedUserId").doesNotExist())
			.andExpect(jsonPath("$.assignedUserName").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();

		Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);
		UUID leadId = UUID.fromString((String) response.get("id"));
		Lead lead = leadRepository.findById(leadId).orElseThrow();

		assertThat(lead.getLeadType()).isEqualTo(LeadType.DIRECT_CONTACT);
		assertThat(lead.getOrigin().getChannel()).isEqualTo(Channel.WEBSITE);
		assertThat(lead.getOrigin().getCaptureMethod()).isEqualTo(CaptureMethod.API);
		assertThat(lead.getFunnelStatus().name()).isEqualTo("NEW");
		assertThat(lead.getAssignedUser().getId()).isEqualTo(defaultAssignee.getId());

		List<FunnelStatusHistory> history = funnelStatusHistoryRepository.findByLeadIdOrderByChangedAtAsc(leadId);
		assertThat(history).hasSize(1);
		assertThat(history.get(0).getPreviousStatus()).isNull();
		assertThat(history.get(0).getNewStatus().name()).isEqualTo("NEW");
		assertThat(history.get(0).getUser().getId()).isEqualTo(defaultAssignee.getId());
	}

}

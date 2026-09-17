package br.com.joaovictornascimento.lead_system.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.joaovictornascimento.lead_system.config.TestcontainersConfiguration;
import br.com.joaovictornascimento.lead_system.domain.CaptureMethod;
import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.Conversation;
import br.com.joaovictornascimento.lead_system.domain.ConversationRepository;
import br.com.joaovictornascimento.lead_system.domain.Lead;
import br.com.joaovictornascimento.lead_system.domain.LeadOrigin;
import br.com.joaovictornascimento.lead_system.domain.LeadOriginRepository;
import br.com.joaovictornascimento.lead_system.domain.LeadRepository;
import br.com.joaovictornascimento.lead_system.domain.LeadType;
import br.com.joaovictornascimento.lead_system.domain.Message;
import br.com.joaovictornascimento.lead_system.domain.MessageDirection;
import br.com.joaovictornascimento.lead_system.domain.MessageRepository;
import br.com.joaovictornascimento.lead_system.domain.MessageStatus;
import br.com.joaovictornascimento.lead_system.domain.Role;
import br.com.joaovictornascimento.lead_system.domain.RoleRepository;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WhatsAppWebhookControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private LeadRepository leadRepository;

	@Autowired
	private LeadOriginRepository leadOriginRepository;

	@Autowired
	private ConversationRepository conversationRepository;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Value("${app.messaging.whatsapp.app-secret}")
	private String appSecret;

	@Value("${app.messaging.whatsapp.webhook-verify-token}")
	private String verifyToken;

	private String sign(String body) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
	}

	private User createUser(String name, String email, Role role) {
		return userRepository.save(new User(name, email, passwordEncoder.encode("somepassword1"), role, null));
	}

	private Lead createLead(User assignedUser, String phone) {
		LeadOrigin origin = leadOriginRepository
			.save(new LeadOrigin(LeadType.DIRECT_CONTACT, Channel.META_WHATSAPP, null, null, null, CaptureMethod.MANUAL));
		return leadRepository
			.save(new Lead("Lead existente", LeadType.DIRECT_CONTACT, phone, null, null, null, null, null, origin,
					assignedUser, null));
	}

	private static String inboundMessagePayload(String from, String externalMessageId, String text) {
		return """
				{"object":"whatsapp_business_account","entry":[{"id":"0","changes":[{"value":{
				"messages":[{"from":"%s","id":"%s","timestamp":"1234567890","type":"text","text":{"body":"%s"}}]
				},"field":"messages"}]}]}
				""".formatted(from, externalMessageId, text);
	}

	private static String statusUpdatePayload(String externalMessageId, String status) {
		return """
				{"object":"whatsapp_business_account","entry":[{"id":"0","changes":[{"value":{
				"statuses":[{"id":"%s","status":"%s","timestamp":"1234567890"}]
				},"field":"messages"}]}]}
				""".formatted(externalMessageId, status);
	}

	@Test
	void verifyWithCorrectTokenReturnsChallenge() throws Exception {
		mockMvc
			.perform(get("/api/webhooks/whatsapp").param("hub.mode", "subscribe")
				.param("hub.verify_token", verifyToken)
				.param("hub.challenge", "challenge-123"))
			.andExpect(status().isOk())
			.andExpect(content().string("challenge-123"));
	}

	@Test
	void verifyWithWrongTokenReturnsForbidden() throws Exception {
		mockMvc
			.perform(get("/api/webhooks/whatsapp").param("hub.mode", "subscribe")
				.param("hub.verify_token", "wrong-token")
				.param("hub.challenge", "challenge-123"))
			.andExpect(status().isForbidden());
	}

	@Test
	void receiveWithInvalidSignatureReturnsUnauthorized() throws Exception {
		String body = inboundMessagePayload("5511999999999", "wamid.INVALID_SIG", "Olá");

		mockMvc
			.perform(post("/api/webhooks/whatsapp").contentType("application/json")
				.header("X-Hub-Signature-256", "sha256=invalid")
				.content(body))
			.andExpect(status().isUnauthorized());

		assertThat(messageRepository.findByExternalMessageId("wamid.INVALID_SIG")).isEmpty();
	}

	@Test
	void receiveInboundMessageFromKnownLeadCreatesConversationAndMessage() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-whatsapp-webhook1@leadsystem.local", salesperson);
		Lead lead = createLead(ana, "5511988887777");
		String body = inboundMessagePayload("5511988887777", "wamid.INBOUND1", "Tenho interesse no produto");

		mockMvc
			.perform(post("/api/webhooks/whatsapp").contentType("application/json")
				.header("X-Hub-Signature-256", sign(body))
				.content(body))
			.andExpect(status().isOk());

		Conversation conversation = conversationRepository.findByChannelAndExternalThreadId(Channel.META_WHATSAPP, "5511988887777")
			.orElseThrow();
		assertThat(conversation.getLead().getId()).isEqualTo(lead.getId());

		List<Message> messages = messageRepository.findByConversationIdOrderBySentAtAsc(conversation.getId());
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0).getDirection()).isEqualTo(MessageDirection.INBOUND);
		assertThat(messages.get(0).getContent()).isEqualTo("Tenho interesse no produto");
	}

	@Test
	void receiveInboundMessageFromUnknownPhoneIsIgnoredButAcknowledged() throws Exception {
		String body = inboundMessagePayload("5500000000000", "wamid.UNKNOWN1", "Oi");

		mockMvc
			.perform(post("/api/webhooks/whatsapp").contentType("application/json")
				.header("X-Hub-Signature-256", sign(body))
				.content(body))
			.andExpect(status().isOk());

		assertThat(messageRepository.findByExternalMessageId("wamid.UNKNOWN1")).isEmpty();
	}

	@Test
	void receivingTheSameMessageTwiceIsIdempotent() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-whatsapp-webhook2@leadsystem.local", salesperson);
		createLead(ana, "5511977776666");
		String body = inboundMessagePayload("5511977776666", "wamid.DUPLICATE1", "Mensagem repetida");

		mockMvc
			.perform(post("/api/webhooks/whatsapp").contentType("application/json")
				.header("X-Hub-Signature-256", sign(body))
				.content(body))
			.andExpect(status().isOk());
		mockMvc
			.perform(post("/api/webhooks/whatsapp").contentType("application/json")
				.header("X-Hub-Signature-256", sign(body))
				.content(body))
			.andExpect(status().isOk());

		Conversation conversation = conversationRepository
			.findByChannelAndExternalThreadId(Channel.META_WHATSAPP, "5511977776666")
			.orElseThrow();
		assertThat(messageRepository.findByConversationIdOrderBySentAtAsc(conversation.getId())).hasSize(1);
	}

	@Test
	void receiveStatusUpdateUpdatesExistingOutboundMessage() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-whatsapp-webhook3@leadsystem.local", salesperson);
		Lead lead = createLead(ana, "5511966665555");
		Conversation conversation = conversationRepository
			.save(new Conversation(lead, Channel.META_WHATSAPP, "5511966665555"));
		Message outbound = messageRepository.save(new Message(conversation, MessageDirection.OUTBOUND, ana,
				"Mensagem enviada", null, "wamid.OUTBOUND1", MessageStatus.SENT));

		String body = statusUpdatePayload("wamid.OUTBOUND1", "delivered");

		mockMvc
			.perform(post("/api/webhooks/whatsapp").contentType("application/json")
				.header("X-Hub-Signature-256", sign(body))
				.content(body))
			.andExpect(status().isOk());

		Message updated = messageRepository.findById(outbound.getId()).orElseThrow();
		assertThat(updated.getStatus()).isEqualTo(MessageStatus.DELIVERED);
	}

}

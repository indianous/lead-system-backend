package br.com.joaovictornascimento.lead_system.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import br.com.joaovictornascimento.lead_system.domain.Role;
import br.com.joaovictornascimento.lead_system.domain.RoleRepository;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import java.util.List;
import java.util.UUID;
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
class TelegramWebhookControllerTest {

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

	@Value("${app.messaging.telegram.webhook-secret-token}")
	private String secretToken;

	private User createUser(String name, String email, Role role) {
		return userRepository.save(new User(name, email, passwordEncoder.encode("somepassword1"), role, null));
	}

	private Lead createLead(User assignedUser) {
		LeadOrigin origin = leadOriginRepository
			.save(new LeadOrigin(LeadType.DIRECT_CONTACT, Channel.TELEGRAM, null, null, null, CaptureMethod.MANUAL));
		return leadRepository
			.save(new Lead("Lead existente", LeadType.DIRECT_CONTACT, null, null, null, null, null, null, origin,
					assignedUser, null));
	}

	private static String updatePayload(long updateId, long chatId, long messageId, String text) {
		return """
				{"update_id":%d,"message":{"message_id":%d,"chat":{"id":%d,"type":"private"},"date":123,"text":"%s"}}
				""".formatted(updateId, messageId, chatId, text);
	}

	@Test
	void receiveWithoutCorrectSecretTokenReturnsUnauthorized() throws Exception {
		mockMvc
			.perform(post("/api/webhooks/telegram").contentType("application/json")
				.header("X-Telegram-Bot-Api-Secret-Token", "wrong-token")
				.content(updatePayload(1, 555, 1, "oi")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void startCommandWithKnownLeadCreatesConversationWithoutMessage() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-telegram-webhook1@leadsystem.local", salesperson);
		Lead lead = createLead(ana);

		mockMvc
			.perform(post("/api/webhooks/telegram").contentType("application/json")
				.header("X-Telegram-Bot-Api-Secret-Token", secretToken)
				.content(updatePayload(1, 111222, 1, "/start " + lead.getId())))
			.andExpect(status().isOk());

		Conversation conversation = conversationRepository.findByChannelAndExternalThreadId(Channel.TELEGRAM, "111222")
			.orElseThrow();
		assertThat(conversation.getLead().getId()).isEqualTo(lead.getId());
		assertThat(messageRepository.findByConversationIdOrderBySentAtAsc(conversation.getId())).isEmpty();
	}

	@Test
	void startCommandWithUnknownLeadIsIgnoredButAcknowledged() throws Exception {
		mockMvc
			.perform(post("/api/webhooks/telegram").contentType("application/json")
				.header("X-Telegram-Bot-Api-Secret-Token", secretToken)
				.content(updatePayload(2, 333444, 1, "/start " + UUID.randomUUID())))
			.andExpect(status().isOk());

		assertThat(conversationRepository.findByChannelAndExternalThreadId(Channel.TELEGRAM, "333444")).isEmpty();
	}

	@Test
	void regularMessageOnExistingConversationCreatesInboundMessage() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-telegram-webhook2@leadsystem.local", salesperson);
		Lead lead = createLead(ana);
		Conversation conversation = conversationRepository.save(new Conversation(lead, Channel.TELEGRAM, "555666"));

		mockMvc
			.perform(post("/api/webhooks/telegram").contentType("application/json")
				.header("X-Telegram-Bot-Api-Secret-Token", secretToken)
				.content(updatePayload(3, 555666, 7, "Quero saber mais")))
			.andExpect(status().isOk());

		List<Message> messages = messageRepository.findByConversationIdOrderBySentAtAsc(conversation.getId());
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0).getDirection()).isEqualTo(MessageDirection.INBOUND);
		assertThat(messages.get(0).getContent()).isEqualTo("Quero saber mais");
	}

	@Test
	void regularMessageWithoutExistingConversationIsIgnored() throws Exception {
		mockMvc
			.perform(post("/api/webhooks/telegram").contentType("application/json")
				.header("X-Telegram-Bot-Api-Secret-Token", secretToken)
				.content(updatePayload(4, 777888, 1, "Olá")))
			.andExpect(status().isOk());

		assertThat(conversationRepository.findByChannelAndExternalThreadId(Channel.TELEGRAM, "777888")).isEmpty();
	}

	@Test
	void receivingTheSameUpdateTwiceIsIdempotent() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-telegram-webhook3@leadsystem.local", salesperson);
		Lead lead = createLead(ana);
		Conversation conversation = conversationRepository.save(new Conversation(lead, Channel.TELEGRAM, "999000"));
		String body = updatePayload(5, 999000, 9, "Mensagem repetida");

		mockMvc
			.perform(post("/api/webhooks/telegram").contentType("application/json")
				.header("X-Telegram-Bot-Api-Secret-Token", secretToken)
				.content(body))
			.andExpect(status().isOk());
		mockMvc
			.perform(post("/api/webhooks/telegram").contentType("application/json")
				.header("X-Telegram-Bot-Api-Secret-Token", secretToken)
				.content(body))
			.andExpect(status().isOk());

		assertThat(messageRepository.findByConversationIdOrderBySentAtAsc(conversation.getId())).hasSize(1);
	}

}

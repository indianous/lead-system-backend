package br.com.joaovictornascimento.lead_system.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import br.com.joaovictornascimento.lead_system.domain.Role;
import br.com.joaovictornascimento.lead_system.domain.RoleRepository;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import br.com.joaovictornascimento.lead_system.messaging.SendMessageResult;
import br.com.joaovictornascimento.lead_system.messaging.TelegramMessageChannelClient;
import br.com.joaovictornascimento.lead_system.messaging.WhatsAppMessageChannelClient;
import br.com.joaovictornascimento.lead_system.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConversationControllerTest {

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
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private WhatsAppMessageChannelClient whatsAppMessageChannelClient;

	@MockitoBean
	private TelegramMessageChannelClient telegramMessageChannelClient;

	private String tokenFor(User user) {
		return "Bearer " + jwtService.generateToken(user.getId());
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

	private static String createConversationJson(String channel) {
		return """
				{"channel":"%s"}
				""".formatted(channel);
	}

	@Test
	void createWhatsAppConversationWithoutLeadPhoneReturnsBadRequest() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-conversations1@leadsystem.local", salesperson);
		Lead lead = createLead(ana, null);

		mockMvc
			.perform(post("/api/leads/" + lead.getId() + "/conversations").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content(createConversationJson("META_WHATSAPP")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createWhatsAppConversationWithLeadPhoneReturnsCreated() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-conversations2@leadsystem.local", salesperson);
		Lead lead = createLead(ana, "5511900000002");

		mockMvc
			.perform(post("/api/leads/" + lead.getId() + "/conversations").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content(createConversationJson("META_WHATSAPP")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.channel").value("META_WHATSAPP"))
			.andExpect(jsonPath("$.externalThreadId").value("5511900000002"))
			.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void createTelegramConversationReturnsDeepLinkWithoutPersisting() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-conversations3@leadsystem.local", salesperson);
		Lead lead = createLead(ana, null);

		mockMvc
			.perform(post("/api/leads/" + lead.getId() + "/conversations").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content(createConversationJson("TELEGRAM")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deepLink", org.hamcrest.Matchers.containsString("?start=" + lead.getId())))
			.andExpect(jsonPath("$.deepLink", org.hamcrest.Matchers.startsWith("https://t.me/")));

		org.assertj.core.api.Assertions.assertThat(conversationRepository.findByLeadId(lead.getId())).isEmpty();
	}

	@Test
	void createConversationWithUnsupportedChannelReturnsBadRequest() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-conversations4@leadsystem.local", salesperson);
		Lead lead = createLead(ana, "5511999999999");

		mockMvc
			.perform(post("/api/leads/" + lead.getId() + "/conversations").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content(createConversationJson("META_INSTAGRAM")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createConversationOnAnotherUsersLeadWithViewOwnLeadsOnlyReturnsForbidden() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-conversations5@leadsystem.local", salesperson);
		User bruno = createUser("Bruno", "bruno-conversations1@leadsystem.local", salesperson);
		Lead brunoLead = createLead(bruno, "5511999999999");

		mockMvc
			.perform(post("/api/leads/" + brunoLead.getId() + "/conversations").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content(createConversationJson("META_WHATSAPP")))
			.andExpect(status().isForbidden());
	}

	@Test
	void listConversationsReturnsThoseBelongingToTheLead() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-conversations6@leadsystem.local", salesperson);
		Lead lead = createLead(ana, "5511900000006");
		conversationRepository.save(new Conversation(lead, Channel.META_WHATSAPP, "5511900000006"));

		mockMvc.perform(get("/api/leads/" + lead.getId() + "/conversations").header(HttpHeaders.AUTHORIZATION, tokenFor(ana)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].externalThreadId").value("5511900000006"));
	}

	@Test
	void listConversationsOnAnotherUsersLeadWithViewOwnLeadsOnlyReturnsForbidden() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-conversations7@leadsystem.local", salesperson);
		User bruno = createUser("Bruno", "bruno-conversations2@leadsystem.local", salesperson);
		Lead brunoLead = createLead(bruno, "5511999999999");

		mockMvc
			.perform(get("/api/leads/" + brunoLead.getId() + "/conversations").header(HttpHeaders.AUTHORIZATION, tokenFor(ana)))
			.andExpect(status().isForbidden());
	}

	@Test
	void sendMessageCallsChannelClientAndPersistsAsSent() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-conversations8@leadsystem.local", salesperson);
		Lead lead = createLead(ana, "5511900000008");
		Conversation conversation = conversationRepository
			.save(new Conversation(lead, Channel.META_WHATSAPP, "5511900000008"));
		when(whatsAppMessageChannelClient.send("5511900000008", "Olá, tudo bem?"))
			.thenReturn(SendMessageResult.success("wamid.XYZ"));

		mockMvc
			.perform(post("/api/conversations/" + conversation.getId() + "/messages").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content("""
						{"content":"Olá, tudo bem?"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.direction").value("OUTBOUND"))
			.andExpect(jsonPath("$.senderUserId").value(ana.getId().toString()))
			.andExpect(jsonPath("$.status").value("SENT"))
			.andExpect(jsonPath("$.externalMessageId").value("wamid.XYZ"));
	}

	@Test
	void sendMessageWithChannelFailureIsPersistedAsFailed() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-conversations9@leadsystem.local", salesperson);
		Lead lead = createLead(ana, "5511900000009");
		Conversation conversation = conversationRepository
			.save(new Conversation(lead, Channel.META_WHATSAPP, "5511900000009"));
		when(whatsAppMessageChannelClient.send("5511900000009", "Oi"))
			.thenReturn(SendMessageResult.failure("outside 24h window"));

		mockMvc
			.perform(post("/api/conversations/" + conversation.getId() + "/messages").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content("""
						{"content":"Oi"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"));
	}

	@Test
	void listMessagesReturnsChronologicalOrder() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-conversations10@leadsystem.local", salesperson);
		Lead lead = createLead(ana, "5511900000010");
		Conversation conversation = conversationRepository
			.save(new Conversation(lead, Channel.META_WHATSAPP, "5511900000010"));
		when(whatsAppMessageChannelClient.send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
			.thenReturn(SendMessageResult.success("wamid.1"));

		mockMvc
			.perform(post("/api/conversations/" + conversation.getId() + "/messages").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content("""
						{"content":"Primeira"}
						"""))
			.andExpect(status().isOk());
		mockMvc
			.perform(post("/api/conversations/" + conversation.getId() + "/messages").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content("""
						{"content":"Segunda"}
						"""))
			.andExpect(status().isOk());

		mockMvc
			.perform(get("/api/conversations/" + conversation.getId() + "/messages").header(HttpHeaders.AUTHORIZATION,
					tokenFor(ana)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].content").value("Primeira"))
			.andExpect(jsonPath("$[1].content").value("Segunda"));
	}

	@Test
	void accessConversationOfAnotherUsersLeadWithViewOwnLeadsOnlyReturnsForbidden() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-conversations11@leadsystem.local", salesperson);
		User bruno = createUser("Bruno", "bruno-conversations3@leadsystem.local", salesperson);
		Lead brunoLead = createLead(bruno, "5511900000011");
		Conversation conversation = conversationRepository
			.save(new Conversation(brunoLead, Channel.META_WHATSAPP, "5511900000011"));

		mockMvc
			.perform(get("/api/conversations/" + conversation.getId() + "/messages").header(HttpHeaders.AUTHORIZATION,
					tokenFor(ana)))
			.andExpect(status().isForbidden());
	}

	@Test
	void createConversationOnUnknownLeadReturnsNotFound() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-conversations1@leadsystem.local", manager);

		mockMvc
			.perform(post("/api/leads/" + UUID.randomUUID() + "/conversations").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(admin))
				.content(createConversationJson("META_WHATSAPP")))
			.andExpect(status().isNotFound());
	}

}

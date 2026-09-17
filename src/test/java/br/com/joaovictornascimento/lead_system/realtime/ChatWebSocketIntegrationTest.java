package br.com.joaovictornascimento.lead_system.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.joaovictornascimento.lead_system.api.MessageResponse;
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
import br.com.joaovictornascimento.lead_system.domain.MessageDirection;
import br.com.joaovictornascimento.lead_system.domain.Role;
import br.com.joaovictornascimento.lead_system.domain.RoleRepository;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import br.com.joaovictornascimento.lead_system.messaging.SendMessageResult;
import br.com.joaovictornascimento.lead_system.messaging.WhatsAppMessageChannelClient;
import br.com.joaovictornascimento.lead_system.security.JwtService;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChatWebSocketIntegrationTest {

	@LocalServerPort
	private int port;

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

	@Value("${app.messaging.whatsapp.app-secret}")
	private String whatsAppAppSecret;

	@Value("${app.messaging.telegram.webhook-secret-token}")
	private String telegramSecretToken;

	private User createUser(String name, String email, Role role) {
		return userRepository.save(new User(name, email, passwordEncoder.encode("somepassword1"), role, null));
	}

	private Lead createLead(User assignedUser, Channel channel, String phone) {
		LeadOrigin origin = leadOriginRepository
			.save(new LeadOrigin(LeadType.DIRECT_CONTACT, channel, null, null, null, CaptureMethod.MANUAL));
		return leadRepository
			.save(new Lead("Lead WS", LeadType.DIRECT_CONTACT, phone, null, null, null, null, null, origin,
					assignedUser, null));
	}

	private String whatsAppSignature(String body) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(whatsAppAppSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
	}

	private static String whatsAppInboundPayload(String from, String externalMessageId, String text) {
		return """
				{"object":"whatsapp_business_account","entry":[{"id":"0","changes":[{"value":{
				"messages":[{"from":"%s","id":"%s","timestamp":"1234567890","type":"text","text":{"body":"%s"}}]
				},"field":"messages"}]}]}
				""".formatted(from, externalMessageId, text);
	}

	private static String telegramUpdatePayload(long updateId, long chatId, long messageId, String text) {
		return """
				{"update_id":%d,"message":{"message_id":%d,"chat":{"id":%d,"type":"private"},"date":123,"text":"%s"}}
				""".formatted(updateId, messageId, chatId, text);
	}

	private StompSession connect(String jwt, CountDownLatch errorLatch) throws Exception {
		WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(new JacksonJsonMessageConverter());

		StompHeaders connectHeaders = new StompHeaders();
		if (jwt != null) {
			connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
		}

		return stompClient
			.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
					new StompSessionHandlerAdapter() {

						@Override
						public void handleException(StompSession session, org.springframework.messaging.simp.stomp.StompCommand command,
								StompHeaders headers, byte[] payload, Throwable exception) {
							if (errorLatch != null) {
								errorLatch.countDown();
							}
						}

						@Override
						public void handleTransportError(StompSession session, Throwable exception) {
							if (errorLatch != null) {
								errorLatch.countDown();
							}
						}

						// A rejeição de SUBSCRIBE chega como frame STOMP ERROR, que o DefaultStompSession
						// entrega aqui (handleFrame genérico), não em handleException — este último é só
						// para falhas do lado do cliente (ex.: erro de conversão de payload).
						@Override
						public void handleFrame(StompHeaders headers, Object payload) {
							if (errorLatch != null) {
								errorLatch.countDown();
							}
						}
					})
			.get(5, TimeUnit.SECONDS);
	}

	private BlockingQueue<MessageResponse> subscribe(StompSession session, java.util.UUID conversationId) {
		BlockingQueue<MessageResponse> received = new LinkedBlockingQueue<>();
		session.subscribe("/topic/conversations/" + conversationId, new StompFrameHandler() {

			@Override
			public Type getPayloadType(StompHeaders headers) {
				return MessageResponse.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				received.add((MessageResponse) payload);
			}
		});
		return received;
	}

	@Test
	void newOutboundMessageIsBroadcastToSubscribersOfTheConversation() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-ws1@leadsystem.local", salesperson);
		Lead lead = createLead(ana, Channel.META_WHATSAPP, "5511900000101");
		Conversation conversation = conversationRepository
			.save(new Conversation(lead, Channel.META_WHATSAPP, "5511900000101"));
		when(whatsAppMessageChannelClient.send("5511900000101", "Olá do teste"))
			.thenReturn(SendMessageResult.success("wamid.WS1"));
		String token = jwtService.generateToken(ana.getId());

		StompSession session = connect(token, null);
		BlockingQueue<MessageResponse> received = subscribe(session, conversation.getId());
		Thread.sleep(300);

		mockMvc
			.perform(post("/api/conversations/" + conversation.getId() + "/messages").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.content("""
						{"content":"Olá do teste"}
						"""))
			.andExpect(status().isOk());

		MessageResponse message = received.poll(5, TimeUnit.SECONDS);
		assertThat(message).isNotNull();
		assertThat(message.direction()).isEqualTo(MessageDirection.OUTBOUND);
		assertThat(message.content()).isEqualTo("Olá do teste");
	}

	@Test
	void whatsAppWebhookInboundMessageIsBroadcast() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-ws2@leadsystem.local", salesperson);
		Lead lead = createLead(ana, Channel.META_WHATSAPP, "5511900000102");
		Conversation conversation = conversationRepository
			.save(new Conversation(lead, Channel.META_WHATSAPP, "5511900000102"));
		String token = jwtService.generateToken(ana.getId());

		StompSession session = connect(token, null);
		BlockingQueue<MessageResponse> received = subscribe(session, conversation.getId());
		Thread.sleep(300);

		String body = whatsAppInboundPayload("5511900000102", "wamid.WS2", "Mensagem via webhook");
		mockMvc
			.perform(post("/api/webhooks/whatsapp").contentType("application/json")
				.header("X-Hub-Signature-256", whatsAppSignature(body))
				.content(body))
			.andExpect(status().isOk());

		MessageResponse message = received.poll(5, TimeUnit.SECONDS);
		assertThat(message).isNotNull();
		assertThat(message.direction()).isEqualTo(MessageDirection.INBOUND);
		assertThat(message.content()).isEqualTo("Mensagem via webhook");
	}

	@Test
	void telegramWebhookInboundMessageIsBroadcast() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-ws3@leadsystem.local", salesperson);
		Lead lead = createLead(ana, Channel.TELEGRAM, null);
		Conversation conversation = conversationRepository.save(new Conversation(lead, Channel.TELEGRAM, "700000003"));
		String token = jwtService.generateToken(ana.getId());

		StompSession session = connect(token, null);
		BlockingQueue<MessageResponse> received = subscribe(session, conversation.getId());
		Thread.sleep(300);

		mockMvc
			.perform(post("/api/webhooks/telegram").contentType("application/json")
				.header("X-Telegram-Bot-Api-Secret-Token", telegramSecretToken)
				.content(telegramUpdatePayload(1, 700000003L, 1, "Mensagem via telegram")))
			.andExpect(status().isOk());

		MessageResponse message = received.poll(5, TimeUnit.SECONDS);
		assertThat(message).isNotNull();
		assertThat(message.direction()).isEqualTo(MessageDirection.INBOUND);
		assertThat(message.content()).isEqualTo("Mensagem via telegram");
	}

	@Test
	void connectWithoutAuthorizationHeaderIsRejected() {
		assertThatThrownBy(() -> connect(null, null)).isInstanceOf(Exception.class);
	}

	@Test
	void subscribingToAnotherUsersConversationWithViewOwnLeadsOnlyIsRejected() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-ws4@leadsystem.local", salesperson);
		User bruno = createUser("Bruno", "bruno-ws4@leadsystem.local", salesperson);
		Lead brunoLead = createLead(bruno, Channel.META_WHATSAPP, "5511900000104");
		Conversation conversation = conversationRepository
			.save(new Conversation(brunoLead, Channel.META_WHATSAPP, "5511900000104"));
		String token = jwtService.generateToken(ana.getId());

		CountDownLatch errorLatch = new CountDownLatch(1);
		StompSession session = connect(token, errorLatch);
		BlockingQueue<MessageResponse> received = subscribe(session, conversation.getId());
		Thread.sleep(300);

		String body = whatsAppInboundPayload("5511900000104", "wamid.WS4", "Não deveria chegar");
		mockMvc
			.perform(post("/api/webhooks/whatsapp").contentType("application/json")
				.header("X-Hub-Signature-256", whatsAppSignature(body))
				.content(body))
			.andExpect(status().isOk());

		assertThat(errorLatch.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(received.poll(2, TimeUnit.SECONDS)).isNull();
	}

	@Test
	void subscribingToAnotherUsersConversationWithViewAllLeadsIsAllowed() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User bruno = createUser("Bruno", "bruno-ws5@leadsystem.local", salesperson);
		User admin = createUser("Gestora", "gestora-ws5@leadsystem.local", manager);
		Lead brunoLead = createLead(bruno, Channel.META_WHATSAPP, "5511900000105");
		Conversation conversation = conversationRepository
			.save(new Conversation(brunoLead, Channel.META_WHATSAPP, "5511900000105"));
		String token = jwtService.generateToken(admin.getId());

		StompSession session = connect(token, null);
		BlockingQueue<MessageResponse> received = subscribe(session, conversation.getId());
		Thread.sleep(300);

		String body = whatsAppInboundPayload("5511900000105", "wamid.WS5", "Gestora vê tudo");
		mockMvc
			.perform(post("/api/webhooks/whatsapp").contentType("application/json")
				.header("X-Hub-Signature-256", whatsAppSignature(body))
				.content(body))
			.andExpect(status().isOk());

		MessageResponse message = received.poll(5, TimeUnit.SECONDS);
		assertThat(message).isNotNull();
		assertThat(message.content()).isEqualTo("Gestora vê tudo");
	}

}

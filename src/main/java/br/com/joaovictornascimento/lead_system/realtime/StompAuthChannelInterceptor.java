package br.com.joaovictornascimento.lead_system.realtime;

import br.com.joaovictornascimento.lead_system.api.LeadService;
import br.com.joaovictornascimento.lead_system.domain.Conversation;
import br.com.joaovictornascimento.lead_system.domain.ConversationRepository;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import br.com.joaovictornascimento.lead_system.security.JwtService;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Autentica e autoriza o canal STOMP "na mão", já que o WebSocket nativo usado pelo frontend
 * (`@stomp/stompjs`, sem SockJS) não permite enviar o header {@code Authorization} no handshake
 * HTTP — o JWT viaja no header nativo do frame {@code CONNECT} em vez disso (ver plano da Etapa 9).
 * No {@code SUBSCRIBE} em {@code /topic/conversations/{id}}, reusa a mesma checagem de acesso já
 * usada pelo resto da API para os sub-recursos do lead ({@link LeadService#findAccessibleLeadOrThrow}).
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	private static final Pattern CONVERSATION_TOPIC = Pattern
		.compile("^/topic/conversations/([0-9a-fA-F-]{36})$");

	private final JwtService jwtService;

	private final UserRepository userRepository;

	private final ConversationRepository conversationRepository;

	private final LeadService leadService;

	public StompAuthChannelInterceptor(JwtService jwtService, UserRepository userRepository,
			ConversationRepository conversationRepository, LeadService leadService) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
		this.conversationRepository = conversationRepository;
		this.leadService = leadService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		// MessageHeaderAccessor.getAccessor(...) devolve o accessor mutável já anexado à Message
		// (a sessão STOMP guarda o Principal nos headers dela) — StompHeaderAccessor.wrap(message)
		// cria uma cópia solta; setUser nela não se refletiria na Message de verdade, e o Principal
		// setado no CONNECT nunca apareceria nos frames seguintes (SUBSCRIBE) da mesma sessão.
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor.getCommand() == StompCommand.CONNECT) {
			User user = authenticate(accessor);
			accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
		}
		else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
			authorizeSubscription(accessor);
		}
		return message;
	}

	private User authenticate(StompHeaderAccessor accessor) {
		String header = accessor.getFirstNativeHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			throw new MessagingException("Token ausente na conexão STOMP");
		}
		String token = header.substring("Bearer ".length());
		return jwtService.parseUserId(token)
			.flatMap(userRepository::findById)
			.filter(User::isActive)
			.orElseThrow(() -> new MessagingException("Token inválido ou usuário inativo"));
	}

	private void authorizeSubscription(StompHeaderAccessor accessor) {
		String destination = accessor.getDestination();
		Matcher matcher = destination != null ? CONVERSATION_TOPIC.matcher(destination) : null;
		if (matcher == null || !matcher.matches()) {
			return;
		}
		User user = currentUser(accessor);
		UUID conversationId = UUID.fromString(matcher.group(1));
		Conversation conversation = conversationRepository.findById(conversationId)
			.orElseThrow(() -> new MessagingException("Conversa não encontrada: " + conversationId));
		leadService.findAccessibleLeadOrThrow(conversation.getLead().getId(), user);
	}

	private User currentUser(StompHeaderAccessor accessor) {
		Principal principal = accessor.getUser();
		if (principal instanceof UsernamePasswordAuthenticationToken auth && auth.getPrincipal() instanceof User user) {
			return user;
		}
		throw new MessagingException("Sessão STOMP não autenticada");
	}

}

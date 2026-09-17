package br.com.joaovictornascimento.lead_system.realtime;

import br.com.joaovictornascimento.lead_system.api.MessageResponse;
import br.com.joaovictornascimento.lead_system.domain.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica uma {@link Message} nova (enviada pelo vendedor ou recebida por webhook) no tópico STOMP
 * da conversa correspondente, para os clientes com a tela de chat aberta.
 */
@Component
public class ConversationBroadcaster {

	private final SimpMessagingTemplate messagingTemplate;

	public ConversationBroadcaster(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	public void broadcast(Message message) {
		String destination = "/topic/conversations/" + message.getConversation().getId();
		messagingTemplate.convertAndSend(destination, MessageResponse.from(message));
	}

}

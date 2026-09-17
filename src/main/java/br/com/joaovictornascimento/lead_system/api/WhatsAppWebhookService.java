package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.Conversation;
import br.com.joaovictornascimento.lead_system.domain.ConversationRepository;
import br.com.joaovictornascimento.lead_system.domain.Lead;
import br.com.joaovictornascimento.lead_system.domain.LeadRepository;
import br.com.joaovictornascimento.lead_system.domain.Message;
import br.com.joaovictornascimento.lead_system.domain.MessageDirection;
import br.com.joaovictornascimento.lead_system.domain.MessageRepository;
import br.com.joaovictornascimento.lead_system.domain.MessageStatus;
import br.com.joaovictornascimento.lead_system.realtime.ConversationBroadcaster;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Processa o payload de eventos do webhook do WhatsApp (mensagens recebidas + atualizações de
 * status de mensagens enviadas). A verificação de assinatura acontece antes, no controller.
 */
@Service
public class WhatsAppWebhookService {

	private final LeadRepository leadRepository;

	private final ConversationRepository conversationRepository;

	private final MessageRepository messageRepository;

	private final ConversationBroadcaster conversationBroadcaster;

	public WhatsAppWebhookService(LeadRepository leadRepository, ConversationRepository conversationRepository,
			MessageRepository messageRepository, ConversationBroadcaster conversationBroadcaster) {
		this.leadRepository = leadRepository;
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.conversationBroadcaster = conversationBroadcaster;
	}

	@Transactional
	public void process(JsonNode payload) {
		for (JsonNode entry : payload.path("entry")) {
			for (JsonNode change : entry.path("changes")) {
				JsonNode value = change.path("value");
				for (JsonNode messageNode : value.path("messages")) {
					handleInboundMessage(messageNode);
				}
				for (JsonNode statusNode : value.path("statuses")) {
					handleStatusUpdate(statusNode);
				}
			}
		}
	}

	private void handleInboundMessage(JsonNode messageNode) {
		String externalMessageId = messageNode.path("id").asText(null);
		if (externalMessageId == null || messageRepository.findByExternalMessageId(externalMessageId).isPresent()) {
			return;
		}

		String from = messageNode.path("from").asText(null);
		if (from == null) {
			return;
		}
		Optional<Lead> lead = leadRepository.findFirstByPhone(from);
		if (lead.isEmpty()) {
			// Nenhum lead com esse telefone — não dá pra criar Conversation sem lead_id.
			return;
		}

		String content = messageNode.path("text").path("body").asText("");
		Conversation conversation = conversationRepository.findByChannelAndExternalThreadId(Channel.META_WHATSAPP, from)
			.orElseGet(() -> conversationRepository.save(new Conversation(lead.get(), Channel.META_WHATSAPP, from)));

		Message message = messageRepository.save(new Message(conversation, MessageDirection.INBOUND, null, content, null,
				externalMessageId, MessageStatus.DELIVERED));
		conversation.touch();
		conversationRepository.save(conversation);
		conversationBroadcaster.broadcast(message);
	}

	private void handleStatusUpdate(JsonNode statusNode) {
		String externalMessageId = statusNode.path("id").asText(null);
		String status = statusNode.path("status").asText(null);
		if (externalMessageId == null || status == null) {
			return;
		}
		messageRepository.findByExternalMessageId(externalMessageId).ifPresent(message -> {
			message.setStatus(mapStatus(status));
			messageRepository.save(message);
		});
	}

	private MessageStatus mapStatus(String metaStatus) {
		return switch (metaStatus) {
			case "sent" -> MessageStatus.SENT;
			case "delivered" -> MessageStatus.DELIVERED;
			case "read" -> MessageStatus.READ;
			case "failed" -> MessageStatus.FAILED;
			default -> MessageStatus.SENT;
		};
	}

}

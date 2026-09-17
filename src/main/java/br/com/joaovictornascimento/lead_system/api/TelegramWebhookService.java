package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.Conversation;
import br.com.joaovictornascimento.lead_system.domain.ConversationRepository;
import br.com.joaovictornascimento.lead_system.domain.LeadRepository;
import br.com.joaovictornascimento.lead_system.domain.Message;
import br.com.joaovictornascimento.lead_system.domain.MessageDirection;
import br.com.joaovictornascimento.lead_system.domain.MessageRepository;
import br.com.joaovictornascimento.lead_system.domain.MessageStatus;
import br.com.joaovictornascimento.lead_system.realtime.ConversationBroadcaster;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Processa updates do Telegram. Uma Conversation só nasce quando chega o comando
 * "/start &lt;leadId&gt;" do deep link (ver plano da Etapa 8 — o chat_id é opaco e só é revelado
 * depois que o lead manda a primeira mensagem ao bot).
 */
@Service
public class TelegramWebhookService {

	private static final String START_COMMAND_PREFIX = "/start ";

	private final LeadRepository leadRepository;

	private final ConversationRepository conversationRepository;

	private final MessageRepository messageRepository;

	private final ConversationBroadcaster conversationBroadcaster;

	public TelegramWebhookService(LeadRepository leadRepository, ConversationRepository conversationRepository,
			MessageRepository messageRepository, ConversationBroadcaster conversationBroadcaster) {
		this.leadRepository = leadRepository;
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.conversationBroadcaster = conversationBroadcaster;
	}

	@Transactional
	public void process(JsonNode update) {
		JsonNode message = update.path("message");
		if (message.isMissingNode()) {
			return;
		}

		String chatId = message.path("chat").path("id").asText(null);
		Long messageId = message.path("message_id").isMissingNode() ? null : message.path("message_id").asLong();
		String text = message.path("text").asText("");
		if (chatId == null || messageId == null) {
			return;
		}

		if (text.startsWith(START_COMMAND_PREFIX)) {
			handleStart(text.substring(START_COMMAND_PREFIX.length()).trim(), chatId);
			return;
		}

		String externalMessageId = chatId + ":" + messageId;
		if (messageRepository.findByExternalMessageId(externalMessageId).isPresent()) {
			return;
		}

		conversationRepository.findByChannelAndExternalThreadId(Channel.TELEGRAM, chatId).ifPresent(conversation -> {
			Message savedMessage = messageRepository.save(new Message(conversation, MessageDirection.INBOUND, null, text,
					null, externalMessageId, MessageStatus.DELIVERED));
			conversation.touch();
			conversationRepository.save(conversation);
			conversationBroadcaster.broadcast(savedMessage);
		});
		// Se ainda não existir Conversation para esse chat_id (sem /start prévio), ignora —
		// não há como saber a qual lead essa mensagem pertence.
	}

	private void handleStart(String leadIdRaw, String chatId) {
		UUID leadId;
		try {
			leadId = UUID.fromString(leadIdRaw);
		}
		catch (IllegalArgumentException e) {
			return;
		}
		leadRepository.findById(leadId).ifPresent(lead -> {
			if (conversationRepository.findByChannelAndExternalThreadId(Channel.TELEGRAM, chatId).isEmpty()) {
				conversationRepository.save(new Conversation(lead, Channel.TELEGRAM, chatId));
			}
		});
	}

}

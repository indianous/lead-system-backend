package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.Message;
import br.com.joaovictornascimento.lead_system.domain.MessageDirection;
import br.com.joaovictornascimento.lead_system.domain.MessageStatus;
import br.com.joaovictornascimento.lead_system.domain.User;
import java.time.Instant;
import java.util.UUID;

public record MessageResponse(UUID id, UUID conversationId, MessageDirection direction, UUID senderUserId,
		String senderUserName, String content, String mediaUrl, String externalMessageId, MessageStatus status,
		Instant sentAt) {

	public static MessageResponse from(Message message) {
		User sender = message.getSenderUser();
		return new MessageResponse(message.getId(), message.getConversation().getId(), message.getDirection(),
				sender != null ? sender.getId() : null, sender != null ? sender.getName() : null, message.getContent(),
				message.getMediaUrl(), message.getExternalMessageId(), message.getStatus(), message.getSentAt());
	}

}

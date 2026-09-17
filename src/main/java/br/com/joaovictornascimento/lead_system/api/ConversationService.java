package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.Conversation;
import br.com.joaovictornascimento.lead_system.domain.ConversationRepository;
import br.com.joaovictornascimento.lead_system.domain.Lead;
import br.com.joaovictornascimento.lead_system.domain.Message;
import br.com.joaovictornascimento.lead_system.domain.MessageDirection;
import br.com.joaovictornascimento.lead_system.domain.MessageRepository;
import br.com.joaovictornascimento.lead_system.domain.MessageStatus;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.messaging.MessageChannelClientResolver;
import br.com.joaovictornascimento.lead_system.messaging.SendMessageResult;
import br.com.joaovictornascimento.lead_system.realtime.ConversationBroadcaster;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

	private final ConversationRepository conversationRepository;

	private final MessageRepository messageRepository;

	private final LeadService leadService;

	private final MessageChannelClientResolver messageChannelClientResolver;

	private final ConversationBroadcaster conversationBroadcaster;

	private final String telegramBotUsername;

	public ConversationService(ConversationRepository conversationRepository, MessageRepository messageRepository,
			LeadService leadService, MessageChannelClientResolver messageChannelClientResolver,
			ConversationBroadcaster conversationBroadcaster,
			@Value("${app.messaging.telegram.bot-username}") String telegramBotUsername) {
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.leadService = leadService;
		this.messageChannelClientResolver = messageChannelClientResolver;
		this.conversationBroadcaster = conversationBroadcaster;
		this.telegramBotUsername = telegramBotUsername;
	}

	@Transactional
	public ConversationCreationResult createConversation(UUID leadId, CreateConversationRequest request,
			User currentUser) {
		Lead lead = leadService.findAccessibleLeadOrThrow(leadId, currentUser);

		if (request.channel() == Channel.TELEGRAM) {
			String deepLink = "https://t.me/%s?start=%s".formatted(telegramBotUsername, lead.getId());
			return ConversationCreationResult.ofTelegramDeepLink(deepLink);
		}

		// Único outro canal aceito por CreateConversationRequest.isChannelSupported(): WhatsApp.
		if (lead.getPhone() == null || lead.getPhone().isBlank()) {
			throw new LeadMissingPhoneException(leadId);
		}
		Conversation conversation = conversationRepository.save(new Conversation(lead, Channel.META_WHATSAPP, lead.getPhone()));
		return ConversationCreationResult.ofConversation(toResponse(conversation));
	}

	public List<ConversationResponse> findByLead(UUID leadId, User currentUser) {
		leadService.findAccessibleLeadOrThrow(leadId, currentUser);
		return conversationRepository.findByLeadId(leadId).stream().map(this::toResponse).toList();
	}

	@Transactional
	public MessageResponse sendMessage(UUID conversationId, CreateMessageRequest request, User currentUser) {
		Conversation conversation = findConversationOrThrow(conversationId);
		leadService.findAccessibleLeadOrThrow(conversation.getLead().getId(), currentUser);

		Message message = new Message(conversation, MessageDirection.OUTBOUND, currentUser, request.content(), null,
				null, MessageStatus.PENDING);
		messageRepository.save(message);

		SendMessageResult result = messageChannelClientResolver.resolve(conversation.getChannel())
			.send(conversation.getExternalThreadId(), request.content());

		message.setStatus(result.success() ? MessageStatus.SENT : MessageStatus.FAILED);
		message.setExternalMessageId(result.externalMessageId());
		Message saved = messageRepository.save(message);

		conversation.touch();
		conversationRepository.save(conversation);

		conversationBroadcaster.broadcast(saved);
		return MessageResponse.from(saved);
	}

	public List<MessageResponse> findMessages(UUID conversationId, User currentUser) {
		Conversation conversation = findConversationOrThrow(conversationId);
		leadService.findAccessibleLeadOrThrow(conversation.getLead().getId(), currentUser);
		return messageRepository.findByConversationIdOrderBySentAtAsc(conversationId).stream().map(MessageResponse::from).toList();
	}

	private Conversation findConversationOrThrow(UUID id) {
		return conversationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Conversation", id));
	}

	private ConversationResponse toResponse(Conversation conversation) {
		return new ConversationResponse(conversation.getId(), conversation.getLead().getId(), conversation.getChannel(),
				conversation.getExternalThreadId(), conversation.getStatus(), conversation.getCreatedAt(),
				conversation.getUpdatedAt());
	}

}

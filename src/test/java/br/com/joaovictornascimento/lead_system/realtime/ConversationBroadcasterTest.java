package br.com.joaovictornascimento.lead_system.realtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.joaovictornascimento.lead_system.api.MessageResponse;
import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.Conversation;
import br.com.joaovictornascimento.lead_system.domain.Message;
import br.com.joaovictornascimento.lead_system.domain.MessageDirection;
import br.com.joaovictornascimento.lead_system.domain.MessageStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ConversationBroadcasterTest {

	@Test
	void broadcastsToTheConversationTopicWithTheMappedPayload() {
		SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
		ConversationBroadcaster broadcaster = new ConversationBroadcaster(messagingTemplate);

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = new Conversation(null, Channel.META_WHATSAPP, "5511999999999");
		ReflectionTestUtils.setField(conversation, "id", conversationId);
		Message message = new Message(conversation, MessageDirection.INBOUND, null, "Olá", null, "wamid.1",
				MessageStatus.DELIVERED);
		ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(message, "sentAt", java.time.Instant.now());

		broadcaster.broadcast(message);

		ArgumentCaptor<MessageResponse> payloadCaptor = ArgumentCaptor.forClass(MessageResponse.class);
		verify(messagingTemplate).convertAndSend(eq("/topic/conversations/" + conversationId), payloadCaptor.capture());
		org.assertj.core.api.Assertions.assertThat(payloadCaptor.getValue()).isEqualTo(MessageResponse.from(message));
	}

}

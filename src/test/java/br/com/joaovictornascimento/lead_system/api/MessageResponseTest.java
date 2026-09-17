package br.com.joaovictornascimento.lead_system.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.Conversation;
import br.com.joaovictornascimento.lead_system.domain.Message;
import br.com.joaovictornascimento.lead_system.domain.MessageDirection;
import br.com.joaovictornascimento.lead_system.domain.MessageStatus;
import br.com.joaovictornascimento.lead_system.domain.Role;
import br.com.joaovictornascimento.lead_system.domain.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MessageResponseTest {

	@Test
	void mapsAllFieldsForAnOutboundMessageWithSender() {
		Conversation conversation = new Conversation(null, Channel.META_WHATSAPP, "5511999999999");
		ReflectionTestUtils.setField(conversation, "id", UUID.randomUUID());
		User sender = new User("Ana", "ana@leadsystem.local", "hash", new Role("Salesperson", "Vendedor"), null);
		ReflectionTestUtils.setField(sender, "id", UUID.randomUUID());
		Message message = new Message(conversation, MessageDirection.OUTBOUND, sender, "Olá", null, "wamid.1",
				MessageStatus.SENT);
		ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(message, "sentAt", Instant.now());

		MessageResponse response = MessageResponse.from(message);

		assertThat(response.id()).isEqualTo(message.getId());
		assertThat(response.conversationId()).isEqualTo(conversation.getId());
		assertThat(response.direction()).isEqualTo(MessageDirection.OUTBOUND);
		assertThat(response.senderUserId()).isEqualTo(sender.getId());
		assertThat(response.senderUserName()).isEqualTo("Ana");
		assertThat(response.content()).isEqualTo("Olá");
		assertThat(response.externalMessageId()).isEqualTo("wamid.1");
		assertThat(response.status()).isEqualTo(MessageStatus.SENT);
		assertThat(response.sentAt()).isEqualTo(message.getSentAt());
	}

	@Test
	void mapsNullSenderForAnInboundMessage() {
		Conversation conversation = new Conversation(null, Channel.TELEGRAM, "700000000");
		ReflectionTestUtils.setField(conversation, "id", UUID.randomUUID());
		Message message = new Message(conversation, MessageDirection.INBOUND, null, "Oi", null, "700000000:1",
				MessageStatus.DELIVERED);
		ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(message, "sentAt", Instant.now());

		MessageResponse response = MessageResponse.from(message);

		assertThat(response.senderUserId()).isNull();
		assertThat(response.senderUserName()).isNull();
	}

}

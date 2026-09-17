package br.com.joaovictornascimento.lead_system.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

	List<Message> findByConversationIdOrderBySentAtAsc(UUID conversationId);

	Optional<Message> findByExternalMessageId(String externalMessageId);

}

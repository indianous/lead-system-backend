package br.com.joaovictornascimento.lead_system.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

	List<Conversation> findByLeadId(UUID leadId);

	Optional<Conversation> findByChannelAndExternalThreadId(Channel channel, String externalThreadId);

}

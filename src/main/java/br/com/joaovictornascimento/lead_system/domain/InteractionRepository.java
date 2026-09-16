package br.com.joaovictornascimento.lead_system.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractionRepository extends JpaRepository<Interaction, UUID> {

	List<Interaction> findByLeadIdOrderByCreatedAtAsc(UUID leadId);

}

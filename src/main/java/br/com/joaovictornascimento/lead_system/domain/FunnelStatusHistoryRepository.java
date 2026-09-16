package br.com.joaovictornascimento.lead_system.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FunnelStatusHistoryRepository extends JpaRepository<FunnelStatusHistory, UUID> {

	List<FunnelStatusHistory> findByLeadIdOrderByChangedAtAsc(UUID leadId);

}

package br.com.joaovictornascimento.lead_system.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

	@Query("""
			SELECT l FROM Lead l
			WHERE (:leadType IS NULL OR l.leadType = :leadType)
			  AND (:channel IS NULL OR l.origin.channel = :channel)
			  AND (:funnelStatus IS NULL OR l.funnelStatus = :funnelStatus)
			  AND (:assignedUserId IS NULL OR l.assignedUser.id = :assignedUserId)
			""")
	List<Lead> search(@Param("leadType") LeadType leadType, @Param("channel") Channel channel,
			@Param("funnelStatus") FunnelStatus funnelStatus, @Param("assignedUserId") UUID assignedUserId);

}

package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.CaptureMethod;
import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.FunnelStatus;
import br.com.joaovictornascimento.lead_system.domain.FunnelStatusHistory;
import br.com.joaovictornascimento.lead_system.domain.FunnelStatusHistoryRepository;
import br.com.joaovictornascimento.lead_system.domain.Lead;
import br.com.joaovictornascimento.lead_system.domain.LeadOrigin;
import br.com.joaovictornascimento.lead_system.domain.LeadOriginRepository;
import br.com.joaovictornascimento.lead_system.domain.LeadRepository;
import br.com.joaovictornascimento.lead_system.domain.LeadType;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicLeadService {

	private final LeadRepository leadRepository;

	private final LeadOriginRepository leadOriginRepository;

	private final UserRepository userRepository;

	private final FunnelStatusHistoryRepository funnelStatusHistoryRepository;

	private final String defaultAssigneeEmail;

	public PublicLeadService(LeadRepository leadRepository, LeadOriginRepository leadOriginRepository,
			UserRepository userRepository, FunnelStatusHistoryRepository funnelStatusHistoryRepository,
			@Value("${app.public-api.default-assignee-email}") String defaultAssigneeEmail) {
		this.leadRepository = leadRepository;
		this.leadOriginRepository = leadOriginRepository;
		this.userRepository = userRepository;
		this.funnelStatusHistoryRepository = funnelStatusHistoryRepository;
		this.defaultAssigneeEmail = defaultAssigneeEmail;
	}

	@Transactional
	public PublicLeadResponse create(CreatePublicLeadRequest request) {
		User defaultAssignee = userRepository.findByEmail(defaultAssigneeEmail)
			.orElseThrow(() -> new IllegalStateException(
					"Usuário padrão para leads do site não encontrado: " + defaultAssigneeEmail));

		LeadOrigin origin = leadOriginRepository
			.save(new LeadOrigin(LeadType.DIRECT_CONTACT, Channel.WEBSITE, null, null, null, CaptureMethod.API));

		Lead lead = new Lead(request.name(), LeadType.DIRECT_CONTACT, request.phone(), request.email(),
				request.initialMessage(), null, null, null, origin, defaultAssignee, new HashSet<>());

		Lead saved = leadRepository.save(lead);
		funnelStatusHistoryRepository
			.save(new FunnelStatusHistory(saved, null, FunnelStatus.NEW, defaultAssignee, null));

		return new PublicLeadResponse(saved.getId(), saved.getCreatedAt());
	}

}

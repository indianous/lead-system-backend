package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.Interaction;
import br.com.joaovictornascimento.lead_system.domain.InteractionRepository;
import br.com.joaovictornascimento.lead_system.domain.Lead;
import br.com.joaovictornascimento.lead_system.domain.User;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InteractionService {

	private final InteractionRepository interactionRepository;

	private final LeadService leadService;

	public InteractionService(InteractionRepository interactionRepository, LeadService leadService) {
		this.interactionRepository = interactionRepository;
		this.leadService = leadService;
	}

	public InteractionResponse create(UUID leadId, CreateInteractionRequest request, User currentUser) {
		Lead lead = leadService.findAccessibleLeadOrThrow(leadId, currentUser);
		Interaction interaction = new Interaction(lead, currentUser, request.type(), request.content());
		return toResponse(interactionRepository.save(interaction));
	}

	public List<InteractionResponse> findByLead(UUID leadId, User currentUser) {
		leadService.findAccessibleLeadOrThrow(leadId, currentUser);
		return interactionRepository.findByLeadIdOrderByCreatedAtAsc(leadId).stream().map(this::toResponse).toList();
	}

	private InteractionResponse toResponse(Interaction interaction) {
		return new InteractionResponse(interaction.getId(), interaction.getLead().getId(),
				interaction.getUser().getId(), interaction.getUser().getName(), interaction.getType(),
				interaction.getContent(), interaction.getCreatedAt());
	}

}

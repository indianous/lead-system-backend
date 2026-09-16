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
import br.com.joaovictornascimento.lead_system.domain.Product;
import br.com.joaovictornascimento.lead_system.domain.ProductRepository;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadService {

	private static final String VIEW_ALL_LEADS = "VIEW_ALL_LEADS";

	private final LeadRepository leadRepository;

	private final LeadOriginRepository leadOriginRepository;

	private final UserRepository userRepository;

	private final ProductRepository productRepository;

	private final FunnelStatusHistoryRepository funnelStatusHistoryRepository;

	public LeadService(LeadRepository leadRepository, LeadOriginRepository leadOriginRepository,
			UserRepository userRepository, ProductRepository productRepository,
			FunnelStatusHistoryRepository funnelStatusHistoryRepository) {
		this.leadRepository = leadRepository;
		this.leadOriginRepository = leadOriginRepository;
		this.userRepository = userRepository;
		this.productRepository = productRepository;
		this.funnelStatusHistoryRepository = funnelStatusHistoryRepository;
	}

	@Transactional
	public LeadResponse create(CreateLeadRequest request, User currentUser) {
		User assignedUser = findUserOrThrow(request.assignedUserId());
		Set<Product> products = findProductsOrThrow(request.productIds());

		LeadOrigin origin = leadOriginRepository.save(new LeadOrigin(request.leadType(), request.channel(),
				request.searchSource(), request.region(), request.searchSegment(), CaptureMethod.MANUAL));

		Lead lead = new Lead(request.name(), request.leadType(), request.phone(), request.email(),
				request.initialMessage(), request.estimatedBudgetCents(), request.desiredTimeline(),
				request.qualificationScore(), origin, assignedUser, products);
		Lead saved = leadRepository.save(lead);

		recordInitialStatus(saved, currentUser);

		return toResponse(saved);
	}

	@Transactional
	public LeadResponse updateStatus(UUID id, UpdateLeadStatusRequest request, User currentUser) {
		Lead lead = findAccessibleLeadOrThrow(id, currentUser);

		FunnelStatus previousStatus = lead.getFunnelStatus();
		FunnelStatus newStatus = request.newStatus();

		lead.setFunnelStatus(newStatus);
		lead.setLossReason(newStatus == FunnelStatus.LOST ? request.reason() : null);
		Lead saved = leadRepository.save(lead);

		funnelStatusHistoryRepository
			.save(new FunnelStatusHistory(saved, previousStatus, newStatus, currentUser, request.reason()));

		return toResponse(saved);
	}

	private void recordInitialStatus(Lead lead, User actor) {
		funnelStatusHistoryRepository.save(new FunnelStatusHistory(lead, null, FunnelStatus.NEW, actor, null));
	}

	public List<LeadResponse> findAll(User currentUser, LeadType leadType, Channel channel,
			FunnelStatus funnelStatus, UUID assignedUserId) {
		UUID effectiveAssignedUserId = hasViewAllLeads(currentUser) ? assignedUserId : currentUser.getId();
		return leadRepository.search(leadType, channel, funnelStatus, effectiveAssignedUserId)
			.stream()
			.map(this::toResponse)
			.toList();
	}

	public LeadResponse findById(UUID id, User currentUser) {
		Lead lead = findAccessibleLeadOrThrow(id, currentUser);
		return toResponse(lead);
	}

	@Transactional
	public LeadResponse update(UUID id, UpdateLeadRequest request, User currentUser) {
		Lead lead = findAccessibleLeadOrThrow(id, currentUser);

		User assignedUser = findUserOrThrow(request.assignedUserId());
		Set<Product> products = findProductsOrThrow(request.productIds());

		lead.setName(request.name());
		lead.setPhone(request.phone());
		lead.setEmail(request.email());
		lead.setInitialMessage(request.initialMessage());
		lead.setEstimatedBudgetCents(request.estimatedBudgetCents());
		lead.setDesiredTimeline(request.desiredTimeline());
		lead.setQualificationScore(request.qualificationScore());
		lead.setAssignedUser(assignedUser);
		lead.setProductsOfInterest(products);

		return toResponse(leadRepository.save(lead));
	}

	/**
	 * Busca o lead e valida a posse (VIEW_OWN_LEADS só acessa o próprio; VIEW_ALL_LEADS acessa
	 * qualquer um) — reaproveitado também pelo InteractionService, para as duas checagens não
	 * divergirem com o tempo (ver plano da Etapa 6).
	 */
	public Lead findAccessibleLeadOrThrow(UUID id, User currentUser) {
		Lead lead = findLeadOrThrow(id);
		if (!hasViewAllLeads(currentUser) && !lead.getAssignedUser().getId().equals(currentUser.getId())) {
			throw new LeadAccessDeniedException(lead.getId());
		}
		return lead;
	}

	private boolean hasViewAllLeads(User user) {
		return user.getRole().getPermissions().stream().anyMatch(permission -> permission.getKey().equals(VIEW_ALL_LEADS));
	}

	private Lead findLeadOrThrow(UUID id) {
		return leadRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lead", id));
	}

	private User findUserOrThrow(UUID id) {
		return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
	}

	private Set<Product> findProductsOrThrow(List<UUID> productIds) {
		if (productIds == null) {
			return new HashSet<>();
		}
		return productIds.stream()
			.map(id -> productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id)))
			.collect(Collectors.toCollection(HashSet::new));
	}

	private LeadResponse toResponse(Lead lead) {
		LeadOrigin origin = lead.getOrigin();
		LeadOriginResponse originResponse = new LeadOriginResponse(origin.getId(), origin.getOriginType(),
				origin.getChannel(), origin.getSearchSource(), origin.getRegion(), origin.getSearchSegment(),
				origin.getCaptureMethod());

		List<ProductResponse> products = lead.getProductsOfInterest()
			.stream()
			.map(product -> new ProductResponse(product.getId(), product.getName(), product.getType(),
					product.getDescription(), product.getMinPriceCents(), product.getMaxPriceCents(),
					product.isActive(), product.getCreatedAt()))
			.toList();

		List<FunnelStatusHistoryResponse> statusHistory = funnelStatusHistoryRepository
			.findByLeadIdOrderByChangedAtAsc(lead.getId())
			.stream()
			.map(history -> new FunnelStatusHistoryResponse(history.getId(), history.getPreviousStatus(),
					history.getNewStatus(), history.getUser().getId(), history.getUser().getName(),
					history.getReason(), history.getChangedAt()))
			.toList();

		return new LeadResponse(lead.getId(), lead.getName(), lead.getLeadType(), lead.getPhone(), lead.getEmail(),
				lead.getInitialMessage(), lead.getEstimatedBudgetCents(), lead.getDesiredTimeline(),
				lead.getQualificationScore(), lead.getFunnelStatus(), lead.getLossReason(), originResponse,
				lead.getAssignedUser().getId(), lead.getAssignedUser().getName(), products, statusHistory,
				lead.getCreatedAt(), lead.getUpdatedAt());
	}

}

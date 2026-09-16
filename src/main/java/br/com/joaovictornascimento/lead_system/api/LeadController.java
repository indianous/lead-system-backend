package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.FunnelStatus;
import br.com.joaovictornascimento.lead_system.domain.LeadType;
import br.com.joaovictornascimento.lead_system.domain.User;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leads")
@PreAuthorize("hasAnyAuthority('VIEW_OWN_LEADS','VIEW_ALL_LEADS')")
public class LeadController {

	private final LeadService leadService;

	public LeadController(LeadService leadService) {
		this.leadService = leadService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public LeadResponse create(@Valid @RequestBody CreateLeadRequest request, @AuthenticationPrincipal User currentUser) {
		return leadService.create(request, currentUser);
	}

	@GetMapping
	public List<LeadResponse> list(@RequestParam(required = false) LeadType leadType,
			@RequestParam(required = false) Channel channel, @RequestParam(required = false) FunnelStatus funnelStatus,
			@RequestParam(required = false) UUID assignedUserId, @AuthenticationPrincipal User currentUser) {
		return leadService.findAll(currentUser, leadType, channel, funnelStatus, assignedUserId);
	}

	@GetMapping("/{id}")
	public LeadResponse getById(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
		return leadService.findById(id, currentUser);
	}

	@PutMapping("/{id}")
	public LeadResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateLeadRequest request,
			@AuthenticationPrincipal User currentUser) {
		return leadService.update(id, request, currentUser);
	}

	@PatchMapping("/{id}/status")
	public LeadResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateLeadStatusRequest request,
			@AuthenticationPrincipal User currentUser) {
		return leadService.updateStatus(id, request, currentUser);
	}

}

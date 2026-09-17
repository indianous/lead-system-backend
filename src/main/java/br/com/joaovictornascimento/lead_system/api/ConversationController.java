package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.User;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyAuthority('VIEW_OWN_LEADS','VIEW_ALL_LEADS')")
public class ConversationController {

	private final ConversationService conversationService;

	public ConversationController(ConversationService conversationService) {
		this.conversationService = conversationService;
	}

	@PostMapping("/api/leads/{leadId}/conversations")
	public ResponseEntity<Object> create(@PathVariable UUID leadId, @Valid @RequestBody CreateConversationRequest request,
			@AuthenticationPrincipal User currentUser) {
		ConversationCreationResult result = conversationService.createConversation(leadId, request, currentUser);
		if (result.conversation() != null) {
			return ResponseEntity.status(HttpStatus.CREATED).body(result.conversation());
		}
		return ResponseEntity.ok(Map.of("deepLink", result.telegramDeepLink()));
	}

	@GetMapping("/api/leads/{leadId}/conversations")
	public List<ConversationResponse> list(@PathVariable UUID leadId, @AuthenticationPrincipal User currentUser) {
		return conversationService.findByLead(leadId, currentUser);
	}

	@PostMapping("/api/conversations/{conversationId}/messages")
	public MessageResponse sendMessage(@PathVariable UUID conversationId, @Valid @RequestBody CreateMessageRequest request,
			@AuthenticationPrincipal User currentUser) {
		return conversationService.sendMessage(conversationId, request, currentUser);
	}

	@GetMapping("/api/conversations/{conversationId}/messages")
	public List<MessageResponse> listMessages(@PathVariable UUID conversationId, @AuthenticationPrincipal User currentUser) {
		return conversationService.findMessages(conversationId, currentUser);
	}

}

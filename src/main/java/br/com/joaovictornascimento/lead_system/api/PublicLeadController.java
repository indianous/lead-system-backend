package br.com.joaovictornascimento.lead_system.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/leads")
@Tag(name = "Leads públicos",
		description = "Endpoint de integração para o formulário de contato do site institucional (fora do escopo deste projeto)")
public class PublicLeadController {

	private final PublicLeadService publicLeadService;

	public PublicLeadController(PublicLeadService publicLeadService) {
		this.publicLeadService = publicLeadService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Recebe um lead do formulário de contato do site",
			description = "Cria um Lead com origem DIRECT_CONTACT/WEBSITE e captureMethod=API, atribuído "
					+ "automaticamente a um usuário padrão para triagem manual (reatribuído depois por um "
					+ "vendedor/gestor via PUT /api/leads/{id}).",
			security = @SecurityRequirement(name = "publicApiKey"))
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Lead criado com sucesso",
					content = @Content(schema = @Schema(implementation = PublicLeadResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Payload inválido (nome em branco, ou nem telefone nem e-mail informados)"),
			@ApiResponse(responseCode = "401", description = "API key ausente ou inválida"),
			@ApiResponse(responseCode = "429", description = "Limite de requisições excedido para este IP") })
	public PublicLeadResponse create(@Valid @RequestBody CreatePublicLeadRequest request) {
		return publicLeadService.create(request);
	}

}

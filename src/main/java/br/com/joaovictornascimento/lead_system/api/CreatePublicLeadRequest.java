package br.com.joaovictornascimento.lead_system.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Submissão do formulário de contato do site institucional")
public record CreatePublicLeadRequest(@Schema(description = "Nome do lead") @NotBlank String name,
		@Schema(description = "Telefone/WhatsApp para contato") String phone,
		@Schema(description = "E-mail para contato") @Email String email,
		@Schema(description = "Mensagem enviada no formulário") String initialMessage) {

	@AssertTrue(message = "Informe phone ou email para contato")
	public boolean isContactInfoPresent() {
		return (phone != null && !phone.isBlank()) || (email != null && !email.isBlank());
	}

}

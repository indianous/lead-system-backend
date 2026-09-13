package br.com.joaovictornascimento.lead_system.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateUserRequest(@NotBlank String name, @NotBlank @Email String email,
		@NotBlank @Size(min = 8) String password, @NotNull UUID roleId) {
}

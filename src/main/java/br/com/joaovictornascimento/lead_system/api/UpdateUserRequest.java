package br.com.joaovictornascimento.lead_system.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateUserRequest(@NotBlank String name, @NotNull UUID roleId, boolean active) {
}

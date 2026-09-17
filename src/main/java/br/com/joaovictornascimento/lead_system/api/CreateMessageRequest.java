package br.com.joaovictornascimento.lead_system.api;

import jakarta.validation.constraints.NotBlank;

public record CreateMessageRequest(@NotBlank String content) {
}

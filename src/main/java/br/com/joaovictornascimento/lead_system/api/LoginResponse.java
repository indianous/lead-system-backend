package br.com.joaovictornascimento.lead_system.api;

import java.util.UUID;

public record LoginResponse(UUID id, String name, String email, String token, String role) {
}

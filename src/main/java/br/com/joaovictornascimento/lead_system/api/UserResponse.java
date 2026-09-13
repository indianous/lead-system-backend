package br.com.joaovictornascimento.lead_system.api;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, String roleName, boolean active, Instant createdAt) {
}

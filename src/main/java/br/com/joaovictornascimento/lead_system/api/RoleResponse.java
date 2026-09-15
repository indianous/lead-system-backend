package br.com.joaovictornascimento.lead_system.api;

import java.util.List;
import java.util.UUID;

public record RoleResponse(UUID id, String name, String description, List<String> permissions) {
}

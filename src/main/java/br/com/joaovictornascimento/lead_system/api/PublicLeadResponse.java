package br.com.joaovictornascimento.lead_system.api;

import java.time.Instant;
import java.util.UUID;

public record PublicLeadResponse(UUID id, Instant createdAt) {
}

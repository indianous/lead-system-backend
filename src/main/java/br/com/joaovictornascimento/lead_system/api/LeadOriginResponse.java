package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.CaptureMethod;
import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.LeadType;
import br.com.joaovictornascimento.lead_system.domain.SearchSource;
import java.util.UUID;

public record LeadOriginResponse(UUID id, LeadType originType, Channel channel, SearchSource searchSource,
		String region, String searchSegment, CaptureMethod captureMethod) {
}

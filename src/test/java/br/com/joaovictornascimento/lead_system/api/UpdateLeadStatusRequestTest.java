package br.com.joaovictornascimento.lead_system.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.joaovictornascimento.lead_system.domain.FunnelStatus;
import org.junit.jupiter.api.Test;

class UpdateLeadStatusRequestTest {

	@Test
	void lostWithReasonIsValid() {
		var request = new UpdateLeadStatusRequest(FunnelStatus.LOST, "Cliente não respondeu");

		assertThat(request.isReasonPresentWhenLost()).isTrue();
	}

	@Test
	void lostWithoutReasonIsInvalid() {
		var request = new UpdateLeadStatusRequest(FunnelStatus.LOST, null);

		assertThat(request.isReasonPresentWhenLost()).isFalse();
	}

	@Test
	void lostWithBlankReasonIsInvalid() {
		var request = new UpdateLeadStatusRequest(FunnelStatus.LOST, "   ");

		assertThat(request.isReasonPresentWhenLost()).isFalse();
	}

	@Test
	void anyOtherStatusWithoutReasonIsValid() {
		var request = new UpdateLeadStatusRequest(FunnelStatus.CONTACTED, null);

		assertThat(request.isReasonPresentWhenLost()).isTrue();
	}

}

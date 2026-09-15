package br.com.joaovictornascimento.lead_system.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.LeadType;
import br.com.joaovictornascimento.lead_system.domain.SearchSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateLeadRequestTest {

	private static CreateLeadRequest request(LeadType leadType, Channel channel, SearchSource searchSource,
			String region, String searchSegment) {
		return new CreateLeadRequest("Lead teste", leadType, null, null, null, null, null, null,
				UUID.randomUUID(), null, channel, searchSource, region, searchSegment);
	}

	@Test
	void directContactWithChannelAndNoSearchFieldsIsValid() {
		CreateLeadRequest request = request(LeadType.DIRECT_CONTACT, Channel.META_WHATSAPP, null, null, null);

		assertThat(request.isOriginConsistent()).isTrue();
	}

	@Test
	void directContactWithoutChannelIsInvalid() {
		CreateLeadRequest request = request(LeadType.DIRECT_CONTACT, null, null, null, null);

		assertThat(request.isOriginConsistent()).isFalse();
	}

	@Test
	void directContactWithSearchFieldsFilledIsInvalid() {
		CreateLeadRequest request = request(LeadType.DIRECT_CONTACT, Channel.TELEGRAM, SearchSource.GOOGLE_MAPS,
				"São Paulo", null);

		assertThat(request.isOriginConsistent()).isFalse();
	}

	@Test
	void localSearchWithSourceRegionAndSegmentIsValid() {
		CreateLeadRequest request = request(LeadType.LOCAL_SEARCH, null, SearchSource.GOOGLE_MAPS, "São Paulo",
				"Restaurantes");

		assertThat(request.isOriginConsistent()).isTrue();
	}

	@Test
	void localSearchMissingSourceIsInvalid() {
		CreateLeadRequest request = request(LeadType.LOCAL_SEARCH, null, null, "São Paulo", "Restaurantes");

		assertThat(request.isOriginConsistent()).isFalse();
	}

	@Test
	void localSearchMissingRegionIsInvalid() {
		CreateLeadRequest request = request(LeadType.LOCAL_SEARCH, null, SearchSource.GOOGLE_MAPS, null,
				"Restaurantes");

		assertThat(request.isOriginConsistent()).isFalse();
	}

	@Test
	void localSearchMissingSegmentIsInvalid() {
		CreateLeadRequest request = request(LeadType.LOCAL_SEARCH, null, SearchSource.GOOGLE_MAPS, "São Paulo", null);

		assertThat(request.isOriginConsistent()).isFalse();
	}

	@Test
	void localSearchWithChannelFilledIsInvalid() {
		CreateLeadRequest request = request(LeadType.LOCAL_SEARCH, Channel.TELEGRAM, SearchSource.GOOGLE_MAPS,
				"São Paulo", "Restaurantes");

		assertThat(request.isOriginConsistent()).isFalse();
	}

}

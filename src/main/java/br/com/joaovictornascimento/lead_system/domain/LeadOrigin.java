package br.com.joaovictornascimento.lead_system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "lead_origins")
public class LeadOrigin {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "origin_type", nullable = false, length = 20)
	private LeadType originType;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private Channel channel;

	@Enumerated(EnumType.STRING)
	@Column(name = "search_source", length = 30)
	private SearchSource searchSource;

	private String region;

	@Column(name = "search_segment")
	private String searchSegment;

	@Enumerated(EnumType.STRING)
	@Column(name = "capture_method", nullable = false, length = 20)
	private CaptureMethod captureMethod;

	protected LeadOrigin() {
	}

	public LeadOrigin(LeadType originType, Channel channel, SearchSource searchSource, String region,
			String searchSegment, CaptureMethod captureMethod) {
		this.originType = originType;
		this.channel = channel;
		this.searchSource = searchSource;
		this.region = region;
		this.searchSegment = searchSegment;
		this.captureMethod = captureMethod;
	}

	public UUID getId() {
		return id;
	}

	public LeadType getOriginType() {
		return originType;
	}

	public Channel getChannel() {
		return channel;
	}

	public SearchSource getSearchSource() {
		return searchSource;
	}

	public String getRegion() {
		return region;
	}

	public String getSearchSegment() {
		return searchSegment;
	}

	public CaptureMethod getCaptureMethod() {
		return captureMethod;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof LeadOrigin other)) {
			return false;
		}
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

}

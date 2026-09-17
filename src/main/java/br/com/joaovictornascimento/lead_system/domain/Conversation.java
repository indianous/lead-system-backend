package br.com.joaovictornascimento.lead_system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lead_id", nullable = false)
	private Lead lead;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Channel channel;

	@Column(name = "external_thread_id", nullable = false)
	private String externalThreadId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ConversationStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Conversation() {
	}

	public Conversation(Lead lead, Channel channel, String externalThreadId) {
		this.lead = lead;
		this.channel = channel;
		this.externalThreadId = externalThreadId;
		this.status = ConversationStatus.OPEN;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public Lead getLead() {
		return lead;
	}

	public Channel getChannel() {
		return channel;
	}

	public String getExternalThreadId() {
		return externalThreadId;
	}

	public ConversationStatus getStatus() {
		return status;
	}

	public void setStatus(ConversationStatus status) {
		this.status = status;
	}

	/**
	 * Marca a conversa como atualizada agora — chamado sempre que uma Message nova é gravada
	 * (envio ou recebimento), já que só alterar a Message não dispara o @PreUpdate desta entidade.
	 * Usado para ordenar a inbox pela mensagem mais recente ({@code updated_at}).
	 */
	public void touch() {
		this.updatedAt = Instant.now();
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Conversation other)) {
			return false;
		}
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

}

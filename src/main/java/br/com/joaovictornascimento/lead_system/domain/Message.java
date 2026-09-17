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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class Message {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conversation_id", nullable = false)
	private Conversation conversation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MessageDirection direction;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "sender_user_id")
	private User senderUser;

	@Column(nullable = false)
	private String content;

	@Column(name = "media_url")
	private String mediaUrl;

	@Column(name = "external_message_id")
	private String externalMessageId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MessageStatus status;

	@Column(name = "sent_at", nullable = false, updatable = false)
	private Instant sentAt;

	protected Message() {
	}

	public Message(Conversation conversation, MessageDirection direction, User senderUser, String content,
			String mediaUrl, String externalMessageId, MessageStatus status) {
		this.conversation = conversation;
		this.direction = direction;
		this.senderUser = senderUser;
		this.content = content;
		this.mediaUrl = mediaUrl;
		this.externalMessageId = externalMessageId;
		this.status = status;
	}

	@PrePersist
	void onCreate() {
		this.sentAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public Conversation getConversation() {
		return conversation;
	}

	public MessageDirection getDirection() {
		return direction;
	}

	public User getSenderUser() {
		return senderUser;
	}

	public String getContent() {
		return content;
	}

	public String getMediaUrl() {
		return mediaUrl;
	}

	public String getExternalMessageId() {
		return externalMessageId;
	}

	public void setExternalMessageId(String externalMessageId) {
		this.externalMessageId = externalMessageId;
	}

	public MessageStatus getStatus() {
		return status;
	}

	public void setStatus(MessageStatus status) {
		this.status = status;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Message other)) {
			return false;
		}
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

}

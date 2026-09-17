package br.com.joaovictornascimento.lead_system.config;

import br.com.joaovictornascimento.lead_system.realtime.StompAuthChannelInterceptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Endpoint STOMP do chat em tempo real (Etapa 9). Sem SockJS: o frontend conecta com WebSocket
 * nativo via {@code @stomp/stompjs}. Conversor Jackson registrado explicitamente (em vez de
 * confiar na auto-detecção do Spring) para não repetir a ambiguidade Jackson 2/3 da Etapa 8 —
 * `tools.jackson` (Jackson 3) é o que o Spring Boot 4.1 autoconfigura por padrão.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final List<String> allowedOrigins;

	private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

	public WebSocketConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins,
			StompAuthChannelInterceptor stompAuthChannelInterceptor) {
		this.allowedOrigins = allowedOrigins;
		this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(stompAuthChannelInterceptor);
	}

	@Override
	public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
		messageConverters.add(new JacksonJsonMessageConverter());
		return false;
	}

}

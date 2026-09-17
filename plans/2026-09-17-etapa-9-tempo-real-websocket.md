# Etapa 9 — Tempo real (WebSocket/STOMP)

> Ref.: roadmap (`plans/roadmap.md`, Etapa 9), `00-documentation/04-rotas-e-telas.md` (`/leads/[id]/chat`: "Mensagens novas chegam em tempo real (WebSocket)"; `/inbox`: lista unificada de conversas). Depende da Etapa 8 (Central de Mensagens — `Conversation`/`Message`, webhooks WhatsApp/Telegram, `ConversationService`), já concluída.

Objetivo: quando uma `Message` nova é persistida — seja porque o vendedor enviou pela tela de chat (`POST /api/conversations/{id}/messages`), seja porque chegou por webhook do WhatsApp/Telegram — os clientes conectados que têm aquela conversa aberta recebem a mensagem via WebSocket, sem precisar dar refresh.

Escopo desta etapa: **broadcast de mensagem nova**, conforme literalmente descrito no roadmap. Atualização de `Message.status` (`SENT`→`DELIVERED`→`READ`, vinda de callback do WhatsApp) **não** entra no broadcast agora — `04-rotas-e-telas.md` só promete tempo real para mensagens novas, não para tique de status; fica como possível extensão futura, fora desta etapa.

---

## 1. Problemas encontrados / decisões de design

Como em toda etapa nova (sem código existente para essa funcionalidade), esta seção lista lacunas reais que precisam de uma decisão antes de codar — não bugs em código já escrito.

1. **JWT não pode viajar em header HTTP no handshake WebSocket.** Todo o resto da API autentica via header `Authorization: Bearer <token>` em uma request HTTP comum (`JwtAuthenticationFilter`). O stack do frontend definido no `CLAUDE.md`/`02-bibliotecas-e-apis.md` é `@stomp/stompjs` puro (sem `sockjs-client`), ou seja, a conexão é um WebSocket nativo do navegador — a API `WebSocket` do browser não permite setar headers HTTP customizados na request de upgrade. Decisão: autenticar no **frame STOMP `CONNECT`**, não no handshake HTTP. `@stomp/stompjs` permite `connectHeaders: { Authorization: "Bearer <token>" }`, então o nome do header fica consistente com o resto da API, só muda a camada em que é lido (um `ChannelInterceptor` no canal de entrada do broker, não mais um `OncePerRequestFilter`).
2. **Subscrição em `/topic/conversations/{id}` não tem ACL nenhuma por padrão.** O `SimpleBroker` do Spring não sabe nada sobre `Lead`/`Conversation` — qualquer client autenticado poderia, em tese, se inscrever no tópico de uma conversa de um lead que não é dele (violando a mesma regra de `VIEW_OWN_LEADS`/`VIEW_ALL_LEADS` já aplicada em `GET /api/conversations/{id}/messages`). Decisão: o mesmo `ChannelInterceptor` que autentica o `CONNECT` também intercepta o frame `SUBSCRIBE`, extrai o `conversationId` do destino (`/topic/conversations/{conversationId}`), carrega a `Conversation`, e reusa `LeadService.findAccessibleLeadOrThrow(conversation.getLead().getId(), user)` — a mesma checagem de acesso já usada por `ConversationService`/`InteractionService`/etc. Falha na checagem rejeita a subscrição.
3. **`SecurityConfig.authorizeHttpRequests` cobre só a request HTTP.** Como a autenticação real acontece dentro do frame STOMP (item 1), a rota HTTP do endpoint (`/ws`) precisa ser alcançável sem JWT na camada de `SecurityFilterChain` — mesmo padrão já usado para `/api/webhooks/whatsapp`/`/api/webhooks/telegram` (`permitAll()` no filtro HTTP, autenticação de verdade feita dentro da aplicação).
4. **Ambiguidade de conversor Jackson, repetição do problema da Etapa 8.** `spring-messaging` traz dois conversores STOMP↔JSON: `JacksonJsonMessageConverter` (Jackson 3, `tools.jackson.databind` — o que o Spring Boot 4.1.1 autoconfigura por padrão, confirmado na Etapa 8) e o legado `MappingJackson2MessageConverter` (Jackson 2, só presente no classpath via `jjwt-jackson`). Decisão: registrar explicitamente `JacksonJsonMessageConverter` em `WebSocketConfig` (`registry.setMessageConverter(...)`) em vez de confiar em auto-detecção — mesma postura defensiva adotada depois do incidente da Etapa 8.
5. **Três pontos de código persistem `Message` hoje, todos precisam disparar o broadcast:** `ConversationService.sendMessage` (envio pelo vendedor), `WhatsAppWebhookService.handleInboundMessage`, `TelegramWebhookService.process` (branch de mensagem regular — `handleStart` não cria `Message`, só `Conversation`, não dispara nada). Decisão: extrair um componente único (`ConversationBroadcaster`), injetado nos três, chamado logo depois de `messageRepository.save(...)`. Isso também exige um jeito único de mapear `Message → MessageResponse` sem duplicar o mapeamento que já existe (privado) em `ConversationService.toResponse(Message)` — decisão: virar um factory estático `MessageResponse.from(Message)`, reusado pelo `ConversationService` e pelo `ConversationBroadcaster`.
6. **CORS do endpoint STOMP** precisa da mesma lista de origens já usada pelo `CorsConfigurationSource` (`app.cors.allowed-origins`) — `StompEndpointRegistry.addEndpoint("/ws").setAllowedOriginPatterns(...)`, senão o handshake é bloqueado pelo navegador nos ambientes de desenvolvimento (Tailscale) já documentados na Etapa 1/4.

---

## 2. Testes a incluir/alterar

Tudo novo (funcionalidade inexistente até agora). Agrupado por arquivo:

- **`src/test/java/.../realtime/ConversationBroadcasterTest.java`** (unitário, `SimpMessagingTemplate` mockado via Mockito puro, sem `@SpringBootTest`):
  - `broadcast` chama `SimpMessagingTemplate.convertAndSend("/topic/conversations/" + conversationId, ...)` com o `MessageResponse` esperado.

- **`src/test/java/.../api/MessageResponseTest.java`** (unitário, opcional/curto):
  - `MessageResponse.from(Message)` mapeia os campos corretamente (inclusive `senderUser` nulo, caso de mensagem `INBOUND`).

- **`src/test/java/.../realtime/ChatWebSocketIntegrationTest.java`** (integração, `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestcontainersConfiguration`, `WebSocketStompClient`/`StandardWebSocketClient` reais conectando em `ws://localhost:{port}/ws`):
  - Conecta com JWT válido (header `Authorization` no frame `CONNECT`), inscreve em `/topic/conversations/{id}` de uma conversa do próprio usuário (`VIEW_OWN_LEADS`), dispara `POST /api/conversations/{id}/messages`, espera receber o mesmo `MessageResponse` no tópico.
  - Mesma coisa, mas a mensagem chega via webhook do WhatsApp (payload assinado) em vez de via REST — confirma a integração `WhatsAppWebhookService → ConversationBroadcaster`.
  - Mesma coisa via webhook do Telegram (update regular sobre uma `Conversation` já existente) — confirma `TelegramWebhookService → ConversationBroadcaster`.
  - Conecta sem header `Authorization` no `CONNECT` → conexão/subscrição rejeitada (frame `ERROR` ou fechamento, o que a implementação de fato produzir — fixar a asserção durante o TDD).
  - Conecta com JWT válido mas tenta se inscrever na conversa de um lead de **outro** vendedor (`VIEW_OWN_LEADS`, sem `VIEW_ALL_LEADS`) → subscrição rejeitada, nenhuma mensagem chega.
  - Conecta com JWT de usuário com `VIEW_ALL_LEADS` (Manager/Administrator) e se inscreve na conversa de um lead de outro vendedor → subscrição aceita, mensagem chega normalmente.

- **`src/main/java/.../security/SecurityConfig.java`** — não é teste, mas `/ws/**` entra no `permitAll()`; os testes HTTP existentes (`AuthControllerTest` etc.) continuam cobrindo que nenhuma outra rota ficou aberta por engano.

---

## 3. O que entra no projeto

Só backend nesta etapa (frontend consome em uma etapa própria do roadmap do frontend, fora deste plano).

- **`domain`**: nenhuma mudança — reusa `Message`/`Conversation`/`ConversationRepository` já existentes.
- **`api`**: `MessageResponse` ganha o factory estático `MessageResponse.from(Message)`; `ConversationService.toResponse(Message)` passa a delegar para ele (elimina a duplicação).
- **novo pacote `realtime`** (`package-info.java`: "Broadcast de eventos em tempo real via WebSocket/STOMP para o chat integrado."):
  - `StompAuthChannelInterceptor implements ChannelInterceptor` — `preSend`: em `CONNECT`, lê o header nativo `Authorization`, valida via `JwtService.parseUserId` + `UserRepository.findById(...).filter(User::isActive)` (mesma regra do `JwtAuthenticationFilter` — usuário inativo não autentica), seta o `Principal` na sessão STOMP; em `SUBSCRIBE` a `/topic/conversations/{id}`, resolve a `Conversation`, chama `LeadService.findAccessibleLeadOrThrow`; qualquer falha rejeita o frame.
  - `ConversationBroadcaster` — `void broadcast(Message message)`, usa `SimpMessagingTemplate.convertAndSend("/topic/conversations/" + message.getConversation().getId(), MessageResponse.from(message))`.
- **`config/WebSocketConfig.java`** (`@Configuration @EnableWebSocketMessageBroker`, implementa `WebSocketMessageBrokerConfigurer`):
  - `registerStompEndpoints`: `registry.addEndpoint("/ws").setAllowedOriginPatterns(<app.cors.allowed-origins>)` (sem SockJS — stack usa `@stomp/stompjs` puro).
  - `configureMessageBroker`: `enableSimpleBroker("/topic")`.
  - `configureClientInboundChannel`: registra o `StompAuthChannelInterceptor`.
  - Registra explicitamente `JacksonJsonMessageConverter` como conversor de mensagem (ver decisão 4 da seção 1).
- **`security/SecurityConfig.java`**: adiciona `/ws/**` à lista de `permitAll()`, com o mesmo comentário de por que (autenticação real acontece dentro do frame STOMP, não na filter chain HTTP).
- **`ConversationService.sendMessage`**, **`WhatsAppWebhookService.handleInboundMessage`**, **`TelegramWebhookService.process`** (branch de mensagem regular): recebem `ConversationBroadcaster` no construtor, chamam `broadcast(message)` logo após `messageRepository.save(...)`.

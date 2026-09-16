# Plano — Backend Etapa 8: Central de Mensagens (Conversation/Message)

> Referência: `backend/plans/roadmap.md` (Etapa 8), `00-documentation/03-entidades.md`
> (`Conversation`, `Message`), `00-documentation/04-rotas-e-telas.md` (`/inbox`,
> `/leads/[id]/chat`, `/api/webhooks/whatsapp`, `/api/webhooks/telegram`),
> `00-documentation/01-estudo-de-caso-coleta-leads.md` (seção "Central de mensagens", riscos da
> janela de 24h do WhatsApp e da exigência do Telegram de o lead iniciar a conversa),
> `00-documentation/02-bibliotecas-e-apis.md` (WhatsApp Business Platform, Telegram Bot API,
> verificação de assinatura de webhook, Bucket4j — já em uso desde a Etapa 4).
>
> **Fora deste plano, de propósito**: a atualização em tempo real da conversa no frontend
> (WebSocket/STOMP) é a Etapa 9 — aqui a conversa é só persistida e exposta via REST.

## 1. Problemas encontrados

- Não existem as entidades `Conversation`/`Message` nem nenhuma integração com WhatsApp/Telegram
  — hoje `Interaction` (Etapa 6) é o único histórico de contato com o lead, e é explicitamente
  **não** para mensagens de chat (`03-entidades.md#Interaction`).
- **Credenciais reais não existem ainda.** O próprio estudo de caso lista, como próximo passo
  ainda pendente (`01-estudo-de-caso-coleta-leads.md`, "Próximos Passos", item 6): "Confirmar
  acesso às... WhatsApp Business Platform / Telegram Bot API para a central de mensagens
  (aprovação de conta comercial, criação e verificação do bot, configuração de webhook)". Ou
  seja: implemento os clients reais (chamadas HTTP de verdade), mas **não há como testar envio
  de mensagem de verdade** nesta sessão — os testes usam Mockito/WireMock (como o roadmap já
  prevê), e a verificação "rodar a aplicação real" desta etapa fica limitada a: persistência,
  endpoints REST, verificação de assinatura de webhook e o client HTTP sendo chamado
  corretamente (contra um servidor fake local, não a Meta/Telegram de verdade).
- **O roadmap só compromete integração real com dois canais** — "Integração WhatsApp Business
  Platform" e "Integração Telegram Bot API" (não lista Instagram Direct nem Messenger, mesmo
  esses sendo valores válidos de `Channel` desde a Etapa 3, usados em `LeadOrigin.channel`).
  Decisão: `Conversation` só pode ser criada/enviar mensagem real para `META_WHATSAPP` e
  `TELEGRAM`; tentar `META_INSTAGRAM`/`META_MESSENGER` retorna 400 ("canal ainda não suportado
  pela central de mensagens") — consistente com o que o roadmap efetivamente pede, sem inventar
  integração com a API do Instagram/Messenger que não foi pedida.
- **Lacuna real de arquitetura, não coberta por nenhum documento**: como o sistema descobre a
  **qual Lead** pertence uma mensagem recebida via webhook?
  - **WhatsApp**: viável — o payload traz o número de telefone do remetente; casa por
    `Lead.phone` (normalizado). Se nenhum lead tiver esse telefone, a mensagem é descartada (logada,
    não vira erro) — não há como criar uma `Conversation` sem um `lead_id` (obrigatório).
  - **Telegram**: **não é viável por número de telefone** — o `chat_id` do Telegram é opaco e
    não tem nenhuma relação com nenhum campo do `Lead`, e a Bot API só revela o `chat_id` depois
    que o usuário manda a primeira mensagem ao bot (o próprio estudo de caso confirma essa
    limitação, seção Riscos: "o vendedor precisa compartilhar o link/usuário do bot... e aguardar
    o cliente iniciar"). Decisão (não documentada explicitamente em nenhum arquivo, é a forma
    padrão de resolver esse problema com bots do Telegram): usar o **parâmetro `start` de deep
    link** (`t.me/<bot>?start=<payload>`) — `POST /api/leads/{id}/conversations` com
    `channel=TELEGRAM` **não cria a `Conversation` na hora**; devolve um link
    `t.me/<bot_username>?start=<leadId em hex>` para o vendedor mandar ao lead por fora do
    sistema (WhatsApp, e-mail etc.). A `Conversation` só nasce quando o webhook recebe a mensagem
    `/start <leadId>` — nesse momento o `chat_id` fica conhecido e associado ao lead certo.
- **Janela de 24h do WhatsApp** (estudo de caso, Riscos): mensagens de formato livre só funcionam
  dentro de 24h da última mensagem do lead; fora da janela, a Cloud API rejeita a chamada.
  Decisão: **não** implementar suporte a templates pré-aprovados nesta etapa (não pedido pelo
  roadmap) — se a Meta rejeitar por estar fora da janela, a `Message` fica com `status=FAILED` e
  o motivo do erro (texto cru retornado pela API) fica só em log, sem novo campo na entidade.
- **Telegram não expõe confirmação de entrega/leitura** pela Bot API (diferente do WhatsApp, que
  manda eventos `statuses` de `sent`/`delivered`/`read`) — mensagens `OUTBOUND` no Telegram
  ficam em `SENT` (ou `FAILED`) e nunca avançam para `DELIVERED`/`READ`; não é uma lacuna da
  implementação, é limitação da própria plataforma.
- Verificação de assinatura de webhook **não é a mesma para os dois provedores**, mesmo
  `02-bibliotecas-e-apis.md` descrevendo genericamente "HMAC-SHA256" para os dois: a Meta assina
  o corpo cru com `X-Hub-Signature-256` (HMAC-SHA256 usando o *App Secret*); o Telegram, em vez
  de assinar o corpo, manda um `secret_token` fixo (configurado uma vez via `setWebhook`) no
  header `X-Telegram-Bot-Api-Secret-Token`, comparado por igualdade (mesmo padrão de comparação
  em tempo constante já usado em `PublicApiKeyFilter.matches`, Etapa 4). Implemento os dois
  mecanismos, documentando a diferença.
- `/api/webhooks/whatsapp` e `/api/webhooks/telegram` precisam ficar fora do `permitAll()`
  genérico de autenticação de usuário — a segurança deles é a verificação de assinatura feita
  dentro do próprio controller, não JWT nem API key. `SecurityConfig` precisa liberar essas duas
  rotas no `permitAll()` (a checagem real acontece depois, no controller).
- Nenhuma dependência de teste HTTP-mock (WireMock) está no `pom.xml` ainda — o roadmap já pede
  "mocks das APIs externas (Mockito/WireMock)"; preciso adicionar `wiremock` como dependência de
  teste.
- `Conversation.channel` reaproveita o enum `Channel` já existente (`domain/Channel.java`, Etapa
  3) — sem enum novo; só valido em código que `WEBSITE` nunca é aceito aqui (não é canal de
  conversa contínua, `03-entidades.md#Conversation`).

## 2. Testes a incluir/alterar (agrupados por arquivo)

- `messaging/WhatsAppSignatureVerifierTest.java` (novo, JUnit puro) — HMAC-SHA256 correta →
  válida; corpo alterado → inválida; header ausente/malformado → inválida.
- `messaging/TelegramSecretTokenVerifierTest.java` (novo, JUnit puro) — mesmo padrão de
  `PublicApiKeyFilterTest`: token correto → válido; errado/vazio/nulo → inválido.
- `messaging/WhatsAppMessageChannelClientTest.java` (novo, WireMock) — monta a chamada HTTP
  correta (`Authorization`, corpo JSON, endpoint com `phone-number-id`); mapeia resposta de
  sucesso para `externalMessageId` presente; mapeia erro HTTP (ex.: fora da janela de 24h) para
  falha, sem lançar exceção não tratada.
- `messaging/TelegramMessageChannelClientTest.java` (novo, WireMock) — mesmo padrão, para
  `sendMessage` do Telegram.
- `api/ConversationControllerTest.java` (novo, MockMvc + Testcontainers, `MessageChannelClient`
  substituído por um fake/mock via `@TestConfiguration` — sem chamada de rede real)
  - `POST /api/leads/{id}/conversations` com `channel=META_WHATSAPP` e lead sem telefone → 400
  - `POST .../conversations` com `channel=META_WHATSAPP` e lead com telefone → 201, `Conversation`
    criada com `external_thread_id` = telefone
  - `POST .../conversations` com `channel=TELEGRAM` → 200, devolve o deep link, **sem** criar
    `Conversation` no banco
  - `POST .../conversations` com `channel=META_INSTAGRAM`/`META_MESSENGER` → 400
  - `POST .../conversations` sem permissão de posse do lead (`VIEW_OWN_LEADS` de outro vendedor) → 403
  - `GET /api/leads/{id}/conversations` lista as conversas do lead, respeita posse
  - `POST /api/conversations/{id}/messages` chama o `MessageChannelClient` (fake) e persiste
    `Message` com `direction=OUTBOUND`, `sender_user_id` do usuário autenticado,
    `status=SENT`/`FAILED` conforme o retorno do client
  - `GET /api/conversations/{id}/messages` lista em ordem cronológica
  - Acesso a conversa/mensagem de lead de outro vendedor (`VIEW_OWN_LEADS`) → 403
- `api/WhatsAppWebhookControllerTest.java` (novo, MockMvc + Testcontainers)
  - `GET /api/webhooks/whatsapp` com `hub.verify_token` certo → 200, corpo = `hub.challenge`
  - `GET` com token errado → 403
  - `POST` com assinatura inválida → 401 (sem persistir nada)
  - `POST` com assinatura válida e telefone que bate com um `Lead.phone` existente → cria
    `Conversation` (se não existir) + `Message` `INBOUND`
  - `POST` com telefone que não bate com nenhum lead → 200 (ack), nada persistido
  - `POST` repetido com o mesmo `external_message_id` → idempotente (não duplica `Message`)
  - `POST` com evento de `status` (ex. `delivered`) referenciando `external_message_id` de uma
    `Message` `OUTBOUND` existente → atualiza o `status` dela
- `api/TelegramWebhookControllerTest.java` (novo, MockMvc + Testcontainers)
  - `POST /api/webhooks/telegram` sem o `secret_token` certo no header → 401
  - `POST` com `/start <leadId>` de um `chat_id` novo → cria a `Conversation` (antes inexistente)
    ligada ao lead do `leadId`, sem criar `Message` para o comando `/start` em si
  - `POST` com `/start <leadId>` inválido (lead inexistente) → 200 (ack), nada criado
  - `POST` de uma mensagem comum de um `chat_id` já com `Conversation` → cria `Message` `INBOUND`
  - `POST` repetido com o mesmo `update_id`/`message_id` → idempotente

## 3. O que entra no projeto

1. **`pom.xml`**: adiciona `com.github.tomakehurst:wiremock-jre8` (ou equivalente atual, escopo
   `test`) para os testes de `messaging/*MessageChannelClientTest`.
2. **Migration** `V9__create_conversations_and_messages_tables.sql` — tabelas `conversations`
   (`id`, `lead_id` FK, `channel VARCHAR(20)`, `external_thread_id VARCHAR(255)`,
   `status VARCHAR(10)`, `created_at`, `updated_at`; índice único em
   `(channel, external_thread_id)` — é o que roteia mensagem recebida para a conversa certa) e
   `messages` (`id`, `conversation_id` FK, `direction VARCHAR(10)`,
   `sender_user_id` FK nullable, `content TEXT`, `media_url VARCHAR(500)` nullable,
   `external_message_id VARCHAR(255)` nullable, `status VARCHAR(10)`, `sent_at TIMESTAMPTZ`).
3. **`domain`**: enums `ConversationStatus {OPEN, CLOSED}`, `MessageDirection {INBOUND, OUTBOUND}`,
   `MessageStatus {PENDING, SENT, DELIVERED, READ, FAILED}`; entidades `Conversation`, `Message`;
   `ConversationRepository` (`findByLeadId`, `findByChannelAndExternalThreadId`),
   `MessageRepository` (`findByConversationIdOrderBySentAtAsc`, `findByExternalMessageId`, para
   idempotência e correlação de status).
4. **`messaging`** (pacote já reservado desde a Etapa 0): `MessageChannelClient` (interface —
   `SendMessageResult send(String externalThreadId, String content)`); `WhatsAppMessageChannelClient`
   e `TelegramMessageChannelClient` (`RestClient` do Spring, configurados via
   `app.messaging.whatsapp.*`/`app.messaging.telegram.*`); `WhatsAppSignatureVerifier` (HMAC-SHA256
   sobre o corpo cru); `TelegramSecretTokenVerifier` (comparação em tempo constante, mesmo padrão
   de `PublicApiKeyFilter`).
5. **`application.properties`**: `app.messaging.whatsapp.api-base-url` (default
   `https://graph.facebook.com/v20.0`), `.phone-number-id`, `.access-token`, `.app-secret`,
   `.webhook-verify-token`; `app.messaging.telegram.api-base-url` (default
   `https://api.telegram.org`), `.bot-token`, `.bot-username`, `.webhook-secret-token` — todas
   com default vazio/só-dev, documentando que produção exige valores reais (credenciais ainda não
   confirmadas, ver seção 1).
6. **`api`**: `CreateConversationRequest`/`ConversationResponse`/`StartTelegramConversationResponse`
   (deep link), `CreateMessageRequest`/`MessageResponse`; `ConversationService` (cria conversa
   WhatsApp direto; gera deep link Telegram sem persistir; envia mensagem via
   `MessageChannelClient` resolvido pelo `channel` da conversa; reaproveita
   `LeadService.findAccessibleLeadOrThrow` para a checagem de posse, mesmo padrão da Etapa 6);
   `ConversationController` (`POST/GET /api/leads/{leadId}/conversations`,
   `GET/POST /api/conversations/{id}/messages`); `WhatsAppWebhookController`
   (`GET/POST /api/webhooks/whatsapp`); `TelegramWebhookController`
   (`POST /api/webhooks/telegram`) — os dois webhooks fazem a verificação de assinatura/secret
   token e persistem `Message`/atualizam `Conversation` diretamente (sem passar pela checagem de
   posse de usuário — não há usuário autenticado nesse fluxo).
7. **`security`**: `SecurityConfig` libera `/api/webhooks/whatsapp` e `/api/webhooks/telegram` no
   `permitAll()` (a segurança real é a assinatura, verificada dentro dos controllers).

### Backlog

- Marcar a Etapa 8 em `backend/plans/roadmap.md` como concluída ao final, registrando: o
  mecanismo de deep link do Telegram (não documentado em nenhum arquivo do domínio, decisão
  própria para resolver uma lacuna real), a exclusão de Instagram/Messenger do envio real, e a
  limitação de status de entrega do Telegram.
- Sem credenciais reais de Meta/Telegram, a verificação "rodar a aplicação real" desta etapa fica
  limitada a persistência/endpoints/assinatura — combinar com o usuário se ele tem (ou vai
  conseguir) credenciais de teste (WhatsApp Cloud API tem um modo sandbox com número de teste
  grátis; Telegram é só criar um bot via @BotFather, gratuito e imediato) para validação manual
  fora desta sessão.

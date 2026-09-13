# Roadmap de implementação — Backend

> Referência: `00-documentation/01-estudo-de-caso-coleta-leads.md`, `02-bibliotecas-e-apis.md`, `03-entidades.md`, `04-rotas-e-telas.md`. Segue o processo de TDD definido no `CLAUDE.md` da raiz: cada tarefa abaixo, ao ser iniciada, ganha seu próprio plano de 3 seções (problemas encontrados / testes a incluir / o que entra no projeto) salvo aqui em `plans/` antes de qualquer alteração de código.

Este documento é o backlog macro, em etapas sequenciais. Cada etapa só deve começar depois que a anterior estiver com os testes passando. Dentro de cada etapa, as tarefas são pequenas o bastante para caber em um único ciclo de TDD (teste → implementação → refactor).

---

## Etapa 0 — Fundação de infraestrutura (concluída)

Detalhe completo em `plans/2026-09-12-etapa-0-fundacao.md`.

- [x] Projeto Spring Boot configurado (`pom.xml`, `application.properties`, Maven wrapper, `.gitignore`)
- [x] `docker-compose.yml` com Postgres local de desenvolvimento (porta 5433)
- [x] Estrutura de pacotes base: `domain`, `api` (controllers/DTOs), `config`, `security`, `messaging`, `prospecting`, `notification` (cada um com `package-info.java`)
- [x] Primeira migration Flyway de baseline (`V1__baseline.sql`, sem tabelas ainda)
- [x] Estratégia de geração de UUID definida e documentada: **geração pela aplicação**
  (Hibernate `GenerationType.UUID`), não `gen_random_uuid()` do Postgres — o id fica
  disponível antes do `INSERT`, útil para publicar eventos de domínio/notificações
  na Etapa 8/11 sem depender de uma função específica do banco. Consequência: a
  baseline não precisa de nenhuma extensão do Postgres.
- [x] Testes de integração isolados do Postgres de desenvolvimento: `TestcontainersConfiguration`
  (`@ServiceConnection` com `PostgreSQLContainer`) importada em `LeadSystemApplicationTests`,
  validando que a app sobe e a migration aplica num Postgres 17 descartável

## Etapa 1 — Autenticação e Permissões (RBAC) (concluída)

Entidades: `User`, `Role`, `Permission` (ver `03-entidades.md`). Detalhe completo,
decisões de design e matriz papel→permissão em `plans/2026-09-12-etapa-1-auth-rbac.md`.

- [x] Migration: tabelas `roles`, `permissions`, `role_permissions`, `users` (`V2__create_rbac_tables.sql`)
- [x] Migration: seed das permissões fixas (`CREATE_USER`, `VIEW_ALL_LEADS`, `VIEW_OWN_LEADS`, `EDIT_CATALOG`, `VIEW_METRICS`, `TRIGGER_PROSPECTING`) e dos papéis iniciais (`Salesperson`, `Manager/Administrator`) (`V3__seed_permissions_and_roles.sql`)
- [x] Migration: seed do primeiro usuário administrador — `V4__SeedInitialAdminUser` (bean `JavaMigration` gerenciado pelo Spring, credenciais via `app.security.initial-admin.*`/variável de ambiente, hash via `PasswordEncoder`)
- [x] Entidades JPA `User`, `Role`, `Permission` + repositórios Spring Data
- [x] Hashing de senha via bean `PasswordEncoder` (`BCryptPasswordEncoder`) do Spring Security
- [x] `JwtService` (JJWT): emissão/validação de JWT
- [x] `POST /api/auth/login` (email + senha → JWT), via `AuthService`
- [x] `SecurityFilterChain` + `JwtAuthenticationFilter`: valida o JWT e, a cada request, recarrega o `User` do banco (papel/permissões e `active`) — desativação tem efeito imediato, sem esperar o token expirar
- [x] `POST /api/users` (`CREATE_USER`) — cria usuário, registra `created_by_id`
- [x] `GET /api/users`, `GET /api/users/{id}`, `PUT /api/users/{id}` (`CREATE_USER`)
- [x] Testes unitários: `JwtServiceTest` (geração, expiração, assinatura inválida)
- [x] Testes de integração (MockMvc + Testcontainers): `AuthControllerTest` (login válido/inválido/inativo), `UserControllerTest` (401 sem token, 403 sem permissão, 201/409/404, desativação invalida token já emitido)

## Etapa 2 — Catálogo de Produtos

Entidade: `Product`.

- [ ] Migration: tabela `product`
- [ ] Entidade JPA `Product` + repositório
- [ ] `GET /api/products` (livre), `POST /api/products` (`EDIT_CATALOG`), `PUT /api/products/{id}` (`EDIT_CATALOG`)
- [ ] Validação: `min_price_cents` ≤ `max_price_cents` quando ambos presentes
- [ ] Testes unitários e de integração

## Etapa 3 — Leads e Origem

Entidades: `LeadOrigin`, `Lead`.

- [ ] Migration: tabelas `lead_origin`, `lead`, `lead_product` (join N:N com `product`)
- [ ] Entidades JPA `LeadOrigin`, `Lead` + repositórios
- [ ] Validação condicional por `origin_type`/`lead_type` (campos de `DIRECT_CONTACT` vs. `LOCAL_SEARCH`) na camada de aplicação, não só no banco
- [ ] `POST /api/leads` (cadastro manual — Meta/Telegram/grupos), `GET /api/leads` (filtro por tipo/canal/status/responsável), `GET /api/leads/{id}`, `PUT /api/leads/{id}`
- [ ] Regra de atribuição: `assigned_user_id` obrigatório na criação
- [ ] Testes unitários (validação condicional) e de integração (CRUD completo)

## Etapa 4 — Endpoint público de recepção de leads do site

- [ ] Mecanismo de autenticação por API key/token de integração (implementação própria sobre Spring Security, fora do fluxo de login de usuário)
- [ ] Bucket4j: rate limiting no endpoint público
- [ ] `POST /api/public/leads` — cria `Lead` com `LeadOrigin.channel = WEBSITE`, `capture_method = API`
- [ ] Documentação OpenAPI (springdoc) explícita desse contrato, para o time do site institucional (fora do escopo deste projeto) consumir
- [ ] Testes: token ausente/inválido → 401, payload inválido → 400 (Spring Validation), acima do limite → 429

## Etapa 5 — Funil e Histórico de Status

Entidade: `FunnelStatusHistory`.

- [ ] Migration: tabela `funnel_status_history`
- [ ] Entidade JPA + repositório
- [ ] Regra de transição de status: `reason`/`loss_reason` obrigatório ao mover para `LOST`
- [ ] `PATCH /api/leads/{id}/status` — registra a transição e atualiza `Lead.funnel_status`
- [ ] Testes: transição válida, transição para `LOST` sem motivo → 400

## Etapa 6 — Interações

Entidade: `Interaction`.

- [ ] Migration: tabela `interaction`
- [ ] Entidade JPA + repositório
- [ ] `POST /api/leads/{id}/interactions` (`CALL` ou `NOTE`)
- [ ] `GET /api/leads/{id}/interactions`
- [ ] Testes

## Etapa 7 — Qualificação automática

- [ ] Serviço de qualificação: regra de score (produto de interesse, orçamento estimado, prazo desejado) → `HIGH`/`MEDIUM`/`LOW`
- [ ] Disparo do serviço na criação/atualização do `Lead`
- [ ] Testes unitários cobrindo as combinações de critério → score

## Etapa 8 — Central de mensagens (Conversation/Message)

Entidades: `Conversation`, `Message`.

- [ ] Migration: tabelas `conversation`, `message`
- [ ] Entidades JPA + repositórios
- [ ] Abstração de canal de mensageria (`MessageChannelClient` — interface com implementações WhatsApp/Telegram)
- [ ] Integração WhatsApp Business Platform (Cloud API) — envio (`sendMessage`)
- [ ] Integração Telegram Bot API — envio (`sendMessage`)
- [ ] Utilitário de verificação de assinatura de webhook (HMAC-SHA256)
- [ ] `GET/POST /api/webhooks/whatsapp` (verificação + recebimento de eventos)
- [ ] `POST /api/webhooks/telegram` (recebimento de atualizações)
- [ ] `GET /api/leads/{id}/conversations`, `GET /api/conversations/{id}/messages`, `POST /api/conversations/{id}/messages` (envio pelo vendedor)
- [ ] Atualização de `Message.status` (`SENT`/`DELIVERED`/`READ`/`FAILED`) a partir de callbacks dos provedores
- [ ] Testes: mocks das APIs externas (Mockito/WireMock), verificação de assinatura válida/inválida, idempotência de webhook

## Etapa 9 — Tempo real (WebSocket/STOMP)

- [ ] Configuração `spring-boot-starter-websocket` (STOMP endpoint, ex. `/ws`)
- [ ] Broadcast de mensagem nova (recebida via webhook) para o tópico da conversa (`/topic/conversations/{id}`)
- [ ] Autenticação do handshake STOMP via JWT já emitido
- [ ] Testes de integração do fluxo webhook → broadcast

## Etapa 10 — Prospecção (busca local)

- [ ] Integração Google Maps Platform — Places API (busca por região/segmento/palavra-chave)
- [ ] Integração BrasilAPI ou ReceitaWS (CNPJ/CNAE/situação cadastral) — confirmar provedor final antes de implementar
- [ ] `POST /api/prospecting/searches` (dispara busca, `TRIGGER_PROSPECTING`)
- [ ] `GET /api/prospecting/searches/{id}/results`
- [ ] `POST /api/prospecting/manual-entry` (levantamento manual em grupos)
- [ ] Cada resultado selecionado vira `Lead` (`lead_type = LOCAL_SEARCH`)
- [ ] Testes com mocks das APIs externas

## Etapa 11 — Notificações

Entidade: `NotificationPreference`.

- [ ] Migration: tabela `notification_preference`
- [ ] Entidade JPA + repositório
- [ ] `GET/PUT /api/users/{id}/notification-preferences`
- [ ] Serviço de notificação disparado quando um lead é qualificado (score alto), respeitando o(s) canal(is) escolhido(s) pelo usuário responsável
- [ ] Integração com provedor de e-mail transacional (Amazon SES, SendGrid ou Resend — confirmar antes de implementar)
- [ ] Reaproveita os clients de WhatsApp/Telegram já criados na Etapa 8
- [ ] Testes: mocks dos provedores, respeita canal(is) ativo(s) do usuário

## Etapa 12 — Métricas

- [ ] `GET /api/metrics/funnel` — taxa de conversão por canal e por tipo de lead, taxa de fechamento por etapa, produtos mais ofertados
- [ ] `GET /api/metrics/response-time` — tempo médio de primeira resposta
- [ ] `GET /api/metrics/prospecting` — volume de leads de busca local por região/fonte
- [ ] Testes de integração das queries de agregação

---

## Fora deste roadmap (explicitamente adiado)

Itens de "Futuro (evolução)" do estudo de caso — novos canais de contato direto, criação automática de lead a partir da primeira mensagem, integração com CRMs externos, automação de follow-up, priorização de leads com IA. Não planejar tarefas para eles até serem priorizados.

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

## Etapa 2 — Catálogo de Produtos (concluída)

Entidade: `Product`. Detalhe completo em `plans/2026-09-12-etapa-2-catalogo-produtos.md`.

- [x] Migration: tabela `products` (`V5__create_products_table.sql`)
- [x] Entidade JPA `Product` (`type` como `@Enumerated(EnumType.STRING)`) + repositório
- [x] `GET /api/products` (livre, só autenticado), `POST /api/products` (`EDIT_CATALOG`), `PUT /api/products/{id}` (`EDIT_CATALOG`)
- [x] Validação: `min_price_cents` ≤ `max_price_cents` quando ambos presentes (`@AssertTrue`)
- [x] Testes de integração (MockMvc + Testcontainers): `ProductControllerTest` (401/403/201/400/200/404)
- [x] Adição fora do roadmap original, motivada pela Etapa 2 do frontend: `GET /api/roles` (`CREATE_USER`) — `RoleController`/`RoleResponse`, reaproveita `RoleRepository` da Etapa 1; `RoleControllerTest` (401/403/200)

## Etapa 3 — Leads e Origem (concluída)

Entidades: `LeadOrigin`, `Lead`. Detalhe completo em `plans/2026-09-15-etapa-3-leads-e-origem.md`.

- [x] Migration: tabelas `lead_origins`, `leads`, `lead_products` (join N:N com `products`) (`V6__create_leads_tables.sql`)
- [x] Entidades JPA `LeadOrigin`, `Lead` + repositórios (`LeadRepository` com `@Query` de filtro opcional por `leadType`/`channel`/`funnelStatus`/`assignedUserId`)
- [x] Validação condicional por `leadType` (campos de `DIRECT_CONTACT` vs. `LOCAL_SEARCH`) via `@AssertTrue` em `CreateLeadRequest`
- [x] `POST /api/leads` (cadastro manual, sempre `captureMethod=MANUAL`), `GET /api/leads` (filtro por tipo/canal/status/responsável, forçado a `assignedUserId=self` para quem só tem `VIEW_OWN_LEADS`), `GET /api/leads/{id}`, `PUT /api/leads/{id}` — os dois últimos com checagem de posse (`LeadAccessDeniedException`→403 para quem só tem `VIEW_OWN_LEADS` e não é o responsável)
- [x] Regra de atribuição: `assigned_user_id` obrigatório na criação, sem restrição de quem pode atribuir a quem (mesma simplificação da Etapa 1 com `roleId`)
- [x] Testes: `CreateLeadRequestTest` (unitário, validação condicional) e `LeadControllerTest` (integração, MockMvc + Testcontainers — CRUD completo, 401/403/404/400)
- [x] **Bug pré-existente corrigido** (achado ao rodar a app real, não pego pelo MockMvc): `OncePerRequestFilter.shouldNotFilterErrorDispatch()` é `true` por padrão, então `JwtAuthenticationFilter` não roda no forward interno do container para `/error` — qualquer resposta 4xx/5xx (ex.: 400 de Bean Validation) virava 401 vazio nesse forward, mascarando o status original. Corrigido adicionando `/error` ao `permitAll()` em `SecurityConfig`. Afetava também `/api/auth/login`, `/api/users` e `/api/products` (Etapas 1 e 2), não só `/api/leads`.

## Etapa 4 — Endpoint público de recepção de leads do site (concluída)

Detalhe completo em `plans/2026-09-15-etapa-4-endpoint-publico-leads-site.md`.

- [x] Mecanismo de autenticação por API key (`PublicApiKeyFilter`, restrito a `/api/public/**`,
  comparação em tempo constante via `MessageDigest.isEqual`, chave única via
  `app.public-api.key`/`PUBLIC_LEADS_API_KEY`) — fora do fluxo de login de usuário
- [x] Bucket4j: rate limiting por IP (`RateLimitFilter`, `Bucket` em memória por
  `request.getRemoteAddr()`, default 20 req/min, configurável)
- [x] `POST /api/public/leads` — cria `Lead` com `leadType=DIRECT_CONTACT`,
  `origin.channel=WEBSITE`, `origin.captureMethod=API`, sempre fixos (não vêm do request);
  atribuído a um usuário padrão configurável (`app.public-api.default-assignee-email`, default o
  admin inicial) para triagem manual — reatribuído depois via `PUT /api/leads/{id}` (Etapa 3)
- [x] Resposta mínima (`PublicLeadResponse`: `id`, `createdAt`) — não expõe dados internos
  (responsável, papel) a um chamador externo
- [x] Documentação OpenAPI explícita (`@Operation`/`@ApiResponses`/`@Schema` no
  `PublicLeadController`, `@SecurityScheme` em `config/OpenApiConfig`) — visível em
  `/v3/api-docs`/Swagger UI para o time do site institucional consumir
- [x] Testes: `PublicApiKeyFilterTest` (unitário), `PublicLeadControllerTest` (401/400/201 +
  verifica origem/atribuição no banco), `PublicLeadRateLimitTest` (429 acima do limite, classe
  isolada com `@TestPropertySource` para não compartilhar o balde com o resto da suíte)

## Etapa 5 — Funil e Histórico de Status (concluída)

Entidade: `FunnelStatusHistory`. Detalhe completo em
`plans/2026-09-16-etapa-5-funil-historico-status.md`.

- [x] Migration: tabela `funnel_status_histories` (`V7__create_funnel_status_history_table.sql`)
- [x] Entidade JPA `FunnelStatusHistory` (imutável, sem `@PreUpdate`) + `FunnelStatusHistoryRepository`
- [x] Regra de transição de status: `reason` obrigatório ao mover para `LOST` (`@AssertTrue` em
  `UpdateLeadStatusRequest`); `Lead.lossReason` é limpo automaticamente ao sair de `LOST`; sem
  máquina de estados (qualquer `FunnelStatus` → qualquer outro é aceito — não documentado como
  restrito)
- [x] `PATCH /api/leads/{id}/status` — registra a transição e atualiza `Lead.funnel_status`,
  reaproveitando a checagem de posse já existente (`VIEW_OWN_LEADS`/`VIEW_ALL_LEADS`)
- [x] **Retroalimentação nas Etapas 3/4**: `LeadService.create` e `PublicLeadService.create`
  passaram a gravar a `FunnelStatusHistory` inicial (`previousStatus=null`, `newStatus=NEW`) —
  conforme `03-entidades.md`, o histórico começa já na criação do lead, não só na primeira
  mudança manual. Para o lead criado via API pública (sem usuário autenticado), o autor da
  transição inicial é o mesmo usuário padrão de atribuição.
- [x] Histórico embutido em `LeadResponse.statusHistory` (sem rota `GET` própria — reaproveita
  `GET /api/leads/{id}` já existente)
- [x] Testes: `UpdateLeadStatusRequestTest` (unitário), `LeadControllerTest` (novos casos de
  `PATCH .../status`: 401/400/200/403/404, mais a verificação do histórico inicial na criação),
  `PublicLeadControllerTest` (verifica o histórico inicial do lead criado via API pública)

## Etapa 6 — Interações (concluída)

Entidade: `Interaction`. Detalhe completo em `plans/2026-09-16-etapa-6-interacoes.md`.

- [x] Migration: tabela `interactions` (`V8__create_interactions_table.sql`)
- [x] Entidade JPA `Interaction` (imutável, mesmo padrão de `FunnelStatusHistory`) + `InteractionRepository`
- [x] `POST /api/leads/{id}/interactions` (`CALL` ou `NOTE`)
- [x] `GET /api/leads/{id}/interactions` (ordem cronológica)
- [x] **Refatoração**: extraído `LeadService.findAccessibleLeadOrThrow(id, currentUser)` público
  (reúne a busca do lead + checagem de posse, antes duplicada em `updateStatus`/`findById`/`update`)
  — reaproveitado pelo novo `InteractionService` para não duplicar essa checagem de segurança
  uma quarta vez
- [x] Testes: `InteractionControllerTest` (401/400/201/403/404 em `POST` e `GET`, ordem cronológica,
  `VIEW_ALL_LEADS` vendo interação de lead de outro vendedor)

## Etapa 7 — Qualificação automática (concluída)

Detalhe completo em `plans/2026-09-16-etapa-7-qualificacao-automatica.md`.

- [x] `QualificationService`: regra de pontuação configurável (produto de interesse: 0/1;
  orçamento: 0/1/2 conforme `app.qualification.budget-threshold-cents`; prazo: 0/1/2 conforme
  palavra-chave de urgência em `app.qualification.urgent-keywords`) — soma `0`–`5` vira
  `LOW`(`0`-`1`)/`MEDIUM`(`2`-`3`)/`HIGH`(`4`-`5`). Regra inicial simples, sujeita a recalibração
  com dados reais (ver estudo de caso, seção Riscos)
- [x] Disparo em `LeadService.create`/`update` e em `PublicLeadService.create` (site sempre
  resulta em `LOW`, pois esse formulário não coleta produto/orçamento/prazo)
- [x] `qualificationScore` removido de `CreateLeadRequest`/`UpdateLeadRequest` — deixa de ser
  aceito do cliente, sempre calculado. **Pendência de frontend** (fora deste plano): o `Select`
  manual de qualificação em `CreateLeadForm`/`EditLeadForm` (Etapa 4 do frontend) fica sem efeito
- [x] Testes unitários (`QualificationServiceTest`) cobrindo a matriz de combinações
  critério→score; casos de integração em `LeadControllerTest`/`PublicLeadControllerTest`
  verificando o score calculado na criação/atualização

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

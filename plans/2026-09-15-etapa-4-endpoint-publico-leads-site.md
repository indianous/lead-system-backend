# Plano — Backend Etapa 4: Endpoint público de recepção de leads do site

> Referência: `backend/plans/roadmap.md` (Etapa 4), `00-documentation/01-estudo-de-caso-coleta-leads.md`
> (Fluxo A — contato direto, canal "Formulário do site"), `00-documentation/03-entidades.md`
> (`LeadOrigin.channel=WEBSITE`/`capture_method=API`), `00-documentation/04-rotas-e-telas.md`
> ("Rotas de API sem tela associada" — `POST /api/public/leads`), `02-bibliotecas-e-apis.md`
> (API Key própria sobre Spring Security + Bucket4j).

## 1. Problemas encontrados

- Não existe nenhum mecanismo de autenticação fora do fluxo de login de usuário — `JwtAuthenticationFilter`
  espera um JWT de um `User` real, o que não existe para o site institucional (ele é externo ao
  projeto, "fora do escopo"). Precisa de um filtro próprio, restrito a `/api/public/**`, validando
  uma API key estática (não há entidade `ApiKey` em `03-entidades.md` — só um consumidor documentado
  hoje, então uma chave única via propriedade/variável de ambiente é suficiente; comparação em
  tempo constante via `MessageDigest.isEqual` para evitar timing attack).
- `Lead.assigned_user_id` é obrigatório (`NOT NULL`, ver `03-entidades.md`), mas a submissão do site
  é anônima — não há vendedor responsável no momento da criação. Decisão (a confirmar com o
  usuário): atribuir a um usuário padrão configurável (`app.public-api.default-assignee-email`,
  default o admin inicial da Etapa 1) para triagem manual; o gestor reatribui depois via
  `PUT /api/leads/{id}` (Etapa 3, já existe). Evita alterar a constraint `NOT NULL` do schema.
- `LeadOrigin`/`Lead` já existem (Etapa 3), mas todos os campos condicionais por `leadType` são
  hoje aceitos via `CreateLeadRequest` (rota autenticada por usuário). O endpoint público não deve
  aceitar `leadType`/`channel`/`captureMethod` do cliente — são sempre fixos
  (`DIRECT_CONTACT`/`WEBSITE`/`API`, respectivamente) para este endpoint; um `DTO` de request
  separado e mais restrito evita o risco de o site (ou um chamador malicioso com a API key)
  forjar uma origem `LOCAL_SEARCH` ou um `captureMethod=MANUAL`.
- Bucket4j (`bucket4j-core`, já no `pom.xml`) é só a lib de cálculo do balde de tokens — não tem
  starter de integração com Spring MVC; precisa de um filtro próprio (`RateLimitFilter`) mantendo
  um `Bucket` por IP de origem (`request.getRemoteAddr()`) em memória (`ConcurrentHashMap`) —
  suficiente para uma instância única, sem necessidade de backend distribuído no MVP.
- Retorno do endpoint: devolver o mesmo `LeadResponse` completo (usado pelas rotas internas)
  exporia dados internos (papel/nome do vendedor responsável, histórico) a um chamador externo e
  menos confiável. Decisão: um `PublicLeadResponse` mínimo (`id`, `createdAt`).
- O formulário de contato do site não está especificado campo a campo em nenhum documento (o site
  está fora do escopo). Decisão: modelar os campos mínimos de um formulário de contato —
  `name` (obrigatório), `phone`/`email` (pelo menos um dos dois obrigatório, para o vendedor
  conseguir retornar o contato) e `initialMessage` (opcional).
- CORS já configurado (Etapa 3) para `/**`, mas restrito às origens do frontend interno — como a
  API key é um segredo, o consumidor deste endpoint precisa ser o **backend** do site (chamada
  servidor-a-servidor), nunca JS de navegador (que exporia a chave); por isso o endpoint não entra
  na lista de origens CORS liberadas, é coerente com o próprio mecanismo de API key.

## 2. Testes a incluir/alterar (agrupados por arquivo)

- `security/PublicApiKeyFilterTest.java` (novo, unitário puro — sem contexto Spring, mesmo padrão
  de `JwtServiceTest`) — método de comparação da chave: chave correta → autoriza; chave errada/
  vazia/ausente → não autoriza; comparação não deve usar `String.equals` (time-safe).
- `api/PublicLeadControllerTest.java` (novo, MockMvc + Testcontainers, rate limit no valor padrão
  — folga suficiente para os poucos requests destes testes)
  - `POST /api/public/leads` sem `Authorization` → 401
  - `POST /api/public/leads` com API key inválida → 401
  - `POST /api/public/leads` com API key válida e `name` em branco → 400
  - `POST /api/public/leads` com API key válida, sem `phone` nem `email` → 400
  - `POST /api/public/leads` com API key válida e payload válido → 201; verifica no banco (via
    `LeadRepository`) que o `Lead` criado tem `leadType=DIRECT_CONTACT`,
    `origin.channel=WEBSITE`, `origin.captureMethod=API`, `funnelStatus=NEW` e
    `assignedUserId` = usuário configurado em `app.public-api.default-assignee-email`
  - Resposta 201 expõe só `id`/`createdAt` (não vaza `assignedUserName` nem dados internos)
- `api/PublicLeadRateLimitTest.java` (novo, MockMvc + Testcontainers, classe isolada com
  `app.public-api.rate-limit.capacity=2` via `@TestPropertySource` — contexto Spring próprio, não
  compartilha o balde com `PublicLeadControllerTest`) — 3 requisições válidas em sequência: as 2
  primeiras não retornam 429, a 3ª retorna 429.

## 3. O que entra no projeto

1. **`application.properties`**: `app.public-api.key` (`PUBLIC_LEADS_API_KEY`, default só-dev),
   `app.public-api.default-assignee-email` (`PUBLIC_LEADS_DEFAULT_ASSIGNEE_EMAIL`, default
   `admin@leadsystem.local`), `app.public-api.rate-limit.capacity`/`.refill-tokens`/
   `.refill-duration-seconds` (defaults 20/20/60 — 20 submissões por minuto por IP).
2. **`security`**: `PublicApiKeyFilter` (`OncePerRequestFilter`, atua só em `/api/public/**`,
   valida `Authorization: Bearer <chave>` contra `app.public-api.key`, concede a authority
   sintética `PUBLIC_API_ACCESS` quando válida); `RateLimitFilter` (`OncePerRequestFilter`, atua só
   em `/api/public/**`, um `Bucket` do Bucket4j por IP em `ConcurrentHashMap`, escreve 429 JSON
   diretamente na resposta quando o balde estiver vazio — roda **antes** da checagem de API key,
   para limitar tentativas de força bruta da chave também). `SecurityConfig`: registra os dois
   filtros (`addFilterBefore`, antes do `JwtAuthenticationFilter`/`UsernamePasswordAuthenticationFilter`)
   e `.requestMatchers(HttpMethod.POST, "/api/public/leads").hasAuthority("PUBLIC_API_ACCESS")`
   (fora do `permitAll()` — só autoriza com a authority concedida pelo filtro).
3. **`api`**: `CreatePublicLeadRequest` (record: `name` `@NotBlank`, `phone`, `email` `@Email`,
   `initialMessage`; `@AssertTrue` exige `phone` ou `email` presente), `PublicLeadResponse(UUID id,
   Instant createdAt)`, `PublicLeadService` (monta `LeadOrigin` fixo — `DIRECT_CONTACT`/`WEBSITE`/
   `API` —, resolve o usuário padrão via `UserRepository.findByEmail`, cria o `Lead` reaproveitando
   `LeadRepository`/`LeadOriginRepository` já existentes), `PublicLeadController`
   (`POST /api/public/leads`, `@ResponseStatus(CREATED)`), com anotações springdoc
   (`@Operation`/`@ApiResponses`/`@Schema`) documentando o contrato para o time do site.
4. Reaproveita `ResourceNotFoundException` (usuário padrão mal configurado → 404/500, ver nota
   abaixo) — não introduz nenhuma entidade nova nem migration.

### Backlog

- Se `app.public-api.default-assignee-email` apontar para um e-mail que não existe, a criação
  falha (erro de configuração do operador, não do chamador) — aceitável para o MVP; não é tratado
  com uma mensagem amigável específica.
- Marcar a Etapa 4 em `backend/plans/roadmap.md` como concluída ao final, documentando as decisões
  acima (chave única via propriedade, atribuição padrão configurável, `PublicLeadResponse` mínimo).

# Plano — Backend Etapa 3: Leads e Origem

> Referência: `backend/plans/roadmap.md` (Etapa 3), `00-documentation/03-entidades.md` (`LeadOrigin`,
> `Lead`), `00-documentation/04-rotas-e-telas.md` (`/leads`, `/leads/new`, `/leads/[id]`,
> `/leads/[id]/edit`), `00-documentation/01-estudo-de-caso-coleta-leads.md` (Fluxos A/B).

## 1. Problemas encontrados

- Não existem as entidades `LeadOrigin`/`Lead` nem tabelas para elas — só RBAC (Etapa 1) e
  `products` (Etapa 2).
- `Lead.lead_type` e `LeadOrigin.origin_type` usam exatamente os mesmos dois valores
  (`DIRECT_CONTACT`, `LOCAL_SEARCH`) com o mesmo significado — decisão: **um único enum**
  `LeadType`, reaproveitado nos dois campos, em vez de dois enums idênticos.
- `03-entidades.md` define obrigatoriedade condicional dos campos de origem por `origin_type`
  (`channel` só para `DIRECT_CONTACT`; `search_source`/`region`/`search_segment` só para
  `LOCAL_SEARCH`) — precisa ser validado na aplicação (Bean Validation), não só documentado.
  Decisão de design (a confirmar com o usuário): expor esses campos **diretamente** em
  `CreateLeadRequest`/`UpdateLeadRequest` (sem uma rota própria para `LeadOrigin`) — o serviço cria
  o `LeadOrigin` junto com o `Lead` na mesma transação, já que não há nenhuma tela nem rota no
  roadmap para gerenciar `LeadOrigin` isoladamente.
- `LeadOrigin.capture_method` tem 3 valores (`MANUAL`, `API`, `AUTOMATED`), mas só `MANUAL` se
  aplica a este endpoint — `API` é o `POST /api/public/leads` (Etapa 4) e `AUTOMATED` é a
  prospecção (Etapa 10). Decisão: `POST /api/leads` **sempre** grava `capture_method = MANUAL` no
  servidor; o campo não é aceito no corpo da requisição.
- `funnel_status` é gerenciado pela Etapa 5 (`PATCH /api/leads/{id}/status`, com
  `FunnelStatusHistory`) — nesta etapa, `Lead` sempre nasce com `funnel_status = NEW` e
  `PUT /api/leads/{id}` **não** altera esse campo (nem `loss_reason`), só os dados descritos na
  tela "Editar lead" (qualificação, produtos de interesse, responsável).
- `qualification_score` é preenchido automaticamente pela Etapa 7 (qualificação automática), mas
  a tela `/leads/[id]/edit` já existe nesta etapa e a doc de rotas diz que ela "atualiza dados de
  qualificação" — decisão: permitir definir/alterar `qualification_score` manualmente já em
  `UpdateLeadRequest` (opcional); a Etapa 7 troca a origem do valor (automática), não o contrato.
- Controle de acesso por *ownership* (`VIEW_OWN_LEADS` vs. `VIEW_ALL_LEADS`, ver
  `04-rotas-e-telas.md`) é mais que `@PreAuthorize("hasAuthority(...)")` simples: quem só tem
  `VIEW_OWN_LEADS` só pode ver/editar leads onde `assigned_user_id` é o próprio usuário — precisa
  de uma checagem de posse na camada de serviço (não existe precedente disso no código ainda;
  `UserController`/`ProductController` só checam permissão, não dono do recurso).
- Nenhuma rota até agora tem filtros de listagem (`GET /api/users` e `GET /api/products` retornam
  tudo) — `GET /api/leads` precisa filtrar por `lead_type`/`channel`/`funnel_status`/
  `assigned_user_id` (`04-rotas-e-telas.md`, tela `/leads`). Decisão: um único método de
  repositório com `@Query` JPQL e parâmetros opcionais (`:param IS NULL OR campo = :param`), em
  vez de introduzir Specifications — mais simples e suficiente para 4 filtros.
- `POST /api/leads` não restringe a quem `assigned_user_id` pode apontar (qualquer usuário
  existente, mesmo criado por um vendedor comum) — mesma simplificação já aceita em
  `CreateUserRequest.roleId` na Etapa 1 (não há restrição de quem pode atribuir qual papel).

## 2. Testes a incluir/alterar (agrupados por arquivo)

- `api/CreateLeadRequestTest.java` (novo, JUnit puro, sem contexto Spring) — testa
  `isOriginConsistent()`: `DIRECT_CONTACT` com `channel` e sem campos de busca → válido;
  `DIRECT_CONTACT` sem `channel` → inválido; `DIRECT_CONTACT` com `region`/`searchSource`
  preenchidos → inválido; `LOCAL_SEARCH` com `searchSource`+`region`+`searchSegment` → válido;
  `LOCAL_SEARCH` faltando qualquer um dos três → inválido; `LOCAL_SEARCH` com `channel`
  preenchido → inválido.
- `api/LeadControllerTest.java` (novo, MockMvc + Testcontainers)
  - `POST /api/leads` sem token → 401
  - `POST /api/leads` com `VIEW_OWN_LEADS`, `DIRECT_CONTACT` + `channel` válido → 201,
    `funnelStatus=NEW`, origem com `captureMethod=MANUAL`
  - `POST /api/leads` `DIRECT_CONTACT` sem `channel` → 400
  - `POST /api/leads` `LOCAL_SEARCH` sem `region`/`searchSource`/`searchSegment` → 400
  - `POST /api/leads` com `assignedUserId` inexistente → 404
  - `POST /api/leads` com `productIds` contendo id inexistente → 404
  - `GET /api/leads` sem token → 401
  - `GET /api/leads` com só `VIEW_OWN_LEADS` retorna somente os leads do próprio usuário, mesmo
    passando `assignedUserId` de outro vendedor na query string
  - `GET /api/leads` com `VIEW_ALL_LEADS` retorna leads de todos e respeita filtro por
    `leadType`/`funnelStatus`/`assignedUserId` quando informado
  - `GET /api/leads/{id}` com `VIEW_OWN_LEADS` tentando abrir lead de outro vendedor → 403
  - `GET /api/leads/{id}` com `VIEW_ALL_LEADS` abre lead de qualquer vendedor → 200
  - `GET /api/leads/{id}` inexistente → 404
  - `PUT /api/leads/{id}` com `VIEW_OWN_LEADS` atualiza lead próprio (qualificação, produtos de
    interesse, reatribuição para outro vendedor) → 200
  - `PUT /api/leads/{id}` com `VIEW_OWN_LEADS` tentando editar lead de outro vendedor → 403
  - `PUT /api/leads/{id}` inexistente → 404

## 3. O que entra no projeto

1. **Migration** `V6__create_leads_tables.sql` — tabelas `lead_origins` (`id`, `origin_type`,
   `channel` nullable, `search_source` nullable, `region` nullable, `search_segment` nullable,
   `capture_method`), `leads` (`id`, `name`, `lead_type`, `phone` nullable, `email` nullable,
   `initial_message` nullable, `estimated_budget_cents` nullable, `desired_timeline` nullable,
   `qualification_score` nullable, `funnel_status` default `'NEW'`, `loss_reason` nullable,
   `origin_id` FK, `assigned_user_id` FK → `users`, `created_at`, `updated_at`), `lead_products`
   (`lead_id`, `product_id`, PK composta — join N:N com `products`).
2. **`domain`**: enums `LeadType` (`DIRECT_CONTACT`, `LOCAL_SEARCH` — reaproveitado em
   `Lead.leadType` e `LeadOrigin.originType`), `Channel` (`META_WHATSAPP`, `META_INSTAGRAM`,
   `META_MESSENGER`, `TELEGRAM`, `WEBSITE`), `SearchSource` (`GOOGLE_MAPS`,
   `CNPJ_FEDERAL_REVENUE`, `GROUP`), `CaptureMethod` (`MANUAL`, `API`, `AUTOMATED`),
   `FunnelStatus` (`NEW`, `CONTACTED`, `PROPOSAL`, `NEGOTIATION`, `CLOSED`, `LOST`),
   `QualificationScore` (`HIGH`, `MEDIUM`, `LOW`); entidades `LeadOrigin` e `Lead`
   (`@ManyToOne` para `origin`/`assignedUser`, `@ManyToMany` para `productsOfInterest`,
   `@PrePersist`/`@PreUpdate` como em `User`); `LeadOriginRepository`, `LeadRepository`
   (com `@Query` de busca por `leadType`/`channel` (via `origin.channel`)/`funnelStatus`/
   `assignedUserId`, todos opcionais).
3. **`api`**: `CreateLeadRequest`/`UpdateLeadRequest` (records com os campos de qualificação +
   origem + `productIds`; `@AssertTrue isOriginConsistent()` valida a obrigatoriedade condicional
   descrita acima), `LeadResponse` (+ `LeadOriginResponse` aninhado, lista de `ProductResponse`
   reaproveitado da Etapa 2, `assignedUserId`/`assignedUserName` denormalizado como em
   `UserResponse.roleName`), `LeadService` (cria `LeadOrigin`+`Lead` na mesma transação; checagem
   de posse — usuário sem `VIEW_ALL_LEADS` só acessa/edita lead onde é o responsável, senão lança
   `LeadAccessDeniedException`), `LeadController`
   (`@PreAuthorize("hasAnyAuthority('VIEW_OWN_LEADS','VIEW_ALL_LEADS')")` nas 4 rotas),
   `LeadAccessDeniedException` (nova, mapeada para 403 em `ApiExceptionHandler`, mesmo padrão de
   `ResourceNotFoundException`→404).
4. Reaproveita `ResourceNotFoundException("User"|"Product"|"Lead", id)` já existente para
   `assignedUserId`/`productIds`/`{id}` inválidos.

### Backlog

- Marcar a Etapa 3 em `backend/plans/roadmap.md` como concluída ao final, com nota sobre as
  decisões acima (enum único `LeadType`, `capture_method` fixo em `MANUAL`, filtro por `@Query`
  em vez de Specifications).

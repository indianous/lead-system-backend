# Plano — Backend Etapa 5: Funil e Histórico de Status

> Referência: `backend/plans/roadmap.md` (Etapa 5), `00-documentation/03-entidades.md`
> (`FunnelStatusHistory`, `Lead.funnel_status`/`loss_reason`), `00-documentation/04-rotas-e-telas.md`
> ("Mudar etapa / marcar perdido" — `/leads/[id]/status`), `01-estudo-de-caso-coleta-leads.md`
> (etapas do funil: `Novo → Contatado → Proposta → Negociação → Fechado/Perdido`).

## 1. Problemas encontrados

- Não existe a entidade `FunnelStatusHistory` nem tabela para ela — hoje `Lead.funnel_status` só é
  setado na criação (sempre `NEW`, `Lead.java`) e nunca muda depois; não há `PATCH /api/leads/{id}/status`.
- `03-entidades.md#FunnelStatusHistory` documenta `previous_status` como "Não" obrigatório,
  "Nulo **na primeira transição (criação do lead como NEW)**" — ou seja, o histórico começa a
  existir já na criação do lead, não só na primeira mudança manual. Isso significa que
  `LeadService.create` (`api/LeadService.java:44-57`) e `PublicLeadService.create`
  (`api/PublicLeadService.java:37-51`), ambos já em produção (Etapas 3 e 4), **também** precisam
  passar a gravar uma `FunnelStatusHistory` inicial (`previousStatus=null`, `newStatus=NEW`) — não
  é só um endpoint novo, é uma retroalimentação nos dois pontos de criação de lead já existentes.
- Para `PublicLeadService.create`, não há um `User` autenticado (a chamada é por API key, ver
  Etapa 4) — mas `FunnelStatusHistory.user_id` é obrigatório ("Sim", `FK → User`). Decisão: usar o
  mesmo usuário padrão (`app.public-api.default-assignee-email`) já usado como responsável do lead
  também como autor dessa transição inicial — é o mesmo usuário, então não introduz um segundo
  "ator do sistema" fictício.
- `03-entidades.md#Lead` marca `loss_reason` como "Apenas se `LOST`" — ao mover um lead **para
  fora** de `LOST` (reabertura), o `loss_reason` antigo ficaria orfão/desatualizado se não for
  limpo. Decisão: `PATCH /api/leads/{id}/status` limpa `Lead.loss_reason` sempre que o novo status
  não for `LOST`, e o define a partir de `reason` quando for.
- Nenhum documento define uma máquina de estados restringindo transições (ex.: proibir voltar de
  `CLOSED` para `NEW`) — os fluxos do estudo de caso mostram o funil como guia, não como regra
  rígida, e `LOST` pode ser alcançado a partir de qualquer etapa ("Não fechou" no Fluxo A/B).
  Decisão: **não** implementar uma máquina de estados nesta etapa (qualquer `FunnelStatus` →
  qualquer outro é aceito, inclusive repetir o mesmo); único requisito documentado é `reason`
  obrigatório ao mover para `LOST`. Se o usuário quiser restringir transições, é um ajuste a parte.
- `04-rotas-e-telas.md` diz que `/leads/[id]` mostra "histórico de mudança de etapa do funil", mas
  o roadmap da Etapa 5 não lista nenhuma rota `GET` nova — decisão: embutir a lista de histórico
  dentro do próprio `LeadResponse` (novo campo `statusHistory`), reaproveitando
  `GET /api/leads/{id}` já existente, em vez de criar uma rota `GET` separada.

## 2. Testes a incluir/alterar (agrupados por arquivo)

- `api/UpdateLeadStatusRequestTest.java` (novo, JUnit puro, mesmo padrão de
  `CreateLeadRequestTest`) — `newStatus=LOST` com `reason` preenchido → válido; `LOST` sem
  `reason`/`reason` em branco → inválido; qualquer outro `newStatus` sem `reason` → válido.
- `api/LeadControllerTest.java` (edita — adiciona casos ao arquivo já existente, mesmo recurso)
  - `PATCH /api/leads/{id}/status` sem token → 401
  - `PATCH .../status` para `LOST` sem `reason` → 400
  - `PATCH .../status` para `LOST` com `reason` → 200; `funnelStatus=LOST`,
    `lossReason` = valor enviado; novo item em `statusHistory` com `previousStatus=NEW`,
    `newStatus=LOST`, `reason` preenchido
  - `PATCH .../status` para `CONTACTED` (transição comum, sem `reason`) → 200, `lossReason=null`
  - `PATCH .../status` com `VIEW_OWN_LEADS` tentando mudar status de lead de outro vendedor → 403
  - `PATCH .../status` de lead inexistente → 404
  - `POST /api/leads` (create já existente) → resposta já inclui `statusHistory` com 1 item
    (`previousStatus=null`, `newStatus=NEW`)
- `api/PublicLeadControllerTest.java` (edita) — no teste de criação já existente, adiciona
  verificação de que o `Lead` criado tem uma `FunnelStatusHistory` inicial associada
  (`previousStatus=null`, `newStatus=NEW`, `userId` = usuário padrão configurado).

## 3. O que entra no projeto

1. **Migration** `V7__create_funnel_status_history_table.sql` — tabela `funnel_status_histories`
   (`id UUID PK`, `lead_id` FK → `leads`, `previous_status VARCHAR(20)` nullable, `new_status
   VARCHAR(20)` NOT NULL, `user_id` FK → `users`, `reason VARCHAR(1000)` nullable,
   `changed_at TIMESTAMPTZ` NOT NULL).
2. **`domain`**: entidade `FunnelStatusHistory` (`@ManyToOne` para `lead`/`user`,
   `@Enumerated(EnumType.STRING)` para `previousStatus`/`newStatus`, `@PrePersist` seta
   `changedAt`, sem `@PreUpdate` — registro é imutável); `FunnelStatusHistoryRepository`
   (`findByLeadIdOrderByChangedAtAsc`).
3. **`api`**: `UpdateLeadStatusRequest` (record: `newStatus` `@NotNull`, `reason`; `@AssertTrue`
   exige `reason` não-branco quando `newStatus == LOST`), `FunnelStatusHistoryResponse` (`id`,
   `previousStatus`, `newStatus`, `userId`, `userName`, `reason`, `changedAt`); `LeadResponse`
   ganha o campo `List<FunnelStatusHistoryResponse> statusHistory`; `LeadService` ganha
   `updateStatus(UUID id, UpdateLeadStatusRequest request, User currentUser)` (reaproveita
   `requireAccess` já existente; grava `FunnelStatusHistory`; atualiza `Lead.funnelStatus` e
   `Lead.lossReason`) e passa a gravar a `FunnelStatusHistory` inicial dentro de `create(...)`;
   `PublicLeadService.create(...)` também passa a gravar a `FunnelStatusHistory` inicial, com o
   usuário padrão como autor; `LeadController` ganha `@PatchMapping("/{id}/status")`.
4. Reaproveita `LeadAccessDeniedException`/`ResourceNotFoundException` já existentes — nenhuma
   exceção nova.

### Backlog

- Marcar a Etapa 5 em `backend/plans/roadmap.md` como concluída ao final, documentando as decisões
  acima (histórico gravado já na criação, sem máquina de estados, `loss_reason` limpo ao sair de
  `LOST`, histórico embutido em `LeadResponse` em vez de rota `GET` própria).

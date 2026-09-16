# Plano — Backend Etapa 6: Interações

> Referência: `backend/plans/roadmap.md` (Etapa 6), `00-documentation/03-entidades.md`
> (`Interaction`), `00-documentation/04-rotas-e-telas.md` ("Registrar interação" —
> `/leads/[id]/interactions/new`).

## 1. Problemas encontrados

- Não existe a entidade `Interaction` nem tabela para ela — hoje o único histórico de um `Lead` é
  o `FunnelStatusHistory` (Etapa 5); ligações e observações do vendedor (`03-entidades.md#Interaction`
  — "histórico de contatos com o lead que **não** são mensagens de chat") não têm onde ser
  registradas.
- A checagem de posse (`VIEW_OWN_LEADS` só acessa o próprio lead; `VIEW_ALL_LEADS` acessa
  qualquer um) está **duplicada** em três métodos de `LeadService`
  (`api/LeadService.java:70-71` em `updateStatus`, `:100-101` em `findById`, `:107-108` em
  `update` — sempre o par `findLeadOrThrow` + `requireAccess`). Como `POST`/`GET`
  `/api/leads/{id}/interactions` precisam da **mesma** checagem (a doc de rotas usa literalmente
  a mesma permissão: "`VIEW_OWN_LEADS` (se responsável) ou `VIEW_ALL_LEADS`"), a opção de
  duplicar essa lógica de novo em um `InteractionService` criaria uma segunda cópia de uma
  checagem sensível a segurança — risco real de as duas divergirem com o tempo. Decisão: extrair
  um método público `LeadService.findAccessibleLeadOrThrow(UUID id, User currentUser)` (reúne
  `findLeadOrThrow` + `requireAccess`, devolve a entidade `Lead`), reaproveitado internamente
  pelos três métodos acima **e** injetado no novo `InteractionService`. Refatoração sem mudança
  de comportamento — os testes de `LeadControllerTest` já existentes continuam cobrindo isso.
- `Interaction` é imutável (não há rota de edição/remoção em nenhum documento) — mesmo padrão já
  usado em `FunnelStatusHistory` (`domain/FunnelStatusHistory.java`): sem `@PreUpdate`, sem
  setters.
- A doc de rotas só lista a tela de **criar** interação (`/leads/[id]/interactions/new`), mas o
  roadmap do backend pede explicitamente `GET /api/leads/{id}/interactions` também — é o dado que
  alimenta a seção "histórico de interações" do detalhe do lead (`/leads/[id]`, já citada na doc
  de rotas). Diferente do `FunnelStatusHistory` (embutido em `LeadResponse.statusHistory`),
  aqui o roadmap já define uma rota `GET` própria — decisão: manter como endpoint dedicado
  (`GET /api/leads/{id}/interactions`), sem embutir em `LeadResponse`, seguindo o que já está
  planejado.

## 2. Testes a incluir/alterar (agrupados por arquivo)

- `api/LeadServiceTest` — não previsto; a refatoração de `findAccessibleLeadOrThrow` não muda
  comportamento observável, já coberta pelos testes de integração existentes de
  `LeadControllerTest` (401/403/404 em `findById`/`update`/`updateStatus` continuam passando).
- `api/InteractionControllerTest.java` (novo, MockMvc + Testcontainers)
  - `POST /api/leads/{id}/interactions` sem token → 401
  - `POST .../interactions` com `type` ausente ou `content` em branco → 400
  - `POST .../interactions` com `VIEW_OWN_LEADS` no próprio lead → 201, grava `userId` do autor
  - `POST .../interactions` com `VIEW_OWN_LEADS` tentando registrar em lead de outro vendedor → 403
  - `POST .../interactions` de lead inexistente → 404
  - `GET .../interactions` sem token → 401
  - `GET .../interactions` com `VIEW_OWN_LEADS` no próprio lead → 200, lista em ordem cronológica
  - `GET .../interactions` com `VIEW_OWN_LEADS` tentando ver interações de lead de outro → 403
  - `GET .../interactions` com `VIEW_ALL_LEADS` vê interações de qualquer lead → 200
  - `GET .../interactions` de lead inexistente → 404

## 3. O que entra no projeto

1. **Migration** `V8__create_interactions_table.sql` — tabela `interactions` (`id UUID PK`,
   `lead_id` FK → `leads`, `user_id` FK → `users`, `type VARCHAR(10)` NOT NULL,
   `content VARCHAR(2000)` NOT NULL, `created_at TIMESTAMPTZ` NOT NULL).
2. **`domain`**: enum `InteractionType { CALL, NOTE }`; entidade `Interaction` (imutável,
   `@ManyToOne` para `lead`/`user`, `@PrePersist` seta `createdAt`, mesmo padrão de
   `FunnelStatusHistory`); `InteractionRepository`
   (`findByLeadIdOrderByCreatedAtAsc`).
3. **`api`**: refatora `LeadService` — extrai `findAccessibleLeadOrThrow(UUID, User)` público
   (usado por `findById`/`update`/`updateStatus`); `CreateInteractionRequest` (record: `type`
   `@NotNull`, `content` `@NotBlank`); `InteractionResponse` (`id`, `leadId`, `userId`,
   `userName`, `type`, `content`, `createdAt`); `InteractionService` (injeta `LeadService` para a
   checagem de posse via `findAccessibleLeadOrThrow`, `InteractionRepository`); `InteractionController`
   (`@RequestMapping("/api/leads/{leadId}/interactions")`,
   `@PreAuthorize("hasAnyAuthority('VIEW_OWN_LEADS','VIEW_ALL_LEADS')")` — mesma authority já
   usada em `LeadController`; `POST` cria com `@AuthenticationPrincipal User` como autor, `GET`
   lista).
4. Reaproveita `ResourceNotFoundException`/`LeadAccessDeniedException` já existentes — nenhuma
   exceção nova.

### Backlog

- Marcar a Etapa 6 em `backend/plans/roadmap.md` como concluída ao final, documentando a
  extração de `findAccessibleLeadOrThrow` como refatoração motivada por reaproveitamento de
  checagem de segurança entre `LeadService` e `InteractionService`.

# Plano — Backend Etapa 2: Catálogo de Produtos + `GET /api/roles`

> Referência: `backend/plans/roadmap.md` (Etapa 2), `00-documentation/03-entidades.md` (`Product`),
> `00-documentation/04-rotas-e-telas.md` (`/products`, `/users`, `/settings/roles`).
>
> Este plano cobre duas coisas: (A) a Etapa 2 do roadmap em si (catálogo de produtos) e (B) um
> endpoint pequeno, `GET /api/roles`, que não está em nenhuma etapa do roadmap do backend mas é
> **pré-requisito** da Etapa 2 do frontend (`/users/new` precisa de um seletor de papel;
> `/settings/roles` precisa listar papéis/permissões) — decidido com o usuário ao planejar a
> Etapa 2 do frontend.

## 1. Problemas encontrados

- Não existe a entidade `Product` nem nenhuma tabela para ela — só as tabelas de RBAC da Etapa 1
  (`users`, `roles`, `permissions`, `role_permissions`).
- Não há nenhum endpoint de leitura para `Role`/`Permission`. `/users/new` (frontend) precisa
  popular um seletor de papel com dados reais, e `/settings/roles` precisa exibir a matriz — hoje
  não há como o frontend obter essa informação sem hardcodar nomes/UUIDs de papel no cliente.
- Nenhuma entidade até agora tem coluna enum (`Product.type` será a primeira) — precisa decidir o
  mapeamento: `@Enumerated(EnumType.STRING)` (grava o nome, ex. `READY_MADE`) em vez de
  `ORDINAL` (índice numérico, frágil a reordenação do enum) — decisão a documentar no código.

## 2. Testes a incluir/alterar (agrupados por arquivo)

- `domain/ProductTest` — não previsto; sem regra de negócio na entidade além de getters/setters e
  timestamps (`@PrePersist`/`@PreUpdate`, mesmo padrão de `User`), não exige teste unitário próprio.
- `api/ProductControllerTest.java` (novo, MockMvc + Testcontainers)
  - `GET /api/products` sem token → 401
  - `GET /api/products` com token de um usuário qualquer (sem `EDIT_CATALOG`) → 200 (rota "livre"
    = qualquer autenticado, sem permissão específica)
  - `POST /api/products` sem `EDIT_CATALOG` → 403
  - `POST /api/products` com `EDIT_CATALOG` → 201
  - `POST /api/products` com `min_price_cents > max_price_cents` → 400
  - `PUT /api/products/{id}` com `EDIT_CATALOG` → atualiza campos e permite desativar
    (`active=false`)
  - `PUT /api/products/{id}` com id inexistente → 404
- `api/RoleControllerTest.java` (novo, MockMvc + Testcontainers)
  - `GET /api/roles` sem token → 401
  - `GET /api/roles` com token sem `CREATE_USER` → 403 (mesma permissão das telas `/users*` e
    `/settings/roles` no frontend, para consistência)
  - `GET /api/roles` com `CREATE_USER` → 200, retorna os dois papéis seedados
    (`Salesperson`, `Manager/Administrator`) com suas permissões

## 3. O que entra no projeto

### A. Catálogo de Produtos

1. **Migration** `V5__create_products_table.sql` — tabela `products` (`id UUID PK`, `name`,
   `type VARCHAR`, `description`, `min_price_cents INTEGER NULL`, `max_price_cents INTEGER NULL`,
   `active BOOLEAN`, `created_at TIMESTAMPTZ`).
2. **`domain`**: enum `ProductType { READY_MADE, CUSTOM }`; entidade `Product`
   (`@Enumerated(EnumType.STRING)` em `type`; `@PrePersist` seta `createdAt`); `ProductRepository`.
3. **`api`**: `CreateProductRequest`/`UpdateProductRequest` (records com `@AssertTrue` validando
   `minPriceCents == null || maxPriceCents == null || minPriceCents <= maxPriceCents`, reaproveita
   o handling padrão do Spring para `MethodArgumentNotValidException` → 400, já em uso desde a
   Etapa 1), `ProductResponse`, `ProductService`, `ProductController`
   (`GET /api/products` sem `@PreAuthorize`, só autenticado; `POST`/`PUT` com
   `@PreAuthorize("hasAuthority('EDIT_CATALOG')")`).

### B. `GET /api/roles` (extra, motivado pela Etapa 2 do frontend)

1. **`api`**: `RoleResponse(UUID id, String name, String description, List<String> permissions)`,
   `RoleController` (`GET /api/roles`, `@PreAuthorize("hasAuthority('CREATE_USER')")`), reaproveita
   `RoleRepository` já existente (Etapa 1) — sem migration nova.

### Backlog

- Marcar a Etapa 2 em `backend/plans/roadmap.md` como concluída ao final, e registrar o
  `GET /api/roles` como adição fora do roadmap original (mesmo padrão usado para o fix de
  `LoginResponse` na Etapa 1).

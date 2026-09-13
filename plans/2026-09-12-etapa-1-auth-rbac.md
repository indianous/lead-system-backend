# Plano — Backend Etapa 1: Autenticação e Permissões (RBAC)

> Referência: `backend/plans/roadmap.md` (Etapa 1), `00-documentation/03-entidades.md` (User/Role/Permission),
> `00-documentation/01-estudo-de-caso-coleta-leads.md` (Controle de Acesso e Permissões),
> `00-documentation/04-rotas-e-telas.md` (rotas `/users`, `/settings/roles`).

## 1. Problemas encontrados

- Não há nenhuma entidade de domínio ainda — só a estrutura de pacotes vazia criada na Etapa 0
  (`domain/package-info.java` etc.). `User`, `Role`, `Permission` (`03-entidades.md:29-64`) precisam
  ser criadas do zero.
- Não existe mecanismo de autenticação: `SecurityConfig` não existe, então o Spring Security está no
  modo automático (login form-based com senha gerada — visível no log do backend como
  `Using generated security password: ...`), incompatível com o modelo de JWT stateless exigido
  (`02-bibliotecas-e-apis.md:31`).
- `application.properties` não tem nenhuma propriedade de segurança (segredo de JWT, credenciais do
  admin inicial). O padrão já estabelecido no arquivo (`DATABASE_PASSWORD:` com default vazio,
  comentário "só para desenvolvimento local" no `docker-compose.yml`) será seguido: variáveis de
  ambiente com default inseguro documentado apenas para dev, nunca hardcoded sem variável.
- Não há mecanismo de seed do primeiro usuário administrador — sem ele, ninguém consegue logar
  (`CLAUDE.md`: "Sem autocadastro").

## 2. Decisões de design (não pedidas explicitamente no roadmap, mas necessárias para implementá-lo)

- **Hashing de senha**: usar diretamente o bean `PasswordEncoder` (`BCryptPasswordEncoder`) do Spring
  Security, sem um serviço wrapper próprio — evita indireção sem ganho (`CLAUDE.md`: não adicionar
  abstração além do necessário).
- **Autenticação sem `UserDetailsService`/`AuthenticationManager`**: como a única forma de autenticação
  do sistema é o filtro JWT (não há login form-based nem HTTP Basic), um `AuthService` próprio que
  busca o `User` por e-mail e compara a senha com `PasswordEncoder.matches` é mais direto que montar
  toda a cadeia `UserDetailsService`/`AuthenticationProvider`/`AuthenticationManager` do Spring Security
  só para descartá-la depois.
- **Autorização por request consulta o banco, não confia só no JWT**: o filtro JWT decodifica apenas o
  `sub` (id do usuário) do token; a cada request, busca o `User` atual no banco (com `role.permissions`
  carregado `EAGER`) e monta as `GrantedAuthority` a partir do estado atual. Isso garante que desativar
  um usuário (`User.active = false`) ou trocar seu papel tenha efeito imediato, sem esperar o token
  expirar — exigido pelo propósito do campo `active` em `03-entidades.md:40`.
- **Migration Java do Flyway gerenciada pelo Spring** para o seed do admin: confirmado via bytecode de
  `FlywayAutoConfiguration$FlywayConfiguration` (Spring Boot 4.1.1) que beans `JavaMigration` são
  coletados automaticamente (`ObjectProvider<JavaMigration>` → `configuration.javaMigrations(...)`).
  Isso permite usar `@Value` para ler as credenciais do admin inicial de `application.properties`
  (e portanto de variável de ambiente) e gerar o hash com o mesmo `PasswordEncoder` da aplicação, em
  vez de `System.getenv` cru ou de depender de `pgcrypto` no SQL.
- **Matriz papel → permissão inicial** (não há uma matriz fechada nos documentos — `/settings/roles`
  existe justamente para o admin ajustar depois): seed inicial razoável, documentado aqui e no
  comentário da migration:
  - `Salesperson`: `VIEW_OWN_LEADS`, `TRIGGER_PROSPECTING`.
  - `Manager/Administrator`: todas as 6 permissões (`CREATE_USER`, `VIEW_ALL_LEADS`, `VIEW_OWN_LEADS`,
    `EDIT_CATALOG`, `VIEW_METRICS`, `TRIGGER_PROSPECTING`).
- **Tabelas no plural** (`users`, `roles`, `permissions`, `role_permissions`) em vez do nome singular que
  o Hibernate geraria por padrão a partir de `User`/`Role`/`Permission` — evita `user`, que é palavra
  reservada em SQL e exigiria aspas em toda query.
- **`PUT /api/users/{id}` não altera senha** — o roadmap não pede redefinição de senha nesta etapa
  (isso é `/reset-password/[token]` no frontend, fora do escopo do roadmap do backend para a Etapa 1);
  o endpoint altera `name`, `role_id` e `active`.

## 3. Testes a incluir/alterar (agrupados por arquivo)

- `security/JwtServiceTest.java` (novo, unitário, sem contexto Spring)
  - gera token e extrai o `sub` (id do usuário) corretamente
  - token expirado → `parseUserId` retorna vazio
  - token com assinatura inválida/adulterado → `parseUserId` retorna vazio
- `api/AuthControllerTest.java` (novo, `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers)
  - login com e-mail/senha do admin seedado → 200 e corpo com `token` não vazio
  - login com senha errada → 401
  - login com e-mail inexistente → 401
  - login de usuário desativado → 401 (cria um usuário via repositório no teste, desativa, tenta logar)
- `api/UserControllerTest.java` (novo, `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers)
  - `POST /api/users` sem token → 401
  - `POST /api/users` com token de um usuário sem `CREATE_USER` (papel Salesperson) → 403
  - `POST /api/users` com token do admin → 201, corpo com o usuário criado, `created_by_id` = id do admin
  - `POST /api/users` com e-mail já existente → 409
  - `GET /api/users` e `GET /api/users/{id}` com token do admin → 200
  - `PUT /api/users/{id}` desativando um usuário e reaproveitando um token JWT emitido **antes** da
    desativação → a chamada seguinte com esse token antigo passa a retornar 401 (valida a decisão de
    "autorização consulta o banco a cada request")
- `LeadSystemApplicationTests.java` (existente) — sem mudança de comportamento esperada; continua
  validando que a aplicação sobe e as migrations (agora incluindo RBAC) aplicam limpo.

## 4. O que entra no projeto

Só backend nesta etapa.

1. **Migrations**
   - `V2__create_rbac_tables.sql` — tabelas `permissions`, `roles`, `role_permissions`, `users`.
   - `V3__seed_permissions_and_roles.sql` — insere as 6 permissões fixas, os papéis `Salesperson` e
     `Manager/Administrator` (UUIDs fixos e documentados) e a matriz papel→permissão da seção 2.
   - `security/SeedInitialAdminUserMigration.java` (`V4__SeedInitialAdminUser`, bean Spring
     `JavaMigration`) — insere o primeiro usuário administrador com `role = Manager/Administrator`,
     e-mail/senha vindos de `app.security.initial-admin.email`/`...password`, hash via
     `PasswordEncoder`.
2. **`application.properties`** — novas propriedades:
   - `app.security.jwt.secret=${JWT_SECRET:dev-only-insecure-jwt-signing-secret-change-in-production-0123456789}`
   - `app.security.jwt.expiration-minutes=${JWT_EXPIRATION_MINUTES:60}`
   - `app.security.initial-admin.email=${ADMIN_INITIAL_EMAIL:admin@leadsystem.local}`
   - `app.security.initial-admin.password=${ADMIN_INITIAL_PASSWORD:ChangeMe123!}`
3. **`domain`** — `Permission`, `Role`, `User` (entidades JPA, id `GenerationType.UUID`) +
   `PermissionRepository`, `RoleRepository`, `UserRepository` (Spring Data).
4. **`security`** — `JwtService` (gera/valida JWT via JJWT), `AuthService` (login),
   `JwtAuthenticationFilter` (`OncePerRextFilter`), `SecurityConfig`
   (`SecurityFilterChain` stateless + `@EnableMethodSecurity` + bean `PasswordEncoder`),
   `SeedInitialAdminUserMigration`.
5. **`api`** — `AuthController` (`POST /api/auth/login`), `UserController` (`POST/GET /api/users`,
   `GET/PUT /api/users/{id}`, todos `@PreAuthorize("hasAuthority('CREATE_USER')")`), `UserService`,
   DTOs (`LoginRequest`, `LoginResponse`, `CreateUserRequest`, `UpdateUserRequest`, `UserResponse`),
   exceções (`InvalidCredentialsException` → 401, `EmailAlreadyInUseException` → 409,
   `ResourceNotFoundException` → 404) e `ApiExceptionHandler` (`@RestControllerAdvice`).
6. Marcar os itens da Etapa 1 em `backend/plans/roadmap.md` como concluídos ao final.

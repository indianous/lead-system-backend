# Plano — Backend Etapa 0: Fundação de infraestrutura (itens restantes)

> Referência: `backend/plans/roadmap.md` (Etapa 0), `00-documentation/03-entidades.md`.

## 1. Problemas encontrados

- `backend/plans/roadmap.md:13` — não existe estrutura de pacotes além do pacote raiz
  (`br.com.joaovictornascimento.lead_system`) com a classe de bootstrap. Pacotes
  `domain`, `api`, `config`, `security`, `messaging`, `prospecting`, `notification`
  ainda não existem.
- `backend/plans/roadmap.md:14` — `spring.flyway.locations=classpath:db/migration`
  (`application.properties:13`) aponta para um diretório que existe mas está vazio.
  Ao subir a aplicação, o log confirma: `WARN ... DbValidate : No migrations found.
  Are your locations set up correctly?`. Não há `V1__baseline.sql`.
- `backend/plans/roadmap.md:15` — estratégia de geração de UUID ainda não definida
  nem documentada. Decisão: **geração pela aplicação** via `GenerationType.UUID`
  do Hibernate (`java.util.UUID.randomUUID()`), não `gen_random_uuid()` do Postgres.
  Motivo: o id fica disponível antes do `INSERT` (necessário para publicar eventos
  de domínio/notificações referenciando o id do agregado ainda na mesma transação,
  ver Etapas 8/11 do roadmap) e evita depender de uma função específica do Postgres.
  Consequência: a migration baseline não precisa de nenhuma extensão do Postgres
  (Postgres 17 nem precisaria de `pgcrypto` para `gen_random_uuid()`, mas o ponto é
  moot já que o id não é gerado pelo banco).
- `LeadSystemApplicationTests.java:6` — `@SpringBootTest` sobe o contexto usando o
  datasource de `application.properties`, ou seja, o Postgres real de
  desenvolvimento em `localhost:5433` (`docker-compose.yml`). O `pom.xml` já traz
  `spring-boot-testcontainers`, `testcontainers-junit-jupiter` e
  `testcontainers-postgresql` (linhas 125-139), mas nenhuma classe de configuração
  de teste os utiliza ainda. Isso acopla os testes ao Postgres local do dev — quebra
  em CI ou em outra máquina sem o container `lead-system-db` rodando, e mistura
  estado de teste com o banco de desenvolvimento.

## 2. Testes a incluir/alterar

Trabalho desta etapa é majoritariamente estrutural (pacotes vazios, migration
baseline sem conteúdo, decisão documentada) — não há regra de negócio para
cobrir com teste unitário. O único comportamento verificável é: **o contexto Spring
sobe e a migration Flyway aplica com sucesso contra um Postgres isolado**, o que
hoje não é garantido (depende do Postgres local). Por isso o foco do TDD aqui é
consertar/isolar esse teste, não criar testes novos de domínio:

- `backend/src/test/java/br/com/joaovictornascimento/lead_system/config/TestcontainersConfiguration.java` (novo)
  — `@TestConfiguration(proxyBeanMethods = false)` com um bean
  `PostgreSQLContainer<>("postgres:17-alpine")` anotado `@ServiceConnection`,
  reutilizável por todos os testes de integração futuros (Etapas 1+).
- `backend/src/test/java/br/com/joaovictornascimento/lead_system/LeadSystemApplicationTests.java` (alterar)
  — `@Import(TestcontainersConfiguration.class)`; `contextLoads()` passa a validar,
  de fato, que a aplicação sobe e a migration V1 aplica limpo num Postgres
  descartável, não no Postgres de desenvolvimento.

## 3. O que entra no projeto

Projeto é só backend nesta etapa (sem tipos compartilhados/BFF/componentes).

1. **Estrutura de pacotes** — criar, cada um com um `package-info.java` contendo uma
   linha de Javadoc (português) descrevendo a responsabilidade, servindo de âncora
   do pacote no VCS até a primeira classe real entrar (Etapa 1 em diante):
   - `domain` — entidades JPA e regras de negócio do domínio.
   - `api` — controllers e DTOs expostos pela API.
   - `config` — configuração de infraestrutura (beans, Flyway, OpenAPI etc.).
   - `security` — autenticação, JWT, RBAC.
   - `messaging` — integração com WhatsApp/Telegram (Etapa 8).
   - `prospecting` — busca local (Google Maps/CNPJ, Etapa 10).
   - `notification` — preferências e disparo de notificações (Etapa 11).
2. **Migration baseline** — `src/main/resources/db/migration/V1__baseline.sql`,
   contendo só um comentário SQL registrando a decisão de UUID (sem `CREATE
   EXTENSION`, pela razão acima).
3. **Testcontainers** — `TestcontainersConfiguration` (test) + import em
   `LeadSystemApplicationTests`.
4. **Documentação** — marcar os 3 itens da Etapa 0 como concluídos em
   `backend/plans/roadmap.md`, com a decisão de UUID registrada ali mesmo.

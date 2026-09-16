# Plano — Backend Etapa 7: Qualificação automática

> Referência: `backend/plans/roadmap.md` (Etapa 7), `00-documentation/01-estudo-de-caso-coleta-leads.md`
> (Objetivos: "critérios simples — produto de interesse, orçamento estimado, urgência, porte do
> negócio"; Fluxo A/B: qualificação roda logo após a criação, para os dois tipos de lead; Riscos:
> "um score mal calibrado... critérios devem ser revisados periodicamente com base em dados reais"),
> `00-documentation/03-entidades.md#Lead` (`estimated_budget_cents`, `desired_timeline`,
> `products_of_interest`, `qualification_score`).

## 1. Problemas encontrados

- **Nenhum documento define os pontos de corte do score** — de propósito: o próprio estudo de
  caso avisa que "os critérios devem ser revisados periodicamente com base em dados reais de
  conversão" (seção Riscos). Este plano propõe uma regra inicial simples e configurável (pontos
  por critério, ver seção 3), a confirmar/ajustar com o usuário — não é um número "oficial" do
  domínio, é um ponto de partida razoável.
- O critério **"porte do negócio do cliente"**, citado nos Objetivos do estudo de caso, **não
  existe como campo em nenhuma entidade** (`03-entidades.md#Lead` não tem esse atributo) — só
  aparece nos Objetivos, não no MVP (`01-estudo-de-caso-coleta-leads.md` linha 144, ao listar o
  que entra no MVP, cita só "produto de interesse, orçamento estimado, prazo desejado", batendo
  com o que o roadmap da Etapa 7 pede). Decisão: fora de escopo, sem dado para calcular esse
  critério hoje.
- **"Urgência" é texto livre** (`Lead.desired_timeline`, ex.: `"imediato"`, `"em 3 meses")` —
  não dá para tratar como um enum estruturado. Decisão: heurística simples por palavra-chave
  (lista configurável, ex. "imediato", "urgente", "hoje") em vez de qualquer NLP — compatível com
  "critérios simples" pedido pelo estudo de caso, mas é uma aproximação grosseira por natureza do
  campo (texto livre), não uma leitura semântica real.
- `CreateLeadRequest`/`UpdateLeadRequest` (`api/CreateLeadRequest.java`, `api/UpdateLeadRequest.java`)
  hoje aceitam `qualificationScore` vindo do cliente (decisão da Etapa 3, antes de existir
  qualificação automática). Como a Etapa 7 pede que o score seja **calculado** na criação e na
  atualização do lead, os dois campos deixam de fazer sentido como entrada do cliente — decisão:
  remover `qualificationScore` dos dois DTOs; o valor final vem sempre do `QualificationService`.
  **Efeito colateral no frontend** (Etapa 4, já commitada): `CreateLeadForm`/`EditLeadForm` têm
  hoje um `Select` manual de qualificação que passa a não ter mais efeito (o Jackson do Spring
  Boot ignora silenciosamente campos desconhecidos por padrão — não quebra a chamada, só o campo
  vira enfeite). Ajustar/remover esse campo no frontend fica para quando essa etapa do frontend
  for revisitada — não é bloqueante para o backend.
- `LeadControllerTest.CreateLeadRequestTest` instancia `CreateLeadRequest` diretamente por
  construtor posicional (`api/CreateLeadRequestTest.java:15-16`) — like remover o parâmetro
  `qualificationScore` do record quebra a compilação desse teste; precisa atualizar o helper
  junto com a mudança do record (não é uma regressão de comportamento, só ajuste de assinatura).
- `PublicLeadService.create` (Etapa 4) não coleta produto de interesse/orçamento/prazo — a
  submissão do site só tem nome/telefone/e-mail/mensagem. Rodar a qualificação automática ali
  hoje sempre resulta em `LOW` (nenhum critério preenchido) — correto pela regra, só documentando
  que não é um bug: falta de dado é, por definição, sinal fraco de qualificação.

## 2. Testes a incluir/alterar (agrupados por arquivo)

- `api/QualificationServiceTest.java` (novo, JUnit puro, sem contexto Spring) — cobre a matriz de
  combinações pedida pelo roadmap ("testes unitários cobrindo as combinações de critério →
  score"): nenhum critério → `LOW`; só produto de interesse → `LOW`/`MEDIUM` (limite); orçamento
  alto sozinho → `MEDIUM`/`HIGH`; prazo urgente sozinho; todos os critérios fortes juntos →
  `HIGH`; combinações parciais nos limites exatos de cada faixa de pontuação.
- `api/CreateLeadRequestTest.java` (edita) — remove o argumento `qualificationScore` do helper
  `request(...)` (só ajuste de assinatura, sem novo caso de teste).
- `api/LeadControllerTest.java` (edita) — adiciona um caso: `POST /api/leads` com produto de
  interesse + orçamento alto + prazo urgente → `qualificationScore=HIGH` na resposta; e um caso
  `PUT /api/leads/{id}` que adiciona produto de interesse a um lead antes `LOW` → recalcula para
  um score maior.
- `api/PublicLeadControllerTest.java` (edita) — adiciona verificação de que o lead criado via API
  pública nasce com `qualificationScore=LOW` (nenhum critério disponível nesse formulário).

## 3. O que entra no projeto

1. **`application.properties`**: `app.qualification.budget-threshold-cents`
   (`QUALIFICATION_BUDGET_THRESHOLD_CENTS`, default `200000` = R$ 2.000,00 — orçamento declarado
   igual ou acima disso conta como sinal forte) e `app.qualification.urgent-keywords`
   (`QUALIFICATION_URGENT_KEYWORDS`, default
   `imediato,urgente,hoje,esta semana,essa semana,amanha,amanhã` — comparação sem acentuação/caixa).
2. **`api`**: `QualificationService` — método `calculate(int productsOfInterestCount,
   Integer estimatedBudgetCents, String desiredTimeline)`, pontuação:
   - produto de interesse: `0` (nenhum) ou `1` (um ou mais)
   - orçamento: `0` (não informado), `1` (informado, abaixo do limite), `2` (informado, no limite
     ou acima)
   - prazo: `0` (não informado), `1` (informado, sem palavra-chave de urgência), `2` (contém
     palavra-chave de urgência)
   - soma (`0`–`5`) → `LOW` (`0`–`1`), `MEDIUM` (`2`–`3`), `HIGH` (`4`–`5`)
3. **`api`**: remove `qualificationScore` de `CreateLeadRequest`/`UpdateLeadRequest`;
   `LeadService.create`/`update` chamam `qualificationService.calculate(...)` com os dados já
   resolvidos (produtos, orçamento, prazo) em vez de usar um valor vindo do request;
   `PublicLeadService.create` também chama o serviço (sempre resulta em `LOW` hoje, pelos campos
   que esse formulário coleta).
4. Nenhuma migration nova — `Lead.qualification_score` já existe desde a Etapa 3.

### Backlog

- Marcar a Etapa 7 em `backend/plans/roadmap.md` como concluída ao final, registrando a regra de
  pontuação escolhida (para facilitar a recalibração futura mencionada no estudo de caso) e a
  remoção de `qualificationScore` dos DTOs de request.
- Abrir uma nota para a Etapa 4 do frontend (já commitada): o `Select` manual de qualificação em
  `CreateLeadForm`/`EditLeadForm` deixa de ter efeito — ajuste de UI não incluído neste plano de
  backend.

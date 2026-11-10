# PediuPatrão 🍕

Sistema de pedidos de pizzaria (Engenharia de Software, UMC).

Esta versão reúne duas entregas sobre o projeto original:

- **Atividade Prática M1:** controle de acesso por perfil, fluxo de pedidos, descontos e cancelamentos exclusivos do gerente e auditoria persistente.
- **Dashboard Administrativo:** painel de vendas para o responsável pelo estabelecimento, alimentado por uma API própria de indicadores.

**Tecnologias:** Java 21, Spring Boot 3.4, Spring Security, MongoDB, Thymeleaf, Bootstrap, Chart.js, Maven.

## Sumário

- [Como executar](#como-executar)
- [Dashboard Administrativo](#dashboard-administrativo)
- [Perfis e permissões](#perfis-e-permissões)
- [Pedidos](#pedidos)
- [Auditoria](#auditoria)
- [API](#api)
- [Testes](#testes)
- [Documentação e entregáveis](#documentação-e-entregáveis)
- [Fluxo de branches](#fluxo-de-branches)
- [Decisões e limitações](#decisões-e-limitações)

## Como executar

Pré-requisitos: JDK 21 e um MongoDB rodando em localhost:27017 (padrão do projeto).

```powershell
# Windows (PowerShell) - opcional: informar outra URI de banco sem editar arquivos
$env:SPRING_DATA_MONGODB_URI="mongodb+srv://usuario:senha@host/?appName=Cluster0"
.\mvnw.cmd spring-boot:run
```

```bash
# Linux / macOS
export SPRING_DATA_MONGODB_URI="mongodb+srv://usuario:senha@host/?appName=Cluster0"
./mvnw spring-boot:run
```

Se a variável não for definida, o app usa o MongoDB local (`mongodb://localhost:27017`, banco `pizzaria`); para outro banco, defina também `SPRING_DATA_MONGODB_DATABASE`.
Acesse http://localhost:8080. **Não versione credenciais reais do banco.**

### Usuários de demonstração (criados automaticamente)

| Usuário | Senha | Perfil |
|---|---|---|
| admin | teste123 | ADMIN |
| gerente | teste123 | GERENTE |
| atendente | teste123 | ATENDENTE |

Senhas apenas para desenvolvimento; ficam armazenadas com BCrypt.

### Dados de exemplo para o dashboard

Para ver o dashboard preenchido, suba o app com a variável abaixo. Ela cria cerca de 120 pedidos dos últimos 40 dias (e 6 produtos, se não houver nenhum). Só semeia quando a coleção `pedidos` está vazia, então nunca duplica nem apaga dados.

```powershell
$env:DASHBOARD_SEED_DEMO="true"
.\mvnw.cmd spring-boot:run
```

## Dashboard Administrativo

Tela em **/dashboard** (perfis ADMIN e GERENTE), que consome a API `/api/dashboard/*`.

| Requisito | O que a tela mostra |
|---|---|
| RF01 Indicadores gerais | Faturamento, pedidos válidos, ticket médio, cancelados, descontos concedidos e itens vendidos |
| RF02 Análise de vendas | Gráfico de faturamento por dia ou mês e gráfico de vendas por tipo de entrega |
| RF03 Situação dos pedidos | Gráfico e contadores por status (os 7 status sempre aparecem) |
| RF04 Produtos mais vendidos | Ranking top 5 por quantidade, com faturamento |
| RF05 Filtros | Data inicial e final, tipo de entrega, status e agrupamento (dia/mês) |
| RF06 Integração com a API | Todos os dados vêm de `fetch` nos endpoints, sem valores fixos na tela |
| RF07 Atualização e tratamento | Atualiza ao mudar filtro, pelo botão e a cada 60 s; mostra erro da API e aviso de período sem vendas |

Regra principal: **venda válida é todo pedido não cancelado**. Faturamento, ticket médio, descontos, ranking e gráficos de venda usam só pedidos válidos; a situação dos pedidos conta todos.

Sem datas informadas, o período é os últimos 30 dias (máximo de 366 dias). Datas usam o fuso `America/Sao_Paulo`.

Requisitos funcionais e não funcionais, modelagem, endpoints com exemplos e erros, e roteiro de testes: [docs/DASHBOARD.md](docs/DASHBOARD.md).

## Perfis e permissões

| Perfil | Pode |
|---|---|
| ADMIN | administrar usuários e perfis, consultar pedidos, consultar a auditoria, **consultar o dashboard** |
| GERENTE | gerenciar pedidos (avançar status), **aplicar descontos**, **cancelar pedidos**, consultar clientes, consultar a auditoria, **consultar o dashboard** |
| ATENDENTE | cadastrar e atualizar clientes, registrar pedidos, acompanhar o andamento |

Desconto e cancelamento são **só do GERENTE**, inclusive em relação ao ADMIN. A proteção é feita no backend
(`SecurityConfig` por rota + `@PreAuthorize` nos métodos de serviço); esconder botões na tela é apenas conveniência.
O dashboard e `/api/dashboard/**` são bloqueados para ATENDENTE (403).
O ADMIN não pode alterar o próprio perfil, o próprio username nem excluir a si mesmo (proteção contra autoelevação),
e perfis só aceitam ADMIN, GERENTE ou ATENDENTE. O CSRF está ativo em formulários e chamadas `fetch`.

## Pedidos

- O pedido registra itens (produto, preço unitário, quantidade, subtotal), desconto, total, usuário responsável e data/hora de entrada.
- **O preço vem do cadastro de produtos e o total é calculado no servidor**; a requisição só informa cliente, tipo e itens (produto + quantidade).
- Fluxo obrigatório: `RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA (entrega) ou RETIRADO (retirada) → FINALIZADO`.
- Saída (entrega/retirada) e finalização guardam data/hora e responsável; todo o histórico de status fica no pedido.
- Transições inválidas (ex.: finalizar um pedido em preparação) e qualquer alteração após o cancelamento são bloqueadas.
- **Desconto:** percentual maior que 0 e até 20% do valor do pedido, somente antes da saída.
- **Cancelamento:** exige justificativa (mínimo 5 caracteres), só antes da saída; os dados do pedido são preservados.

## Auditoria

Coleção `auditoria` no MongoDB. Cada evento guarda: usuário e perfil, data/hora, operação, entidade e id,
campos alterados com valor anterior e novo, e justificativa quando exigida.

Operações auditadas: `INCLUSAO_PEDIDO`, `ALTERACAO_STATUS_PEDIDO`, `DESCONTO_PEDIDO`, `CANCELAMENTO_PEDIDO`,
`INCLUSAO_CLIENTE`, `ALTERACAO_CLIENTE` (inclui endereço e telefone), `EXCLUSAO_CLIENTE`.

- Consulta: tela **/auditoria** e `GET /api/auditoria?entidade=&entidadeId=&limite=` — somente ADMIN e GERENTE.
- Integridade: o repositório de auditoria é somente leitura e o serviço só insere; não existe endpoint de alteração ou exclusão.
- Proteção de dados: senhas e tokens nunca entram na auditoria; telefones são gravados mascarados (ex.: `(11) *****-1234`).

## API

| Método e rota | Quem acessa |
|---|---|
| `GET /api/pedidos` | ADMIN, GERENTE, ATENDENTE |
| `POST /api/pedidos` | GERENTE, ATENDENTE |
| `PUT /api/pedidos/{id}/status?status=...` | GERENTE |
| `POST /api/pedidos/{id}/desconto` `{"percentual": 10}` | somente GERENTE |
| `POST /api/pedidos/{id}/cancelamento` `{"justificativa": "..."}` | somente GERENTE |
| `GET /api/auditoria` | ADMIN, GERENTE |
| `/api/usuarios/**` | ADMIN |
| `GET /api/dashboard/resumo` | ADMIN, GERENTE |
| `GET /api/dashboard/vendas-por-periodo` | ADMIN, GERENTE |
| `GET /api/dashboard/pedidos-por-status` | ADMIN, GERENTE |
| `GET /api/dashboard/produtos-mais-vendidos` | ADMIN, GERENTE |
| `GET /api/dashboard/vendas-por-tipo-entrega` | ADMIN, GERENTE |

Filtros dos endpoints do dashboard (todos opcionais): `inicio` e `fim` (`yyyy-MM-dd`), `tipoEntrega` (`ENTREGA` ou `RETIRADA`) e `status`; além de `agrupamento` (`DIA` ou `MES`) em vendas por período e `limite` (1 a 50) no ranking.
Exemplo: `GET /api/dashboard/resumo?inicio=2026-11-01&fim=2026-11-10&tipoEntrega=ENTREGA`.

Acesso negado retorna 403 em JSON (`{"erro":"Acesso negado"}`) e sem login retorna 401; cada tentativa negada é registrada em log.
Entradas inválidas nos endpoints do dashboard retornam 400 com `{"erro":"..."}`. A lista completa de erros e exemplos de resposta está em [docs/DASHBOARD.md](docs/DASHBOARD.md).

## Testes

```powershell
.\mvnw.cmd test
```

93 testes automatizados, sem necessidade de banco (Mockito e MockMvc com Spring Security Test).

| Área | Testes |
|---|---|
| Regras de pedido, status e auditoria | `PedidoServiceTest`, `StatusPedidoTest`, `AuditoriaServiceTest` |
| Clientes e usuários | `ClienteServiceTest`, `UsuarioServiceTest` |
| Autorização por perfil | `SegurancaApiTest`, `SegurancaUsuariosClientesTest` |
| **Dashboard: regras e cálculos** | `DashboardServiceTest` (16 testes) |
| **Dashboard: API e segurança** | `DashboardApiTest` (6 testes) |

## Documentação e entregáveis

| Entregável | Onde |
|---|---|
| Requisitos funcionais e não funcionais, endpoints, parâmetros, exemplos e erros | [docs/DASHBOARD.md](docs/DASHBOARD.md) |
| Evidências de testes | seção Testes de [docs/DASHBOARD.md](docs/DASHBOARD.md) |
| Dashboard administrativo funcional | `/dashboard` |
| Código-fonte da API integrada ao banco | `DashboardController`, `DashboardService`, `PedidoRepository` |
| Vídeo de apresentação (até 3 min) | _colar o link aqui_ |

## Fluxo de branches

O projeto segue gitflow. O dashboard foi desenvolvido em `feature/dashboard-admin`, a partir de `develop`, e integrado por Pull Request.

## Decisões e limitações

- O enunciado da M1 não atribui a exclusão de clientes a nenhum perfil. Foi decidido que **somente o GERENTE exclui clientes** (ATENDENTE cadastra e atualiza; ADMIN não acessa clientes). A exclusão também é auditada (`EXCLUSAO_CLIENTE`).
- Somente o GERENTE avança o status dos pedidos; o ATENDENTE registra o pedido e acompanha o andamento.
- A auditoria é gravada logo após a alteração do pedido ou cliente, sem transação entre as duas coleções.
- Telefones na auditoria são gravados mascarados; quando a máscara fica igual antes e depois, o valor novo recebe a marca `(alterado)`.
- Dashboard: o faturamento usa o `total` do pedido (já com desconto), enquanto o faturamento do ranking de produtos é o valor bruto dos itens, antes do desconto.
- Dashboard: o filtro de período é feito no MongoDB e as somas e agrupamentos são feitos em memória sobre o resultado, o que atende bem o volume de uma pizzaria; para volumes muito maiores, o caminho seria usar agregações do próprio MongoDB e um índice em `dataEntrada`.
- Os dados de demonstração só existem quando `DASHBOARD_SEED_DEMO=true` e nunca sobrescrevem pedidos existentes.

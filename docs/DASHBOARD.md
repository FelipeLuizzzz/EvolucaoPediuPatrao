# Dashboard Administrativo — PediuPatrão

Painel para o responsável pelo estabelecimento acompanhar vendas, pedidos e tendências.
Acesso: `http://localhost:8080/dashboard` (perfis **ADMIN** e **GERENTE**).

## 1. Análise

### Requisitos funcionais

| Id | Requisito | Como foi atendido |
|---|---|---|
| RF01 | Indicadores gerais de vendas | Cards: faturamento, pedidos válidos, ticket médio, cancelados, descontos concedidos e itens vendidos (`GET /api/dashboard/resumo`) |
| RF02 | Análise de vendas (gráficos) | Barras de faturamento por dia ou mês (`vendas-por-periodo`) e rosca de vendas por tipo de entrega (`vendas-por-tipo-entrega`) |
| RF03 | Situação dos pedidos | Rosca e contadores por status, com os 7 status sempre presentes (`pedidos-por-status`) |
| RF04 | Produtos mais vendidos | Ranking top 5 por quantidade, com faturamento (`produtos-mais-vendidos`) |
| RF05 | Filtros de consulta | Período (data inicial/final), tipo de entrega, status e agrupamento (dia/mês) |
| RF06 | Integração com a API | A tela consome os 5 endpoints via `fetch`, sem dados fixos no front-end |
| RF07 | Atualização e tratamento de dados | Atualização ao mudar filtro, botão Atualizar e refresh automático a cada 60 s; botão desabilitado durante a carga; mensagem de erro da API; aviso quando não há vendas; valores nulos tratados como zero |

### Regras de negócio

- **Venda válida** é todo pedido que não está `CANCELADO`. Faturamento, ticket médio, descontos, itens, ranking e gráficos de venda usam só pedidos válidos.
- A situação dos pedidos (RF03) conta todos os pedidos do período, inclusive cancelados.
- O faturamento usa o campo `total` do pedido (já com desconto). O faturamento do ranking é o valor bruto dos itens (`subtotal`, antes do desconto).
- O período filtra por `dataEntrada`, no fuso `America/Sao_Paulo`, com a data final inclusiva.
- Sem datas informadas, o período é os últimos 30 dias. O período máximo é 366 dias.

### Requisitos não funcionais

| Id | Requisito |
|---|---|
| RNF01 | Segurança: `/dashboard` e `/api/dashboard/**` só para ADMIN e GERENTE, validado no backend (`SecurityConfig`) |
| RNF02 | Precisão: valores monetários em `BigDecimal`, arredondamento HALF_UP com 2 casas |
| RNF03 | Consistência: gráficos devolvem todos os dias do período (zero quando não há venda) e todos os status |
| RNF04 | Confiabilidade: entradas inválidas retornam 400 com `{"erro": "..."}`; nenhum erro vaza como stack trace |
| RNF05 | Usabilidade: layout responsivo (Bootstrap), valores em R$ no formato pt-BR |
| RNF06 | Manutenibilidade: regra de negócio isolada em `DashboardService`, coberta por testes sem banco |
| RNF07 | Desempenho: a consulta ao MongoDB filtra por período no banco (`$gte`/`$lt` em `dataEntrada`); agregações em memória sobre o conjunto já filtrado |

### Modelagem de dados

Não foram criadas coleções novas. O dashboard lê a coleção `pedidos`:

| Campo | Uso |
|---|---|
| `dataEntrada` | filtro de período e agrupamento |
| `status` | situação dos pedidos, exclusão de cancelados |
| `tipoEntrega` | filtro e gráfico por tipo |
| `total`, `valorDesconto` | faturamento e descontos |
| `itens[]` (`produtoId`, `nome`, `quantidade`, `subtotal`) | ranking e itens vendidos |

Consulta base: `{ dataEntrada: { $gte: <inicio 00:00>, $lt: <fim + 1 dia 00:00> } }`.

## 2. API

Base: `/api/dashboard`. Todos os endpoints são `GET`, exigem sessão autenticada com perfil ADMIN ou GERENTE e retornam JSON.
Parâmetros comuns (todos opcionais):

| Parâmetro | Formato | Descrição |
|---|---|---|
| `inicio` | `yyyy-MM-dd` | Data inicial (padrão: 29 dias antes de `fim`) |
| `fim` | `yyyy-MM-dd` | Data final, inclusiva (padrão: hoje) |
| `tipoEntrega` | `ENTREGA` ou `RETIRADA` | Filtra por tipo |
| `status` | `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO`, `CANCELADO` | Filtra por status |

### GET /api/dashboard/resumo

Parâmetros: `inicio`, `fim`, `tipoEntrega`, `status`.

```
GET /api/dashboard/resumo?inicio=2026-11-01&fim=2026-11-10&tipoEntrega=ENTREGA
```
```json
{
  "inicio": "2026-11-01",
  "fim": "2026-11-10",
  "totalPedidos": 4,
  "pedidosValidos": 3,
  "pedidosCancelados": 1,
  "faturamento": 175.00,
  "ticketMedio": 58.33,
  "totalDescontos": 5.00,
  "itensVendidos": 7
}
```

### GET /api/dashboard/vendas-por-periodo

Parâmetros: `inicio`, `fim`, `tipoEntrega`, `status`, `agrupamento` (`DIA` padrão, ou `MES`).

```
GET /api/dashboard/vendas-por-periodo?inicio=2026-11-01&fim=2026-11-03&agrupamento=DIA
```
```json
[
  { "periodo": "2026-11-01", "quantidade": 1, "total": 100.00 },
  { "periodo": "2026-11-02", "quantidade": 0, "total": 0.00 },
  { "periodo": "2026-11-03", "quantidade": 2, "total": 75.00 }
]
```
Com `agrupamento=MES`, `periodo` vem como `2026-11`.

### GET /api/dashboard/pedidos-por-status

Parâmetros: `inicio`, `fim`, `tipoEntrega`.

```json
[
  { "status": "RECEBIDO", "quantidade": 1 },
  { "status": "EM_PREPARACAO", "quantidade": 0 },
  { "status": "PRONTO", "quantidade": 0 },
  { "status": "SAIU_PARA_ENTREGA", "quantidade": 0 },
  { "status": "RETIRADO", "quantidade": 0 },
  { "status": "FINALIZADO", "quantidade": 2 },
  { "status": "CANCELADO", "quantidade": 1 }
]
```

### GET /api/dashboard/produtos-mais-vendidos

Parâmetros: `inicio`, `fim`, `tipoEntrega`, `status`, `limite` (1 a 50, padrão 5). Ordenado por quantidade, depois por faturamento.

```
GET /api/dashboard/produtos-mais-vendidos?limite=2
```
```json
[
  { "produtoId": "p2", "nome": "Refrigerante", "quantidade": 4, "faturamento": 42.00 },
  { "produtoId": "p1", "nome": "Pizza Calabresa", "quantidade": 3, "faturamento": 150.00 }
]
```

### GET /api/dashboard/vendas-por-tipo-entrega

Parâmetros: `inicio`, `fim`, `status`.

```json
[
  { "tipoEntrega": "ENTREGA", "quantidade": 2, "total": 130.00 },
  { "tipoEntrega": "RETIRADA", "quantidade": 1, "total": 45.00 }
]
```

### Erros

| HTTP | Quando | Corpo |
|---|---|---|
| 400 | Data fora do formato `yyyy-MM-dd` ou `limite` não numérico | `{"erro":"Parâmetro inválido: inicio"}` |
| 400 | `inicio` posterior a `fim` | `{"erro":"A data inicial não pode ser posterior à data final."}` |
| 400 | Período maior que 366 dias | `{"erro":"O período máximo de consulta é de 366 dias."}` |
| 400 | `tipoEntrega` inválido | `{"erro":"Tipo de entrega inválido. Use ENTREGA ou RETIRADA."}` |
| 400 | `status` inválido | `{"erro":"Status inválido. Use: RECEBIDO, EM_PREPARACAO, ..."}` |
| 400 | `agrupamento` inválido | `{"erro":"Agrupamento inválido. Use DIA ou MES."}` |
| 400 | `limite` fora de 1 a 50 | `{"erro":"O limite deve estar entre 1 e 50."}` |
| 401 | Sem sessão | corpo vazio |
| 403 | Perfil ATENDENTE | `{"erro":"Acesso negado"}` |

## 3. Testes

### Automatizados

```powershell
.\mvnw.cmd test
```

Não precisam de banco (Mockito e MockMvc).

| Classe | O que valida |
|---|---|
| `DashboardServiceTest` (16 testes) | faturamento sem cancelados, ticket médio, período sem pedidos, filtros de tipo e status, dias sem venda preenchidos, agrupamento por mês, 7 status sempre presentes, ordem e limite do ranking, vendas por tipo, e todos os erros de validação |
| `DashboardApiTest` (6 testes) | 401 sem login, 403 para ATENDENTE, 200 para GERENTE e ADMIN, JSON de resposta, 400 para data inválida e para regra de negócio |

### Evidência

Execução dos testes automatizados com `.\mvnw.cmd test`:

```text
[INFO] Results:
[INFO]
[INFO] Tests run: 93, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  12.787 s
[INFO] Finished at: 2026-11-10T18:19:03-03:00
[INFO] ------------------------------------------------------------------------
```

| Resultado | Valor |
|---|---|
| Testes executados | 93 |
| Falhas | 0 |
| Erros | 0 |
| Ignorados | 0 |
| Status | ✅ BUILD SUCCESS |
| Tempo total | 12,8 s |
| Data/hora | 10/11/2026 às 18:19 (UTC-3) |

### Manuais (roteiro com dados de demonstração)

Subir com dados de exemplo (só cria dados se a coleção `pedidos` estiver vazia):

```powershell
$env:DASHBOARD_SEED_DEMO="true"
.\mvnw.cmd spring-boot:run
```

Entrar como `gerente` / `teste123`.

| # | Passo | Resultado esperado | Print |
|---|---|---|---|
| 1 | Abrir `/dashboard` | cards, 3 gráficos e ranking preenchidos | |
| 2 | Escolher tipo "Retirada" | números e gráficos diminuem | |
| 3 | Escolher status "Cancelado" | faturamento zera e o aviso de "nenhuma venda" aparece | |
| 4 | Agrupar por "Mês" | gráfico de barras passa a ter 1 ou 2 barras | |
| 5 | Data inicial maior que a final | mensagem de erro vermelha da API | |
| 6 | Abrir `/api/dashboard/resumo` no navegador como `atendente` | 403 | |
| 7 | Abrir `/dashboard` como `admin` | acesso liberado | |

## 4. Gitflow

```powershell
git checkout develop
git pull
git checkout -b feature/dashboard-admin
git add .
git commit -m "feat: dashboard administrativo com API de indicadores"
git push -u origin feature/dashboard-admin
```

Depois, abrir Pull Request de `feature/dashboard-admin` para `develop`.

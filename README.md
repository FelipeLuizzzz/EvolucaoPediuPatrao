# PediuPatrão 🍕

Sistema de pedidos de pizzaria (Atividade Prática M1 — Engenharia de Software, UMC).
Esta versão evolui o projeto original com **controle de acesso por perfil**, **fluxo de pedidos**,
**descontos e cancelamentos exclusivos do gerente** e **auditoria persistente**.

**Tecnologias:** Java 21, Spring Boot 3.4, Spring Security, MongoDB, Thymeleaf, Maven.

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

### Testes

```powershell
.\mvnw.cmd test
```

Os testes não precisam de banco: usam Mockito e MockMvc (Spring Security Test).

## Perfis e permissões

| Perfil | Pode |
|---|---|
| ADMIN | administrar usuários e perfis, consultar pedidos, consultar a auditoria |
| GERENTE | gerenciar pedidos (avançar status), **aplicar descontos**, **cancelar pedidos**, consultar clientes, consultar a auditoria |
| ATENDENTE | cadastrar e atualizar clientes, registrar pedidos, acompanhar o andamento |

Desconto e cancelamento são **só do GERENTE**, inclusive em relação ao ADMIN. A proteção é feita no backend
(`SecurityConfig` por rota + `@PreAuthorize` nos métodos de serviço); esconder botões na tela é apenas conveniência.
O ADMIN não pode alterar o próprio perfil, o próprio username nem excluir a si mesmo (proteção contra autoelevação),
e perfis só aceitam ADMIN, GERENTE ou ATENDENTE. O CSRF está ativo em formulários e chamadas `fetch`.

## Pedidos (RF02, RF03, RF04)

- O pedido registra itens (produto, preço unitário, quantidade, subtotal), desconto, total, usuário responsável e data/hora de entrada.
- **O preço vem do cadastro de produtos e o total é calculado no servidor**; a requisição só informa cliente, tipo e itens (produto + quantidade).
- Fluxo obrigatório: `RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA (entrega) ou RETIRADO (retirada) → FINALIZADO`.
- Saída (entrega/retirada) e finalização guardam data/hora e responsável; todo o histórico de status fica no pedido.
- Transições inválidas (ex.: finalizar um pedido em preparação) e qualquer alteração após o cancelamento são bloqueadas.
- **Desconto:** percentual maior que 0 e até 20% do valor do pedido, somente antes da saída.
- **Cancelamento:** exige justificativa (mínimo 5 caracteres), só antes da saída; os dados do pedido são preservados.

## Auditoria (RF05)

Coleção `auditoria` no MongoDB. Cada evento guarda: usuário e perfil, data/hora, operação, entidade e id,
campos alterados com valor anterior e novo, e justificativa quando exigida.

Operações auditadas: `INCLUSAO_PEDIDO`, `ALTERACAO_STATUS_PEDIDO`, `DESCONTO_PEDIDO`, `CANCELAMENTO_PEDIDO`,
`INCLUSAO_CLIENTE`, `ALTERACAO_CLIENTE` (inclui endereço e telefone), `EXCLUSAO_CLIENTE`.

- Consulta: tela **/auditoria** e `GET /api/auditoria?entidade=&entidadeId=&limite=` — somente ADMIN e GERENTE.
- Integridade: o repositório de auditoria é somente leitura e o serviço só insere; não existe endpoint de alteração ou exclusão.
- Proteção de dados: senhas e tokens nunca entram na auditoria; telefones são gravados mascarados (ex.: `(11) *****-1234`).

## API principal

| Método e rota | Quem acessa |
|---|---|
| `GET /api/pedidos` | ADMIN, GERENTE, ATENDENTE |
| `POST /api/pedidos` | GERENTE, ATENDENTE |
| `PUT /api/pedidos/{id}/status?status=...` | GERENTE |
| `POST /api/pedidos/{id}/desconto` `{"percentual": 10}` | somente GERENTE |
| `POST /api/pedidos/{id}/cancelamento` `{"justificativa": "..."}` | somente GERENTE |
| `GET /api/auditoria` | ADMIN, GERENTE |
| `/api/usuarios/**` | ADMIN |

Acesso negado retorna 403 em JSON (`{"erro":"Acesso negado"}`) e sem login retorna 401; cada tentativa negada é registrada em log.

## Principais alterações

- Novas entidades/tipos: `StatusPedido`, `ItemPedido`, `MovimentoStatus`, `Auditoria`; `Pedido` reescrito.
- Novos: `AuditoriaService`, `AuditoriaRepository` (somente leitura), `AuditoriaController`, tela de auditoria e DTOs de pedido, desconto e cancelamento.
- `PedidoService` com as regras de negócio e `@PreAuthorize`; `ClienteService` com auditoria das alterações.
- Telas de pedidos (novo pedido, ações por status, desconto e cancelamento) e menu por perfil.
- Testes automatizados de regras de negócio, auditoria e autorização.

## Decisões de permissão e limitações

- O enunciado não atribui a exclusão de clientes a nenhum perfil. Foi decidido que **somente o GERENTE exclui clientes** (ATENDENTE cadastra e atualiza; ADMIN não acessa clientes). A exclusão também é auditada (`EXCLUSAO_CLIENTE`).
- Somente o GERENTE avança o status dos pedidos; o ATENDENTE registra o pedido e acompanha o andamento.
- A auditoria é gravada logo após a alteração do pedido ou cliente, sem transação entre as duas coleções.
- Telefones na auditoria são gravados mascarados; quando a máscara fica igual antes e depois, o valor novo recebe a marca `(alterado)`.
- Testes: 71 testes automatizados (`.\mvnw.cmd test`), sem necessidade de banco.
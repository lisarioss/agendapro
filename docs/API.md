# API AgendaPro 1.0

Base: `/api`. JSON UTF-8; IDs UUID; nomes de campos em camelCase. Exceto login/cadastro e saúde, envie `Authorization: Bearer TOKEN`. Em Swagger, use **Authorize** com o token. O identificador da empresa vem do token. `tenantId` em um corpo de criação é rejeitado.

## Autenticação

```http
POST /api/auth/register
Content-Type: application/json

{"companyName":"Studio Lisa","slug":"studio-lisa","timezone":"America/Sao_Paulo","name":"Lisa","email":"lisa@example.com","password":"Uma-senha-forte-2026!"}
```

Resposta `201`: `accessToken`, `tokenType`, `expiresIn` (segundos), `tenantId`, `userId`. Cria empresa e usuário ADMIN atomicamente. Slug único de 3 a 60 caracteres (`a-z`, `0-9`, hífen); senhas com mínimo de 12 caracteres e máximo de 72 bytes UTF-8.

```http
POST /api/auth/login

{"slug":"studio-lisa","email":"lisa@example.com","password":"Uma-senha-forte-2026!"}
```

Resposta `200` com o mesmo contrato de token. `GET /api/auth/me` retorna usuário, empresa, role, professionalId e timezone. `PUT /api/auth/password` recebe `currentPassword` e `newPassword`, retorna `204` e revoga tokens anteriores.

## Cadastros

| Recurso | Corpo POST/PUT |
|---|---|
| `/clients` | `{"name":"Mariana","email":"mariana@example.com","phone":"11999990000"}` |
| `/professionals` | `{"name":"Ana"}` |
| `/services` | `{"name":"Consulta","durationMinutes":30,"price":100.00}` |

`GET /recurso?limit=100&offset=0` lista cadastros. `GET /recurso/{id}` consulta um registro. `POST` retorna `201`, `PUT /{id}` retorna `200`, `DELETE /{id}` desativa e retorna `204`. Listagens incluem cadastros inativos; verifique `active`. Só clientes têm e-mail/telefone; os dois campos são opcionais. As respostas incluem `id` e `tenantId`.

`GET /users` lista usuários sem senha/hash; `POST /users` cria usuário:

```json
{"name":"Ana","email":"ana@example.com","password":"Uma-senha-forte-2026!","role":"PROFESSIONAL","professionalId":"UUID_DO_PROFISSIONAL"}
```

Roles: `ADMIN`, `ATTENDANT`, `PROFESSIONAL`. Somente PROFESSIONAL deve fornecer professionalId. `DELETE /users/{id}` desativa a conta. Gerenciamento de usuários exige ADMIN.

## Serviços, jornada e folgas do profissional

`PUT /professionals/{id}/services` substitui os vínculos. Corpo: `["UUID_SERVICO_1","UUID_SERVICO_2"]`. `GET` no mesmo caminho retorna os serviços vinculados.

`PUT /professionals/{id}/working-hours` substitui toda a jornada; lista vazia deixa o profissional sem horários:

```json
[
 {"dayOfWeek":1,"startTime":"09:00","endTime":"12:00"},
 {"dayOfWeek":1,"startTime":"13:00","endTime":"17:00"}
]
```

`dayOfWeek`: 1 segunda, 7 domingo. Máximo de 28 períodos, sem sobreposição. `GET` retorna a jornada.

`POST /professionals/{id}/time-off` retorna `201`:

```json
{"startsAt":"2030-01-07T13:00:00-03:00","endsAt":"2030-01-07T17:00:00-03:00","reason":"Folga"}
```

`GET` lista folgas; `DELETE /professionals/{id}/time-off/{offId}` remove o bloqueio. Alterações exigem ADMIN. Folga que sobrepõe reserva ativa retorna `409`.

## Disponibilidade e reservas

```http
GET /api/availability?professionalId=UUID&serviceId=UUID&date=2030-01-07&stepMinutes=15
```

Retorna lista de `{startsAt, endsAt}` com offset no fuso da empresa. `stepMinutes`: 5 a 60; default 15. A data é local à empresa. Não garante a reserva; criar o agendamento valida o horário novamente.

```http
POST /api/appointments

{"professionalId":"UUID","clientId":"UUID","serviceId":"UUID","startsAt":"2030-01-07T09:00:00-03:00","notes":"Primeira consulta"}
```

Resposta `201` com agendamento, estado `PENDING`, fim e preço calculados pelo servidor. Não envie preço, fim, status ou tenantId. Escolha uma data futura no limite de 366 dias.

```http
GET /api/appointments?from=2030-01-07T00:00:00Z&to=2030-01-08T00:00:00Z&limit=100&offset=0
GET /api/appointments/{id}
PATCH /api/appointments/{id}/reschedule

{"startsAt":"2030-01-07T10:00:00-03:00"}
```

Listagem retorna reservas que intersectam `[from,to)` e aceita filtros `professionalId` e `status`. Em URLs, codifique `+` do offset como `%2B`, ou use `Z`. O período máximo é 93 dias. Respostas incluem clientName, professionalName e serviceName.

```http
PATCH /api/appointments/{id}/status

{"status":"CONFIRMED"}
```

Estados: `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELED`, `NO_SHOW`. Veja as transições e regras de horário no README.

`GET /appointments/{id}/events` retorna `id`, `action`, `actorId`, `occurredAt`. Ações registradas: `CREATED`, `RESCHEDULED`, e transições como `PENDING_TO_CONFIRMED`.

## Erros

| Status | Significado |
|---|---|
| 400 | Contrato/UUID/data inválida, regra de entrada ou parâmetro incorreto |
| 401 | Credenciais incorretas, token ausente/inválido/expirado ou conta desativada |
| 403 | Usuário sem permissão |
| 404 | Recurso não existe na empresa autenticada |
| 409 | Horário ocupado, dados duplicados ou transição inválida |
| 429 | Limite de tentativas de autenticação excedido |
| 503 | Banco indisponível no endpoint de saúde |

Erros de aplicação retornam `{status,message}`, e validações podem incluir `errors`. Erros de token inválido também podem ser produzidos pelo filtro Spring Security; use o status HTTP como referência. Dados internos, SQL e hashes não são retornados ao cliente.

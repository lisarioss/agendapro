# AgendaPro

Sistema de agendamento multi-tenant em Java 21 e Spring Boot 3.5.16, com API REST, interface web, autenticação JWT e PostgreSQL. Cada empresa mantém seus clientes, profissionais, serviços e agenda isolados.

## Experimentar agora no Windows

O pacote ZIP da entrega inclui `agendapro.jar`, já compilado. No repositório Git, o JAR não é versionado: `demo.ps1` compila o projeto automaticamente com o Maven Wrapper quando necessário. Tenha Java 21 e, para a primeira compilação, acesso à internet:

```powershell
cd "CAMINHO\agendapro"
.\demo.ps1
```

Abra [http://localhost:8080](http://localhost:8080).

Se a porta 8080 já estiver ocupada, execute `.\demo.ps1 -Port 8088` e abra `http://localhost:8088`.

| Campo | Demonstração |
|---|---|
| Empresa | `studio-demo` |
| E-mail | `lisa@example.com` |
| Senha | `Demo-AgendaPro-2026!` |

O modo demo cria o Studio Aurora com dois profissionais, dois serviços, três clientes e três atendimentos para amanhã. Usa H2 em memória, fica restrito a `127.0.0.1` e **perde os dados ao encerrar**. Não use as credenciais ou a chave do demo em produção. Ctrl+C encerra a aplicação.

## Executar com PostgreSQL e persistência

Instale Docker Desktop e execute, na pasta do projeto:

```powershell
.\setup.ps1
docker compose up --build -d
docker compose logs -f app
```

`setup.ps1` cria `.env` com senha do banco e chave JWT aleatórias e preserva um `.env` existente. O Compose inicia PostgreSQL e aplicação; o Flyway aplica as migrações automaticamente. Abra `http://localhost:8080` e use **Criar empresa**. Não há conta predefinida fora do demo.

Os dados ficam no volume `postgres_data`. `docker compose down` para os serviços e preserva o volume. Não use `down -v` se quiser manter os dados. O banco não publica porta no host; a aplicação publica apenas em localhost.

Para usar PostgreSQL já instalado, crie um banco vazio `agendapro`, configure as variáveis no PowerShell e execute o JAR:

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/agendapro'
$env:DB_USERNAME = 'agendapro'
$env:DB_PASSWORD = 'SENHA_DO_SEU_BANCO'
$env:JWT_SECRET = 'SEGREDO_ALEATORIO_COM_PELO_MENOS_32_BYTES'
java -jar .\agendapro.jar
```

A migração PostgreSQL usa a extensão `btree_gist`. Se o usuário da aplicação não puder instalá-la, um administrador deve executar `CREATE EXTENSION IF NOT EXISTS btree_gist;` no banco antes da primeira inicialização. Use banco separado para testes.

## Usar o sistema

1. Crie sua empresa ou entre na conta demo.
2. Cadastre clientes, profissionais e serviços (duração e preço).
3. Em **Disponibilidade**, escolha um profissional, marque os serviços oferecidos e salve.
4. Adicione os períodos de trabalho semanais. Por exemplo: segunda-feira, 09:00–12:00 e 13:00–17:00. Dias sem períodos não têm horários disponíveis.
5. Cadastre folgas quando necessário.
6. Em **Agenda**, crie um atendimento escolhendo cliente, profissional, serviço, data e um horário disponível.
7. Confirme, reagende, cancele ou conclua o atendimento. O histórico fica disponível na própria linha.
8. Em **Equipe e acessos**, crie atendentes ou usuários de profissionais.

Datas da interface usam o fuso IANA da empresa. Horários da API devem ter offset (`2030-01-07T09:00:00-03:00`) ou `Z`. O servidor calcula a duração e copia o preço; o cliente não pode definir o fim ou o preço do agendamento.

## Permissões

| Operação | ADMIN | ATTENDANT | PROFESSIONAL |
|---|---|---|---|
| Consultar serviços e profissionais | Sim | Sim | Sim |
| Consultar/alterar clientes | Sim | Sim | Não |
| Criar/reagendar/cancelar/confirmar atendimentos | Sim | Sim | Não |
| Consultar agenda e histórico | Toda a empresa | Toda a empresa | Somente seu profissional |
| Concluir ou registrar falta | Sim | Sim | Somente seus atendimentos |
| Configurar serviços, profissionais, jornadas e folgas | Sim | Não | Não |
| Gerenciar usuários | Sim | Não | Não |

Um usuário `PROFESSIONAL` deve estar vinculado a um profissional da própria empresa. Usuários e cadastros são desativados, sem apagar históricos. A conta atual não pode desativar a si própria. Um profissional com reservas ativas não pode ser desativado. A desativação de usuário invalida imediatamente seus tokens. Alterar a senha também revoga os tokens existentes e exige novo login.

## Regras de agendamento

- Estado inicial: `PENDING`; tanto pendentes quanto confirmados reservam o horário.
- `PENDING` → `CONFIRMED` ou `CANCELED`.
- `CONFIRMED` → `COMPLETED`, `CANCELED` ou `NO_SHOW`.
- Estados encerrados não podem ser reabertos ou reagendados.
- Confirmar exige início futuro; concluir exige horário de término atingido; falta exige início atingido.
- Reservas devem começar no futuro, até 366 dias à frente, com precisão de minutos.
- Duração de serviços: 5 a 480 minutos; preço não negativo.
- A reserva inteira precisa caber em um período de trabalho do mesmo dia, sem folgas nem sobreposição. Períodos não atravessam meia-noite.
- Intervalos são `[início, fim)`: um atendimento pode começar exatamente quando o anterior termina.
- Reagendamento mantém profissional, cliente, serviço, estado, duração e preço originais.
- Mudanças no preço/duração do catálogo não alteram reservas existentes.
- Alterar a jornada ou adicionar folga não pode invalidar uma reserva ativa.
- A disponibilidade é uma consulta momentânea; a criação da reserva valida tudo novamente dentro da transação.
- Datas com horários locais inexistentes ou ambíguos por mudança de fuso não são oferecidas pela consulta de disponibilidade.

## API e documentação

- Interface Swagger: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).
- Especificação OpenAPI: `/v3/api-docs`.
- Saúde da aplicação e banco: `GET /api/health`.
- Referência de requisições e respostas: [docs/API.md](docs/API.md).

O token deve ser enviado em `Authorization: Bearer TOKEN`. Nenhum cabeçalho ou corpo enviado pelo cliente escolhe a empresa: o tenant vem do JWT validado. Credenciais nunca aparecem nas respostas de usuários.

Listagens de cadastros e usuários aceitam `limit` (1 a 500) e `offset` (>=0). Agendamentos exigem `from` e `to`, com período máximo de 93 dias, e aceitam filtros por profissional e estado. A interface pagina os cadastros em 50 registros e mostra até 500 atendimentos por consulta; os seletores suportam até 10 mil cadastros. Folgas retornam as 500 mais recentes. Para volumes maiores, use os filtros/API e amplie a paginação conforme a necessidade.

## Compilar e testar

O Maven Wrapper evita precisar instalar Maven globalmente. Java 21 e acesso à internet são necessários na primeira compilação:

```powershell
.\mvnw.cmd -B -ntp verify
```

Esse comando executa a suíte H2. Os testes PostgreSQL ficam desabilitados quando `pg.tests` não é informado. Para executar as duas suítes com Docker:

```powershell
.\test-postgres.ps1
```

Ou, com um banco de testes existente:

```powershell
.\mvnw.cmd -B -ntp verify '-Dpg.tests=true' '-Dpg.url=jdbc:postgresql://localhost:5432/agendapro_test' '-Dpg.user=agendapro' '-Dpg.password=SENHA_DE_TESTE'
```

Use exclusivamente um banco descartável para esse comando. Os testes criam empresas próprias e não removem dados existentes. Há testes de isolamento, autenticação, permissões, validação, disponibilidade, folgas, máquina de estados, auditoria, snapshot de preço/duração e concorrência. A suíte PostgreSQL também tenta inserir uma reserva conflitante diretamente no banco para verificar a constraint de exclusão. O workflow GitHub Actions executa as duas suítes e publica o JAR como artefato de build.

Depois de recompilar, execute `target/agendapro-1.0.0.jar` ou atualize `agendapro.jar`. `demo.ps1` usa o JAR entregue quando ele existe.

## Arquitetura

Monólito modular por domínio: `auth`, `catalog`, `scheduling` e `common`. Spring MVC expõe a API; Bean Validation valida os contratos; Spring Security valida JWT HS256; BCrypt armazena senhas; Flyway versiona o banco.

A persistência usa **Spring JDBC** em vez de JPA. As consultas explícitas mantêm o filtro por tenant visível, e os bloqueios de agenda são expressos como `SELECT ... FOR UPDATE`. Não há dependência de Lombok ou microsserviços.

As transações de reservas, jornadas e folgas bloqueiam a linha do profissional. Isso serializa alterações concorrentes para o mesmo profissional e continua funcionando em múltiplas instâncias conectadas ao mesmo PostgreSQL. Uma constraint PostgreSQL `EXCLUDE ... tstzrange` também impede sobreposição de reservas ativas. Chaves estrangeiras compostas `(tenant_id, id)` impedem associações entre empresas. H2 é usado somente para demonstração e testes rápidos; a constraint PostgreSQL é validada na suíte própria.

## Publicação

O projeto inclui Docker e está pronto para execução local. **Não foi publicado em um servidor externo.** Docker não estava disponível no ambiente de entrega; o Dockerfile e Compose não foram executados aqui. A aplicação e as migrações foram executadas diretamente com Java e PostgreSQL; veja [VALIDATION.md](VALIDATION.md).

Para disponibilizar a aplicação na internet, configure domínio, HTTPS no proxy, segredos privados, backups do PostgreSQL e restauração testada. Não habilite o profile `demo`. A chave JWT deve ser diferente em cada ambiente. O limite de autenticação é local: 30 tentativas de login/cadastro por IP a cada 15 minutos; com múltiplas réplicas, configure um limite compartilhado no gateway. O cadastro de empresas é público nesta versão; para um serviço restrito, proteja ou desabilite `/api/auth/register` no gateway.

O token dura 60 minutos, não possui refresh token e fica apenas em memória no navegador. Sair ou recarregar a página exige novo login. O logout remove a cópia local; um token copiado continua válido até expirar, trocar a senha ou desativar o usuário. Para uso comercial, recursos como recuperação de senha por e-mail, verificação de e-mail, cobrança e notificações podem ser adicionados; não fazem parte desta versão de agendamento.

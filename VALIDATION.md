# Validação da entrega

Verificado em 30/09/2026, com Java 21.0.12, Maven 3.9.16 e PostgreSQL 17.11 no Windows.

## Compilação e testes

Executado `mvn verify` com a suíte PostgreSQL habilitada, apontando para um banco temporário separado.

```text
AgendaProIntegrationTest: 10 testes, 0 falhas, 0 erros, 0 ignorados
PostgresIntegrationTest: 11 testes, 0 falhas, 0 erros, 0 ignorados
Total: 21 testes, 0 falhas, 0 erros, 0 ignorados
BUILD SUCCESS
```

Foi gerado um JAR executável com todas as dependências: `agendapro.jar`. As duas migrações Flyway foram aplicadas no PostgreSQL real, incluindo `btree_gist` e a constraint de exclusão. O teste de concorrência recebeu exatamente uma resposta `201` e uma `409` para duas reservas simultâneas no mesmo horário. Uma inserção direta de reserva conflitante no PostgreSQL também foi rejeitada.

## Verificação da interface

A aplicação foi iniciada em modo demo, no endereço `http://localhost:8088`, porque a porta 8080 já estava ocupada por outro processo. Foram verificados no navegador:

- Login na conta de demonstração e carregamento da agenda.
- Consulta de horários livres para um profissional e serviço.
- Criação de reserva às 11:00 e exibição na agenda.
- Carregamento e salvamento de serviços vinculados e jornada semanal.
- Cadastro de um novo cliente e exibição na listagem.

O JavaScript também passou na verificação de sintaxe `node --check`. Capturas com dados fictícios estão em `docs/screenshots/agenda.png` e `docs/screenshots/clientes.png`, exibidas no README.

## GitHub Actions

O [primeiro workflow](https://github.com/lisarioss/agendapro/actions/runs/36758094290), referente ao commit `4f50efd`, terminou com sucesso no GitHub. Ele executou a compilação e as suítes H2/PostgreSQL e gerou o artefato JAR.

O [workflow do commit `bcf7bee`](https://github.com/lisarioss/agendapro/actions/runs/36759364097) também terminou com sucesso. O job `verify` executou os 21 testes com zero falhas, erros ou testes ignorados. O job `docker-smoke` construiu a imagem e executou o Compose em um runner Linux do GitHub com PostgreSQL.

O teste de containers confirmou:

- Saúde da aplicação e do banco, cadastro de empresa, login, clientes, profissionais e serviços.
- Consulta de disponibilidade, criação e confirmação de reserva.
- Rejeição de uma segunda reserva no mesmo horário com HTTP 409.
- Recriação dos containers `db` e `app`, preservando o volume nomeado.
- Persistência da empresa, credenciais, cliente, reserva confirmada e dois eventos de histórico.
- Preservação do bloqueio de horário após recriar os serviços.
- Rejeição de acesso de outra empresa ao cliente e ao agendamento com HTTP 404.

Trechos do log Docker:

```text
OK: saude, cadastro, login, disponibilidade, reserva e conflito no container.
OK: empresa, senha, cliente, reserva, historico e bloqueio de horario preservados apos recriar db/app.
OK: isolamento entre empresas no ambiente Docker/PostgreSQL.
```

## Limites desta verificação

Docker não está instalado neste computador; a execução Docker foi validada no GitHub Actions. O projeto não foi publicado em hospedagem externa. Os testes PostgreSQL também foram executados com uma distribuição temporária local, apenas para validação. A prévia em localhost:8088 continua usando H2 em memória e não constitui ambiente persistente de produção.

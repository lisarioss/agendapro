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

O workflow agora também inclui `docker-smoke`, que constrói a imagem, inicia os containers, exercita a API e recria `db`/`app` preservando o volume para verificar a persistência dos dados. O resultado dessa execução será registrado após a conclusão.

## Limites desta verificação

Docker não estava instalado; Dockerfile, Compose e workflow CI foram incluídos, mas não executados neste computador. O projeto não foi publicado em hospedagem externa. Os testes PostgreSQL foram executados com uma distribuição temporária local, apenas para validação. A prévia usa H2 em memória e não constitui ambiente persistente de produção.

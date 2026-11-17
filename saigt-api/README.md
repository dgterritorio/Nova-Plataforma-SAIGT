# saigt-api

Backend da Nova Plataforma SAIGT (PC5979 — DGT). Java 21 + Spring Boot 3.x, PostgreSQL + Liquibase, storage S3 (RustFS), autenticação própria (Spring Authorization Server em-processo, LDAP para utilizadores DGT e INCM para utilizadores externos).

## Desenvolvimento local
A orquestração completa (Postgres, RustFS, este serviço, o frontend) está no `docker-compose.yml` da raiz do repositório (ver o `README.md` da raiz). Para correr só este serviço isoladamente:

```bash
./mvnw spring-boot:run
```

Com `SPRING_PROFILES_ACTIVE=local` o log sai em texto legível em vez de JSON (ver «Logs»). O perfil é só para desenvolvimento fora dos contentores.

Exige `STORAGE_ACCESS_KEY` e `STORAGE_SECRET_KEY` no ambiente (ver `.env.example`); sem elas o arranque falha.

## Makefile
- `make test` — testes unitários/integração. **Exige Docker a correr**: os testes de armazenamento levantam um RustFS (API S3) real com Testcontainers (os restantes correm em H2)
- `make analyse` — análise estática (Sonar)
- `make migrate` — `liquibase:update`
- `make build` — build da imagem Docker

## Estrutura
Organização use-case-first: `controller/{usecase}/`, `dto/{usecase}/`, `service/{usecase}/`, `mapper/{usecase}/`, `persistence/{usecase}/`; adaptadores de sistemas externos em `integracao/{sistema}/` (desvio deliberado à organização use-case-first); código transversal em `core/` de cada camada.

## Jobs periódicos
O `saigt-api` pode correr com mais de uma instância, mas cada job periódico corre numa só de cada vez.

- **Mecanismo:** o agendamento é o `@Scheduled` do Spring, e todos os jobs estão em `service/agendamento/JobsAgendados`. Cada job passa pelo `ExecutorJobs`, que, antes de executar, tenta obter um lock com [ShedLock](https://github.com/lukas-krecan/ShedLock). Se outra instância já o tem, não executa e regista em `DEBUG`. Uma exceção fica no log e não impede a execução seguinte.
- **Onde fica o lock:** na tabela `shedlock` da própria BD PostgreSQL, com uma linha por job (`name`). A coluna `lock_until` diz até quando o lock está ocupado, `locked_at` diz quando foi obtido e `locked_by` tem o hostname da instância que o obteve. As horas vêm do relógio da BD, em UTC.
- **Durações:** no fim de cada execução, o lock liberta-se. Se a instância morrer a meio, o lock expira ao fim da duração máxima do job e outra instância pode voltar a executá-lo. Cada lock fica ocupado pelo menos `APP_JOBS_LOCK_MINIMO` (30s), para que outra instância cujo cron dispare uns instantes depois não repita um job que acabou de terminar.

| Job | Cron (omissão) | Duração máxima do lock (omissão) |
|---|---|---|
| `purga-sessoes` | `APP_SESSAO_PURGA_CRON` (`0 0 3 * * *`) | `APP_SESSAO_PURGA_LOCK_MAXIMO` (`15m`) |
| `limpeza-armazenamento` | `APP_DOCUMENTOS_LIMPEZA_CRON` (`0 30 3 * * *`); idade mínima das intenções `APP_DOCUMENTOS_LIMPEZA_IDADE_MINIMA` (`24h`) | `APP_DOCUMENTOS_LIMPEZA_LOCK_MAXIMO` (`2h`) |
| `consulta-incm` | `INCM_CONSULTA_CRON` (`0 0 6 * * *`) | `INCM_CONSULTA_LOCK_MAXIMO` (`30m`) |
| `caducidade-dinamicas` | `DINAMICAS_CADUCIDADE_CRON` (`0 0 5 * * *`) | `DINAMICAS_CADUCIDADE_LOCK_MAXIMO` (`30m`) |

O `consulta-incm` pergunta à INCM o estado de cada processo em `ENVIADO_INCM` ou `EM_VALIDACAO_INCM` com ato atribuído, e passa-o a `PUBLICADO` ou `ANULADO_INCM` conforme a resposta. Uma falha num processo fica em `ERROR` com o `processoId` e o job segue para o seguinte; esse processo volta a ser consultado na execução seguinte. No fim escreve `Consulta à INCM concluída | job=consulta-incm total=… publicados=… anulados=… semAlteracao=… falhas=…`, em que `falhas` junta as exceções e as falhas técnicas da INCM. Para testes E2E, `INCM_CONSULTA_CRON=0 * * * * *` corre-o a cada minuto.

O `caducidade-dinamicas` verifica todos os dias a `data_caducidade` de cada Dinâmica `EM_CURSO` e regista em `alertas_enviados` os limiares atingidos (`UM_MES`, `QUINZE_DIAS` e `DATA`). Na data, passa a Dinâmica a `CADUCADA` e grava a auditoria de sistema `CADUCIDADE`. Por cada Dinâmica com limiares novos publica um `AlertaCaducidadeDinamicaEvento` com o limiar mais avançado; os processos não mudam de estado e nenhuma ação é recusada. Uma falha numa Dinâmica fica em `ERROR` com o `dinamicaId` e o job segue para a seguinte. No fim escreve `Verificação de caducidade concluída | job=caducidade-dinamicas total=… umMes=… quinzeDias=… caducadas=… falhas=…`. Para testar, `DINAMICAS_CADUCIDADE_CRON=0 * * * * *` corre-o a cada minuto.

**Como se observa.** Cada execução escreve no log uma linha `Job iniciado | job=…` e outra `Job terminado | job=… duracaoMs=… resultado=OK|ERRO`. Os eventos de uma execução têm o mesmo `execucaoJob`, que é novo em cada execução, e não têm `correlationId` (ver «Logs»). A instância que correu o job é a do contentor cujo log tem essas linhas. O registo da última execução está na BD:

```sql
SELECT name, locked_by, locked_at, lock_until FROM shedlock;
```

Para ver também as tentativas que não obtiveram o lock, use `LOGGING_LEVEL_COM_CONKORD_SAIGT_SERVICE_AGENDAMENTO=DEBUG`.

## Logs
O log técnico sai **só na saída padrão**, em JSON, um evento por linha (`logback-spring.xml`, com o encoder `configuration/EncoderLogJson` sobre o [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder), versão fixa no `pom.xml`). A recolha, a agregação, a pesquisa e a retenção são da infraestrutura: no `docker-compose.yml` do `saigt-implementation`, o driver `json-file` com rotação. A aplicação não escreve ficheiros de log nem guarda o log na BD. O log técnico é distinto da `auditoria`, que é imutável e legal: nenhuma linha da `auditoria` vem do log.

**Campos de todos os eventos**

| Campo | Conteúdo |
|---|---|
| `@timestamp` | Instante ISO-8601 em UTC |
| `level` | `TRACE`, `DEBUG`, `INFO`, `WARN` ou `ERROR` |
| `logger`, `thread`, `message` | Classe que escreveu, thread e mensagem (as quebras de linha ficam escapadas) |
| `correlationId` | Identificador do pedido HTTP: o `X-Trace-Id` recebido ou um UUID gerado, devolvido no cabeçalho `X-Trace-Id` da resposta, no `traceId` do envelope de erro e enviado às chamadas de saída. Vem da chave MDC `apiTraceId`. Não existe nos eventos sem pedido (jobs agendados, arranque) |
| `traceId`, `spanId` | Do Micrometer Tracing, quando existem |
| `execucaoJob` | Só nos jobs agendados: identificador da execução, igual em todos os eventos dela |
| `exceptionClass`, `exceptionMessage`, `stackTrace` | Só com exceção: classe, mensagem e stack trace (até 60 linhas por exceção), no mesmo evento |

Do MDC só saem as chaves da tabela.

**Pedido HTTP recebido** (`controller/core/RequestLoggingFilter`, `INFO`): `metodo`, `caminho`, `estadoHttp` e `duracaoMs`. O caminho vai sem a query string, com o ticket das descargas e o token dos links públicos substituídos por `***`.

**Chamada externa** (`integracao/core/ExecutorChamadasExternas`, um evento por chamada a INCM, SNIT, PCGT ou GeoConform):

| Campo | Conteúdo |
|---|---|
| `sistema` | `INCM`, `SNIT`, `PCGT` ou `GEOCONFORM` |
| `operacao` | Operação do cliente, por exemplo `SUBMIT_NEW_ACT` ou `REGISTAR_PUBLICACAO` |
| `processoId`, `idus`, `actIdentifier` | Referências da chamada, só as que existem |
| `metodo`, `url` | Método HTTP e URL sem query string, sem fragmento e sem utilizador e palavra-passe |
| `estadoHttp` | Código da resposta; `0` sem resposta |
| `duracaoMs`, `enviadoEm` | Duração e instante de envio (ISO-8601 UTC) |
| `resultado` | `OK` ou a falha técnica: `ERRO_HTTP`, `TIMEOUT`, `ERRO_REDE`, `CIRCUITO_ABERTO`, `NAO_CONFIGURADO` |
| `cabecalhosPedido` | Só o nome do cabeçalho da credencial, com o valor `***` |
| `cabecalhosResposta` | Cabeçalhos da resposta, com `Authorization`, `Set-Cookie` e `Proxy-Authorization` em `***` |

O nível é `INFO` com `OK`, `WARN` com `CIRCUITO_ABERTO` e `ERROR` nas outras falhas. A transição de um Circuit Breaker é um evento `WARN` com `circuito`, `estadoOrigem` e `estadoDestino`.

**O que fica fora do log:** valores de credenciais (chaves de API, segredos, palavras-passe, tokens e JWT), corpos de pedido e de resposta, query strings, o ticket das descargas e o token dos links públicos.

Exemplos com a stack local:

```bash
docker logs saigt-api | jq -c 'select(.correlationId=="teste-1")'
docker logs saigt-api | jq -c 'select(.sistema) | {sistema, operacao, estadoHttp, duracaoMs, resultado}'
```

## Documentação de arquitetura
O desenho em vigor é o LLD v13, em `saigt-implementation/docs/lld/`; o índice das fontes está em `saigt-implementation/docs/references.md`. O que o código ainda não cumpre face ao LLD está em `saigt-implementation/docs/analise-lacunas.md`.

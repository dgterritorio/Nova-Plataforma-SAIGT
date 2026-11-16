# saigt-api

Backend da Nova Plataforma SAIGT (PC5979 — DGT). Java 21 + Spring Boot 3.x, PostgreSQL + Liquibase, storage S3 (RustFS), autenticação própria (Spring Authorization Server em-processo, LDAP para utilizadores DGT e INCM para utilizadores externos).

## Desenvolvimento local
A orquestração completa (Postgres, RustFS, este serviço, o frontend) vive em `saigt-implementation`. Para correr só este serviço isoladamente:

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

## Integração contínua
O pipeline do GitLab CI está no `.gitlab-ci.yml` e é o gate dos merge requests: com "Pipelines must succeed" ativo no projeto (Settings → Merge requests), um MR só se integra com o pipeline verde.

**Quando corre**
- Em cada merge request (pipeline de MR).
- Em cada push em `develop`, `staging` e `master` (pipeline de ramo).
- Um ramo com MR aberto corre só o pipeline do MR. Um push noutro ramo sem MR não cria pipeline.

**Jobs**

| Job | Stage | O que faz |
|---|---|---|
| `testes` | `testes` | `mvn -B --no-transfer-progress verify` na imagem `maven:3.9-eclipse-temurin-21` (a da fase de build do `Dockerfile`): a suite completa, com os testes unitários, os `@SpringBootTest` em H2 (perfil `test`) e os `@Tag("docker")` com Testcontainers (PostgreSQL, RustFS). Um teste a falhar faz falhar o job. Os relatórios `target/surefire-reports/TEST-*.xml` vão para o separador de testes do MR (guardados 14 dias, também quando falha). A cache `.m2/repository` muda só quando muda o `pom.xml`. |
| `imagem` | `imagem` | Só nos merge requests, depois do `testes`: `docker build -t saigt-api:<sha curto> .`, confirma que o `Dockerfile` constrói. Não faz login nem push. |
| `publicar` | `imagem` | Só nos pipelines de ramo de `develop`, `staging` e `master`, depois do `testes`: constrói e publica a imagem no registry (ver «Publicação da imagem»). |
| `deploy-desenvolvimento`, `deploy-qualidade`, `deploy-producao` | `deploy` | Manuais, só nos pipelines de ramo de ramos protegidos, depois do `publicar`: instalam no ambiente a imagem publicada por esse pipeline e esperam que o `saigt-api` migre e fique saudável (ver «Deploy por ambiente»). |

Os jobs podem ser cancelados por um push mais recente no mesmo ramo (`interruptible`). Só o `publicar` e os jobs de deploy usam credenciais (as do registry e, no deploy, a chave SSH do ambiente); o perfil `test` só tem valores fictícios.

**Publicação da imagem.** Só nos pipelines de ramo de `develop`, `staging` e `master`, e só com o `testes` verde, o job `publicar` faz `docker login` com a palavra-passe lida do stdin, constrói a imagem e publica-a como `$CI_REGISTRY_IMAGE:<sha curto do commit>` (por exemplo `registry.exemplo/delivery-iberia/saigt/saigt-api:5f1dade`). É a única etiqueta: a do commit, que o deploy usa e que nunca se sobrescreve (se já existe, num pipeline repetido ou no mesmo commit promovido a `staging` ou `master`, o job termina sem reconstruir). Nos merge requests o `imagem` só constrói, sem login nem push. Variáveis de CI/CD de que depende: `CI_REGISTRY` (endereço do registry), `CI_REGISTRY_IMAGE` (nome da imagem, sem etiqueta), `CI_REGISTRY_USER` e `CI_REGISTRY_PASSWORD`. Com o Container Registry do GitLab são as predefinidas (a palavra-passe é o token do job, já mascarado). Com outro registry, definem-se no projeto (Settings → CI/CD → Variables) com os mesmos nomes, protegidas, para só os ramos protegidos as receberem, e a palavra-passe mascarada; o `.gitlab-ci.yml` não muda. `develop`, `staging` e `master` têm de ser ramos protegidos.

**Runner.** Os jobs usam um serviço Docker-in-Docker (`docker:<versão>-dind`, alias `docker`) ligado por TLS na porta 2376 (`DOCKER_TLS_VERIFY=1`, certificados em `/certs/client`). O runner tem de ter o executor `docker` em modo privilegiado e o volume `/certs/client` na configuração (`volumes = ["/certs/client", "/cache"]`). Os contentores do Testcontainers correm dentro desse serviço; `TESTCONTAINERS_HOST_OVERRIDE=docker` faz os testes chegarem às suas portas. A versão do Docker está fixa no `.gitlab-ci.yml`, igual na imagem dos jobs `imagem` e `publicar` e no serviço; muda-se nos dois sítios ao mesmo tempo.

**Simuladores da INCM e do SNIT.** Os testes contra os mocks (`ClienteIncmContraMockTest`, `ConsultaIncmServiceTest`, `EnvioIncmServiceContraMockTest`, `FluxoPublicacaoIncmIntegrationTest`, `ClienteSnitContraMockTest`) usam os mappings de `docker/incm-mock` e `docker/snit-mock` do `saigt-implementation`, os mesmos do docker-compose. O `before_script` do `testes` faz um clone raso desse repositório para a pasta irmã (`../saigt-implementation`), no ramo da variável `SAIGT_IMPLEMENTATION_REF` (por omissão `master`), com o `CI_JOB_TOKEN`; o `SimuladorWireMockTeste` levanta um WireMock por simulador no dind. Para testar contra um ramo de mocks ainda por integrar, corre-se o pipeline à mão (Build → Pipelines → Run pipeline, num ramo com pipeline, como `develop`) com `SAIGT_IMPLEMENTATION_REF` nesse ramo. Pré-requisito: no `saigt-implementation`, Settings → CI/CD → Job token permissions, o `saigt-api` na allowlist (ação de Maintainer).

**Reproduzir localmente.** Com o Docker a correr, `make test` corre a mesma suite (os testes com contentores precisam dele). Os testes contra os simuladores usam, por esta ordem, o mock do compose (`INCM_MOCK_URL=http://localhost:8089`, `SNIT_MOCK_URL=http://localhost:8090`, sem contentor extra), um WireMock com os mappings da pasta irmã `../saigt-implementation`, ou ficam ignorados quando não há nenhum dos dois. `make build` constrói a imagem como o job `imagem`.

**Deploy por ambiente.** Um job manual por ambiente, criado só nos pipelines de ramo e só quando o ramo é protegido (`CI_COMMIT_REF_PROTECTED`), nunca nos merge requests:

| Ramo | Job | Ambiente no GitLab (`environment:name`) | Tier |
|---|---|---|---|
| `develop` | `deploy-desenvolvimento` | `desenvolvimento` | `development` |
| `staging` | `deploy-qualidade` | `qualidade` | `staging` |
| `master` | `deploy-producao` | `producao` | `production` |

O mapa de ramos e ambientes e o gatilho manual são a proposta da story, ainda por confirmar pela DGT com a decisão de infraestrutura dos ambientes (OI-392); confirmá-los ou mudá-los só muda este ficheiro, não o código. Cada job tem um `resource_group` próprio (dois deploys no mesmo ambiente esperam um pelo outro) e não é cancelado por um push mais recente.

*O que o job faz.* Cada ambiente tem, num servidor, uma pasta com o seu `docker-compose.yml` e o seu `.env`, com os segredos de runtime, geridos pela infraestrutura fora dos repositórios. O job liga-se por SSH a essa pasta e:
1. confirma que o ambiente dá ao serviço `saigt-api` as seis variáveis `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` (runtime, role `saigt_app`) e `SPRING_LIQUIBASE_URL/USERNAME/PASSWORD` (dono da base, para a migração) com valor; conta-as no servidor e nada sai de lá. Sem elas pára antes de mexer no ambiente: o `saigt-api` nunca arranca com os valores por omissão de desenvolvimento do `application.properties`;
2. faz `docker login` no registry com a palavra-passe pelo stdin, `docker compose pull saigt-api` e `docker logout`;
3. faz `docker compose up -d --no-deps --force-recreate saigt-api` com `SAIGT_API_IMAGEM=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA` só no ambiente desse comando: a imagem que o `publicar` do mesmo pipeline publicou. Não escreve no `.env` nem em nenhum ficheiro do ambiente, não lê os valores das credenciais e não toca nos outros serviços;
4. espera até `DEPLOY_TEMPO_LIMITE` segundos (300 por omissão) que todos os contentores do serviço estejam a correr a imagem nova e `healthy` (o healthcheck do compose chama o `/actuator/health`) e mostra o `/actuator/health` de cada um. Falha logo se um contentor parar ou reiniciar, e falha ao fim do tempo limite; em qualquer falha mostra as últimas `DEPLOY_LINHAS_LOG` linhas (100 por omissão) do log do `saigt-api`.

*Migração da base de dados.* Corre no arranque do `saigt-api` (Liquibase do Spring, `db/master.xml`), com as credenciais do dono que o ambiente lhe dá; o pipeline não acede à base de dados. Um changeset que falhe impede o arranque e o job falha (o PostgreSQL desfaz o changeset). Um changeset já aplicado e alterado faz falhar a validação por checksum e o job falha: a correção é sempre um changeset novo. Com várias instâncias a arrancar ao mesmo tempo, o `DATABASECHANGELOGLOCK` garante que só uma aplica os changesets; as outras esperam e arrancam sem os repetir. Numa base vazia (primeiro deploy), as tabelas de controlo do Liquibase ainda não existem e duas instâncias podem tentar criá-las ao mesmo tempo: uma falha e reinicia, e o job falha por causa do reinício; por isso o primeiro deploy faz-se com uma só instância. Repetir o deploy é seguro: sem changesets novos, o Liquibase não aplica nada.

*Repetir um deploy.* Correr outra vez o job (Retry), no mesmo pipeline: volta a fazer pull e a recriar o serviço com a mesma imagem.

*Voltar à versão anterior.* Novo deploy da etiqueta anterior: em Operate → Environments → ambiente, Rollback no deploy anterior (repete o job de deploy desse pipeline, com a imagem desse commit), ou Retry do job de deploy no pipeline anterior. A migração não volta atrás: a versão anterior arranca sobre a base já migrada (o Liquibase ignora os changesets que não conhece), por isso uma alteração ao esquema tem de manter a versão anterior a funcionar, ou a correção é um changeset novo. O rollback automático da migração não está definido.

*Variáveis de CI/CD* (Settings → CI/CD → Variables), com o âmbito de cada ambiente (`desenvolvimento`, `qualidade`, `producao`), protegidas (só os ramos protegidos as recebem) e, as que o permitem, mascaradas; nenhuma tem valor no `.gitlab-ci.yml`:

| Variável | Tipo | O que é |
|---|---|---|
| `DEPLOY_SSH_HOST` | Variable | Utilizador e servidor do ambiente para o SSH (`utilizador@servidor`) |
| `DEPLOY_SSH_PORT` | Variable | Porta SSH, se não for a 22 |
| `DEPLOY_SSH_KEY` | File | Chave privada SSH do utilizador de deploy, só para este fim |
| `DEPLOY_SSH_KNOWN_HOSTS` | File | Linha(s) `known_hosts` com a chave pública do servidor, obtida da infraestrutura por um canal fiável; o host é sempre verificado (`StrictHostKeyChecking=yes`) |
| `DEPLOY_DIR` | Variable | Pasta do ambiente no servidor (onde estão o `docker-compose.yml` e o `.env`) |
| `DEPLOY_URL` | Variable | Endereço público do ambiente (`environment:url`) |
| `DEPLOY_TEMPO_LIMITE`, `DEPLOY_LINHAS_LOG` | Variable | Opcionais: sobrepõem-se aos valores por omissão do job |
| `CI_REGISTRY`, `CI_REGISTRY_IMAGE`, `CI_REGISTRY_USER`, `CI_REGISTRY_PASSWORD` | — | As mesmas do `publicar` (as predefinidas do Container Registry do GitLab, ou as do projeto) |

*Pré-requisitos do ambiente* (infraestrutura, fora do repositório): a base de dados e os roles do dono e `saigt_app` criados antes do primeiro deploy (como em `docker/postgres/init/01-init.sql` do `saigt-implementation`); no `docker-compose.yml` do ambiente, o serviço `saigt-api` com `image: ${SAIGT_API_IMAGEM}` (com um valor por omissão), o healthcheck do `/actuator/health` com `start_period` que cubra a migração, as seis variáveis `SPRING_*` no `.env` e sem `container_name` (para poder ter várias instâncias); o servidor com Docker e Docker Compose v2, SSH só por chave e um utilizador de deploy com acesso ao Docker e à pasta do ambiente, que confia no certificado do registry. Os jobs de deploy não precisam de Docker-in-Docker: só do cliente SSH da imagem `docker`.

**Fora do pipeline:** a análise SonarQube (`make analyse` precisa de um servidor que ainda não existe), a criação dos ambientes e o rollback automático da migração.

## Estrutura
Organização use-case-first: `controller/{usecase}/`, `dto/{usecase}/`, `service/{usecase}/`, `mapper/{usecase}/`, `persistence/{usecase}/`; adaptadores de sistemas externos em `integracao/{sistema}/` (desvio declarado no `CLAUDE.md`); código transversal em `core/` de cada camada.

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

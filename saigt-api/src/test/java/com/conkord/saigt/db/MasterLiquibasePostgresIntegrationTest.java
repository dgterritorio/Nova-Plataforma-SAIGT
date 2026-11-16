package com.conkord.saigt.db;

import com.conkord.saigt.configuration.AgendamentoConfig;
import com.conkord.saigt.exception.ExtensaoNaoAceiteException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.RegrasTiff;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.conkord.saigt.service.documento.RegrasExtensaoDocumento;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.EventoIdus;
import com.conkord.saigt.persistence.operacional.SegmentoIdus;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Corre o {@code master.xml} completo num PostgreSQL real (mesma imagem do docker-compose): é o único sítio onde
 * a estrutura e os seeds reais são aplicados, já que os restantes testes usam H2 com o schema do Hibernate.
 */
@Tag("docker")
class MasterLiquibasePostgresIntegrationTest {

    private static final int PORTA_POSTGRES = 5432;
    private static final String BASE_MODELO = "saigt";
    private static final String BASE_DO_TESTE = "teste";

    // Migra-se uma vez e clona-se a base em cada teste: um servidor e uma migração por teste custavam 15 minutos à suite.
    @SuppressWarnings("resource")
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:15-alpine")
            .withEnv("POSTGRES_PASSWORD", "teste")
            .withEnv("POSTGRES_DB", BASE_MODELO)
            .withExposedPorts(PORTA_POSTGRES)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\n", 2));

    @BeforeAll
    static void prepararBaseModelo() throws Exception {
        POSTGRES.start();
        try (Connection ligacao = ligar(BASE_MODELO)) {
            executar(ligacao, "CREATE ROLE saigt_app LOGIN PASSWORD 'teste'");
            migrar(ligacao);
            ligacao.setAutoCommit(true);
        }
    }

    @AfterAll
    static void pararPostgres() {
        POSTGRES.stop();
    }

    @Test
    void deveAplicarEstruturaESeedsNumaBaseVazia() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(contar(ligacao, "tipo_instrumento")).isEqualTo(14 + 1);
            assertThat(contar(ligacao, "tipo_dinamica")).isEqualTo(17);
            assertThat(contar(ligacao, "tipo_procedimento")).isEqualTo(30 + 1 + 1);
            assertThat(contar(ligacao, "instrumento_procedimento_config"))
                    .isEqualTo(491 - PROCEDIMENTOS_DA_RERAE - ATOS_PONTUAIS_DA_RERAE + PROCEDIMENTOS_DAS_RESTANTES_SERVIDOES + PROCEDIMENTOS_DA_REN);
            assertThat(contar(ligacao, "permissao_acao")).isEqualTo(1146 - 2 * PROCEDIMENTOS_DA_RERAE
                    - PERMISSOES_DOS_ATOS_PONTUAIS_DA_RERAE + PERMISSOES_DA_PCM_NA_PRIMEIRA_SERIE + PERMISSOES_DAS_RESTANTES_SERVIDOES + PERMISSOES_DA_REN);
        });
    }

    private static final String INSERCAO_PERMISSAO_ACAO = "INSERT INTO permissao_acao (tipo_entidade, "
            + "instrumento_procedimento_config_id, ativo) VALUES ('%s', (SELECT min(id) FROM instrumento_procedimento_config), true)";

    @Test
    void devePassarAPermissaoAcaoParaTipoEntidadeComAsLinhasCmEDgtEMantendoAUnique() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(colunaExiste(ligacao, "permissao_acao", "tipo_entidade")).isTrue();
            assertThat(colunaExiste(ligacao, "permissao_acao", "ator")).isFalse();
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'CM'"))
                    .isEqualTo(303 + 3 - PROCEDIMENTOS_DA_RERAE - ATOS_PONTUAIS_DA_RERAE);
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'CCDR_DR'")).isEqualTo(40 + 3);
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'SEC_ESTADO'")).isEqualTo(146 + 3);
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'DGT'"))
                    .isEqualTo(491 + 3 + 3 - PROCEDIMENTOS_DA_RERAE - ATOS_PONTUAIS_DA_RERAE);
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'ENTIDADE'")).isZero();
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'PCM'")).isEqualTo(PERMISSOES_DA_PCM_NA_PRIMEIRA_SERIE);
            assertThat(contar(ligacao, "permissao_acao")).isEqualTo(1146 - 2 * PROCEDIMENTOS_DA_RERAE
                    - PERMISSOES_DOS_ATOS_PONTUAIS_DA_RERAE + PERMISSOES_DA_PCM_NA_PRIMEIRA_SERIE + PERMISSOES_DAS_RESTANTES_SERVIDOES + PERMISSOES_DA_REN);
            assertThat(linhas(ligacao, "SELECT DISTINCT ti.codigo FROM permissao_acao pa "
                    + "JOIN instrumento_procedimento_config ipc ON ipc.id = pa.instrumento_procedimento_config_id "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "WHERE pa.tipo_entidade = 'CM' ORDER BY 1")).containsExactly("PDIM", "PDM", "PP", "PPIM", "PU", "PUIM", "SERVIDAO");

            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PERMISSAO_ACAO.formatted("XPTO")))
                    .hasMessageContaining("ck_permissao_acao_tipo_entidade");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PERMISSAO_ACAO.formatted("ENTIDADE")))
                    .hasMessageContaining("ck_permissao_acao_tipo_entidade");
            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO permissao_acao (tipo_entidade, "
                    + "instrumento_procedimento_config_id, ativo) SELECT tipo_entidade, instrumento_procedimento_config_id, true "
                    + "FROM permissao_acao LIMIT 1"))
                    .hasMessageContaining("uq_permissao_acao");
            for (String tipo : List.of("CM", "CCDR_DR", "AM", "CIM", "PCM", "SEC_ESTADO", "DGT", "ICNF", "APA", "DGPC", "IP")) {
                assertThat(contar(ligacao, "pg_constraint WHERE conname = 'ck_permissao_acao_tipo_entidade' "
                        + "AND pg_get_constraintdef(oid) LIKE '%''" + tipo + "''%'")).isEqualTo(1);
            }
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'ck_permissao_acao_ator'")).isZero();
        });
    }

    @Test
    void deveReverterAPermissaoAcaoParaAtorEVoltarAMigrar() throws Exception {
        comBaseMigrada(ligacao -> {
            executar(ligacao, "INSERT INTO permissao_acao (tipo_entidade, instrumento_procedimento_config_id, ativo) "
                    + "SELECT 'CCDR_DR', min(id), true FROM instrumento_procedimento_config");
            assertThat(contar(ligacao, "permissao_acao")).isEqualTo(1147 - 2 * PROCEDIMENTOS_DA_RERAE
                    - PERMISSOES_DOS_ATOS_PONTUAIS_DA_RERAE + PERMISSOES_DA_PCM_NA_PRIMEIRA_SERIE + PERMISSOES_DAS_RESTANTES_SERVIDOES + PERMISSOES_DA_REN);

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "permissao_acao", "ator")).isTrue();
            assertThat(colunaExiste(ligacao, "permissao_acao", "tipo_entidade")).isFalse();
            assertThat(contar(ligacao, "permissao_acao WHERE ator = 'ENTIDADE'")).isEqualTo(56);
            assertThat(contar(ligacao, "permissao_acao WHERE ator = 'DGT'")).isEqualTo(56);
            assertThat(contar(ligacao, "permissao_acao")).isEqualTo(112);
            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO permissao_acao (ator, instrumento_procedimento_config_id, "
                    + "ativo) VALUES ('CM', (SELECT min(id) FROM instrumento_procedimento_config), true)"))
                    .hasMessageContaining("ck_permissao_acao_ator");

            migrarOsProximos(ligacao, CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'CM'")).isEqualTo(54);
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'CCDR_DR'")).isEqualTo(2);
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'DGT'")).isEqualTo(56);
        });
    }

    private static final String INSERCAO_UTILIZADOR = "INSERT INTO utilizador (id, nome, email, origem_autenticacao, "
            + "perfil_interno) VALUES ('%s', %s, %s, '%s', %s)";

    @Test
    void devePassarONomeDasContasAdDgtParaONomeDeAutenticacaoETornarOEmailOpcional() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD);
            ligacao.setAutoCommit(true);
            String ana = "00000000-0000-0000-0000-0000000000a1";
            String semNome = "00000000-0000-0000-0000-0000000000a2";
            String incm = "00000000-0000-0000-0000-0000000000a3";
            executar(ligacao, INSERCAO_UTILIZADOR.formatted(ana, "'Ana Silva'", "'ana.silva@dgt.pt'", "AD_DGT", "'MASTER'"));
            executar(ligacao, INSERCAO_UTILIZADOR.formatted(semNome, "NULL", "'carlos.tecnico@dgt.pt'", "AD_DGT", "'TECNICO'"));
            executar(ligacao, INSERCAO_UTILIZADOR.formatted(incm, "'Helena Sintra'", "'helena.sintra@incm.pt'", "INCM", "NULL"));
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_UTILIZADOR.formatted(
                    "00000000-0000-0000-0000-0000000000a4", "'sem.email'", "NULL", "AD_DGT", "'VISITANTE'")))
                    .hasMessageContaining("email");

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT nome FROM utilizador WHERE id = '" + ana + "'")).isEqualTo("ana.silva");
            assertThat(texto(ligacao, "SELECT nome FROM utilizador WHERE id = '" + semNome + "'")).isEqualTo("carlos.tecnico");
            assertThat(texto(ligacao, "SELECT nome FROM utilizador WHERE id = '" + incm + "'")).isEqualTo("Helena Sintra");
            assertThat(texto(ligacao, "SELECT email FROM utilizador WHERE id = '" + ana + "'")).isEqualTo("ana.silva@dgt.pt");
            executar(ligacao, INSERCAO_UTILIZADOR.formatted(
                    "00000000-0000-0000-0000-0000000000a4", "'sem.email'", "NULL", "AD_DGT", "'VISITANTE'"));
            assertThat(contar(ligacao, "utilizador WHERE email IS NULL")).isEqualTo(1);
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_UTILIZADOR.formatted(
                    "00000000-0000-0000-0000-0000000000a5", "'outra'", "'ana.silva@dgt.pt'", "AD_DGT", "'VISITANTE'")))
                    .hasMessageContaining("uq_utilizador_email");
        });
    }

    @Test
    void deveReverterOEmailOpcionalQuandoTodasAsContasTemEmail() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD);
            ligacao.setAutoCommit(true);

            assertThatThrownBy(() -> executar(ligacao, INSERCAO_UTILIZADOR.formatted(
                    "00000000-0000-0000-0000-0000000000b1", "'sem.email'", "NULL", "AD_DGT", "'VISITANTE'")))
                    .hasMessageContaining("email");
            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD);
        });
    }

    @Test
    void deveTerAUtilizadorSemAColunaDaDesativacaoComOEmailUnicoPorOrigemEONomeUnicoNasContasAd() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(colunaExiste(ligacao, "utilizador", "desativado_em")).isFalse();
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'uq_utilizador_email'")).isZero();
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'uq_utilizador_email_origem'")).isEqualTo("UNIQUE (email, origem_autenticacao)");
            assertThat(definicaoDoIndice(ligacao, "uq_utilizador_nome_ad")).contains("UNIQUE").contains("(nome)")
                    .contains("origem_autenticacao")
                    .contains("AD_DGT");

            executar(ligacao, INSERCAO_UTILIZADOR.formatted("00000000-0000-0000-0000-0000000000c1", "'pessoa.dupla'",
                    "'pessoa.dupla@dgt.pt'", "AD_DGT", "'TECNICO'"));
            executar(ligacao, INSERCAO_UTILIZADOR.formatted("00000000-0000-0000-0000-0000000000c2", "'Pessoa Dupla'",
                    "'pessoa.dupla@dgt.pt'", "INCM", "NULL"));
            executar(ligacao, INSERCAO_UTILIZADOR.formatted("00000000-0000-0000-0000-0000000000c3", "'sem.email.um'",
                    "NULL", "AD_DGT", "'VISITANTE'"));
            executar(ligacao, INSERCAO_UTILIZADOR.formatted("00000000-0000-0000-0000-0000000000c4", "'sem.email.dois'",
                    "NULL", "AD_DGT", "'VISITANTE'"));
            // O nome só é chave nas contas AD_DGT: um externo pode ter o mesmo nome.
            executar(ligacao, INSERCAO_UTILIZADOR.formatted("00000000-0000-0000-0000-0000000000c5", "'pessoa.dupla'",
                    "'outra.pessoa@municipio.pt'", "INCM", "NULL"));
            assertThat(contar(ligacao, "utilizador WHERE email = 'pessoa.dupla@dgt.pt'")).isEqualTo(2);

            assertThatThrownBy(() -> executar(ligacao, INSERCAO_UTILIZADOR.formatted("00000000-0000-0000-0000-0000000000c6",
                    "'outra.conta'", "'pessoa.dupla@dgt.pt'", "INCM", "NULL")))
                    .hasMessageContaining("uq_utilizador_email_origem");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_UTILIZADOR.formatted("00000000-0000-0000-0000-0000000000c7",
                    "'pessoa.dupla'", "'outro.email@dgt.pt'", "AD_DGT", "'VISITANTE'")))
                    .hasMessageContaining("uq_utilizador_nome_ad");
        });
    }

    @Test
    void deveManterAsLinhasDosDesativadosAoMigrarEReporAColunaEAUnicidadeDoEmailNoRollback() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "utilizador", "desativado_em")).isTrue();
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'uq_utilizador_email'")).isEqualTo(1);
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'uq_utilizador_email_origem'")).isZero();
            assertThat(definicaoDoIndice(ligacao, "uq_utilizador_nome_ad")).isNull();
            String desativado = "00000000-0000-0000-0000-0000000000d1";
            executar(ligacao, INSERCAO_UTILIZADOR.formatted(desativado, "'Pessoa Desativada'", "'desativada@cm-teste.pt'",
                    "INCM", "NULL"));
            executar(ligacao, "UPDATE utilizador SET desativado_em = now() WHERE id = '" + desativado + "'");

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "utilizador", "desativado_em")).isFalse();
            assertThat(texto(ligacao, "SELECT nome || '|' || email FROM utilizador WHERE id = '" + desativado + "'"))
                    .isEqualTo("Pessoa Desativada|desativada@cm-teste.pt");
        });
    }

    private static final String AUTOR_DOS_CONTACTOS = "00000000-0000-0000-0000-0000000000e1";

    private static final String INSERCAO_CONTACTO = "INSERT INTO entidade_contacto (entidade_id, email, principal, ativo, "
            + "criado_por) VALUES ((SELECT min(id) FROM entidade), '%s', %s, %s, '" + AUTOR_DOS_CONTACTOS + "')";

    private void inserirAutorDosContactos(Connection ligacao) throws SQLException {
        executar(ligacao, INSERCAO_UTILIZADOR.formatted(AUTOR_DOS_CONTACTOS, "'autor.contactos'", "NULL", "AD_DGT",
                "'MASTER'"));
    }

    @Test
    void deveRecusarNaBaseUmSegundoContactoPrincipalAtivoNaMesmaEntidade() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(definicaoDoIndice(ligacao, "uq_entidade_contacto_principal_ativo")).contains("UNIQUE")
                    .contains("(entidade_id)").contains("WHERE (principal AND ativo)");
            ligacao.setAutoCommit(true);
            inserirAutorDosContactos(ligacao);

            executar(ligacao, INSERCAO_CONTACTO.formatted("principal@cm-teste.pt", "true", "true"));
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_CONTACTO.formatted("segundo@cm-teste.pt", "true", "true")))
                    .hasMessageContaining("uq_entidade_contacto_principal_ativo");

            assertThatCode(() -> executar(ligacao, INSERCAO_CONTACTO.formatted("antigo@cm-teste.pt", "true", "false")))
                    .doesNotThrowAnyException();
            assertThatCode(() -> executar(ligacao, INSERCAO_CONTACTO.formatted("outro@cm-teste.pt", "false", "true")))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> executar(ligacao, "UPDATE entidade_contacto SET ativo = true "
                    + "WHERE email = 'antigo@cm-teste.pt'"))
                    .hasMessageContaining("uq_entidade_contacto_principal_ativo");
        });
    }

    @Test
    void deveReverterSoOIndiceDoContactoPrincipalEVoltarAMigrar() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL);
            ligacao.setAutoCommit(true);
            assertThat(definicaoDoIndice(ligacao, "uq_entidade_contacto_principal_ativo")).isNull();
            assertThat(definicaoDoIndice(ligacao, "idx_entidade_contacto")).isNotNull();
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'uq_entidade_contacto'")).isEqualTo(1);

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL);
            ligacao.setAutoCommit(true);
            assertThat(definicaoDoIndice(ligacao, "uq_entidade_contacto_principal_ativo")).contains("UNIQUE");
        });
    }

    @Test
    void deveTerTelefoneEMoradaSemTelemovelEReporOTelemovelComOsValoresNoRollback() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(colunaExiste(ligacao, "utilizador", "telefone")).isTrue();
            assertThat(colunaExiste(ligacao, "utilizador", "morada")).isTrue();
            assertThat(colunaExiste(ligacao, "utilizador", "telemovel")).isFalse();
            assertThat(texto(ligacao, "SELECT character_maximum_length || '|' || is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'utilizador' AND column_name = 'morada'")).isEqualTo("300|YES");
            assertThat(texto(ligacao, "SELECT character_maximum_length || '|' || is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'utilizador' AND column_name = 'telefone'")).isEqualTo("30|YES");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "utilizador", "telemovel")).isTrue();
            assertThat(colunaExiste(ligacao, "utilizador", "telefone")).isFalse();
            assertThat(colunaExiste(ligacao, "utilizador", "morada")).isFalse();
            String conta = "00000000-0000-0000-0000-0000000000f1";
            executar(ligacao, INSERCAO_UTILIZADOR.formatted(conta, "'perfil.telefone'", "NULL", "AD_DGT", "'VISITANTE'"));
            executar(ligacao, "UPDATE utilizador SET telemovel = '912345678' WHERE id = '" + conta + "'");

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, "SELECT telefone || '|' || coalesce(morada, 'nula') FROM utilizador WHERE id = '"
                    + conta + "'")).isEqualTo("912345678|nula");
        });
    }

    private static final String PERMISSOES_ATIVAS_POR_TIPO = "SELECT tipo_entidade || ':' || count(*) FROM permissao_acao "
            + "WHERE ativo GROUP BY tipo_entidade ORDER BY 1";

    private static final String TODAS_AS_PERMISSOES = "SELECT id || ':' || instrumento_procedimento_config_id || ':' "
            + "|| tipo_entidade || ':' || ativo FROM permissao_acao ORDER BY id";

    private static final String CCDR_NA_CONSTITUICAO_DA_CC = "SELECT pa.id || ':' || pa.instrumento_procedimento_config_id "
            + "FROM permissao_acao pa JOIN instrumento_procedimento_config c ON c.id = pa.instrumento_procedimento_config_id "
            + "JOIN tipo_procedimento tp ON tp.id = c.tipo_procedimento_id "
            + "WHERE pa.tipo_entidade = 'CCDR_DR' AND tp.codigo = 'CONSTITUICAO_CC' ORDER BY 1";

    private static final String CM_DGT_E_PCM = "SELECT id || ':' || instrumento_procedimento_config_id || ':' || tipo_entidade "
            + "FROM permissao_acao WHERE tipo_entidade IN ('CM', 'DGT', 'PCM') ORDER BY id";

    @Test
    void deveDarACadaConfiguracaoOsTiposDeEntidadeDaMatrizComAsContagensPorTipo() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(MatrizPermissaoAcao.desacordos(ligacao)).isEmpty();
            assertThat(linhas(ligacao, PERMISSOES_ATIVAS_POR_TIPO)).containsExactly("AM:2", "APA:54", "CCDR_DR:43", "CIM:29",
                    "CM:" + (306 - PROCEDIMENTOS_DA_RERAE - ATOS_PONTUAIS_DA_RERAE), "DGPC:27",
                    "DGT:" + (497 - PROCEDIMENTOS_DA_RERAE - ATOS_PONTUAIS_DA_RERAE), "ICNF:54", "IP:3",
                    "PCM:" + PERMISSOES_DA_PCM_NA_PRIMEIRA_SERIE, "SEC_ESTADO:" + (149 - PERMISSOES_DO_SEC_ESTADO_NO_PROT));
        });
    }

    @Test
    void deveCorrerAMatrizDuasVezesSemDuplicarNemMexerNasLinhasQueJaExistiam() throws Exception {
        comBaseMigrada(ligacao -> {
            // Os seeds posteriores à matriz também dão permissões: saem antes de se tirar a fotografia.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA);
            ligacao.setAutoCommit(true);
            List<String> antes = linhas(ligacao, TODAS_AS_PERMISSOES);
            reverterOsUltimos(ligacao, CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, TODAS_AS_PERMISSOES)).isEqualTo(antes);
            configurarTiposDosOutrosSeeds(ligacao);

            migrarOsProximos(ligacao, CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES);
            ligacao.setAutoCommit(true);
            List<String> depoisDaPrimeira = linhas(ligacao, TODAS_AS_PERMISSOES);
            assertThat(MatrizPermissaoAcao.desacordosDaMatrizInicial(ligacao)).isEmpty();
            assertThat(depoisDaPrimeira).containsAll(antes).hasSizeGreaterThan(antes.size() + 1);
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'PCM'")).isEqualTo(1);

            executar(ligacao, "DELETE FROM databasechangelog WHERE id = '2026-11-11-4-seed-matriz-permissao-acao'");
            migrarOsProximos(ligacao, CHANGESETS_DA_MATRIZ_DE_PERMISSOES);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, TODAS_AS_PERMISSOES)).isEqualTo(depoisDaPrimeira);
            assertThat(MatrizPermissaoAcao.desacordosDaMatrizInicial(ligacao)).isEmpty();
        });
    }

    @Test
    void deveReverterAMatrizSoNosTiposQueElaDaMantendoACcDaCcdrEAsLinhasCmDgtEPcm() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES);
            ligacao.setAutoCommit(true);
            configurarTiposDosOutrosSeeds(ligacao);
            migrarOsProximos(ligacao, CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES);
            ligacao.setAutoCommit(true);
            for (String tipo : List.of("AM", "CIM", "SEC_ESTADO", "ICNF", "APA", "DGPC", "IP")) {
                assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = '" + tipo + "'")).as(tipo).isPositive();
            }
            List<String> ccDaCcdr = linhas(ligacao, CCDR_NA_CONSTITUICAO_DA_CC);
            List<String> cmDgtEPcm = linhas(ligacao, CM_DGT_E_PCM);
            assertThat(ccDaCcdr).hasSize(4);
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'CCDR_DR'")).isGreaterThan(ccDaCcdr.size());

            reverterOsUltimos(ligacao, CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade IN ('AM', 'CIM', 'SEC_ESTADO', 'ICNF', 'APA', "
                    + "'DGPC', 'IP')")).isZero();
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'CCDR_DR'")).isEqualTo(ccDaCcdr.size());
            assertThat(linhas(ligacao, CCDR_NA_CONSTITUICAO_DA_CC)).isEqualTo(ccDaCcdr);
            assertThat(linhas(ligacao, CM_DGT_E_PCM)).isEqualTo(cmDgtEPcm);
        });
    }

    /**
     * Tipos de Instrumento e de Dinâmica que os seeds das outras configurações vão trazer, com nomes de teste: uma
     * Elaboração (Decisão de início) por tipo, a Constituição da CC no PDIM e no PIMOT, as Dinâmicas da CCDR e do
     * Governo no PDM e no PROT, e uma linha PCM numa configuração da 1.ª série.
     */
    private void configurarTiposDosOutrosSeeds(Connection ligacao) throws SQLException {
        executar(ligacao, "INSERT INTO tipo_instrumento (codigo, nome, categoria, ativo) VALUES "
                + "('PDIM', 'Teste PDIM', 'PLANO', true), ('PNPOT', 'Teste PNPOT', 'PROGRAMA', true), "
                + "('PS', 'Teste PS', 'PROGRAMA', true), ('PIMOT', 'Teste PIMOT', 'PROGRAMA', true), "
                + "('REN', 'Teste REN', 'SRUP', true), ('PRA', 'Teste PRA', 'SRUP', true), ('PSA', 'Teste PSA', 'SRUP', true), "
                + "('SRUP_TESTE', 'Teste de outra servidão', 'SRUP', true)");
        executar(ligacao, "INSERT INTO tipo_dinamica (codigo, nome, ativo) VALUES ('SUSPENSAO_CCDR', 'Teste', true), "
                + "('SUSPENSAO_GOVERNO', 'Teste', true), ('MEDIDAS_PREVENTIVAS_GOVERNO', 'Teste', true), ('RATIFICACAO', 'Teste', true)");
        executar(ligacao, "INSERT INTO instrumento_dinamica_config (tipo_instrumento_id, tipo_dinamica_id) "
                + "SELECT ti.id, td.id FROM tipo_instrumento ti, tipo_dinamica td "
                + "WHERE (ti.codigo IN ('PDIM', 'PNPOT', 'PROT', 'PS', 'PEOT', 'PIMOT', 'REN', 'PRA', 'PSA', 'SRUP_TESTE') "
                + "AND td.codigo = 'ELABORACAO') OR (ti.codigo IN ('PDM', 'PROT') AND td.codigo IN ('SUSPENSAO_CCDR', "
                + "'SUSPENSAO_GOVERNO', 'MEDIDAS_PREVENTIVAS_GOVERNO', 'RATIFICACAO'))");
        executar(ligacao, "INSERT INTO instrumento_procedimento_config (instrumento_dinamica_config_id, tipo_procedimento_id, "
                + "ordem, serie_dr) SELECT idc.id, (SELECT id FROM tipo_procedimento WHERE codigo = 'DECISAO_INICIO'), 1, "
                + "CASE WHEN ti.codigo = 'PROT' THEN 'PRIMEIRA' ELSE 'SEGUNDA_H' END FROM instrumento_dinamica_config idc "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE NOT EXISTS ("
                + "SELECT 1 FROM instrumento_procedimento_config c WHERE c.instrumento_dinamica_config_id = idc.id)");
        executar(ligacao, "INSERT INTO instrumento_procedimento_config (instrumento_dinamica_config_id, tipo_procedimento_id, "
                + "ordem, serie_dr) SELECT idc.id, (SELECT id FROM tipo_procedimento WHERE codigo = 'CONSTITUICAO_CC'), 2, "
                + "'SEGUNDA_C' FROM instrumento_dinamica_config idc JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id WHERE ti.codigo IN ('PDIM', 'PIMOT') AND td.codigo = 'ELABORACAO'");
        executar(ligacao, "INSERT INTO permissao_acao (tipo_entidade, instrumento_procedimento_config_id, ativo) "
                + "SELECT 'PCM', min(c.id), true FROM instrumento_procedimento_config c WHERE c.serie_dr = 'PRIMEIRA'");
    }

    @Test
    void deveDarATodosOsItensDocumentaisOsLimitesDoBaselineLegado() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(contar(ligacao, "documento_config")).isPositive();
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE tamanho_maximo_ficheiro IS DISTINCT FROM "
                    + "524288000 OR tamanho_maximo_total IS DISTINCT FROM 2147483648 "
                    + "OR numero_maximo_ficheiros IS DISTINCT FROM 10")).isEqualTo("0");
        });
    }

    @Test
    void deveRecusarZeroEmCadaLimiteDoItemEAceitarVazio() throws Exception {
        comBaseMigrada(ligacao -> {
            String item = "(SELECT min(id) FROM documento_config)";
            assertThatThrownBy(() -> executar(ligacao,
                    "UPDATE documento_config SET tamanho_maximo_ficheiro = 0 WHERE id = " + item))
                    .hasMessageContaining("ck_documento_config_tamanho_ficheiro");
            assertThatThrownBy(() -> executar(ligacao,
                    "UPDATE documento_config SET tamanho_maximo_total = 0 WHERE id = " + item))
                    .hasMessageContaining("ck_documento_config_tamanho_total");
            assertThatThrownBy(() -> executar(ligacao,
                    "UPDATE documento_config SET numero_maximo_ficheiros = 0 WHERE id = " + item))
                    .hasMessageContaining("ck_documento_config_numero_ficheiros");
            assertThatCode(() -> executar(ligacao, "UPDATE documento_config SET tamanho_maximo_ficheiro = NULL, "
                    + "tamanho_maximo_total = NULL, numero_maximo_ficheiros = NULL WHERE id = " + item))
                    .doesNotThrowAnyException();
        });
    }

    @Test
    void deveReverterOsLimitesDocumentaisSemTocarNasPlantasPorFolha() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DOS_LIMITES_DOCUMENTAIS);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "documento_config", "tamanho_maximo_ficheiro")).isFalse();
            assertThat(colunaExiste(ligacao, "documento_config", "tamanho_maximo_total")).isFalse();
            assertThat(colunaExiste(ligacao, "documento_config", "numero_maximo_ficheiros")).isFalse();
            assertThat(tabelaExiste(ligacao, "planta")).isTrue();
            assertThat(colunaExiste(ligacao, "instrumento_procedimento_config", "tamanho_maximo_processo")).isTrue();
        });
    }

    @Test
    void deveCriarAFichaMotuComOsIndicesEAsRegrasDoTipoELigarAPlanta() throws Exception {
        comBaseMigrada(ligacao -> {
            for (String coluna : new String[] {"id", "processo_id", "tipo", "opcional", "processo_documento_id",
                    "completo", "criado_em", "atualizado_em"}) {
                assertThat(colunaExiste(ligacao, "metadados_motu", coluna)).as(coluna).isTrue();
            }
            assertThat(colunaExiste(ligacao, "planta", "metadados_motu_id")).isTrue();
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_constraint WHERE conname IN ('pk_metadados_motu', "
                    + "'fk_metadados_motu_processo', 'fk_metadados_motu_processo_documento', 'ck_metadados_motu_tipo', "
                    + "'ck_metadados_motu_tipo_documento', 'fk_planta_metadados_motu')")).isEqualTo("6");
            assertThat(definicaoDoIndice(ligacao, "idx_metadados_motu_processo_id")).contains("processo_id");
            assertThat(definicaoDoIndice(ligacao, "uq_metadados_motu_serie")).contains("UNIQUE").contains("processo_id")
                    .contains("'SERIE'");
            assertThat(definicaoDoIndice(ligacao, "uq_metadados_motu_cdg_documento")).contains("UNIQUE")
                    .contains("processo_documento_id").contains("'CDG'").contains("opcional");
            assertThat(definicaoDoIndice(ligacao, "idx_planta_metadados_motu_id")).contains("metadados_motu_id");

            ligacao.setAutoCommit(true);
            inserirProcessoComDocumento(ligacao);
            assertThatThrownBy(() -> inserirFichaMotu(ligacao, "SERIE", false, DOCUMENTO_DA_PLANTA))
                    .hasMessageContaining("ck_metadados_motu_tipo_documento");
            assertThatThrownBy(() -> inserirFichaMotu(ligacao, "CDG", false, "NULL"))
                    .hasMessageContaining("ck_metadados_motu_tipo_documento");
            assertThatThrownBy(() -> inserirFichaMotu(ligacao, "OUTRO", false, "NULL"))
                    .hasMessageContaining("ck_metadados_motu_tipo");

            inserirFichaMotu(ligacao, "SERIE", false, "NULL");
            assertThatThrownBy(() -> inserirFichaMotu(ligacao, "SERIE", false, "NULL"))
                    .hasMessageContaining("uq_metadados_motu_serie");
            inserirFichaMotu(ligacao, "CDG", false, DOCUMENTO_DA_PLANTA);
            assertThatThrownBy(() -> inserirFichaMotu(ligacao, "CDG", false, DOCUMENTO_DA_PLANTA))
                    .hasMessageContaining("uq_metadados_motu_cdg_documento");
            assertThatCode(() -> inserirFichaMotu(ligacao, "CDG", true, DOCUMENTO_DA_PLANTA)).doesNotThrowAnyException();
            assertThatCode(() -> inserirFichaMotu(ligacao, "CDG", true, "NULL")).doesNotThrowAnyException();

            String conjunto = "(SELECT id FROM metadados_motu WHERE tipo = 'CDG' AND NOT opcional)";
            executar(ligacao, "INSERT INTO planta (id, processo_id, processo_documento_id, metadados_motu_id, criado_em, "
                    + "atualizado_em) VALUES (gen_random_uuid(), '" + PROCESSO_ID + "', " + DOCUMENTO_DA_PLANTA + ", "
                    + conjunto + ", now(), now())");
            assertThatThrownBy(() -> executar(ligacao, "DELETE FROM metadados_motu WHERE id = " + conjunto))
                    .hasMessageContaining("fk_planta_metadados_motu");
        });
    }

    @Test
    void deveAcrescentarOsCodigosDaPcgtAosCatalogosComOsDoIdusEReverterAsColunas() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT table_name || ':' || data_type || ':' || character_maximum_length || ':' "
                    + "|| is_nullable FROM information_schema.columns WHERE table_schema = current_schema() "
                    + "AND column_name = 'pcgt_codigo' ORDER BY table_name"))
                    .containsExactly("tipo_dinamica:character varying:20:YES", "tipo_instrumento:character varying:20:YES");
            assertThat(linhas(ligacao, "SELECT codigo || ':' || coalesce(pcgt_codigo, '-') FROM tipo_instrumento"))
                    .containsExactlyInAnyOrder("PDM:01", "PP:06", "PU:07", "PROT:-", "PEOT:-", "REN:-", "PRA:-", "PSA:-", "PDIM:-", "PUIM:-", "PPIM:-", "PNPOT:-", "PS:-", "PIMOT:-",
                            "SERVIDAO:-");
            assertThat(linhas(ligacao, "SELECT codigo || ':' || coalesce(pcgt_codigo, '-') FROM tipo_dinamica"))
                    .containsExactlyInAnyOrder("ELABORACAO:01", "ALTERACAO:02", "REVISAO:03", "ALTERACAO_ADAPTACAO:04",
                            "ALTERACAO_SIMPLIFICADA_ART123:06", "ALTERACAO_SIMPLIFICADA_DL10_2024:06", "CORRECAO_MATERIAL:10",
                            "REVOGACAO:15", "MEDIDAS_PREVENTIVAS:-", "NORMAS_PROVISORIAS:-",
                            "SUSPENSAO:-", "DELIMITACAO:-", "CORRECAO_MATERIAL_RETIFICACAO:-", "SUSPENSAO_CCDR:-",
                            "SUSPENSAO_GOVERNO:-", "MEDIDAS_PREVENTIVAS_GOVERNO:-", "RATIFICACAO:-");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + 1);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "tipo_instrumento WHERE pcgt_codigo IS NOT NULL")).isZero();
            assertThat(contar(ligacao, "tipo_dinamica WHERE pcgt_codigo IS NOT NULL")).isZero();

            reverterOsUltimos(ligacao, 1);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "tipo_instrumento", "pcgt_codigo")).isFalse();
            assertThat(colunaExiste(ligacao, "tipo_dinamica", "pcgt_codigo")).isFalse();
            assertThat(tabelaExiste(ligacao, "metadados_motu")).isTrue();
        });
    }

    @Test
    void deveAcrescentarEReverterALinhaTxtDaPlanta() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT data_type || '|' || is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'planta' AND column_name = 'linha_txt'")).isEqualTo("integer|YES");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "planta", "linha_txt")).isFalse();
            assertThat(colunaExiste(ligacao, "planta", "nome_regulamento")).isTrue();
            assertThat(colunaExiste(ligacao, "metadados_motu", "dados")).isTrue();
        });
    }

    @Test
    void deveAcrescentarEReverterOsDadosDaFichaMotu() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(colunaExiste(ligacao, "metadados_motu", "dados")).isTrue();
            assertThat(texto(ligacao, "SELECT data_type || '|' || is_nullable || '|' || column_default "
                    + "FROM information_schema.columns WHERE table_name = 'metadados_motu' AND column_name = 'dados'"))
                    .startsWith("jsonb|NO|'{}'::jsonb");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "metadados_motu", "dados")).isFalse();
            assertThat(tabelaExiste(ligacao, "metadados_motu")).isTrue();
        });
    }

    @Test
    void deveReverterAFichaMotuSemTocarNaPlanta() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "metadados_motu")).isFalse();
            assertThat(colunaExiste(ligacao, "planta", "metadados_motu_id")).isFalse();
            assertThat(definicaoDoIndice(ligacao, "idx_planta_metadados_motu_id")).isNull();
            assertThat(tabelaExiste(ligacao, "planta")).isTrue();
            assertThat(colunaExiste(ligacao, "documento_config", "tamanho_maximo_ficheiro")).isTrue();
        });
    }

    private static final String UTILIZADOR_DA_SESSAO = "00000000-0000-0000-0000-000000000396";

    private static final String INSERCAO_SESSAO = "INSERT INTO sessao (jti, utilizador_id, tipo, contexto_sessao, emitido_em, "
            + "expira_em, revogado_em) VALUES ('%s', '" + UTILIZADOR_DA_SESSAO + "', '%s', %s, now(), now() + interval '8 hours', %s)";

    private void inserirUtilizadorDaSessao(Connection ligacao) throws SQLException {
        executar(ligacao, "INSERT INTO utilizador (id, email, origem_autenticacao) VALUES ('" + UTILIZADOR_DA_SESSAO
                + "', 'sessao.unica@cm-teste.pt', 'INCM')");
    }

    @Test
    void deveRecusarUmaSegundaSessaoAtivaNaoRevogadaDoMesmoUtilizador() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(definicaoDoIndice(ligacao, "uq_sessao_ativa_utilizador")).contains("UNIQUE").contains("utilizador_id")
                    .contains("'ATIVA'").contains("revogado_em IS NULL");
            ligacao.setAutoCommit(true);
            inserirUtilizadorDaSessao(ligacao);

            executar(ligacao, INSERCAO_SESSAO.formatted("ativa-1", "ATIVA", "NULL", "NULL"));
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_SESSAO.formatted("ativa-2", "ATIVA", "NULL", "NULL")))
                    .hasMessageContaining("uq_sessao_ativa_utilizador");

            assertThatCode(() -> executar(ligacao, INSERCAO_SESSAO.formatted("revogada", "ATIVA", "NULL", "now()")))
                    .doesNotThrowAnyException();
            assertThatCode(() -> executar(ligacao, INSERCAO_SESSAO.formatted("pre", "PRE_AUTENTICACAO", "'{}'", "NULL")))
                    .doesNotThrowAnyException();
            assertThat(contar(ligacao, "sessao WHERE utilizador_id = '" + UTILIZADOR_DA_SESSAO + "'")).isEqualTo(3);
        });
    }

    @Test
    void deveRevogarAsSessoesAtivasAntigasAoMigrarEReverterSoOIndice() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA);
            ligacao.setAutoCommit(true);
            assertThat(definicaoDoIndice(ligacao, "uq_sessao_ativa_utilizador")).isNull();
            inserirUtilizadorDaSessao(ligacao);
            executar(ligacao, INSERCAO_SESSAO.formatted("refresh-opaco-1", "ATIVA", "NULL", "NULL"));
            executar(ligacao, INSERCAO_SESSAO.formatted("refresh-opaco-2", "ATIVA", "NULL", "NULL"));
            executar(ligacao, INSERCAO_SESSAO.formatted("pre", "PRE_AUTENTICACAO", "'{}'", "NULL"));

            migrarOsProximos(ligacao, CHANGESETS_DA_SESSAO_UNICA);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "sessao WHERE tipo = 'ATIVA' AND revogado_em IS NULL")).isZero();
            assertThat(contar(ligacao, "sessao WHERE tipo = 'ATIVA' AND revogado_em IS NOT NULL")).isEqualTo(2);
            assertThat(contar(ligacao, "sessao WHERE tipo = 'PRE_AUTENTICACAO' AND revogado_em IS NULL")).isEqualTo(1);
            assertThat(definicaoDoIndice(ligacao, "uq_sessao_ativa_utilizador")).contains("UNIQUE");

            reverterOsUltimos(ligacao, CHANGESETS_DA_SESSAO_UNICA);
            ligacao.setAutoCommit(true);
            assertThat(definicaoDoIndice(ligacao, "uq_sessao_ativa_utilizador")).isNull();
            assertThat(contar(ligacao, "sessao WHERE tipo = 'ATIVA' AND revogado_em IS NOT NULL")).isEqualTo(2);
        });
    }

    private static final String UTILIZADOR_DO_PROCESSO = "00000000-0000-0000-0000-000000000001";

    private static final String COLUNAS_DA_TABELA = "SELECT column_name || '|' || data_type || '|' || is_nullable "
            + "FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = '%s' "
            + "ORDER BY ordinal_position";

    private static final String INSERCAO_NOTIFICACAO = "INSERT INTO notificacao (id, tipo, titulo, texto, prazo, "
            + "perfil_destino) VALUES ('%s', '%s', 'Título', 'Texto', %s, %s)";

    private static final String INSERCAO_NOTIFICACAO_MENSAGEM = "INSERT INTO notificacao_mensagem (id, notificacao_id, "
            + "autor_id, texto) VALUES (gen_random_uuid(), '%s', '" + UTILIZADOR_DO_PROCESSO + "', 'Mensagem')";

    private static final String INSERCAO_NOTIFICACAO_DOCUMENTO = "INSERT INTO notificacao_documento (id, notificacao_id, "
            + "nome_ficheiro, caminho_ficheiro, tamanho_bytes, criado_por) VALUES (gen_random_uuid(), '%1$s', 'anexo.pdf', "
            + "'notificacoes/%1$s/anexo.pdf', 10, '" + UTILIZADOR_DO_PROCESSO + "')";

    private static String notificacao(String tipo, String prazo, String perfilDestino) {
        return INSERCAO_NOTIFICACAO.formatted(UUID.randomUUID(), tipo, prazo, perfilDestino);
    }

    @Test
    void deveCriarAsTabelasDasNotificacoesComAsColunasAsFkEOsIndices() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, COLUNAS_DA_TABELA.formatted("notificacao"))).containsExactly(
                    "id|uuid|NO",
                    "tipo|character varying|NO",
                    "titulo|text|NO",
                    "texto|text|NO",
                    "processo_id|uuid|YES",
                    "id_igt_snit|text|YES",
                    "descricao_igt|text|YES",
                    "utilizador_id|uuid|YES",
                    "entidade_id|bigint|YES",
                    "perfil_destino|character varying|YES",
                    "lote_id|uuid|YES",
                    "prazo|timestamp with time zone|YES",
                    "estado|character varying|NO",
                    "canal_email|boolean|NO",
                    "email_enviado_em|timestamp with time zone|YES",
                    "criado_por|uuid|YES",
                    "criado_em|timestamp with time zone|NO",
                    "atualizado_em|timestamp with time zone|NO",
                    "lido_em|timestamp with time zone|YES",
                    "resolvido_em|timestamp with time zone|YES");
            assertThat(linhas(ligacao, COLUNAS_DA_TABELA.formatted("notificacao_mensagem"))).containsExactly(
                    "id|uuid|NO",
                    "notificacao_id|uuid|NO",
                    "autor_id|uuid|NO",
                    "texto|text|NO",
                    "criado_em|timestamp with time zone|NO",
                    "atualizado_em|timestamp with time zone|NO");
            assertThat(linhas(ligacao, COLUNAS_DA_TABELA.formatted("notificacao_documento"))).containsExactly(
                    "id|uuid|NO",
                    "notificacao_id|uuid|NO",
                    "nome_ficheiro|text|NO",
                    "caminho_ficheiro|text|NO",
                    "tamanho_bytes|bigint|NO",
                    "criado_por|uuid|NO",
                    "criado_em|timestamp with time zone|NO",
                    "atualizado_em|timestamp with time zone|NO");

            // confdeltype 'a' (NO ACTION): nenhuma FK apaga em cascata.
            assertThat(linhas(ligacao, "SELECT conname || '|' || confrelid::regclass::text || '|' || confdeltype::text "
                    + "FROM pg_constraint WHERE contype = 'f' AND conrelid::regclass::text IN ('notificacao', "
                    + "'notificacao_mensagem', 'notificacao_documento') ORDER BY conname")).containsExactly(
                    "fk_notificacao_criado_por|utilizador|a",
                    "fk_notificacao_documento_criado_por|utilizador|a",
                    "fk_notificacao_documento_notificacao|notificacao|a",
                    "fk_notificacao_entidade|entidade|a",
                    "fk_notificacao_mensagem_autor|utilizador|a",
                    "fk_notificacao_mensagem_notificacao|notificacao|a",
                    "fk_notificacao_processo|processo|a",
                    "fk_notificacao_utilizador|utilizador|a");

            for (String[] indice : new String[][] {{"tipo", "tipo"}, {"processo", "processo_id"},
                    {"utilizador", "utilizador_id"}, {"entidade", "entidade_id"}, {"perfil_destino", "perfil_destino"},
                    {"lote", "lote_id"}, {"estado", "estado"}, {"criado_em", "criado_em"}}) {
                assertThat(definicaoDoIndice(ligacao, "idx_notificacao_" + indice[0])).as(indice[0]).contains(indice[1]);
            }
            assertThat(definicaoDoIndice(ligacao, "idx_notificacao_mensagem_notificacao")).contains("notificacao_id");
            assertThat(definicaoDoIndice(ligacao, "idx_notificacao_documento_notificacao")).contains("notificacao_id");
        });
    }

    @Test
    void deveGravarANotificacaoAtivaSemEmailQuandoNaoSeIndicaOEstado() throws Exception {
        comBaseMigrada(ligacao -> {
            String id = UUID.randomUUID().toString();
            executar(ligacao, "INSERT INTO notificacao (id, tipo, titulo, texto) VALUES ('" + id + "', 'ALERTA', 'T', 'X')");

            assertThat(texto(ligacao, "SELECT estado || '|' || canal_email || '|' || (criado_em IS NOT NULL) || '|' "
                    + "|| (atualizado_em IS NOT NULL) FROM notificacao WHERE id = '" + id + "'"))
                    .isEqualTo("ATIVA|false|true|true");
        });
    }

    @Test
    void deveRecusarTipoEstadoPerfilEPrazoForaDoDominioDaNotificacao() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThatThrownBy(() -> executar(ligacao, notificacao("OUTRO", "NULL", "NULL")))
                    .hasMessageContaining("ck_notificacao_tipo");
            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO notificacao (id, tipo, titulo, texto, estado) "
                    + "VALUES (gen_random_uuid(), 'ALERTA', 'T', 'X', 'APAGADA')"))
                    .hasMessageContaining("ck_notificacao_estado");
            assertThatThrownBy(() -> executar(ligacao, notificacao("ALERTA", "NULL", "'TECNICO'")))
                    .hasMessageContaining("ck_notificacao_perfil_destino");
            assertThatThrownBy(() -> executar(ligacao, notificacao("TEMPORARIA", "now()", "NULL")))
                    .hasMessageContaining("ck_notificacao_prazo");

            assertThatCode(() -> executar(ligacao, notificacao("ESTATICA", "now()", "NULL"))).doesNotThrowAnyException();
            assertThatCode(() -> executar(ligacao, notificacao("RESPOSTA_OBRIGATORIA", "now()", "'MASTER'")))
                    .doesNotThrowAnyException();
            assertThatCode(() -> executar(ligacao, notificacao("TEMPORARIA", "NULL", "NULL"))).doesNotThrowAnyException();
        });
    }

    @Test
    void deveTerNosEnumsOsMesmosValoresDosCheckDaNotificacao() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(valoresDoCheck(ligacao, "ck_notificacao_tipo")).containsExactlyInAnyOrder(
                    Arrays.stream(TipoNotificacao.values()).map(Enum::name).toArray(String[]::new));
            assertThat(valoresDoCheck(ligacao, "ck_notificacao_estado")).containsExactlyInAnyOrder(
                    Arrays.stream(EstadoNotificacao.values()).map(Enum::name).toArray(String[]::new));
        });
    }

    @Test
    void deveImpedirQueOProprietarioApagueANotificacaoComMensagensOuAnexos() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            inserirProcessoComDocumento(ligacao);
            String comMensagem = UUID.randomUUID().toString();
            String comAnexo = UUID.randomUUID().toString();
            executar(ligacao, INSERCAO_NOTIFICACAO.formatted(comMensagem, "PEDIDO_APOIO", "NULL", "'MASTER'"));
            executar(ligacao, INSERCAO_NOTIFICACAO.formatted(comAnexo, "REGULARIZACAO_PROCEDIMENTO", "NULL", "'MASTER'"));
            executar(ligacao, INSERCAO_NOTIFICACAO_MENSAGEM.formatted(comMensagem));
            executar(ligacao, INSERCAO_NOTIFICACAO_DOCUMENTO.formatted(comAnexo));

            assertThatThrownBy(() -> executar(ligacao, "DELETE FROM notificacao WHERE id = '" + comMensagem + "'"))
                    .hasMessageContaining("fk_notificacao_mensagem_notificacao");
            assertThatThrownBy(() -> executar(ligacao, "DELETE FROM notificacao WHERE id = '" + comAnexo + "'"))
                    .hasMessageContaining("fk_notificacao_documento_notificacao");
            assertThat(contar(ligacao, "notificacao")).isEqualTo(2);
        });
    }

    @Test
    void deveDeixarASaigtAppGravarEMudarOEstadoMasNuncaApagarNotificacoesNemMensagens() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            inserirProcessoComDocumento(ligacao);
            String id = UUID.randomUUID().toString();

            try (Connection app = DriverManager.getConnection(ligacao.getMetaData().getURL(), "saigt_app", "teste")) {
                executar(app, INSERCAO_NOTIFICACAO.formatted(id, "PEDIDO_APOIO", "NULL", "'MASTER'"));
                executar(app, INSERCAO_NOTIFICACAO_MENSAGEM.formatted(id));
                executar(app, INSERCAO_NOTIFICACAO_DOCUMENTO.formatted(id));
                executar(app, "UPDATE notificacao SET estado = 'RESOLVIDA', resolvido_em = now() WHERE id = '" + id + "'");

                assertThatThrownBy(() -> executar(app, "DELETE FROM notificacao WHERE false"))
                        .hasMessageContaining("permission denied");
                assertThatThrownBy(() -> executar(app, "DELETE FROM notificacao_mensagem WHERE false"))
                        .hasMessageContaining("permission denied");
                assertThatCode(() -> executar(app, "DELETE FROM notificacao_documento WHERE notificacao_id = '" + id + "'"))
                        .doesNotThrowAnyException();
            }
            assertThat(texto(ligacao, "SELECT estado FROM notificacao WHERE id = '" + id + "'")).isEqualTo("RESOLVIDA");
            assertThat(contar(ligacao, "notificacao_mensagem")).isEqualTo(1);
            assertThat(contar(ligacao, "notificacao_documento")).isZero();
        });
    }

    @Test
    void deveReverterAsNotificacoesSemTocarNaSessaoUnica() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "notificacao_documento")).isFalse();
            assertThat(tabelaExiste(ligacao, "notificacao_mensagem")).isFalse();
            assertThat(tabelaExiste(ligacao, "notificacao")).isFalse();
            assertThat(definicaoDoIndice(ligacao, "uq_sessao_ativa_utilizador")).contains("UNIQUE");
        });
    }

    private void inserirFichaMotu(Connection ligacao, String tipo, boolean opcional, String documento)
            throws SQLException {
        executar(ligacao, "INSERT INTO metadados_motu (id, processo_id, tipo, opcional, processo_documento_id, "
                + "criado_em, atualizado_em) VALUES (gen_random_uuid(), '" + PROCESSO_ID + "', '" + tipo + "', "
                + opcional + ", " + documento + ", now(), now())");
    }

    @Test
    void deveGuardarACaducidadeNaDinamicaENaoNoProcesso() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(colunaExiste(ligacao, "dinamica", "data_caducidade")).isTrue();
            assertThat(colunaExiste(ligacao, "dinamica", "alertas_enviados")).isTrue();
            assertThat(colunaExiste(ligacao, "processo", "data_limite")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "alertas_enviados")).isFalse();

            assertThat(definicaoDoIndice(ligacao, "idx_dinamica_data_caducidade"))
                    .contains("data_caducidade")
                    .contains("EM_CURSO");
            assertThat(definicaoDoIndice(ligacao, "idx_processo_data_limite")).isNull();
        });
    }

    @Test
    void deveLigarAFundamentacaoRjigtSoNosDoisProcedimentosDl10DoPdmEReverterAColuna() throws Exception {
        comBaseMigrada(ligacao -> {
            List<String> ligados = new ArrayList<>();
            try (Statement consulta = ligacao.createStatement();
                 ResultSet linhas = consulta.executeQuery("SELECT ti.codigo || ':' || tp.codigo FROM instrumento_procedimento_config ipc "
                         + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                         + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                         + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                         + "WHERE ipc.pede_fundamentacao_rjigt ORDER BY 1")) {
                while (linhas.next()) {
                    ligados.add(linhas.getString(1));
                }
            }
            assertThat(ligados).containsExactly("PDM:ABERTURA_DISCUSSAO_PUBLICA_DL10", "PDM:ALTERACAO_SIMPLIFICADA_DL10");
            assertThat(contar(ligacao, "lista_valores WHERE tipo = 'FUNDAMENTACAO_RJIGT'")).isZero();

            reverter(ligacao, 6);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "instrumento_procedimento_config", "pede_fundamentacao_rjigt")).isFalse();
        });
    }

    @Test
    void deveCriarAsObservacoesDosItensComChaveCompostaERecusarTextoVazio() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);
            String insercao = "INSERT INTO processo_documento_observacao (processo_id, documento_config_id, observacoes) "
                    + "VALUES ('" + PROCESSO_ID + "', (SELECT min(id) FROM documento_config), ";

            executar(ligacao, insercao + "'NA')");
            assertThat(contar(ligacao, "processo_documento_observacao")).isEqualTo(1);
            assertThatThrownBy(() -> executar(ligacao, insercao + "'Outra')"))
                    .hasMessageContaining("pk_processo_documento_observacao");
            executar(ligacao, "DELETE FROM processo_documento_observacao");
            assertThatThrownBy(() -> executar(ligacao, insercao + "'   ')"))
                    .hasMessageContaining("ck_processo_documento_observacao_nao_vazia");

            reverter(ligacao, 13);
            ligacao.setAutoCommit(true);
            assertThat(tabelaExiste(ligacao, "processo_documento_observacao")).isFalse();
        });
    }

    @Test
    void deveRecusarValoresForaDosDominiosFechados() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThatThrownBy(() -> executar(ligacao,
                    "INSERT INTO tipo_instrumento (codigo, nome, categoria) VALUES ('X', 'X', 'OUTRA')"))
                    .hasMessageContaining("ck_tipo_instrumento_categoria");
            assertThatThrownBy(() -> executar(ligacao,
                    "INSERT INTO utilizador (id, email, origem_autenticacao, perfil_interno) "
                            + "VALUES (gen_random_uuid(), 'a@dgt.pt', 'AD_DGT', 'ADMIN_DGT')"))
                    .hasMessageContaining("ck_utilizador_perfil_interno_valor");
        });
    }

    @Test
    void deveAplicarOModeloDocumentalDoLld() throws Exception {
        comBaseMigrada(ligacao -> {
            for (String coluna : new String[] {"codigo", "separador", "tipo_elemento", "formato"}) {
                assertThat(colunaExiste(ligacao, "documento_config", coluna)).isTrue();
            }
            for (String coluna : new String[] {"categoria", "extensao", "origem", "versao_objeto", "bloqueado_edicao"}) {
                assertThat(colunaExiste(ligacao, "processo_documento", coluna)).isTrue();
            }
            assertThat(colunaExiste(ligacao, "processo", "elementos_afetados")).isTrue();
            assertThat(colunaExiste(ligacao, "processo_documento", "token_publico")).isFalse();
            assertThat(colunaExiste(ligacao, "processo_documento", "exposto_em")).isFalse();

            assertThat(definicaoDoIndice(ligacao, "idx_processo_documento_processo_categoria"))
                    .contains("processo_id").contains("categoria");
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_constraint "
                    + "WHERE conname IN ('uq_processo_documento_token_publico', 'uq_documento_config_codigo')"))
                    .isEqualTo("1");
        });
    }

    @Test
    void deveCriarODocumentoLinkPublicoComUmLinkAtivoPorDocumentoEDestino() throws Exception {
        comBaseMigrada(ligacao -> {
            for (String coluna : new String[] {"processo_documento_id", "destino", "token", "criado_em", "revogado_em",
                    "motivo_revogacao"}) {
                assertThat(colunaExiste(ligacao, "documento_link_publico", coluna)).as(coluna).isTrue();
            }
            assertThat(definicaoDoIndice(ligacao, "uq_documento_link_publico_ativo"))
                    .contains("UNIQUE").contains("processo_documento_id").contains("destino")
                    .contains("revogado_em IS NULL");
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_constraint WHERE conname IN "
                    + "('uq_documento_link_publico_token', 'ck_documento_link_publico_destino', "
                    + "'ck_documento_link_publico_revogacao', 'fk_documento_descarga_documento_link_publico')"))
                    .isEqualTo("4");
        });
    }

    @Test
    void deveSemearOLinkPublicoSoNaPublicacaoENosAtosEnviadosAPcgt() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config "
                    + "WHERE categoria = 'PUBLICACAO' AND NOT link_publico_permitido")).isEqualTo("0");
            assertThat(texto(ligacao, "SELECT string_agg(DISTINCT codigo, ',' ORDER BY codigo) FROM documento_config "
                    + "WHERE categoria = 'DEPOSITO' AND link_publico_permitido"))
                    .isEqualTo("DELIBERACAO_AM_DEP,DELIBERACAO_CM_DEP,DELIBERACAO_CM_ELABORACAO_DEP,DELIBERACAO_CM_SUSPENSAO_DEP,"
                            + "DELIBERACAO_DEP,DELIBERACAO_ELABORACAO_DEP,DESPACHO_CCDR_DEP,RCM_DEP");
        });
    }

    @Test
    void deveCriarADocumentoDescargaComOsCamposDoDbDoc010() throws Exception {
        comBaseMigrada(ligacao -> {
            for (String coluna : new String[] {"consumidor", "processo_id", "processo_documento_id",
                    "notificacao_documento_id", "manifesto_id", "utilizador_id", "documento_link_publico_id", "ip",
                    "iniciada_em", "resultado", "bytes_enviados"}) {
                assertThat(colunaExiste(ligacao, "documento_descarga", coluna)).as(coluna).isTrue();
            }
            assertThat(colunaExiste(ligacao, "documento_descarga", "ticket")).isFalse();
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_constraint WHERE conname IN "
                    + "('ck_documento_descarga_consumidor', 'ck_documento_descarga_resultado')")).isEqualTo("2");
            assertThat(definicaoDoIndice(ligacao, "idx_documento_descarga_processo_id")).contains("processo_id");
            assertThat(definicaoDoIndice(ligacao, "idx_documento_descarga_iniciada_em")).contains("iniciada_em");
        });
    }

    @Test
    void deveManterADescargaAuditadaQuandoODocumentoERemovido() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            inserirProcessoComDocumento(ligacao);
            executar(ligacao, "INSERT INTO documento_descarga (consumidor, processo_id, processo_documento_id, "
                    + "iniciada_em, resultado) VALUES ('INCM', '" + PROCESSO_ID + "', " + DOCUMENTO_DA_PLANTA
                    + ", now(), 'CONCLUIDA')");

            executar(ligacao, "DELETE FROM processo_documento");

            assertThat(linhas(ligacao, "SELECT consumidor || '|' || processo_id || '|' "
                    + "|| coalesce(processo_documento_id::text, 'null') FROM documento_descarga"))
                    .containsExactly("INCM|" + PROCESSO_ID + "|null");
        });
    }

    @Test
    void deveReverterADescargaQueSobreviveAoDocumento() throws Exception {
        comBaseMigrada(ligacao -> {
            String regraDeApagamento = "SELECT confdeltype::text FROM pg_constraint "
                    + "WHERE conname = 'fk_documento_descarga_processo_documento'";
            assertThat(texto(ligacao, regraDeApagamento)).isEqualTo("n");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DOS_LIMITES_DOCUMENTAIS + 1);

            assertThat(texto(ligacao, regraDeApagamento)).isEqualTo("a");
        });
    }

    @Test
    void deveRecusarCodigoRepetidoEValoresForaDoDominioDocumental() throws Exception {
        comBaseMigrada(ligacao -> {
            String insercao = "INSERT INTO documento_config (instrumento_procedimento_config_id, codigo, nome, separador, "
                    + "categoria, tipo_elemento, formato, extensoes_aceites, ordem) "
                    + "SELECT min(id), '%s', 'Item', 'Item', '%s', '%s', '%s', '[\"pdf\"]', 1 "
                    + "FROM instrumento_procedimento_config";
            executar(ligacao, insercao.formatted("TEXTO", "PUBLICACAO", "PECA_ESCRITA", "EDITAVEL"));

            assertThat(texto(ligacao, "SELECT obrigatorio::text FROM documento_config WHERE codigo = 'TEXTO'"))
                    .isEqualTo("false");
            assertThatThrownBy(() -> executar(ligacao, insercao.formatted("TEXTO", "DEPOSITO", "OUTROS", "EDITAVEL")))
                    .hasMessageContaining("uq_documento_config_codigo");
            assertThatThrownBy(() -> executar(ligacao, insercao.formatted("A", "OUTRA", "OUTROS", "EDITAVEL")))
                    .hasMessageContaining("ck_documento_config_categoria");
            assertThatThrownBy(() -> executar(ligacao, insercao.formatted("B", "DEPOSITO", "OUTRO", "EDITAVEL")))
                    .hasMessageContaining("ck_documento_config_tipo_elemento");
            assertThatThrownBy(() -> executar(ligacao, insercao.formatted("C", "DEPOSITO", "OUTROS", "OUTRO")))
                    .hasMessageContaining("ck_documento_config_formato");
        });
    }

    @Test
    void devePreencherAsLinhasExistentesAoMigrarOModeloDocumental() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 44);
            ligacao.setAutoCommit(true);
            inserirDocumentosDoModeloAnterior(ligacao);

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT codigo || '|' || separador || '|' || tipo_elemento || '|' || formato "
                    + "|| '|' || obrigatorio::text FROM documento_config WHERE nome = 'Texto do ato'"))
                    .isEqualTo("ITEM_" + texto(ligacao, "SELECT id::text FROM documento_config WHERE nome = 'Texto do ato'")
                            + "|Texto do ato|OUTROS|EDITAVEL|true");
            assertThat(texto(ligacao, "SELECT tipo_elemento || '|' || formato FROM documento_config "
                    + "WHERE nome = 'Planta'")).isEqualTo("OUTROS|NAO_EDITAVEL");

            assertThat(texto(ligacao, linhaDocumento("Texto.PDF"))).isEqualTo("PUBLICACAO|pdf|CARREGADO|false");
            assertThat(texto(ligacao, linhaDocumento("planta.shp"))).isEqualTo("DEPOSITO|shp|CARREGADO|false");
            assertThat(texto(ligacao, linhaDocumento("anexo"))).isEqualTo("DEPOSITO||CARREGADO|false");
            assertThat(colunaExiste(ligacao, "processo_documento", "token_publico")).isFalse();
        });
    }

    private static final String PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PDM = "209";

    private static final String EXTENSOES_PUBLICACAO_ESCRITA = "doc,docx,rtf,odf,odt,xls,xlsx,csv,ods,pdf,txt";

    private static final String PROCEDIMENTO_ABERTURA_DISCUSSAO_PUBLICA_ALTERACAO_PDM = "210";

    private static final String PROCEDIMENTO_PUBLICACAO_ALTERACAO_PDM = "211";

    private static final String EXTENSOES_DEPOSITO_ESCRITA = "pdf,jpg,png";

    /** Os procedimentos de PDM do seed de 2026-11-09, sem os da Alteração (209 a 211). */
    private static final String FORA_DA_ALTERACAO_209_A_211 = "instrumento_procedimento_config_id BETWEEN 201 AND 220 "
            + "AND instrumento_procedimento_config_id NOT IN (209, 210, 211) "
            + "AND codigo NOT IN ('PROVA_NOTIFICACAO_AM_DEP', 'PROVA_NOTIFICACAO_CCDR_DEP')";

    private static final String ITENS_DO_PROCEDIMENTO = itensDoProcedimento(PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PDM);

    private static String itensDoProcedimento(String procedimento) {
        return "SELECT codigo || '|' || separador || '|' || nome || '|' || categoria "
                + "|| '|' || tipo_elemento || '|' || formato || '|' || obrigatorio::text || '|' "
                + "|| array_to_string(ARRAY(SELECT jsonb_array_elements_text(extensoes_aceites)), ',') || '|' || ativo::text "
                + "FROM documento_config WHERE instrumento_procedimento_config_id = " + procedimento + " ORDER BY ordem";
    }

    @Test
    void deveSemearOsSeisItensDaDecisaoDeInicioDaAlteracaoDePdm() throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            assertThat(texto(ligacao, "SELECT ti.codigo || '|' || td.codigo || '|' || tp.codigo || '|' || ipc.serie_dr "
                    + "FROM instrumento_procedimento_config ipc "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                    + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                    + "WHERE ipc.id = " + PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PDM))
                    .isEqualTo("PDM|ALTERACAO|DECISAO_INICIO|SEGUNDA_H");

            String deliberacao = "Deliberação do início de alteração do plano territorial";
            String textoAto = "Texto do ato a publicar no Diário da República";
            String planta = "Planta com área de intervenção";
            assertThat(linhas(ligacao, ITENS_DO_PROCEDIMENTO)).containsExactly(
                    "DELIBERACAO_PUB|Deliberação|" + deliberacao + "|PUBLICACAO|PECA_ESCRITA|EDITAVEL|true|"
                            + EXTENSOES_PUBLICACAO_ESCRITA + "|true",
                    "DELIBERACAO_DEP|Deliberação|" + deliberacao + "|DEPOSITO|PECA_ESCRITA|NAO_EDITAVEL|false|pdf,jpg,png|true",
                    "TEXTO_ATO_PUB|Texto do Ato|" + textoAto + "|PUBLICACAO|PECA_ESCRITA|EDITAVEL|true|"
                            + EXTENSOES_PUBLICACAO_ESCRITA + "|true",
                    "TEXTO_ATO_DEP|Texto do Ato|" + textoAto + "|DEPOSITO|PECA_ESCRITA|NAO_EDITAVEL|false|pdf,jpg,png|true",
                    "PLANTA_LOCALIZACAO_PUB|Planta de Localização|" + planta
                            + "|PUBLICACAO|PECA_GRAFICA|EDITAVEL|true|zip|true",
                    "PLANTA_LOCALIZACAO_DEP|Planta de Localização|" + planta
                            + "|DEPOSITO|PECA_GRAFICA|NAO_EDITAVEL|false|zip|true");
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE instrumento_procedimento_config_id = "
                    + PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PDM)).isEqualTo("6");
        });
    }

    @Test
    void deveDevolverOsSeisItensAtivosPelaOrdemQueOProcedimentoDisponivelLe() throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            assertThat(linhas(ligacao, "SELECT codigo FROM documento_config WHERE ativo "
                    + "AND instrumento_procedimento_config_id IN (" + PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PDM + ") "
                    + "ORDER BY ordem")).containsExactly("DELIBERACAO_PUB", "DELIBERACAO_DEP", "TEXTO_ATO_PUB",
                    "TEXTO_ATO_DEP", "PLANTA_LOCALIZACAO_PUB", "PLANTA_LOCALIZACAO_DEP");
        });
    }

    @Test
    void deveExigirApenasOsItensDePublicacaoENaoCruzarOTipoDeElementoDaPlanta() throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            assertThat(linhas(ligacao, "SELECT tipo_elemento || ':' || string_agg(codigo, ',' ORDER BY ordem) "
                    + "FROM documento_config WHERE obrigatorio "
                    + "AND instrumento_procedimento_config_id = " + PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PDM
                    + " GROUP BY tipo_elemento ORDER BY tipo_elemento")).containsExactly(
                    "PECA_ESCRITA:DELIBERACAO_PUB,TEXTO_ATO_PUB", "PECA_GRAFICA:PLANTA_LOCALIZACAO_PUB");
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE obrigatorio "
                    + "AND categoria = 'DEPOSITO'")).isEqualTo("0");
        });
    }

    private static final String PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PU = "3207";

    private static final String PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PP = "2207";

    /** Todas as colunas que mudam o comportamento do item, para comparar o seed com o do 209. */
    private static final String ITEM_COMPLETO = "codigo || '|' || separador || '|' || nome || '|' || categoria || '|' "
            + "|| tipo_elemento || '|' || formato || '|' || obrigatorio::text || '|' || extensoes_aceites::text || '|' "
            + "|| ordem || '|' || ativo::text || '|' || coalesce(elemento_informacao_base, '-') || '|' "
            + "|| link_publico_permitido::text || '|' || pagina || '|' || tamanho_maximo_ficheiro || '|' "
            + "|| tamanho_maximo_total || '|' || numero_maximo_ficheiros";

    private List<String> itensCompletos(Connection ligacao, String procedimento) throws SQLException {
        return linhas(ligacao, "SELECT " + ITEM_COMPLETO + " FROM documento_config WHERE instrumento_procedimento_config_id = "
                + procedimento + " ORDER BY ordem");
    }

    static Stream<Arguments> decisoesDeInicioDaAlteracaoDePlano() {
        return Stream.of(Arguments.of(PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PU, "PU"),
                Arguments.of(PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PP, "PP"));
    }

    @ParameterizedTest
    @MethodSource("decisoesDeInicioDaAlteracaoDePlano")
    void deveSemearOsSeisItensDaDecisaoDeInicioDaAlteracaoDoPlanoIguaisAosDaAlteracaoDePdm(String procedimento,
                                                                                         String instrumento) throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            assertThat(texto(ligacao, "SELECT ti.codigo || '|' || ti.categoria || '|' || td.codigo || '|' || tp.codigo "
                    + "FROM instrumento_procedimento_config ipc "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                    + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE ipc.id = " + procedimento))
                    .isEqualTo(instrumento + "|PLANO|ALTERACAO|DECISAO_INICIO");

            String deliberacao = "Deliberação do início de alteração do plano territorial";
            String textoAto = "Texto do ato a publicar no Diário da República";
            String planta = "Planta com área de intervenção";
            assertThat(linhas(ligacao, itensDoProcedimento(procedimento))).containsExactly(
                    "DELIBERACAO_PUB|Deliberação|" + deliberacao + "|PUBLICACAO|PECA_ESCRITA|EDITAVEL|true|"
                            + EXTENSOES_PUBLICACAO_ESCRITA + "|true",
                    "DELIBERACAO_DEP|Deliberação|" + deliberacao + "|DEPOSITO|PECA_ESCRITA|NAO_EDITAVEL|false|pdf,jpg,png|true",
                    "TEXTO_ATO_PUB|Texto do Ato|" + textoAto + "|PUBLICACAO|PECA_ESCRITA|EDITAVEL|true|"
                            + EXTENSOES_PUBLICACAO_ESCRITA + "|true",
                    "TEXTO_ATO_DEP|Texto do Ato|" + textoAto + "|DEPOSITO|PECA_ESCRITA|NAO_EDITAVEL|false|pdf,jpg,png|true",
                    "PLANTA_LOCALIZACAO_PUB|Planta de Localização|" + planta
                            + "|PUBLICACAO|PECA_GRAFICA|EDITAVEL|true|zip|true",
                    "PLANTA_LOCALIZACAO_DEP|Planta de Localização|" + planta
                            + "|DEPOSITO|PECA_GRAFICA|NAO_EDITAVEL|false|zip|true");
            assertThat(linhas(ligacao, "SELECT codigo || '|' || ordem || '|' || coalesce(elemento_informacao_base, '-') "
                    + "FROM documento_config WHERE instrumento_procedimento_config_id = " + procedimento + " ORDER BY ordem"))
                    .containsExactly("DELIBERACAO_PUB|1|DELIBERACAO", "DELIBERACAO_DEP|2|DELIBERACAO",
                            "TEXTO_ATO_PUB|3|TEXTO_ATO", "TEXTO_ATO_DEP|4|TEXTO_ATO", "PLANTA_LOCALIZACAO_PUB|5|-",
                            "PLANTA_LOCALIZACAO_DEP|6|-");
            assertThat(itensCompletos(ligacao, procedimento)).hasSize(6)
                    .isEqualTo(itensCompletos(ligacao, PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PDM));

            // Como a estrutura documental: separadores pela menor ordem, com o elemento do item que o tenha.
            assertThat(linhas(ligacao, "SELECT separador || '|' || coalesce(min(elemento_informacao_base), '-') "
                    + "FROM documento_config WHERE ativo AND instrumento_procedimento_config_id = " + procedimento
                    + " GROUP BY separador ORDER BY min(ordem)"))
                    .containsExactly("Deliberação|DELIBERACAO", "Texto do Ato|TEXTO_ATO", "Planta de Localização|-");
            assertThat(linhas(ligacao, "SELECT codigo || '|' || categoria FROM documento_config WHERE ativo AND obrigatorio "
                    + "AND instrumento_procedimento_config_id = " + procedimento + " ORDER BY ordem")).containsExactly(
                    "DELIBERACAO_PUB|PUBLICACAO", "TEXTO_ATO_PUB|PUBLICACAO", "PLANTA_LOCALIZACAO_PUB|PUBLICACAO");
            // A captura cria uma linha de Informação Base por elemento marcado nos itens ativos.
            assertThat(linhas(ligacao, "SELECT DISTINCT elemento_informacao_base FROM documento_config WHERE ativo "
                    + "AND elemento_informacao_base IS NOT NULL AND instrumento_procedimento_config_id = " + procedimento
                    + " ORDER BY 1")).containsExactly("DELIBERACAO", "TEXTO_ATO");
        });
    }

    @ParameterizedTest
    @MethodSource("decisoesDeInicioDaAlteracaoDePlano")
    void deveRecusarUmExecutavelNaDeliberacaoDePublicacaoDaDecisaoDeInicioDaAlteracaoDoPlano(String procedimento,
                                                                                          String instrumento) throws Exception {
        comBaseMigrada(ligacao -> {
            DocumentoConfig deliberacao;
            try (Statement sql = ligacao.createStatement(); ResultSet linha = sql.executeQuery(
                    "SELECT codigo, categoria, tipo_elemento, formato, extensoes_aceites::text, ordem FROM documento_config "
                            + "WHERE codigo = 'DELIBERACAO_PUB' AND instrumento_procedimento_config_id = " + procedimento)) {
                assertThat(linha.next()).as(instrumento).isTrue();
                deliberacao = new DocumentoConfig(null, linha.getString(1), linha.getString(1), linha.getString(1),
                        CategoriaDocumento.valueOf(linha.getString(2)), TipoElemento.valueOf(linha.getString(3)),
                        FormatoDocumento.valueOf(linha.getString(4)), linha.getString(5), linha.getInt(6));
            }

            assertThatThrownBy(() -> RegrasExtensaoDocumento.validarFicheiro("deliberacao.exe", deliberacao))
                    .isInstanceOf(ExtensaoNaoAceiteException.class).hasMessage(RegrasExtensaoDocumento.MSG_ITEM);
            assertThatCode(() -> RegrasExtensaoDocumento.validarFicheiro("deliberacao.pdf", deliberacao))
                    .doesNotThrowAnyException();
        });
    }

    /** Reverter o seed do PU leva também o do PP e a tabela das tags, que vêm depois e voltam com a migração. */
    static Stream<Arguments> seedsDaDecisaoDeInicioDaAlteracaoDePlano() {
        return Stream.of(
                Arguments.of(PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PU, "2026-11-11-7-seed-documento-config-decisao-inicio-alteracao-pu",
                        CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO +CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU,
                        PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PU + ", " + PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PP),
                Arguments.of(PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PP, "2026-11-11-8-seed-documento-config-decisao-inicio-alteracao-pp",
                        CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO +CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP, PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PP));
    }

    @ParameterizedTest
    @MethodSource("seedsDaDecisaoDeInicioDaAlteracaoDePlano")
    void deveReverterSoOsSeisItensDaDecisaoDeInicioDaAlteracaoDoPlanoEAplicarOSeedUmaVezSemTocarNoPdm(String procedimento,
            String changeset, int changesetsAReverter, String procedimentosRevertidos) throws Exception {
        String registo = "SELECT count(*) FROM databasechangelog WHERE id = '" + changeset + "'";
        // Sem `regras_tiff` nem `regra_nomenclatura`: as colunas, mais recentes que o seed, saem com o revert e voltam
        // com a migração.
        String restantes = "SELECT md5(string_agg((to_jsonb(d) - 'regras_tiff' - 'regra_nomenclatura')::text, ',' ORDER BY d.id)) "
                + "FROM documento_config d WHERE instrumento_procedimento_config_id NOT IN (" + procedimentosRevertidos + ") "
                + "AND " + FORA_DAS_PLANTAS_DA_DECISAO_DE_ELABORAR;
        int itensRevertidos = 6 * procedimentosRevertidos.split(",").length;
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            assertThat(texto(ligacao, registo)).isEqualTo("1");
            String antes = texto(ligacao, restantes);
            List<String> doPdm = itensCompletos(ligacao, PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PDM);
            int total = contar(ligacao, "documento_config");

            reverterOsUltimos(ligacao, changesetsAReverter - CHANGESETS_DA_PUBLICACAO_FINAL - CHANGESETS_DO_CARTOGRAMA_SEM_FICHA - CHANGESETS_DO_FORMULARIO_REN - CHANGESETS_DO_PCM_DEVOLVIDO_EM - CHANGESETS_DO_RESUMO_VALIDADOS - CHANGESETS_DA_SERIE_DO_PROCESSO - CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO - CHANGESETS_DO_PROCESSO_REFERIDO - CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 - CHANGESETS_DA_RETIRADA_DA_RERAE - CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT - CHANGESETS_DA_TENTATIVA_DO_PROCESSO - CHANGESETS_DO_PROT_SO_DA_CCDR - CHANGESETS_DAS_RESTANTES_SERVIDOES - CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR - CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP - CHANGESETS_DO_DESTINO_DO_PROCESSO - CHANGESETS_DA_DINAMICA_ASSOCIADA - CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO - CHANGESETS_DO_URL_GEOCONFORM - CHANGESETS_DA_CORRECAO_MATERIAL - CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE - CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT - CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT - CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, registo)).isEqualTo("0");
            assertThat(contar(ligacao, "documento_config")).isEqualTo(total - itensRevertidos);
            assertThat(contar(ligacao, "documento_config WHERE instrumento_procedimento_config_id = " + procedimento)).isZero();
            assertThat(texto(ligacao, restantes)).isEqualTo(antes);

            migrarOsProximos(ligacao, changesetsAReverter);
            ligacao.setAutoCommit(true);
            migrar(ligacao);
            semAListaDaDgt(ligacao);

            assertThat(texto(ligacao, registo)).isEqualTo("1");
            assertThat(contar(ligacao, "documento_config")).isEqualTo(total);
            assertThat(itensCompletos(ligacao, procedimento)).isEqualTo(doPdm);
            assertThat(texto(ligacao, restantes)).isEqualTo(antes);
        });
    }

    private static String linhaDeItem(String codigo, String separador, String nome, String categoria, String tipoElemento,
                                      String obrigatorio, String extensoes) {
        String formato = "PUBLICACAO".equals(categoria) ? "EDITAVEL" : "NAO_EDITAVEL";
        return codigo + "|" + separador + "|" + nome + "|" + categoria + "|" + tipoElemento + "|" + formato + "|"
                + obrigatorio + "|" + extensoes + "|true";
    }

    @Test
    void deveSemearOsSeisItensDaAberturaDaDiscussaoPublicaDaAlteracaoDePdm() throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            assertThat(texto(ligacao, "SELECT ti.codigo || '|' || td.codigo || '|' || tp.codigo "
                    + "FROM instrumento_procedimento_config ipc "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                    + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                    + "WHERE ipc.id = " + PROCEDIMENTO_ABERTURA_DISCUSSAO_PUBLICA_ALTERACAO_PDM))
                    .isEqualTo("PDM|ALTERACAO|ABERTURA_DISCUSSAO_PUBLICA");

            String deliberacao = "Deliberação do início de alteração do plano territorial";
            String textoAto = "Texto do ato a publicar no Diário da República";
            String planta = "Planta com área de intervenção";
            assertThat(linhas(ligacao, itensDoProcedimento(PROCEDIMENTO_ABERTURA_DISCUSSAO_PUBLICA_ALTERACAO_PDM)))
                    .containsExactly(
                            linhaDeItem("DELIBERACAO_PUB", "Deliberação", deliberacao, "PUBLICACAO", "PECA_ESCRITA", "true",
                                    EXTENSOES_PUBLICACAO_ESCRITA),
                            linhaDeItem("DELIBERACAO_DEP", "Deliberação", deliberacao, "DEPOSITO", "PECA_ESCRITA", "false",
                                    EXTENSOES_DEPOSITO_ESCRITA),
                            linhaDeItem("TEXTO_ATO_PUB", "Texto do Ato", textoAto, "PUBLICACAO", "PECA_ESCRITA", "true",
                                    EXTENSOES_PUBLICACAO_ESCRITA),
                            linhaDeItem("TEXTO_ATO_DEP", "Texto do Ato", textoAto, "DEPOSITO", "PECA_ESCRITA", "false",
                                    EXTENSOES_DEPOSITO_ESCRITA),
                            linhaDeItem("PLANTA_LOCALIZACAO_PUB", "Planta de Localização", planta, "PUBLICACAO",
                                    "PECA_GRAFICA", "true", "zip"),
                            linhaDeItem("PLANTA_LOCALIZACAO_DEP", "Planta de Localização", planta, "DEPOSITO",
                                    "PECA_GRAFICA", "false", "zip"));
        });
    }

    @Test
    void deveSemearOsItensDaPublicacaoDaAlteracaoDePdm() throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            assertThat(texto(ligacao, "SELECT ti.codigo || '|' || td.codigo || '|' || tp.codigo "
                    + "FROM instrumento_procedimento_config ipc "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                    + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                    + "WHERE ipc.id = " + PROCEDIMENTO_PUBLICACAO_ALTERACAO_PDM)).isEqualTo("PDM|ALTERACAO|ALTERACAO");

            String textoAto = "Texto do ato a publicar no Diário da República";
            String deliberacao = "Deliberação da Assembleia Municipal (AM) que aprova a alteração do plano";
            String alteracoes = "Alterações ao Regulamento — extrato com as alterações introduzidas";
            String regulamento = "Regulamento alterado na sua versão integral";
            String escrita = "PECA_ESCRITA";
            String grafica = "PECA_GRAFICA";
            String pub = "PUBLICACAO";
            String dep = "DEPOSITO";
            String epub = EXTENSOES_PUBLICACAO_ESCRITA;
            String edep = EXTENSOES_DEPOSITO_ESCRITA;
            assertThat(linhas(ligacao, itensDoProcedimento(PROCEDIMENTO_PUBLICACAO_ALTERACAO_PDM))).containsExactly(
                    linhaDeItem("TEXTO_ATO_PUB", "Texto do Ato", textoAto, pub, escrita, "true", epub),
                    linhaDeItem("TEXTO_ATO_DEP", "Texto do Ato", textoAto, dep, escrita, "false", edep),
                    linhaDeItem("DELIBERACAO_PUB", "Deliberação", deliberacao, pub, escrita, "true", epub),
                    linhaDeItem("DELIBERACAO_DEP", "Deliberação", deliberacao, dep, escrita, "false", edep),
                    linhaDeItem("ALTERACOES_REGULAMENTO_PUB", "Alterações ao Regulamento", alteracoes, pub, escrita,
                            "false", epub),
                    linhaDeItem("ALTERACOES_REGULAMENTO_DEP", "Alterações ao Regulamento", alteracoes, dep, escrita,
                            "false", edep),
                    linhaDeItem("REGULAMENTO_INTEGRAL_PUB", "Regulamento", regulamento, pub, escrita, "false", epub),
                    linhaDeItem("REGULAMENTO_INTEGRAL_DEP", "Regulamento", regulamento, dep, escrita, "false", edep),
                    linhaDeItem("PLANTA_ORDENAMENTO_PUB", "Planta de Ordenamento", "Planta de ordenamento", pub, grafica,
                            "false", "zip"),
                    linhaDeItem("PLANTA_ORDENAMENTO_DEP", "Planta de Ordenamento", "Planta de ordenamento", dep, grafica,
                            "false", "zip"),
                    linhaDeItem("PLANTA_CONDICIONANTES_PUB", "Planta de Condicionantes", "Planta de condicionantes", pub,
                            grafica, "false", "zip"),
                    linhaDeItem("PLANTA_CONDICIONANTES_DEP", "Planta de Condicionantes", "Planta de condicionantes", dep,
                            grafica, "false", "zip"),
                    linhaDeItem("RELATORIO_FUNDAMENTACAO_DEP", "Relatório de Fundamentação",
                            "Relatório fundamentando as alterações introduzidas", dep, escrita, "false", edep),
                    linhaDeItem("RELATORIO_AMBIENTAL_DEP", "Relatório Ambiental", "Relatório ambiental (AAE)", dep,
                            escrita, "false", edep),
                    linhaDeItem("DEMAIS_ELEMENTOS_DEP", "Outros Elementos do Plano",
                            "Os demais elementos do plano afetados pela alteração", dep, "OUTROS", "false", edep),
                    linhaDeItem("RELATORIO_PONDERACAO_DEP", "Relatório de Ponderação",
                            "Relatório de ponderação dos resultados da discussão pública", dep, escrita, "false", edep),
                    linhaDeItem("DELIBERACAO_CM_DEP", "Deliberação da CM",
                            "Deliberação da Câmara Municipal (CM) que determina a alteração do plano", dep, escrita,
                            "false", edep),
                    linhaDeItem("PARECER_CCDR_DEP", "Parecer da CCDR", "Parecer da CCDR", dep, escrita, "false", edep),
                    linhaDeItem("PARECERES_ATA_DEP", "Pareceres", "Pareceres emitidos / Ata da conferência de serviços",
                            dep, escrita, "false", edep),
                    linhaDeItem("PLANTAS_ZIP_ORDENAMENTO_DEP", "Planta de Ordenamento", "Planta de ordenamento", dep, grafica,
                            "false", "zip"),
                    linhaDeItem("PLANTAS_ZIP_CONDICIONANTES_DEP", "Planta de Condicionantes", "Planta de condicionantes", dep,
                            grafica, "false", "zip"),
                    linhaDeItem("PLANTAS_TXT_NOMES_DEP", "Nomes das plantas", "Nomes das plantas conforme o regulamento", dep,
                            "OUTROS", "false", "txt"),
                    linhaDeItem("PLANTAS_CARTOGRAMA_DEP", "Cartograma das folhas", "Cartograma das folhas", dep, "OUTROS",
                            "false", "pdf,jpg,png"));
        });
    }

    @Test
    void deveTerTodosOsItensAtivosDoSeedDentroDaTabelaDeExtensoesDaEf() throws Exception {
        comBaseMigrada(ligacao -> {
            List<DocumentoConfig> itens = new ArrayList<>();
            try (Statement sql = ligacao.createStatement(); ResultSet linha = sql.executeQuery(
                    "SELECT codigo, categoria, tipo_elemento, formato, extensoes_aceites::text, ordem "
                            + "FROM documento_config WHERE ativo")) {
                while (linha.next()) {
                    itens.add(new DocumentoConfig(null, linha.getString(1), linha.getString(1), linha.getString(1),
                            CategoriaDocumento.valueOf(linha.getString(2)), TipoElemento.valueOf(linha.getString(3)),
                            FormatoDocumento.valueOf(linha.getString(4)), linha.getString(5), linha.getInt(6)));
                }
            }

            assertThat(itens).isNotEmpty().allSatisfy(item ->
                    assertThatCode(() -> RegrasExtensaoDocumento.validarConfig(item))
                            .as(item.getCodigo()).doesNotThrowAnyException());
        });
    }

    @Test
    void deveReporAsExtensoesDasPlantasAoReverterOSoZip() throws Exception {
        comBaseMigrada(ligacao -> {
            String extensoesDasPlantas = "SELECT DISTINCT categoria || ':' "
                    + "|| array_to_string(ARRAY(SELECT jsonb_array_elements_text(extensoes_aceites)), ',') "
                    + "FROM documento_config WHERE tipo_elemento = 'PECA_GRAFICA' ORDER BY 1";
            assertThat(linhas(ligacao, extensoesDasPlantas)).containsExactly("DEPOSITO:zip", "PUBLICACAO:zip");

            reverter(ligacao, 4);
            ligacao.setAutoCommit(true);

            assertThat(linhas(ligacao, extensoesDasPlantas))
                    .containsExactly("DEPOSITO:tif,tiff,tfw", "PUBLICACAO:gpkg,zip");
        });
    }

    @Test
    void deveExigirNaAberturaENaPublicacaoSoOsItensDePublicacaoDoTextoDoAtoEDaDeliberacao() throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || ':' "
                    + "|| string_agg(codigo, ',' ORDER BY ordem) FROM documento_config WHERE obrigatorio "
                    + "AND instrumento_procedimento_config_id IN (210, 211) GROUP BY instrumento_procedimento_config_id "
                    + "ORDER BY instrumento_procedimento_config_id")).containsExactly(
                    "210:DELIBERACAO_PUB,TEXTO_ATO_PUB,PLANTA_LOCALIZACAO_PUB", "211:TEXTO_ATO_PUB,DELIBERACAO_PUB");
            assertThat(linhas(ligacao, "SELECT tipo_elemento || ':' || string_agg(codigo, ',' ORDER BY ordem) "
                    + "FROM documento_config WHERE obrigatorio AND instrumento_procedimento_config_id = "
                    + PROCEDIMENTO_ABERTURA_DISCUSSAO_PUBLICA_ALTERACAO_PDM + " GROUP BY tipo_elemento ORDER BY tipo_elemento"))
                    .containsExactly("PECA_ESCRITA:DELIBERACAO_PUB,TEXTO_ATO_PUB", "PECA_GRAFICA:PLANTA_LOCALIZACAO_PUB");
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE obrigatorio "
                    + "AND categoria = 'DEPOSITO' AND instrumento_procedimento_config_id IN (210, 211)")).isEqualTo("0");
        });
    }

    @Test
    void deveAceitarSoAsExtensoesDeCadaCaixaNosNovosProcedimentos() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE categoria = 'DEPOSITO' "
                    + "AND tipo_elemento <> 'PECA_GRAFICA' AND instrumento_procedimento_config_id IN (210, 211) "
                    + "AND extensoes_aceites @> '\"docx\"'::jsonb")).isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE codigo = 'PLANTA_ORDENAMENTO_DEP' "
                    + "AND instrumento_procedimento_config_id = " + PROCEDIMENTO_PUBLICACAO_ALTERACAO_PDM
                    + " AND extensoes_aceites = '[\"zip\"]'::jsonb")).isEqualTo("1");
        });
    }

    @Test
    void deveTerOsDemaisElementosDoPlanoComoOutrosSoNaPublicacao() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || ':' || codigo "
                    + "FROM documento_config WHERE tipo_elemento = 'OUTROS' "
                    + "AND instrumento_procedimento_config_id IN (210, 211)")).containsExactlyInAnyOrder("211:DEMAIS_ELEMENTOS_DEP", "211:PLANTAS_TXT_NOMES_DEP", "211:PLANTAS_CARTOGRAMA_DEP");
        });
    }

    @Test
    void deveMarcarAInformacaoBaseNaDeliberacaoENoTextoDoAtoDosNovosProcedimentos() throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || '|' || codigo || '|' "
                    + "|| elemento_informacao_base FROM documento_config WHERE elemento_informacao_base IS NOT NULL "
                    + "AND instrumento_procedimento_config_id IN (210, 211) "
                    + "ORDER BY instrumento_procedimento_config_id, ordem")).containsExactly(
                    "210|DELIBERACAO_PUB|DELIBERACAO", "210|DELIBERACAO_DEP|DELIBERACAO",
                    "210|TEXTO_ATO_PUB|TEXTO_ATO", "210|TEXTO_ATO_DEP|TEXTO_ATO",
                    "211|TEXTO_ATO_PUB|TEXTO_ATO", "211|TEXTO_ATO_DEP|TEXTO_ATO",
                    "211|DELIBERACAO_PUB|DELIBERACAO", "211|DELIBERACAO_DEP|DELIBERACAO");
        });
    }

    @Test
    void deveReverterOSeedDaDiscussaoPublicaEDaPublicacaoSemTocarNaDecisaoDeInicio() throws Exception {
        String changeset = "2026-11-02-1-seed-documento-config-discussao-publica-publicacao-alteracao-pdm";
        String registo = "SELECT count(*) FROM databasechangelog WHERE id = '" + changeset + "'";
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, registo)).isEqualTo("1");
            assertThat(contar(ligacao, "documento_config")).isEqualTo(1783 + ITENS_DOS_SEEDS_DOCUMENTAIS_DO_BUILD);

            reverter(ligacao, 24);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, registo)).isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE instrumento_procedimento_config_id IN (210, 211)"))
                    .isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE instrumento_procedimento_config_id = "
                    + PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PDM)).isEqualTo("6");

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE instrumento_procedimento_config_id IN (210, 211)"))
                    .isEqualTo("29");
        });
    }

    @Test
    void deveSemearOsItensDocumentaisDaElaboracaoDaRevisaoEDasVariantesDaAlteracaoComOsTotaisPorProcedimento()
            throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || ':' || count(*) FROM documento_config "
                    + "WHERE " + FORA_DA_ALTERACAO_209_A_211 + " GROUP BY instrumento_procedimento_config_id "
                    + "ORDER BY instrumento_procedimento_config_id")).containsExactly("201:10", "202:4", "203:2", "204:31",
                    "205:10", "206:4", "207:2", "208:31", "214:4", "215:17", "218:14", "219:13", "220:4");
            assertThat(contar(ligacao, "documento_config")).isEqualTo(193);
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config "
                    + "WHERE instrumento_procedimento_config_id IN (216, 217)")).isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config "
                    + "WHERE " + FORA_DA_ALTERACAO_209_A_211 + " AND " + FORA_DAS_PLANTAS_DA_DECISAO_DE_ELABORAR
                    + " AND NOT ativo")).isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(*) FROM (SELECT 1 FROM documento_config WHERE pagina = 'DOCUMENTOS' GROUP BY "
                    + "instrumento_procedimento_config_id HAVING min(ordem) <> 1 OR max(ordem) <> count(*) "
                    + "OR count(DISTINCT ordem) <> count(*)) irregulares")).isEqualTo("0");
        });
    }

    private List<String> codigosPorOrdem(Connection ligacao, int procedimento) throws SQLException {
        return linhas(ligacao, "SELECT codigo FROM documento_config WHERE instrumento_procedimento_config_id = "
                + procedimento + " ORDER BY ordem");
    }

    @Test
    void deveOrdenarOsItensDaDecisaoDeInicioDaPublicacaoDoPlanoEDaCorrecaoMaterialComPublicacaoAntesDeDeposito()
            throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            assertThat(codigosPorOrdem(ligacao, 201)).containsExactly("TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "DELIBERACAO_PUB",
                    "DELIBERACAO_DEP", "PLANTA_AREA_INTERVENCAO_PUB", "PLANTA_AREA_INTERVENCAO_DEP",
                    "PLANTAS_ZIP_ORDENAMENTO_DEP", "PLANTAS_ZIP_CONDICIONANTES_DEP", "PLANTAS_TXT_NOMES_DEP",
                    "PLANTAS_CARTOGRAMA_DEP");
            assertThat(codigosPorOrdem(ligacao, 204)).containsExactly("TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "DELIBERACAO_PUB",
                    "DELIBERACAO_DEP", "REGULAMENTO_PUB", "REGULAMENTO_DEP", "PLANTA_ORDENAMENTO_PUB",
                    "PLANTA_ORDENAMENTO_DEP", "PLANTA_CONDICIONANTES_PUB", "PLANTA_CONDICIONANTES_DEP",
                    "ESTUDOS_CARACTERIZACAO_DEP", "RELATORIO_PLANO_DEP", "RELATORIO_AMBIENTAL_DEP",
                    "PROGRAMA_EXECUCAO_DEP", "PLANTA_ENQUADRAMENTO_DEP", "PLANTA_SITUACAO_EXISTENTE_DEP",
                    "COMPROMISSOS_URBANISTICOS_DEP", "PLANTAS_INFRAESTRUTURAS_DEP", "ESTRUTURA_ECOLOGICA_DEP",
                    "CARTA_EDUCATIVA_DEP", "MAPA_RUIDO_DEP", "OUTRAS_PLANTAS_DEP", "RELATORIO_PONDERACAO_DEP",
                    "PARECERES_ATA_DEP", "DELIBERACAO_CM_DEP", "PARECER_CA_DEP", "PARECER_CCDR_DEP",
                    "PLANTAS_ZIP_ORDENAMENTO_DEP", "PLANTAS_ZIP_CONDICIONANTES_DEP", "PLANTAS_TXT_NOMES_DEP",
                    "PLANTAS_CARTOGRAMA_DEP");
            assertThat(codigosPorOrdem(ligacao, 219)).containsExactly("TEXTO_ATO_PUB", "TEXTO_ATO_DEP",
                    "ALTERACOES_REGULAMENTO_PUB", "ALTERACOES_REGULAMENTO_DEP", "REGULAMENTO_INTEGRAL_PUB",
                    "REGULAMENTO_INTEGRAL_DEP", "PLANTA_ORDENAMENTO_PUB", "PLANTA_ORDENAMENTO_DEP",
                    "PLANTA_CONDICIONANTES_PUB", "PLANTA_CONDICIONANTES_DEP", "RELATORIO_FUNDAMENTACAO_DEP",
                    "DEMAIS_ELEMENTOS_DEP", "DECLARACAO_CM_DEP");
        });
    }

    @Test
    void deveExigirSoAsPecasDePublicacaoDosDocumentosMarcadosComoObrigatoriosNaLista() throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            String textoEDeliberacao = "TEXTO_ATO_PUB,DELIBERACAO_PUB";
            String publicacaoDoPlano = textoEDeliberacao + ",REGULAMENTO_PUB,PLANTA_ORDENAMENTO_PUB,PLANTA_CONDICIONANTES_PUB";
            assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || ':' "
                    + "|| string_agg(codigo, ',' ORDER BY ordem) FROM documento_config WHERE obrigatorio "
                    + "AND " + FORA_DA_ALTERACAO_209_A_211 + " GROUP BY instrumento_procedimento_config_id "
                    + "ORDER BY instrumento_procedimento_config_id")).containsExactly(
                    "201:" + textoEDeliberacao, "202:TEXTO_ATO_PUB,DESPACHO_CCDR_PUB", "203:TEXTO_ATO_PUB",
                    "204:" + publicacaoDoPlano, "205:" + textoEDeliberacao, "206:TEXTO_ATO_PUB,DESPACHO_CCDR_PUB",
                    "207:TEXTO_ATO_PUB", "208:" + publicacaoDoPlano,
                    "214:" + textoEDeliberacao, "215:" + textoEDeliberacao,
                    "218:TEXTO_ATO_PUB", "219:TEXTO_ATO_PUB", "220:" + textoEDeliberacao);
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE obrigatorio "
                    + "AND categoria = 'DEPOSITO'")).isEqualTo("0");
        });
    }

    @Test
    void deveMarcarAInformacaoBaseSoNaDecisaoDeInicioNaDiscussaoPublicaENaPublicacaoDaElaboracaoEDaRevisao()
            throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            List<String> esperado = new ArrayList<>();
            for (int procedimento : new int[] {201, 203, 204, 205, 207, 208}) {
                esperado.add(procedimento + "|TEXTO_ATO_PUB|TEXTO_ATO");
                esperado.add(procedimento + "|TEXTO_ATO_DEP|TEXTO_ATO");
                if (procedimento != 203 && procedimento != 207) {
                    esperado.add(procedimento + "|DELIBERACAO_PUB|DELIBERACAO");
                    esperado.add(procedimento + "|DELIBERACAO_DEP|DELIBERACAO");
                }
            }
            assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || '|' || codigo || '|' "
                    + "|| elemento_informacao_base FROM documento_config WHERE elemento_informacao_base IS NOT NULL "
                    + "AND " + FORA_DA_ALTERACAO_209_A_211 + " ORDER BY instrumento_procedimento_config_id, ordem"))
                    .containsExactlyElementsOf(esperado);
            // Os separadores da planta com área de intervenção e das Plantas não têm Informação Base.
            assertThat(linhas(ligacao, "SELECT DISTINCT separador || '|' || elemento_informacao_base FROM documento_config "
                    + "WHERE instrumento_procedimento_config_id = 201 AND elemento_informacao_base IS NOT NULL"))
                    .containsExactlyInAnyOrder("Texto do Ato|TEXTO_ATO", "Deliberação|DELIBERACAO");
        });
    }

    @Test
    void deveAceitarPorCaixaETipoDeElementoSoAsExtensoesEOFormatoDaTabelaDaEf() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT DISTINCT categoria || ':' || tipo_elemento || ':' || formato || ':' "
                    + "|| array_to_string(ARRAY(SELECT jsonb_array_elements_text(extensoes_aceites)), ',') "
                    + "FROM documento_config WHERE " + FORA_DA_ALTERACAO_209_A_211)).containsExactlyInAnyOrder(
                    "PUBLICACAO:PECA_ESCRITA:EDITAVEL:" + EXTENSOES_PUBLICACAO_ESCRITA,
                    "PUBLICACAO:PECA_GRAFICA:EDITAVEL:zip",
                    "DEPOSITO:PECA_ESCRITA:NAO_EDITAVEL:" + EXTENSOES_DEPOSITO_ESCRITA,
                    "DEPOSITO:OUTROS:NAO_EDITAVEL:" + EXTENSOES_DEPOSITO_ESCRITA,
                    "DEPOSITO:PECA_GRAFICA:NAO_EDITAVEL:zip",
                    // O TXT dos nomes das plantas, na página das Plantas (EF v1.5 §3.1.2).
                    "DEPOSITO:OUTROS:NAO_EDITAVEL:txt",
                    // O Cartograma de publicação, um esquema das folhas em pdf, jpg ou png como o da página das Plantas.
                    "PUBLICACAO:OUTROS:EDITAVEL:" + EXTENSOES_DEPOSITO_ESCRITA);
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE " + FORA_DA_ALTERACAO_209_A_211
                    + " AND ((categoria = 'PUBLICACAO') <> (codigo LIKE '%\\_PUB' ESCAPE '\\') "
                    + "OR (categoria = 'DEPOSITO') <> (codigo LIKE '%\\_DEP' ESCAPE '\\'))")).isEqualTo("0");
        });
    }

    @Test
    void deveCopiarOsItensDaElaboracaoParaARevisaoMudandoSoOsNomesDaDeliberacaoEDaDeliberacaoDaCm() throws Exception {
        comBaseMigrada(ligacao -> {
            String semNome = "SELECT codigo || '|' || separador || '|' || categoria || '|' || tipo_elemento || '|' || formato "
                    + "|| '|' || obrigatorio::text || '|' || extensoes_aceites::text || '|' "
                    + "|| coalesce(elemento_informacao_base, '-') FROM documento_config "
                    + "WHERE instrumento_procedimento_config_id = ";
            for (int procedimento = 201; procedimento <= 204; procedimento++) {
                assertThat(linhas(ligacao, semNome + (procedimento + 4) + " ORDER BY ordem, codigo"))
                        .as("procedimento %d", procedimento + 4)
                        .isEqualTo(linhas(ligacao, semNome + procedimento + " ORDER BY ordem, codigo"));
            }
            assertThat(linhas(ligacao, "SELECT a.instrumento_procedimento_config_id || ':' || a.codigo "
                    + "FROM documento_config a JOIN documento_config b ON b.codigo = a.codigo "
                    + "AND b.instrumento_procedimento_config_id = a.instrumento_procedimento_config_id + 4 "
                    + "WHERE a.instrumento_procedimento_config_id BETWEEN 201 AND 204 AND a.nome <> b.nome "
                    + "ORDER BY a.instrumento_procedimento_config_id, a.ordem"))
                    .containsExactly("201:DELIBERACAO_PUB", "201:DELIBERACAO_DEP", "204:DELIBERACAO_CM_DEP");
            assertThat(texto(ligacao, "SELECT nome FROM documento_config "
                    + "WHERE instrumento_procedimento_config_id = 205 AND codigo = 'DELIBERACAO_PUB'"))
                    .isEqualTo("Deliberação do início de revisão de plano territorial");
            assertThat(texto(ligacao, "SELECT nome FROM documento_config "
                    + "WHERE instrumento_procedimento_config_id = 208 AND codigo = 'DELIBERACAO_CM_DEP'"))
                    .isEqualTo("Deliberação da Câmara Municipal (CM) que determina a revisão do plano");
        });
    }

    @Test
    void deveGuardarATextoIntegralDaListaNaDescricaoSoQuandoONomeFoiEncurtado() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || ':' || codigo || '|' || descricao "
                    + "FROM documento_config WHERE descricao IS NOT NULL AND " + FORA_DA_ALTERACAO_209_A_211
                    + " ORDER BY instrumento_procedimento_config_id")).containsExactly(
                    "204:ESTUDOS_CARACTERIZACAO_DEP|Estudos de caraterização económica, social, biofísica e da estrutura "
                            + "fundiária do território municipal",
                    "208:ESTUDOS_CARACTERIZACAO_DEP|Estudos de caraterização económica, social, biofísica e da estrutura "
                            + "fundiária do território municipal");
        });
    }

    @Test
    void deveReverterSoAsLinhasDoSeedDosProcedimentosDePdmEDeixarAsDo209Ao211EAsDeProvaDeNotificacao() throws Exception {
        String changeset = "2026-11-09-1-seed-documento-config-procedimentos-pdm";
        String registo = "SELECT count(*) FROM databasechangelog WHERE id = '" + changeset + "'";
        comBaseMigrada(ligacao -> {
            String prova = "INSERT INTO documento_config (instrumento_procedimento_config_id, codigo, separador, nome, "
                    + "categoria, tipo_elemento, formato, obrigatorio, extensoes_aceites, ordem, ativo) "
                    + "VALUES (219, '%s', 'Prova de notificação', 'Prova de notificação', 'DEPOSITO', 'PECA_ESCRITA', "
                    + "'NAO_EDITAVEL', true, '[\"pdf\"]', %d, true)";
            // Sem a coluna dos independentes: as provas entram como criadas em backoffice. A cadeia também reverte a
            // lista dos programas, que vem depois.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL);
            ligacao.setAutoCommit(true);
            executar(ligacao, prova.formatted("PROVA_NOTIFICACAO_AM_DEP", 98));
            executar(ligacao, prova.formatted("PROVA_NOTIFICACAO_CCDR_DEP", 99));
            assertThat(texto(ligacao, registo)).isEqualTo("1");
            assertThat(contar(ligacao, "documento_config")).isEqualTo(1783 - ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + 2);

            // O seed vem antes da planta, da auditoria das descargas, da NUT II e das Plantas e Metadados e da localização.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DOS_LIMITES_DOCUMENTAIS + CHANGESETS_DOS_LINKS_PUBLICOS + CHANGESETS_DA_PLANTA_POR_FOLHA + CHANGESETS_DAS_LISTAS_DA_ALTERACAO + CHANGESETS_DO_MANIFESTO_SNIT + CHANGESETS_DO_NUMERO_DEPOSITO + CHANGESETS_DO_SNIT_CODIGO + 1 + CHANGESETS_DA_PLANTA + CHANGESETS_DA_DESCARGA + CHANGESETS_DA_NUT_II_E_PLANTAS
                    + CHANGESETS_DA_LOCALIZACAO);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, registo)).isEqualTo("0");
            assertThat(contar(ligacao, "documento_config")).isEqualTo(33);
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config "
                    + "WHERE instrumento_procedimento_config_id IN (209, 210, 211)")).isEqualTo("31");
            assertThat(codigosPorOrdem(ligacao, 219)).containsExactly("PROVA_NOTIFICACAO_AM_DEP",
                    "PROVA_NOTIFICACAO_CCDR_DEP");

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            // Sem seed das provas (a lista da DGT prevalece, FR-193): ficam só as duas do backoffice no 219.
            assertThat(contar(ligacao, "documento_config")).isEqualTo(1783 + ITENS_DOS_SEEDS_DOCUMENTAIS_DO_BUILD + 2);
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config "
                    + "WHERE instrumento_procedimento_config_id = 219")).isEqualTo("15");
        });
    }

    @Test
    void deveReverterOSeedDosItensDocumentaisSemTocarNoRestoDoSeed() throws Exception {
        comBaseMigrada(ligacao -> {
            int procedimentos = contar(ligacao, "instrumento_procedimento_config");
            int tiposDeAto = contar(ligacao, "tipo_ato");
            int permissoes = contar(ligacao, "permissao_acao");

            reverter(ligacao, 43);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "documento_config")).isZero();
            assertThat(contar(ligacao, "instrumento_procedimento_config"))
                    .isEqualTo(procedimentos - PROCEDIMENTOS_DOS_PLANOS_INTERMUNICIPAIS - PROCEDIMENTOS_DOS_PROGRAMAS
                            - PROCEDIMENTOS_DO_SGIFR - PROCEDIMENTOS_DE_MP_NP_E_SUSPENSAO - PROCEDIMENTOS_DAS_DINAMICAS_TRANSVERSAIS
                            - (PROCEDIMENTOS_DOS_ATOS_PONTUAIS - ATOS_PONTUAIS_DA_RERAE) - PROCEDIMENTOS_DAS_RESTANTES_SERVIDOES
                            + PROCEDIMENTOS_DA_RERAE - PROCEDIMENTOS_DA_REN);
            assertThat(contar(ligacao, "tipo_ato")).isEqualTo(tiposDeAto);
            assertThat(contar(ligacao, "permissao_acao")).isEqualTo(permissoes - 2 * PROCEDIMENTOS_DOS_PLANOS_INTERMUNICIPAIS
                    - PERMISSOES_DOS_PROGRAMAS - PERMISSOES_DO_SGIFR - PERMISSOES_DE_MP_NP_E_SUSPENSAO
                    - PERMISSOES_DAS_DINAMICAS_TRANSVERSAIS - PERMISSOES_DA_MATRIZ_NOS_PROGRAMAS
                    - (PERMISSOES_DOS_ATOS_PONTUAIS - PERMISSOES_DOS_ATOS_PONTUAIS_DA_RERAE)
                    - PERMISSOES_DA_PCM_NA_PRIMEIRA_SERIE - PERMISSOES_DAS_RESTANTES_SERVIDOES
                    + 2 * PROCEDIMENTOS_DA_RERAE - PERMISSOES_DA_REN);

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "documento_config")).isEqualTo(1783 + ITENS_DOS_SEEDS_DOCUMENTAIS_DO_BUILD);
        });
    }

    @Test
    void deveMigrarARevalidacaoDeItensDeixandoOsDocumentosExistentesPorValidar() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 42);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo_documento", "estado_validacao")).isFalse();
            assertThat(tabelaExiste(ligacao, "processo_informacao_base")).isFalse();
            inserirProcessoComDocumento(ligacao);

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT estado_validacao FROM processo_documento WHERE nome_ficheiro = 'texto.pdf'"))
                    .isEqualTo("POR_VALIDAR");
            assertThat(tabelaExiste(ligacao, "processo_informacao_base")).isTrue();
            for (String coluna : new String[] {"id", "processo_id", "elemento", "estado_validacao", "criado_em", "atualizado_em"}) {
                assertThat(colunaExiste(ligacao, "processo_informacao_base", coluna)).isTrue();
            }
            assertThat(colunaExiste(ligacao, "processo_informacao_base", "rejeicao_motivo_id")).isFalse();
            assertThat(definicaoDoIndice(ligacao, "idx_processo_informacao_base_processo")).contains("processo_id");
        });
    }

    @Test
    void deveRecusarElementoRepetidoEEstadosForaDoDominioNaValidacaoDeItens() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);
            String insercao = "INSERT INTO processo_informacao_base (id, processo_id, elemento, estado_validacao) "
                    + "VALUES (gen_random_uuid(), '" + PROCESSO_ID + "', '%s', '%s')";
            executar(ligacao, insercao.formatted("DELIBERACAO", "POR_VALIDAR"));

            assertThatThrownBy(() -> executar(ligacao, insercao.formatted("DELIBERACAO", "VALIDADO")))
                    .hasMessageContaining("uq_processo_informacao_base_processo_elemento");
            assertThatThrownBy(() -> executar(ligacao, insercao.formatted("OUTRO", "POR_VALIDAR")))
                    .hasMessageContaining("ck_processo_informacao_base_elemento");
            assertThatThrownBy(() -> executar(ligacao, insercao.formatted("TEXTO_ATO", "ACEITE")))
                    .hasMessageContaining("ck_processo_informacao_base_estado_validacao");
            assertThatThrownBy(() -> executar(ligacao,
                    "UPDATE processo_documento SET estado_validacao = 'ACEITE'"))
                    .hasMessageContaining("ck_processo_documento_estado_validacao");
        });
    }

    @Test
    void deveSemearAPaginaDasPlantasNosProcedimentosFinaisComONomeDosItensDePublicacaoEReverter() throws Exception {
        String paginaDasPlantas = "documento_config WHERE pagina = 'PLANTAS' AND instrumento_procedimento_config_id IN ";
        comBaseMigrada(ligacao -> {
            assertThat(contar(ligacao, paginaDasPlantas + "(204, 208, 211)")).isEqualTo(12);
            for (int procedimento : new int[] {204, 208, 211}) {
                for (String planta : new String[] {"ORDENAMENTO", "CONDICIONANTES"}) {
                    String doProcedimento = "SELECT nome FROM documento_config WHERE instrumento_procedimento_config_id = "
                            + procedimento + " AND codigo = ";
                    assertThat(texto(ligacao, doProcedimento + "'PLANTAS_ZIP_" + planta + "_DEP'"))
                            .isEqualTo(texto(ligacao, doProcedimento + "'PLANTA_" + planta + "_PUB'"));
                }
            }

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, paginaDasPlantas + "(204, 208, 211)")).isZero();
            assertThat(contar(ligacao, paginaDasPlantas + "(201, 205)")).isEqualTo(8);
        });
    }

    @Test
    void deveAceitarSubstituidoNaValidacaoDosDocumentosEReporAConstraintNoRollback() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);
            executar(ligacao, "UPDATE processo_documento SET estado_validacao = 'SUBSTITUIDO'");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE processo_documento SET estado_validacao = 'ACEITE'"))
                    .hasMessageContaining("ck_processo_documento_estado_validacao");

            executar(ligacao, "UPDATE processo_documento SET estado_validacao = 'REJEITADO'");
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO);
            ligacao.setAutoCommit(true);

            assertThatThrownBy(() -> executar(ligacao, "UPDATE processo_documento SET estado_validacao = 'SUBSTITUIDO'"))
                    .hasMessageContaining("ck_processo_documento_estado_validacao");
            executar(ligacao, "UPDATE processo_documento SET estado_validacao = 'REJEITADO'");
        });
    }

    @Test
    void deveReverterAValidacaoDeItensSemTocarNoModeloDocumental() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 42);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "processo_documento", "estado_validacao")).isFalse();
            assertThat(tabelaExiste(ligacao, "processo_informacao_base")).isFalse();
            assertThat(colunaExiste(ligacao, "processo_documento", "categoria")).isTrue();
            assertThat(contar(ligacao, "documento_config")).isEqualTo(6);
        });
    }

    private static final String COLUNAS_PLANTA = "SELECT column_name || '|' || data_type || '|' "
            + "|| coalesce(character_maximum_length::text, '-') || '|' || is_nullable || '|' || coalesce(column_default, '-') "
            + "FROM information_schema.columns WHERE table_schema = current_schema() "
            + "AND table_name = 'planta' ORDER BY ordinal_position";

    @Test
    void deveCriarAPlantaComAsColunasAsConstraintsEOsIndices() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, COLUNAS_PLANTA)).containsExactly(
                    "id|uuid|-|NO|-",
                    "processo_id|uuid|-|NO|-",
                    "processo_documento_id|uuid|-|NO|-",
                    "nome_regulamento|character varying|500|YES|-",
                    "numero_folha|character varying|20|YES|-",
                    "nome_tiff|character varying|300|YES|-",
                    "nome_zip|character varying|300|YES|-",
                    "link_jpeg|text|-|YES|-",
                    "em_vigor|boolean|-|NO|false",
                    "criado_em|timestamp with time zone|-|NO|-",
                    "atualizado_em|timestamp with time zone|-|NO|-",
                    "atualizado_por|uuid|-|YES|-",
                    "metadados_motu_id|uuid|-|YES|-",
                    "linha_txt|integer|-|YES|-");
            assertThat(texto(ligacao, "SELECT confrelid::regclass::text FROM pg_constraint "
                    + "WHERE conname = 'fk_planta_processo'")).isEqualTo("processo");
            assertThat(texto(ligacao, "SELECT confrelid::regclass::text || '|' || confdeltype::text FROM pg_constraint "
                    + "WHERE conname = 'fk_planta_processo_documento'")).isEqualTo("processo_documento|a");
            assertThat(texto(ligacao, "SELECT confrelid::regclass::text FROM pg_constraint "
                    + "WHERE conname = 'fk_planta_atualizado_por'")).isEqualTo("utilizador");
            assertThat(definicaoDoIndice(ligacao, "idx_planta_processo_id")).contains("(processo_id)");
            assertThat(definicaoDoIndice(ligacao, "idx_planta_em_vigor")).contains("(em_vigor)");

            inserirProcessoComDocumento(ligacao);
            executar(ligacao, INSERCAO_PLANTA.formatted(DOCUMENTO_DA_PLANTA, "NULL"));
            assertThat(texto(ligacao, "SELECT em_vigor::text FROM planta")).isEqualTo("false");
            executar(ligacao, INSERCAO_PLANTA.formatted(DOCUMENTO_DA_PLANTA, "NULL"));
            assertThat(contar(ligacao, "planta")).isEqualTo(2);
            assertThat(definicaoDoIndice(ligacao, "idx_planta_processo_documento_id")).contains("(processo_documento_id)");
            assertThatThrownBy(() -> executar(ligacao, "DELETE FROM processo_documento"))
                    .hasMessageContaining("fk_planta_processo_documento");
            executar(ligacao, "DELETE FROM planta");
            assertThatThrownBy(() -> executar(ligacao,
                    INSERCAO_PLANTA.formatted(DOCUMENTO_DA_PLANTA, "'" + UUID_SEM_PROCESSO + "'")))
                    .hasMessageContaining("fk_planta_atualizado_por");
        });
    }

    @Test
    void deveLigarALocalizacaoEOMotivoSoNaDecisaoDeInicioDaAlteracaoDoPdmEReverterAsColunas() throws Exception {
        comBaseMigrada(ligacao -> {
            // Sem a localização das Dinâmicas municipais, que vem depois e liga outros Procedimentos.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || td.codigo || ':' || tp.codigo || ':' || ipc.ordem || ':' "
                    + "|| ipc.pede_localizacao || ':' || ipc.pede_motivo_alteracao FROM instrumento_procedimento_config ipc "
                    + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                    + "WHERE ipc.pede_localizacao <> 'NAO' OR ipc.pede_motivo_alteracao <> 'NAO' ORDER BY 1"))
                    .containsExactly("PDM:ALTERACAO:DECISAO_INICIO:1:OBRIGATORIA:COM_PERGUNTA");
            assertThat(contar(ligacao, "lista_valores WHERE tipo = 'TOPONIMO'")).isZero();
            assertThatThrownBy(() -> executar(ligacao,
                    "UPDATE instrumento_procedimento_config SET pede_localizacao = 'SEMPRE' WHERE id = 209"))
                    .hasMessageContaining("ck_instrumento_procedimento_config_pede_localizacao");
            assertThatThrownBy(() -> executar(ligacao,
                    "UPDATE instrumento_procedimento_config SET pede_motivo_alteracao = 'SIM' WHERE id = 209"))
                    .hasMessageContaining("ck_instrumento_procedimento_config_pede_motivo_alteracao");

            reverterOsUltimos(ligacao, CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DOS_LIMITES_DOCUMENTAIS + CHANGESETS_DOS_LINKS_PUBLICOS + CHANGESETS_DA_PLANTA_POR_FOLHA + CHANGESETS_DAS_LISTAS_DA_ALTERACAO + CHANGESETS_DO_MANIFESTO_SNIT + CHANGESETS_DO_NUMERO_DEPOSITO + CHANGESETS_DO_SNIT_CODIGO + 1);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "instrumento_procedimento_config WHERE pede_localizacao <> 'NAO' "
                    + "OR pede_motivo_alteracao <> 'NAO'")).isZero();

            reverterOsUltimos(ligacao, 1);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "instrumento_procedimento_config", "pede_localizacao")).isFalse();
            assertThat(colunaExiste(ligacao, "instrumento_procedimento_config", "pede_motivo_alteracao")).isFalse();
        });
    }

    @Test
    void deveReverterAPlantaSemTocarNosDocumentos() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DOS_LIMITES_DOCUMENTAIS + CHANGESETS_DOS_LINKS_PUBLICOS + CHANGESETS_DA_PLANTA_POR_FOLHA + CHANGESETS_DAS_LISTAS_DA_ALTERACAO + CHANGESETS_DO_MANIFESTO_SNIT + CHANGESETS_DO_NUMERO_DEPOSITO + CHANGESETS_DO_SNIT_CODIGO + CHANGESETS_DA_LOCALIZACAO + CHANGESETS_DA_NUT_II_E_PLANTAS + CHANGESETS_DA_DESCARGA
                    + CHANGESETS_DA_PLANTA);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "planta")).isFalse();
            assertThat(tabelaExiste(ligacao, "processo_documento")).isTrue();
        });
    }

    private static final String COLUNAS_INCM_COMUNICACAO = "SELECT column_name || '|' || data_type || '|' || is_nullable "
            + "FROM information_schema.columns WHERE table_schema = current_schema() "
            + "AND table_name = 'incm_comunicacao' ORDER BY ordinal_position";

    @Test
    void deveCriarAIncmComunicacaoComAsColunasOsIndicesEADominios() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, COLUNAS_INCM_COMUNICACAO)).containsExactly(
                    "id|bigint|NO",
                    "processo_id|uuid|NO",
                    "operacao|character varying|NO",
                    "act_identifier|bigint|YES",
                    "submissao|smallint|NO",
                    "serie_dr|character varying|YES",
                    "pedido|jsonb|NO",
                    "resposta|jsonb|YES",
                    "http_status|integer|YES",
                    "is_success|boolean|YES",
                    "processing_status|character varying|YES",
                    "act_status|character varying|YES",
                    "falha_tecnica|character varying|YES",
                    "transicao_derivada|character varying|YES",
                    "correlation_id|character varying|NO",
                    "enviado_em|timestamp with time zone|NO",
                    "recebido_em|timestamp with time zone|YES");
            assertThat(texto(ligacao, "SELECT is_identity FROM information_schema.columns "
                    + "WHERE table_name = 'incm_comunicacao' AND column_name = 'id'")).isEqualTo("YES");
            assertThat(texto(ligacao, "SELECT confrelid::regclass::text FROM pg_constraint "
                    + "WHERE conname = 'fk_incm_comunicacao_processo'")).isEqualTo("processo");
            for (String coluna : new String[] {"processo_id", "act_identifier", "correlation_id", "enviado_em"}) {
                assertThat(definicaoDoIndice(ligacao, "idx_incm_comunicacao_" + coluna)).contains(coluna);
            }

            inserirProcessoComDocumento(ligacao);
            executar(ligacao, INSERCAO_INCM_COMUNICACAO.formatted("SUBMIT_NEW_ACT", "'SEGUNDA_C'", "'ERRO_HTTP'"));
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_INCM_COMUNICACAO.formatted("OUTRA", "NULL", "NULL")))
                    .hasMessageContaining("ck_incm_comunicacao_operacao");
            assertThatThrownBy(() -> executar(ligacao,
                    INSERCAO_INCM_COMUNICACAO.formatted("GET_ACT_DETAILS", "'SEGUNDA'", "NULL")))
                    .hasMessageContaining("ck_incm_comunicacao_serie_dr");
            assertThatThrownBy(() -> executar(ligacao,
                    INSERCAO_INCM_COMUNICACAO.formatted("GET_ACT_DETAILS", "NULL", "'OUTRA'")))
                    .hasMessageContaining("ck_incm_comunicacao_falha_tecnica");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_INCM_COMUNICACAO
                    .formatted("GET_ACT_DETAILS", "NULL", "NULL").replace(PROCESSO_ID, UUID_SEM_PROCESSO)))
                    .hasMessageContaining("fk_incm_comunicacao_processo");
        });
    }

    @Test
    void deveDeixarASaigtAppInserirELerMasNuncaAlterarNemApagarAIncmComunicacao() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);

            try (Connection app = DriverManager.getConnection(ligacao.getMetaData().getURL(), "saigt_app", "teste")) {
                executar(app, INSERCAO_INCM_COMUNICACAO.formatted("SUBMIT_NEW_ACT", "'PRIMEIRA'", "NULL"));
                assertThat(texto(app, "SELECT count(*) FROM incm_comunicacao")).isEqualTo("1");

                assertThatThrownBy(() -> executar(app, "UPDATE incm_comunicacao SET http_status = 200"))
                        .hasMessageContaining("permission denied");
                assertThatThrownBy(() -> executar(app, "DELETE FROM incm_comunicacao"))
                        .hasMessageContaining("permission denied");
            }
            assertThat(texto(ligacao, "SELECT count(*) || '|' || count(http_status) FROM incm_comunicacao"))
                    .isEqualTo("1|0");
        });
    }

    @Test
    void deveReverterAIncmComunicacaoSemTocarNaValidacaoDeItens() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 41);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "incm_comunicacao")).isFalse();
            assertThat(tabelaExiste(ligacao, "processo_informacao_base")).isTrue();
        });
    }

    private static final String COLUNAS_PROCESSO_REJEICAO = "SELECT column_name || '|' || data_type || '|' || is_nullable "
            + "FROM information_schema.columns WHERE table_schema = current_schema() "
            + "AND table_name = 'processo_rejeicao' ORDER BY ordinal_position";

    @Test
    void deveCriarAProcessoRejeicaoComAsColunasAsConstraintsEOIndice() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, COLUNAS_PROCESSO_REJEICAO)).containsExactly(
                    "id|bigint|NO",
                    "processo_id|uuid|NO",
                    "sequencia|integer|NO",
                    "origem|character varying|NO",
                    "motivo_texto|text|YES",
                    "notas_gerais|text|YES",
                    "criado_em|timestamp with time zone|NO",
                    "criado_por|uuid|YES");
            assertThat(texto(ligacao, "SELECT is_identity FROM information_schema.columns "
                    + "WHERE table_name = 'processo_rejeicao' AND column_name = 'id'")).isEqualTo("YES");
            assertThat(texto(ligacao, "SELECT confrelid::regclass::text FROM pg_constraint "
                    + "WHERE conname = 'fk_processo_rejeicao_processo'")).isEqualTo("processo");
            assertThat(texto(ligacao, "SELECT confrelid::regclass::text FROM pg_constraint "
                    + "WHERE conname = 'fk_processo_rejeicao_criado_por'")).isEqualTo("utilizador");
            assertThat(definicaoDoIndice(ligacao, "idx_processo_rejeicao_processo_id")).contains("processo_id");

            inserirProcessoComDocumento(ligacao);
            executar(ligacao, INSERCAO_PROCESSO_REJEICAO.formatted(1, "INCM", "'ImageURLList cannot be empty.'"));
            executar(ligacao, INSERCAO_PROCESSO_REJEICAO.formatted(2, "DGT", "NULL"));
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PROCESSO_REJEICAO.formatted(3, "INCM", "NULL")))
                    .hasMessageContaining("ck_processo_rejeicao_motivo_incm");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PROCESSO_REJEICAO.formatted(3, "PCM", "'x'")))
                    .hasMessageContaining("ck_processo_rejeicao_origem");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PROCESSO_REJEICAO.formatted(0, "DGT", "NULL")))
                    .hasMessageContaining("ck_processo_rejeicao_sequencia");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PROCESSO_REJEICAO.formatted(1, "DGT", "NULL")))
                    .hasMessageContaining("uq_processo_rejeicao_sequencia");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PROCESSO_REJEICAO.formatted(3, "DGT", "NULL")
                    .replace(PROCESSO_ID, UUID_SEM_PROCESSO)))
                    .hasMessageContaining("fk_processo_rejeicao_processo");
            assertThat(texto(ligacao, "SELECT string_agg(sequencia || ':' || origem, ',' ORDER BY sequencia) "
                    + "FROM processo_rejeicao")).isEqualTo("1:INCM,2:DGT");
        });
    }

    @Test
    void deveDeixarASaigtAppInserirELerMasNuncaAlterarNemApagarAProcessoRejeicao() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);

            try (Connection app = DriverManager.getConnection(ligacao.getMetaData().getURL(), "saigt_app", "teste")) {
                executar(app, INSERCAO_PROCESSO_REJEICAO.formatted(1, "INCM", "'Entidade inexistente.'"));
                assertThat(texto(app, "SELECT motivo_texto FROM processo_rejeicao")).isEqualTo("Entidade inexistente.");

                assertThatThrownBy(() -> executar(app, "UPDATE processo_rejeicao SET motivo_texto = 'outro'"))
                        .hasMessageContaining("permission denied");
                assertThatThrownBy(() -> executar(app, "DELETE FROM processo_rejeicao"))
                        .hasMessageContaining("permission denied");
            }
            assertThat(texto(ligacao, "SELECT count(*) || '|' || min(motivo_texto) FROM processo_rejeicao"))
                    .isEqualTo("1|Entidade inexistente.");
        });
    }

    @Test
    void deveReverterAProcessoRejeicaoSemTocarNaIncmComunicacao() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 12);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "processo_rejeicao")).isFalse();
            assertThat(tabelaExiste(ligacao, "incm_comunicacao")).isTrue();
            assertThat(tabelaExiste(ligacao, "processo")).isTrue();
        });
    }

    private static final String COLUNAS_PROCESSO_REJEICAO_ITEM = "SELECT column_name || '|' || data_type || '|' "
            + "|| is_nullable FROM information_schema.columns WHERE table_schema = current_schema() "
            + "AND table_name = 'processo_rejeicao_item' ORDER BY ordinal_position";

    @Test
    void deveCriarAProcessoRejeicaoItemComAsColunasAsFkAsConstraintsEOIndice() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, COLUNAS_PROCESSO_REJEICAO_ITEM)).containsExactly(
                    "id|bigint|NO",
                    "rejeicao_id|bigint|NO",
                    "processo_documento_id|uuid|YES",
                    "processo_informacao_base_id|uuid|YES",
                    "categoria_id|bigint|YES",
                    "motivo_id|bigint|YES",
                    "notas|text|NO");
            assertThat(texto(ligacao, "SELECT is_identity FROM information_schema.columns "
                    + "WHERE table_name = 'processo_rejeicao_item' AND column_name = 'id'")).isEqualTo("YES");
            assertThat(linhas(ligacao, "SELECT conname || '>' || confrelid::regclass::text || '|' || confdeltype::text "
                    + "FROM pg_constraint WHERE conrelid = 'processo_rejeicao_item'::regclass AND contype = 'f' "
                    + "ORDER BY conname")).containsExactly(
                    "fk_processo_rejeicao_item_categoria>lista_valores|a",
                    "fk_processo_rejeicao_item_documento>processo_documento|a",
                    "fk_processo_rejeicao_item_informacao_base>processo_informacao_base|a",
                    "fk_processo_rejeicao_item_motivo>lista_valores|a",
                    "fk_processo_rejeicao_item_rejeicao>processo_rejeicao|a");
            assertThat(definicaoDoIndice(ligacao, "idx_processo_rejeicao_item_rejeicao")).contains("rejeicao_id");

            prepararRecusaComAlvos(ligacao);
            executar(ligacao, INSERCAO_PROCESSO_REJEICAO_ITEM.formatted(DOCUMENTO_DA_RECUSA, "NULL", "'Ilegível.'"));
            executar(ligacao, INSERCAO_PROCESSO_REJEICAO_ITEM.formatted("NULL", INFORMACAO_BASE_DA_RECUSA, "'Sem data.'"));
            executar(ligacao, "INSERT INTO processo_rejeicao_item (rejeicao_id, processo_documento_id, notas) VALUES "
                    + "((SELECT min(id) FROM processo_rejeicao), " + DOCUMENTO_DA_RECUSA + ", 'Listas vazias.')");

            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PROCESSO_REJEICAO_ITEM.formatted(
                    DOCUMENTO_DA_RECUSA, INFORMACAO_BASE_DA_RECUSA, "'Os dois.'")))
                    .hasMessageContaining("ck_processo_rejeicao_item_alvo");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PROCESSO_REJEICAO_ITEM.formatted(
                    "NULL", "NULL", "'Nenhum.'")))
                    .hasMessageContaining("ck_processo_rejeicao_item_alvo");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PROCESSO_REJEICAO_ITEM.formatted(
                    DOCUMENTO_DA_RECUSA, "NULL", "'   '")))
                    .hasMessageContaining("ck_processo_rejeicao_item_notas");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PROCESSO_REJEICAO_ITEM.formatted(
                    DOCUMENTO_DA_RECUSA, "NULL", "NULL")))
                    .hasMessageContaining("notas");
            assertThat(texto(ligacao, "SELECT string_agg(notas, ',' ORDER BY id) FROM processo_rejeicao_item"))
                    .isEqualTo("Ilegível.,Sem data.,Listas vazias.");
        });
    }

    @Test
    void deveDeixarASaigtAppInserirMasNuncaAlterarNemApagarAProcessoRejeicaoItem() throws Exception {
        comBaseMigrada(ligacao -> {
            prepararRecusaComAlvos(ligacao);

            try (Connection app = DriverManager.getConnection(ligacao.getMetaData().getURL(), "saigt_app", "teste")) {
                executar(app, INSERCAO_PROCESSO_REJEICAO_ITEM.formatted(DOCUMENTO_DA_RECUSA, "NULL", "'Ilegível.'"));
                assertThat(texto(app, "SELECT notas FROM processo_rejeicao_item")).isEqualTo("Ilegível.");

                assertThatThrownBy(() -> executar(app, "UPDATE processo_rejeicao_item SET notas = 'outra'"))
                        .hasMessageContaining("permission denied");
                assertThatThrownBy(() -> executar(app, "DELETE FROM processo_rejeicao_item"))
                        .hasMessageContaining("permission denied");
            }
            assertThat(texto(ligacao, "SELECT has_table_privilege('saigt_app', 'processo_rejeicao_item', 'UPDATE')"))
                    .isEqualTo("f");
            assertThat(texto(ligacao, "SELECT count(*) || '|' || min(notas) FROM processo_rejeicao_item"))
                    .isEqualTo("1|Ilegível.");
        });
    }

    @Test
    void deveReverterAProcessoRejeicaoItemSemTocarNaProcessoRejeicao() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 8);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "processo_rejeicao_item")).isFalse();
            assertThat(tabelaExiste(ligacao, "processo_rejeicao")).isTrue();
            assertThat(tabelaExiste(ligacao, "lista_valores")).isTrue();
        });
    }

    private static final String COLUNAS_PROCESSO_VALIDACAO_REJEICAO = "SELECT column_name || '|' || data_type || '|' "
            + "|| is_nullable FROM information_schema.columns WHERE table_schema = current_schema() "
            + "AND table_name = 'processo_validacao_rejeicao' ORDER BY ordinal_position";

    private static final String INSERCAO_PROCESSO_VALIDACAO_REJEICAO = "INSERT INTO processo_validacao_rejeicao ("
            + "processo_id, processo_documento_id, processo_informacao_base_id, categoria_id, motivo_id, notas, "
            + "atualizado_por, atualizado_em) VALUES ('00000000-0000-0000-0000-000000000004', %s, %s, "
            + "(SELECT id FROM lista_valores WHERE tipo = 'CATEGORIA_REJEICAO_DGT'), NULL, %s, "
            + "'00000000-0000-0000-0000-000000000001', now())";

    @Test
    void deveCriarAProcessoValidacaoRejeicaoComAsColunasAsFkAsConstraintsEOsUnicos() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, COLUNAS_PROCESSO_VALIDACAO_REJEICAO)).containsExactly(
                    "id|bigint|NO",
                    "processo_id|uuid|NO",
                    "processo_documento_id|uuid|YES",
                    "processo_informacao_base_id|uuid|YES",
                    "categoria_id|bigint|YES",
                    "motivo_id|bigint|YES",
                    "notas|text|NO",
                    "atualizado_por|uuid|NO",
                    "atualizado_em|timestamp with time zone|NO");
            assertThat(linhas(ligacao, "SELECT conname || '>' || confrelid::regclass::text || '|' || confdeltype::text "
                    + "FROM pg_constraint WHERE conrelid = 'processo_validacao_rejeicao'::regclass AND contype = 'f' "
                    + "ORDER BY conname")).containsExactly(
                    "fk_processo_validacao_rejeicao_atualizado_por>utilizador|a",
                    "fk_processo_validacao_rejeicao_categoria>lista_valores|a",
                    "fk_processo_validacao_rejeicao_documento>processo_documento|a",
                    "fk_processo_validacao_rejeicao_informacao_base>processo_informacao_base|a",
                    "fk_processo_validacao_rejeicao_motivo>lista_valores|a",
                    "fk_processo_validacao_rejeicao_processo>processo|a");
            assertThat(linhas(ligacao, "SELECT conname FROM pg_constraint WHERE conrelid = "
                    + "'processo_validacao_rejeicao'::regclass AND contype IN ('u', 'c') ORDER BY conname")).containsExactly(
                    "ck_processo_validacao_rejeicao_alvo",
                    "ck_processo_validacao_rejeicao_notas",
                    "uq_processo_validacao_rejeicao_documento",
                    "uq_processo_validacao_rejeicao_informacao_base");
            assertThat(definicaoDoIndice(ligacao, "idx_processo_validacao_rejeicao_processo")).contains("processo_id");

            prepararRecusaComAlvos(ligacao);
            executar(ligacao, INSERCAO_PROCESSO_VALIDACAO_REJEICAO.formatted(DOCUMENTO_DA_RECUSA, "NULL", "'Ilegível.'"));
            executar(ligacao, INSERCAO_PROCESSO_VALIDACAO_REJEICAO.formatted("NULL", INFORMACAO_BASE_DA_RECUSA,
                    "'Sem data.'"));

            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PROCESSO_VALIDACAO_REJEICAO.formatted(
                    DOCUMENTO_DA_RECUSA, INFORMACAO_BASE_DA_RECUSA, "'Os dois.'")))
                    .hasMessageContaining("ck_processo_validacao_rejeicao_alvo");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PROCESSO_VALIDACAO_REJEICAO.formatted(
                    "NULL", "NULL", "'Nenhum.'")))
                    .hasMessageContaining("ck_processo_validacao_rejeicao_alvo");
            assertThatThrownBy(() -> executar(ligacao, "DELETE FROM processo_validacao_rejeicao; "
                    + INSERCAO_PROCESSO_VALIDACAO_REJEICAO.formatted(DOCUMENTO_DA_RECUSA, "NULL", "'   '")))
                    .hasMessageContaining("ck_processo_validacao_rejeicao_notas");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PROCESSO_VALIDACAO_REJEICAO.formatted(
                    DOCUMENTO_DA_RECUSA, "NULL", "'Outra.'")))
                    .hasMessageContaining("uq_processo_validacao_rejeicao_documento");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_PROCESSO_VALIDACAO_REJEICAO.formatted(
                    "NULL", INFORMACAO_BASE_DA_RECUSA, "'Outra.'")))
                    .hasMessageContaining("uq_processo_validacao_rejeicao_informacao_base");
        });
    }

    @Test
    void deveDeixarASaigtAppAlterarEApagarORascunhoDaValidacao() throws Exception {
        comBaseMigrada(ligacao -> {
            prepararRecusaComAlvos(ligacao);

            try (Connection app = DriverManager.getConnection(ligacao.getMetaData().getURL(), "saigt_app", "teste")) {
                executar(app, INSERCAO_PROCESSO_VALIDACAO_REJEICAO.formatted(DOCUMENTO_DA_RECUSA, "NULL", "'Ilegível.'"));
                executar(app, "UPDATE processo_validacao_rejeicao SET notas = 'Página 3 ilegível.'");
                assertThat(texto(app, "SELECT notas FROM processo_validacao_rejeicao")).isEqualTo("Página 3 ilegível.");
                executar(app, "DELETE FROM processo_validacao_rejeicao");
            }
            assertThat(contar(ligacao, "processo_validacao_rejeicao")).isZero();
        });
    }

    @Test
    void deveReverterAProcessoValidacaoRejeicaoSemTocarNaProcessoRejeicaoItem() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 7);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "processo_validacao_rejeicao")).isFalse();
            assertThat(tabelaExiste(ligacao, "processo_rejeicao_item")).isTrue();
            assertThat(tabelaExiste(ligacao, "processo_documento")).isTrue();
        });
    }

    private static final String DOCUMENTO_DA_RECUSA = "(SELECT min(id::text)::uuid FROM processo_documento)";

    private static final String INFORMACAO_BASE_DA_RECUSA = "(SELECT min(id::text)::uuid FROM processo_informacao_base)";

    private static final String INSERCAO_PROCESSO_REJEICAO_ITEM = "INSERT INTO processo_rejeicao_item (rejeicao_id, "
            + "processo_documento_id, processo_informacao_base_id, categoria_id, motivo_id, notas) VALUES ("
            + "(SELECT min(id) FROM processo_rejeicao), %s, %s, "
            + "(SELECT id FROM lista_valores WHERE tipo = 'CATEGORIA_REJEICAO_DGT'), "
            + "(SELECT min(id) FROM lista_valores WHERE tipo = 'MOTIVO_REJEICAO_DGT'), %s)";

    private void prepararRecusaComAlvos(Connection ligacao) throws SQLException {
        inserirProcessoComDocumento(ligacao);
        executar(ligacao, "INSERT INTO processo_informacao_base (id, processo_id, elemento, estado_validacao) "
                + "VALUES (gen_random_uuid(), '" + PROCESSO_ID + "', 'DELIBERACAO', 'POR_VALIDAR')");
        executar(ligacao, "INSERT INTO lista_valores (tipo, codigo, descricao, ordem) "
                + "VALUES ('CATEGORIA_REJEICAO_DGT', 'TESTE', 'Categoria de teste', 1)");
        executar(ligacao, INSERCAO_PROCESSO_REJEICAO.formatted(1, "DGT", "NULL"));
    }

    @Test
    void deveObrigarTokenPublicoEExpostoEmAAndaremJuntos() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DOS_LIMITES_DOCUMENTAIS + CHANGESETS_DOS_LINKS_PUBLICOS);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, "SELECT data_type || '|' || is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'processo_documento' AND column_name = 'exposto_em'"))
                    .isEqualTo("timestamp with time zone|YES");
            inserirProcessoComDocumento(ligacao);

            assertThatThrownBy(() -> executar(ligacao,
                    "UPDATE processo_documento SET token_publico = gen_random_uuid()"))
                    .hasMessageContaining("ck_processo_documento_exposicao");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE processo_documento SET exposto_em = now()"))
                    .hasMessageContaining("ck_processo_documento_exposicao");
            executar(ligacao, "UPDATE processo_documento SET token_publico = gen_random_uuid(), exposto_em = now()");
        });
    }

    @Test
    void deveReverterAExposicaoSemTocarNaIncmComunicacao() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 40);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "processo_documento", "exposto_em")).isFalse();
            assertThat(colunaExiste(ligacao, "processo_documento", "token_publico")).isTrue();
            assertThat(tabelaExiste(ligacao, "incm_comunicacao")).isTrue();
        });
    }

    @Test
    void deveAcrescentarOLinkDrNuloAoProcesso() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT data_type || '|' || is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'processo' AND column_name = 'link_dr'")).isEqualTo("text|YES");
            inserirProcessoComDocumento(ligacao);

            assertThat(texto(ligacao, "SELECT count(*) || '|' || count(link_dr) FROM processo")).isEqualTo("1|0");
        });
    }

    @Test
    void deveReverterOLinkDrSemTocarNaExposicao() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 39);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "processo", "link_dr")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "numero_dr")).isTrue();
            assertThat(colunaExiste(ligacao, "processo_documento", "exposto_em")).isTrue();
        });
    }

    @Test
    void deveDeixarASaigtAppObterELibertarOLockDosJobsComAHoraDaBd() throws Exception {
        comBaseMigrada(ligacao -> {
            try (Connection app = DriverManager.getConnection(ligacao.getMetaData().getURL(), "saigt_app", "teste")) {
                app.setAutoCommit(true);
                SingleConnectionDataSource dataSource = new SingleConnectionDataSource(app, true);
                LockProvider instanciaA = new AgendamentoConfig().lockProvider(dataSource);
                LockProvider instanciaB = new AgendamentoConfig().lockProvider(dataSource);
                LockConfiguration configuracao = new LockConfiguration(Instant.now(), "purga-sessoes",
                        Duration.ofMinutes(15), Duration.ZERO);

                Optional<SimpleLock> lock = instanciaA.lock(configuracao);
                assertThat(lock).isPresent();
                assertThat(instanciaB.lock(configuracao)).isEmpty();
                lock.get().unlock();
                assertThat(instanciaB.lock(configuracao)).isPresent();
            }
            assertThat(texto(ligacao, "SELECT count(*) FROM shedlock WHERE locked_by <> ''")).isEqualTo("1");
        });
    }

    @Test
    void deveAlinharADinamicaEOProcessoComOLld() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT string_agg(column_name || ':' || is_nullable, ',' ORDER BY column_name) "
                    + "FROM information_schema.columns WHERE table_name = 'dinamica' "
                    + "AND column_name IN ('numero_dinamica', 'descritivo', 'tentativa')"))
                    .isEqualTo("descritivo:NO,numero_dinamica:NO,tentativa:NO");
            assertThat(colunaExiste(ligacao, "processo", "numero_tentativa")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "ocorrencia")).isTrue();
            assertThat(colunaExiste(ligacao, "instrumento", "id_igt_snit")).isTrue();
            assertThat(definicaoDoIndice(ligacao, "idx_dinamica_instrumento_instrumento")).contains("instrumento_id");

            inserirProcessoComDocumento(ligacao);
            executar(ligacao, "INSERT INTO dinamica_instrumento (dinamica_id, instrumento_id) "
                    + "VALUES ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002')");

            assertThat(texto(ligacao, "SELECT numero_dinamica || '|' || tentativa || '|' || estado FROM dinamica"))
                    .isEqualTo("00001|1|EM_CURSO");
            assertThat(texto(ligacao, "SELECT numero_processo || '|' || ocorrencia FROM processo")).isEqualTo("00001|1");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE dinamica SET estado = 'CONCLUIDA'"))
                    .hasMessageContaining("ck_dinamica_estado");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE dinamica SET descritivo = '" + "x".repeat(51) + "'"))
                    .hasMessageContaining("50");
            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO dinamica_instrumento (dinamica_id, instrumento_id) "
                    + "VALUES ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002')"))
                    .hasMessageContaining("pk_dinamica_instrumento");
            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO processo (id, dinamica_id, "
                    + "instrumento_procedimento_config_id, criado_por, ocorrencia, numero_processo, entidade_id) VALUES "
                    + "(gen_random_uuid(), '00000000-0000-0000-0000-000000000003', "
                    + "(SELECT min(id) FROM instrumento_procedimento_config), "
                    + "'00000000-0000-0000-0000-000000000001', 2, '00001', (SELECT min(id) FROM entidade))"))
                    .hasMessageContaining("uq_processo_numero");
        });
    }

    @Test
    void devePreencherAsDinamicasEOsProcessosExistentesAoAlinharComOLld() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 38);
            ligacao.setAutoCommit(true);
            inserirProcessoComDocumento(ligacao);
            executar(ligacao, "UPDATE dinamica SET estado = 'CONCLUIDA'");
            executar(ligacao, "UPDATE processo SET numero_tentativa = 2");
            executar(ligacao, "INSERT INTO condicao (regra_id, avaliador, parametros) SELECT min(id), 'OCORRENCIA_EM_ESTADO', "
                    + "'{\"tipo_ocorrencia\":\"DINAMICA\",\"codigo\":\"ELABORACAO\",\"estado\":\"CONCLUIDA\"}' FROM regra");

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT d.numero_dinamica || '|' || (d.descritivo = left(td.nome, 50))::text || '|' "
                    + "|| d.tentativa || '|' || d.estado FROM dinamica d "
                    + "JOIN instrumento_dinamica_config c ON c.id = d.instrumento_dinamica_config_id "
                    + "JOIN tipo_dinamica td ON td.id = c.tipo_dinamica_id")).isEqualTo("00001|true|1|TERMINADA");
            assertThat(texto(ligacao, "SELECT dinamica_id || '|' || instrumento_id FROM dinamica_instrumento"))
                    .isEqualTo("00000000-0000-0000-0000-000000000003|00000000-0000-0000-0000-000000000002");
            assertThat(texto(ligacao, "SELECT numero_processo || '|' || ocorrencia FROM processo")).isEqualTo("00001|2");
            assertThat(texto(ligacao, "SELECT parametros ->> 'estado' FROM condicao WHERE avaliador = 'OCORRENCIA_EM_ESTADO' "
                    + "AND parametros ->> 'codigo' = 'ELABORACAO'")).isEqualTo("TERMINADA");

            assertThat(texto(ligacao, "SELECT nome || '=' || valor_atual FROM idus_contador "
                    + "WHERE nome = 'NUMERO_PROCESSO'")).isEqualTo("NUMERO_PROCESSO=1");
            assertThat(texto(ligacao, "SELECT nome || '=' || valor_atual FROM idus_contador "
                    + "WHERE nome = 'NUMERO_DINAMICA'")).isEqualTo("NUMERO_DINAMICA=1");
        });
    }

    @Test
    void deveSemearOsContadoresDoNumeroDeProcessoEDeDinamicaComOMaiorValorJaAtribuido() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 16);
            ligacao.setAutoCommit(true);
            inserirProcessoComDocumento(ligacao);
            executar(ligacao, "SELECT setval('seq_processo_numero', 41)");
            executar(ligacao, "INSERT INTO processo (id, dinamica_id, instrumento_procedimento_config_id, criado_por, "
                    + "ocorrencia, numero_processo) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000003', "
                    + "(SELECT min(id) FROM instrumento_procedimento_config), '00000000-0000-0000-0000-000000000001', "
                    + "2, '00050')");

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT valor_atual FROM idus_contador WHERE nome = 'NUMERO_PROCESSO'"))
                    .isEqualTo("50");
            assertThat(texto(ligacao, "SELECT valor_atual FROM idus_contador WHERE nome = 'NUMERO_DINAMICA'"))
                    .isEqualTo("1");
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_class WHERE relkind = 'S' "
                    + "AND relname IN ('seq_processo_numero', 'seq_dinamica_numero')")).isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(column_default) FROM information_schema.columns "
                    + "WHERE table_schema = current_schema() AND ((table_name = 'processo' AND column_name = 'numero_processo') "
                    + "OR (table_name = 'dinamica' AND column_name = 'numero_dinamica'))")).isEqualTo("0");
        });
    }

    @Test
    void deveReverterOsContadoresDoNumeroDeProcessoEDeDinamicaParaAsSequencias() throws Exception {
        comBaseMigrada(ligacao -> {
            executar(ligacao, "UPDATE idus_contador SET valor_atual = 7 WHERE nome = 'NUMERO_PROCESSO'");
            executar(ligacao, "INSERT INTO idus_contador (nome, valor_atual) VALUES ('NUMERO_DINAMICA', 3) "
                    + "ON CONFLICT (nome) DO UPDATE SET valor_atual = 3");

            reverter(ligacao, 16);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT nextval('seq_processo_numero')")).isEqualTo("8");
            assertThat(texto(ligacao, "SELECT nextval('seq_dinamica_numero')")).isEqualTo("4");
            assertThat(texto(ligacao, "SELECT count(column_default) FROM information_schema.columns "
                    + "WHERE table_schema = current_schema() AND ((table_name = 'processo' AND column_name = 'numero_processo') "
                    + "OR (table_name = 'dinamica' AND column_name = 'numero_dinamica'))")).isEqualTo("2");
            assertThat(texto(ligacao, "SELECT count(*) FROM idus_contador WHERE nome IN ('NUMERO_PROCESSO', 'NUMERO_DINAMICA')"))
                    .isEqualTo("0");
            assertThat(tabelaExiste(ligacao, "idus_contador")).isTrue();
        });
    }

    @Test
    void deveRecusarAlinharComOLldEnquantoHouverDinamicasCanceladas() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 38);
            ligacao.setAutoCommit(true);
            inserirProcessoComDocumento(ligacao);
            executar(ligacao, "UPDATE dinamica SET estado = 'CANCELADA'");

            assertThatThrownBy(() -> migrar(ligacao)).hasStackTraceContaining("CANCELADA");
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_dinamica_estado'")).isEqualTo("0");
            assertThat(tabelaExiste(ligacao, "dinamica_instrumento")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "numero_processo")).isFalse();
        });
    }

    @Test
    void deveReverterOAlinhamentoComOLldSemTocarNoLinkDr() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);
            executar(ligacao, "UPDATE dinamica SET estado = 'TERMINADA'");

            reverter(ligacao, 38);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "dinamica", "numero_dinamica")).isFalse();
            assertThat(colunaExiste(ligacao, "dinamica", "descritivo")).isFalse();
            assertThat(colunaExiste(ligacao, "dinamica", "tentativa")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "numero_processo")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "numero_tentativa")).isTrue();
            assertThat(colunaExiste(ligacao, "instrumento", "id_igt_snit")).isFalse();
            assertThat(tabelaExiste(ligacao, "dinamica_instrumento")).isFalse();
            assertThat(texto(ligacao, "SELECT estado FROM dinamica")).isEqualTo("CONCLUIDA");
            assertThat(colunaExiste(ligacao, "processo", "link_dr")).isTrue();
        });
    }

    @Test
    void deveCriarEReverterOShedlockSemTocarNoAlinhamentoComOLld() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(tabelaExiste(ligacao, "shedlock")).isTrue();

            reverter(ligacao, 33);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "shedlock")).isFalse();
            assertThat(colunaExiste(ligacao, "instrumento", "id_igt_snit")).isTrue();
        });
    }

    @Test
    void deveRetirarOFormularioSchemaNumaBaseVazia() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(tabelaExiste(ligacao, "formulario_schema")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "formulario_schema_id")).isFalse();
            assertThat(texto(ligacao, "SELECT count(*) FROM databasechangelog WHERE id LIKE '%seed-formulario-schema%'"))
                    .isEqualTo("0");
        });
    }

    @Test
    void deveReporOFormularioSchemaSemDadosAoReverterSemTocarNoShedlock() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 32);
            ligacao.setAutoCommit(true);

            assertThat(linhas(ligacao, "SELECT column_name || '|' || data_type || '|' || is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'formulario_schema' ORDER BY ordinal_position")).containsExactly(
                    "id|bigint|NO",
                    "instrumento_procedimento_config_id|bigint|NO",
                    "versao|integer|NO",
                    "schema|jsonb|NO",
                    "ativo|boolean|NO",
                    "criado_por|character varying|NO",
                    "criado_em|timestamp with time zone|NO");
            assertThat(texto(ligacao, "SELECT character_maximum_length FROM information_schema.columns "
                    + "WHERE table_name = 'formulario_schema' AND column_name = 'criado_por'")).isEqualTo("200");
            assertThat(texto(ligacao, "SELECT count(*) FROM formulario_schema")).isEqualTo("0");
            assertThat(texto(ligacao, "SELECT data_type || '|' || is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'processo' AND column_name = 'formulario_schema_id'")).isEqualTo("bigint|YES");
            assertThat(linhas(ligacao, "SELECT conname || '|' || contype::text || '|' || CASE WHEN contype = 'f' THEN confrelid::regclass::text ELSE '' END "
                    + "FROM pg_constraint WHERE conname IN ('uq_formulario_schema_versao', "
                    + "'fk_formulario_schema_procedimento_config', 'fk_processo_formulario_schema') ORDER BY conname"))
                    .containsExactly(
                            "fk_formulario_schema_procedimento_config|f|instrumento_procedimento_config",
                            "fk_processo_formulario_schema|f|formulario_schema",
                            "uq_formulario_schema_versao|u|");

            executar(ligacao, "INSERT INTO formulario_schema (instrumento_procedimento_config_id, schema, criado_por) "
                    + "SELECT min(id), '{}', 'seed' FROM instrumento_procedimento_config");
            assertThat(texto(ligacao, "SELECT versao || '|' || ativo::text || '|' || (criado_em IS NOT NULL)::text "
                    + "FROM formulario_schema")).isEqualTo("1|true|true");
            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO formulario_schema (instrumento_procedimento_config_id, "
                    + "schema, criado_por) SELECT min(id), '{}', 'seed' FROM instrumento_procedimento_config"))
                    .hasMessageContaining("uq_formulario_schema_versao");
            assertThat(tabelaExiste(ligacao, "shedlock")).isTrue();
        });
    }

    @Test
    void deveLargarOFormularioSchemaNumaBaseJaMigradaComOsSeedsAntigos() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 32);
            ligacao.setAutoCommit(true);
            for (String seed : List.of("2026-09-17-2-seed-formulario-schema-pdm", "2026-09-28-4-seed-formulario-schema-pu-pp")) {
                executar(ligacao, "INSERT INTO databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, "
                        + "md5sum) VALUES ('" + seed + "', 'seed', 'db/changelog/seed/" + seed + ".xml', now(), 0, 'EXECUTED', "
                        + "'9:00000000000000000000000000000000')");
            }
            executar(ligacao, "INSERT INTO formulario_schema (instrumento_procedimento_config_id, schema, criado_por) "
                    + "SELECT min(id), '{\"type\":\"object\"}', 'seed' FROM instrumento_procedimento_config");
            inserirProcessoComDocumento(ligacao);
            executar(ligacao, "UPDATE processo SET formulario_schema_id = (SELECT min(id) FROM formulario_schema)");

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "formulario_schema")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "formulario_schema_id")).isFalse();
            assertThat(texto(ligacao, "SELECT count(*) FROM processo")).isEqualTo("1");
        });
    }

    private static String colunasDe(String tabela) {
        return "SELECT column_name || '|' || data_type || '|' || is_nullable || '|' "
                + "|| coalesce(character_maximum_length::text, '') || '|' || coalesce(column_default, '') "
                + "FROM information_schema.columns WHERE table_schema = current_schema() "
                + "AND table_name = '" + tabela + "' ORDER BY ordinal_position";
    }

    private String chavePrimaria(Connection ligacao, String tabela) throws SQLException {
        return texto(ligacao, "SELECT string_agg(a.attname, ',' ORDER BY a.attnum) FROM pg_index i "
                + "JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey) "
                + "WHERE i.indrelid = '" + tabela + "'::regclass AND i.indisprimary");
    }

    @Test
    void deveCriarAsTabelasDoIdusComAsColunasAsChavesEOsIndices() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, colunasDe("idus_processo"))).containsExactly(
                    "processo_id|uuid|NO||",
                    "idus|character varying|NO|50|",
                    "sistema_origem|character varying|NO|10|",
                    "id_igt|character varying|NO|10|",
                    "id_igt_definitivo|boolean|NO||false",
                    "ordem_dinamica|character varying|NO|10|",
                    "ordem_definitiva|boolean|NO||false",
                    "tentativa|smallint|NO||",
                    "procedimento|character varying|NO|3|",
                    "submissao|smallint|NO||0",
                    "pub_incm|character varying|YES|20|",
                    "atualizado_em|timestamp with time zone|NO||");
            assertThat(linhas(ligacao, colunasDe("idus_processo_historico"))).containsExactly(
                    "id|bigint|NO||",
                    "processo_id|uuid|NO||",
                    "segmento|character varying|NO|20|",
                    "idus_anterior|character varying|YES|50|",
                    "idus_novo|character varying|NO|50|",
                    "evento|character varying|NO|20|",
                    "registado_em|timestamp with time zone|NO||",
                    "correlation_id|character varying|NO|100|");
            assertThat(linhas(ligacao, colunasDe("idus_dinamica"))).containsExactly(
                    "dinamica_id|uuid|NO||",
                    "idus|character varying|NO|50|",
                    "id_igt_snit|character varying|NO|20|",
                    "ordem_snit|character varying|NO|10|",
                    "gerado_em|timestamp with time zone|NO||");
            assertThat(linhas(ligacao, colunasDe("idus_contador"))).containsExactly(
                    "nome|character varying|NO|50|",
                    "valor_atual|integer|NO||");
            assertThat(texto(ligacao, "SELECT is_identity FROM information_schema.columns "
                    + "WHERE table_name = 'idus_processo_historico' AND column_name = 'id'")).isEqualTo("YES");

            assertThat(chavePrimaria(ligacao, "idus_processo")).isEqualTo("processo_id");
            assertThat(chavePrimaria(ligacao, "idus_processo_historico")).isEqualTo("id");
            assertThat(chavePrimaria(ligacao, "idus_dinamica")).isEqualTo("dinamica_id");
            assertThat(chavePrimaria(ligacao, "idus_contador")).isEqualTo("nome");
            assertThat(linhas(ligacao, "SELECT conname || '|' || contype::text || '|' "
                    + "|| CASE WHEN contype = 'f'THEN confrelid::regclass::text ELSE '' END FROM pg_constraint "
                    + "WHERE conrelid::regclass::text IN ('idus_processo', 'idus_processo_historico', 'idus_dinamica') "
                    + "AND contype IN ('f', 'u') ORDER BY conname COLLATE \"C\"")).containsExactly(
                    "fk_idus_dinamica_dinamica|f|dinamica",
                    "fk_idus_processo_historico_idus_processo|f|idus_processo",
                    "fk_idus_processo_processo|f|processo",
                    "uq_idus_dinamica_idus|u|",
                    "uq_idus_processo_idus|u|");
            assertThat(linhas(ligacao, "SELECT conname FROM pg_constraint WHERE conrelid::regclass::text "
                    + "IN ('idus_processo', 'idus_processo_historico', 'idus_contador') AND contype = 'c' "
                    + "ORDER BY conname COLLATE \"C\"")).containsExactly(
                    "ck_idus_contador_valor_atual",
                    "ck_idus_processo_historico_evento",
                    "ck_idus_processo_historico_segmento",
                    "ck_idus_processo_procedimento",
                    "ck_idus_processo_submissao",
                    "ck_idus_processo_tentativa");
            assertThat(definicaoDoIndice(ligacao, "idx_idus_processo_id_igt")).contains("idus_processo", "(id_igt)");
            assertThat(definicaoDoIndice(ligacao, "idx_idus_processo_historico_processo_id"))
                    .contains("idus_processo_historico", "(processo_id)");
        });
    }

    @Test
    void deveMarcarComoExecutadaAIdusContadorQueJaExistia() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 18);
            ligacao.setAutoCommit(true);
            assertThat(tabelaExiste(ligacao, "idus_contador")).isFalse();
            executar(ligacao, "CREATE TABLE idus_contador (nome VARCHAR(50) PRIMARY KEY, valor_atual INTEGER NOT NULL)");
            executar(ligacao, "INSERT INTO idus_contador (nome, valor_atual) VALUES ('CONTADOR_DE_TESTE', 41)");

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            for (String tabela : new String[] {"idus_processo", "idus_processo_historico", "idus_dinamica", "idus_contador"}) {
                assertThat(tabelaExiste(ligacao, tabela)).as(tabela).isTrue();
            }
            assertThat(texto(ligacao, "SELECT valor_atual FROM idus_contador WHERE nome = 'CONTADOR_DE_TESTE'")).isEqualTo("41");
            assertThat(texto(ligacao, "SELECT exectype FROM databasechangelog "
                    + "WHERE id = '2026-11-03-3-create-idus-contador'")).isEqualTo("MARK_RAN");
            assertThat(texto(ligacao, "SELECT exectype FROM databasechangelog "
                    + "WHERE id = '2026-11-03-3-create-idus'")).isEqualTo("EXECUTED");
        });
    }

    @Test
    void deveRecusarNaIdusProcessoSubmissaoTentativaEProcedimentoForaDoDominio() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);
            executar(ligacao, INSERCAO_IDUS_PROCESSO.formatted(PROCESSO_ID, "IDUS-A", 1, "031", 99));

            assertThatThrownBy(() -> executar(ligacao, INSERCAO_IDUS_PROCESSO.formatted(PROCESSO_ID, "IDUS-B", 1, "031", 100)))
                    .hasMessageContaining("ck_idus_processo_submissao");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_IDUS_PROCESSO.formatted(PROCESSO_ID, "IDUS-B", 1, "031", -1)))
                    .hasMessageContaining("ck_idus_processo_submissao");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_IDUS_PROCESSO.formatted(PROCESSO_ID, "IDUS-B", 0, "031", 0)))
                    .hasMessageContaining("ck_idus_processo_tentativa");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_IDUS_PROCESSO.formatted(PROCESSO_ID, "IDUS-B", 1, "31", 0)))
                    .hasMessageContaining("ck_idus_processo_procedimento");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_IDUS_PROCESSO.formatted(PROCESSO_ID, "IDUS-B", 1, "0A1", 0)))
                    .hasMessageContaining("ck_idus_processo_procedimento");
            assertThat(texto(ligacao, "SELECT count(*) FROM idus_processo")).isEqualTo("1");
        });
    }

    @Test
    void deveRecusarOMesmoIdusEmDoisProcessos() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);
            inserirOutroProcesso(ligacao);
            executar(ligacao, INSERCAO_IDUS_PROCESSO.formatted(PROCESSO_ID, "IDUS-A", 1, "031", 0));

            assertThatThrownBy(() -> executar(ligacao, INSERCAO_IDUS_PROCESSO.formatted(OUTRO_PROCESSO_ID, "IDUS-A", 1, "031", 0)))
                    .hasMessageContaining("uq_idus_processo_idus");
            executar(ligacao, INSERCAO_IDUS_PROCESSO.formatted(OUTRO_PROCESSO_ID, "IDUS-B", 1, "031", 0));
            assertThat(texto(ligacao, "SELECT count(*) FROM idus_processo")).isEqualTo("2");
        });
    }

    @Test
    void deveRecusarOMesmoIdusEmDuasDinamicasEUmIdusDeDinamicaInexistente() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);
            executar(ligacao, "INSERT INTO dinamica (id, instrumento_id, instrumento_dinamica_config_id, criado_por, descritivo, "
                    + "numero_dinamica) VALUES ('" + OUTRA_DINAMICA_ID + "', '00000000-0000-0000-0000-000000000002', "
                    + "(SELECT min(id) FROM instrumento_dinamica_config), '00000000-0000-0000-0000-000000000001', 'Outra', '00002')");
            executar(ligacao, INSERCAO_IDUS_DINAMICA.formatted("00000000-0000-0000-0000-000000000003", "SNIT-A"));

            assertThatThrownBy(() -> executar(ligacao, INSERCAO_IDUS_DINAMICA.formatted(OUTRA_DINAMICA_ID, "SNIT-A")))
                    .hasMessageContaining("uq_idus_dinamica_idus");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_IDUS_DINAMICA.formatted(UUID_SEM_PROCESSO, "SNIT-B")))
                    .hasMessageContaining("fk_idus_dinamica_dinamica");
            executar(ligacao, INSERCAO_IDUS_DINAMICA.formatted(OUTRA_DINAMICA_ID, "SNIT-B"));
            assertThat(texto(ligacao, "SELECT count(*) FROM idus_dinamica")).isEqualTo("2");
        });
    }

    @Test
    void deveRecusarUmValorNegativoNoContadorEUmNomeRepetido() throws Exception {
        comBaseMigrada(ligacao -> {
            executar(ligacao, "INSERT INTO idus_contador (nome, valor_atual) VALUES ('CONTADOR_DE_TESTE', 0)");

            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO idus_contador (nome, valor_atual) VALUES ('OUTRO', -1)"))
                    .hasMessageContaining("ck_idus_contador_valor_atual");
            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO idus_contador (nome, valor_atual) VALUES ('CONTADOR_DE_TESTE', 5)"))
                    .hasMessageContaining("idus_contador_pkey");
            assertThat(texto(ligacao, "SELECT count(*) FROM idus_contador WHERE nome = 'CONTADOR_DE_TESTE'")).isEqualTo("1");
        });
    }

    @Test
    void deveRecusarUmIdusDeProcessoInexistenteEUmHistoricoSemIdusDoProcesso() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);

            assertThatThrownBy(() -> executar(ligacao, INSERCAO_IDUS_PROCESSO.formatted(UUID_SEM_PROCESSO, "IDUS-A", 1, "031", 0)))
                    .hasMessageContaining("fk_idus_processo_processo");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_IDUS_HISTORICO.formatted(PROCESSO_ID, "CRIACAO", "PROCESSO_CRIADO")))
                    .hasMessageContaining("fk_idus_processo_historico_idus_processo");
        });
    }

    @Test
    void deveRecusarNoHistoricoUmSegmentoOuUmEventoForaDasListas() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);
            executar(ligacao, INSERCAO_IDUS_PROCESSO.formatted(PROCESSO_ID, "IDUS-A", 1, "031", 0));
            executar(ligacao, INSERCAO_IDUS_HISTORICO.formatted(PROCESSO_ID, "CRIACAO", "PROCESSO_CRIADO"));

            assertThatThrownBy(() -> executar(ligacao, INSERCAO_IDUS_HISTORICO.formatted(PROCESSO_ID, "OUTRO", "PROCESSO_CRIADO")))
                    .hasMessageContaining("ck_idus_processo_historico_segmento");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_IDUS_HISTORICO.formatted(PROCESSO_ID, "CRIACAO", "OUTRO")))
                    .hasMessageContaining("ck_idus_processo_historico_evento");
            assertThat(texto(ligacao, "SELECT count(*) FROM idus_processo_historico")).isEqualTo("1");
        });
    }

    @Test
    void deveTerOsEnumsDoIdusIguaisAosChecksDoHistorico() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(valoresDoCheck(ligacao, "ck_idus_processo_historico_segmento"))
                    .containsExactlyInAnyOrder(Arrays.stream(SegmentoIdus.values()).map(Enum::name).toArray(String[]::new));
            assertThat(valoresDoCheck(ligacao, "ck_idus_processo_historico_evento"))
                    .containsExactlyInAnyOrder(Arrays.stream(EventoIdus.values()).map(Enum::name).toArray(String[]::new));
        });
    }

    @Test
    void deveDeixarASaigtAppInserirELerMasNuncaAlterarNemApagarOHistoricoDoIdus() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);

            try (Connection app = DriverManager.getConnection(ligacao.getMetaData().getURL(), "saigt_app", "teste")) {
                executar(app, INSERCAO_IDUS_PROCESSO.formatted(PROCESSO_ID, "IDUS-A", 1, "031", 0));
                executar(app, INSERCAO_IDUS_HISTORICO.formatted(PROCESSO_ID, "CRIACAO", "PROCESSO_CRIADO"));
                assertThat(texto(app, "SELECT count(*) FROM idus_processo_historico")).isEqualTo("1");
                executar(app, "UPDATE idus_processo SET submissao = 1");

                assertThatThrownBy(() -> executar(app, "UPDATE idus_processo_historico SET idus_novo = 'IDUS-B'"))
                        .hasMessageContaining("permission denied");
                assertThatThrownBy(() -> executar(app, "DELETE FROM idus_processo_historico"))
                        .hasMessageContaining("permission denied");
            }
            assertThat(texto(ligacao, "SELECT idus_novo FROM idus_processo_historico")).isEqualTo("IDUS-A");
        });
    }

    @Test
    void deveReverterAsTabelasDoIdusSemTocarNoProcesso() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);

            reverter(ligacao, 18);
            ligacao.setAutoCommit(true);

            for (String tabela : new String[] {"idus_processo", "idus_processo_historico", "idus_dinamica", "idus_contador"}) {
                assertThat(tabelaExiste(ligacao, tabela)).as(tabela).isFalse();
            }
            assertThat(texto(ligacao, "SELECT count(*) FROM processo")).isEqualTo("1");
        });
    }

    @Test
    void deveReverterAsTabelasDoIdusSemApagarAIdusContadorCriadaNoutroSitio() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 18);
            ligacao.setAutoCommit(true);
            executar(ligacao, "CREATE TABLE idus_contador (nome VARCHAR(50) PRIMARY KEY, valor_atual INTEGER NOT NULL)");
            executar(ligacao, "INSERT INTO idus_contador (nome, valor_atual) VALUES ('CONTADOR_DE_TESTE', 41)");
            migrar(ligacao);
            ligacao.setAutoCommit(true);

            reverter(ligacao, 18);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "idus_processo")).isFalse();
            assertThat(tabelaExiste(ligacao, "idus_contador")).isTrue();
            assertThat(texto(ligacao, "SELECT valor_atual FROM idus_contador")).isEqualTo("41");
        });
    }

    private static final String OUTRO_PROCESSO_ID = "00000000-0000-0000-0000-000000000005";

    private static final String OUTRA_DINAMICA_ID = "00000000-0000-0000-0000-000000000006";

    private static final String INSERCAO_IDUS_DINAMICA = "INSERT INTO idus_dinamica (dinamica_id, idus, id_igt_snit, "
            + "ordem_snit, gerado_em) VALUES ('%s', '%s', '1001', 'T001', now())";

    private static final String INSERCAO_IDUS_PROCESSO = "INSERT INTO idus_processo (processo_id, idus, sistema_origem, "
            + "id_igt, ordem_dinamica, tentativa, procedimento, submissao, atualizado_em) "
            + "VALUES ('%s', '%s', 'SAIGT', '1001', 'T001', %d, '%s', %d, now())";

    private static final String INSERCAO_IDUS_HISTORICO = "INSERT INTO idus_processo_historico (processo_id, segmento, "
            + "idus_novo, evento, registado_em, correlation_id) VALUES ('%s', '%s', 'IDUS-A', '%s', now(), 'corr-1')";

    private void inserirOutroProcesso(Connection ligacao) throws SQLException {
        boolean comEntidade = colunaExiste(ligacao, "processo", "entidade_id");
        executar(ligacao, "INSERT INTO processo (id, dinamica_id, instrumento_procedimento_config_id, criado_por, ocorrencia, "
                + "numero_processo" + (comEntidade ? ", entidade_id" : "") + ") VALUES ('" + OUTRO_PROCESSO_ID
                + "', '00000000-0000-0000-0000-000000000003', "
                + "(SELECT min(id) FROM instrumento_procedimento_config), '00000000-0000-0000-0000-000000000001', 2, '00002'"
                + (comEntidade ? ", (SELECT min(id) FROM entidade)" : "") + ")");
    }

    private boolean semValorPorOmissao(Connection ligacao, String tabela, String coluna) throws SQLException {
        return Integer.parseInt(texto(ligacao, "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = '" + tabela + "' AND column_name = '" + coluna
                + "' AND column_default IS NULL")) == 1;
    }

    private List<String> valoresDoCheck(Connection ligacao, String restricao) throws SQLException {
        Matcher valores = Pattern.compile("'([A-Z_]+)'")
                .matcher(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = '" + restricao + "'"));
        List<String> encontrados = new ArrayList<>();
        while (valores.find()) {
            encontrados.add(valores.group(1));
        }
        return encontrados;
    }

    private static final String PROCESSO_ID = "00000000-0000-0000-0000-000000000004";

    private static final String UUID_SEM_PROCESSO = "00000000-0000-0000-0000-0000000000ff";

    private static final String INSERCAO_INCM_COMUNICACAO = "INSERT INTO incm_comunicacao (processo_id, operacao, "
            + "submissao, serie_dr, pedido, falha_tecnica, correlation_id, enviado_em) VALUES ('" + PROCESSO_ID
            + "', '%s', 1, %s, '{}', %s, 'corr-1', now())";

    private static final String INSERCAO_PROCESSO_REJEICAO = "INSERT INTO processo_rejeicao (processo_id, sequencia, "
            + "origem, motivo_texto, criado_em) VALUES ('" + PROCESSO_ID + "', %d, '%s', %s, now())";

    private static final String INSERCAO_PLANTA = "INSERT INTO planta (id, processo_id, processo_documento_id, "
            + "atualizado_por, criado_em, atualizado_em) VALUES (gen_random_uuid(), '" + PROCESSO_ID + "', %s, %s, now(), now())";

    private static final String DOCUMENTO_DA_PLANTA = "(SELECT min(id::text)::uuid FROM processo_documento)";

    private void inserirProcessoComDocumento(Connection ligacao) throws SQLException {
        executar(ligacao, "INSERT INTO utilizador (id, email, origem_autenticacao) "
                + "VALUES ('00000000-0000-0000-0000-000000000001', 'autor@cm-val.pt', 'INCM')");
        inserirEntidade(ligacao, "505187531");
        boolean instrumentoComEntidade = colunaExiste(ligacao, "instrumento", "entidade_id");
        boolean processoComEntidade = colunaExiste(ligacao, "processo", "entidade_id");
        executar(ligacao, "INSERT INTO instrumento (id, tipo_instrumento_id, " + (instrumentoComEntidade ? "entidade_id, " : "")
                + "numero, designacao, responsavel_id, criado_por) VALUES ('00000000-0000-0000-0000-000000000002', "
                + "(SELECT min(id) FROM tipo_instrumento), " + (instrumentoComEntidade ? "(SELECT min(id) FROM entidade), " : "")
                + "'1', 'PDM', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001')");
        // Serve antes e depois do alinhamento da Dinâmica com o LLD, que torna o descritivo obrigatório.
        boolean comDescritivo = colunaExiste(ligacao, "dinamica", "descritivo");
        boolean comNumeroDinamica = semValorPorOmissao(ligacao, "dinamica", "numero_dinamica");
        boolean comNumeroProcesso = semValorPorOmissao(ligacao, "processo", "numero_processo");
        executar(ligacao, "INSERT INTO dinamica (id, instrumento_id, instrumento_dinamica_config_id, criado_por"
                + (comDescritivo ? ", descritivo" : "") + (comNumeroDinamica ? ", numero_dinamica" : "") + ") "
                + "VALUES ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', "
                + "(SELECT min(id) FROM instrumento_dinamica_config), '00000000-0000-0000-0000-000000000001'"
                + (comDescritivo ? ", 'Alteração do PDM'" : "") + (comNumeroDinamica ? ", '00001'" : "") + ")");
        executar(ligacao, "INSERT INTO processo (id, dinamica_id, instrumento_procedimento_config_id, criado_por"
                + (comNumeroProcesso ? ", numero_processo" : "") + (processoComEntidade ? ", entidade_id" : "") + ") "
                + "VALUES ('" + PROCESSO_ID + "', '00000000-0000-0000-0000-000000000003', "
                + "(SELECT min(id) FROM instrumento_procedimento_config), '00000000-0000-0000-0000-000000000001'"
                + (comNumeroProcesso ? ", '00001'" : "") + (processoComEntidade ? ", (SELECT min(id) FROM entidade)" : "") + ")");
        executar(ligacao, "INSERT INTO processo_documento (id, processo_id, categoria, extensao, nome_ficheiro, "
                + "caminho_ficheiro, hash_sha256, versao_objeto, tamanho_bytes, criado_por) VALUES (gen_random_uuid(), '"
                + PROCESSO_ID + "', 'DEPOSITO', 'pdf', 'texto.pdf', 'processos/00001/DEPOSITO/x', '" + "a".repeat(64)
                + "', 'v1', 10, '00000000-0000-0000-0000-000000000001')");
    }

    private boolean tabelaExiste(Connection ligacao, String tabela) throws SQLException {
        return Integer.parseInt(texto(ligacao, "SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = current_schema() AND table_name = '" + tabela + "'")) == 1;
    }

    private static final String TOKEN_ANTIGO = "11111111-1111-1111-1111-111111111111";

    private static String linhaDocumento(String nomeFicheiro) {
        return "SELECT categoria || '|' || extensao || '|' || origem || '|' || bloqueado_edicao::text "
                + "FROM processo_documento WHERE nome_ficheiro = '" + nomeFicheiro + "'";
    }

    /** Estado anterior à migração: um item por caixa e três documentos (com item de publicação, de depósito e sem item). */
    private void inserirDocumentosDoModeloAnterior(Connection ligacao) throws SQLException {
        executar(ligacao, "INSERT INTO utilizador (id, email, origem_autenticacao) "
                + "VALUES ('00000000-0000-0000-0000-000000000001', 'autor@cm-doc.pt', 'INCM')");
        inserirEntidade(ligacao, "506609553");
        boolean instrumentoComEntidade = colunaExiste(ligacao, "instrumento", "entidade_id");
        boolean processoComEntidade = colunaExiste(ligacao, "processo", "entidade_id");
        executar(ligacao, "INSERT INTO instrumento (id, tipo_instrumento_id, " + (instrumentoComEntidade ? "entidade_id, " : "")
                + "numero, designacao, responsavel_id, criado_por) VALUES ('00000000-0000-0000-0000-000000000002', "
                + "(SELECT min(id) FROM tipo_instrumento), " + (instrumentoComEntidade ? "(SELECT min(id) FROM entidade), " : "")
                + "'1', 'PDM', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001')");
        executar(ligacao, "INSERT INTO dinamica (id, instrumento_id, instrumento_dinamica_config_id, criado_por) "
                + "VALUES ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', "
                + "(SELECT min(id) FROM instrumento_dinamica_config), '00000000-0000-0000-0000-000000000001')");
        executar(ligacao, "INSERT INTO processo (id, dinamica_id, instrumento_procedimento_config_id, criado_por"
                + (processoComEntidade ? ", entidade_id" : "") + ") "
                + "VALUES ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003', "
                + "(SELECT min(id) FROM instrumento_procedimento_config), '00000000-0000-0000-0000-000000000001'"
                + (processoComEntidade ? ", (SELECT min(id) FROM entidade)" : "") + ")");

        executar(ligacao, "INSERT INTO documento_config (instrumento_procedimento_config_id, nome, categoria, "
                + "extensoes_aceites, ordem) SELECT min(id), 'Texto do ato', 'PUBLICACAO', '[\"pdf\"]', 1 "
                + "FROM instrumento_procedimento_config");
        executar(ligacao, "INSERT INTO documento_config (instrumento_procedimento_config_id, nome, categoria, "
                + "extensoes_aceites, ordem) SELECT min(id), 'Planta', 'DEPOSITO', '[\"shp\"]', 2 "
                + "FROM instrumento_procedimento_config");

        String documento = "INSERT INTO processo_documento (id, processo_id, documento_config_id, nome_ficheiro, "
                + "caminho_ficheiro, hash_sha256, token_publico, tamanho_bytes, criado_por) VALUES "
                + "(gen_random_uuid(), '00000000-0000-0000-0000-000000000004', %s, '%s', 'privado/x', '%s', %s, 10, "
                + "'00000000-0000-0000-0000-000000000001')";
        String hash = "a".repeat(64);
        executar(ligacao, documento.formatted("(SELECT id FROM documento_config WHERE nome = 'Texto do ato')",
                "Texto.PDF", hash, "'" + TOKEN_ANTIGO + "'"));
        executar(ligacao, documento.formatted("(SELECT id FROM documento_config WHERE nome = 'Planta')",
                "planta.shp", hash, "NULL"));
        executar(ligacao, documento.formatted("NULL", "anexo", hash, "NULL"));
    }

    @Test
    void deveRecusarACategoriaAruNoTipoDeInstrumento() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThatThrownBy(() -> executar(ligacao,
                    "INSERT INTO tipo_instrumento (codigo, nome, categoria) VALUES ('TESTE_ARU', 'Teste', 'ARU')"))
                    .hasMessageContaining("ck_tipo_instrumento_categoria");
            assertThatCode(() -> executar(ligacao,
                    "INSERT INTO tipo_instrumento (codigo, nome, categoria) VALUES ('TESTE_SRUP', 'Teste', 'SRUP')"))
                    .doesNotThrowAnyException();
        });
    }

    @Test
    void devePararAMigracaoDaCategoriaSeJaExistirUmTipoAru() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU);
            ligacao.setAutoCommit(true);
            executar(ligacao, "INSERT INTO tipo_instrumento (codigo, nome, categoria) VALUES ('TESTE_ARU', 'Teste', 'ARU')");

            assertThatThrownBy(() -> migrarOsProximos(ligacao, CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU))
                    .hasMessageContaining("Há tipos de Instrumento com categoria ARU");

            ligacao.setAutoCommit(true);
            executar(ligacao, "DELETE FROM tipo_instrumento WHERE codigo = 'TESTE_ARU'");
            assertThatCode(() -> migrarOsProximos(ligacao, CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU)).doesNotThrowAnyException();
        });
    }

    @Test
    void deveReporOCheckAntigoAoReverterACategoria() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU);
            ligacao.setAutoCommit(true);

            assertThatCode(() -> executar(ligacao,
                    "INSERT INTO tipo_instrumento (codigo, nome, categoria) VALUES ('TESTE_ARU', 'Teste', 'ARU')"))
                    .doesNotThrowAnyException();
        });
    }

    @Test
    void deveSemearOsDominiosAsClassesEAsTipologiasDasServidoesComOsPais() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT tipo || ' ' || count(*) || ' ' || count(pai_id) FROM lista_valores "
                    + "WHERE tipo LIKE '%_SRUP' GROUP BY tipo ORDER BY tipo"))
                    .containsExactly("CLASSE_SRUP 33 33", "DOMINIO_SRUP 9 0", "TIPOLOGIA_SRUP 74 74");
            assertThat(contar(ligacao, "lista_valores c JOIN lista_valores d ON d.id = c.pai_id "
                    + "WHERE c.tipo = 'CLASSE_SRUP' AND d.tipo = 'DOMINIO_SRUP'")).isEqualTo(33);
            assertThat(contar(ligacao, "lista_valores t JOIN lista_valores c ON c.id = t.pai_id "
                    + "WHERE t.tipo = 'TIPOLOGIA_SRUP' AND c.tipo = 'CLASSE_SRUP'")).isEqualTo(74);
            assertThat(contar(ligacao, "lista_valores WHERE tipo LIKE '%_SRUP' AND NOT ativo")).isZero();

            assertThat(linhas(ligacao, "SELECT c.descricao || ' | ' || d.descricao FROM lista_valores t "
                    + "JOIN lista_valores c ON c.id = t.pai_id JOIN lista_valores d ON d.id = c.pai_id "
                    + "WHERE t.tipo = 'TIPOLOGIA_SRUP' AND t.descricao = 'Zona especial de proteção'"))
                    .containsExactly("Imóveis Classificados | Património Edificado");
            assertThat(linhas(ligacao, "SELECT c.descricao || ' | ' || d.descricao FROM lista_valores c "
                    + "JOIN lista_valores d ON d.id = c.pai_id WHERE c.tipo = 'CLASSE_SRUP' "
                    + "AND c.descricao LIKE 'Rede Natura 2000%' ORDER BY c.codigo"))
                    .containsExactly("Rede Natura 2000 – ZEC | Recursos Ecológicos",
                            "Rede Natura 2000 – ZPE | Recursos Ecológicos");
            assertThat(contar(ligacao, "lista_valores t JOIN lista_valores c ON c.id = t.pai_id "
                    + "WHERE t.tipo = 'TIPOLOGIA_SRUP' AND c.codigo = 'C17'")).isEqualTo(13);
            assertThat(contar(ligacao, "lista_valores t JOIN lista_valores c ON c.id = t.pai_id "
                    + "WHERE t.tipo = 'TIPOLOGIA_SRUP' AND c.codigo IN ('C02', 'C03', 'C07', 'C08', 'C09', 'C16', "
                    + "'C22', 'C24', 'C28', 'C29', 'C30')")).isZero();
            assertThat(texto(ligacao, "SELECT min(codigo) || ' ' || max(codigo) FROM lista_valores "
                    + "WHERE tipo = 'TIPOLOGIA_SRUP'")).isEqualTo("T001 T074");

            assertThat(colunaExiste(ligacao, "lista_valores", "pai_id")).isTrue();
            assertThat(colunaExiste(ligacao, "instrumento", "tipologia_srup_id")).isTrue();
            assertThat(contar(ligacao, "pg_indexes WHERE indexname IN ('idx_lista_valores_pai', "
                    + "'idx_instrumento_tipologia_srup')")).isEqualTo(2);
            assertThat(contar(ligacao, "pg_constraint WHERE conname IN ('fk_lista_valores_pai', "
                    + "'fk_instrumento_tipologia_srup')")).isEqualTo(2);
        });
    }

    @Test
    void deveReverterAClassificacaoDasServidoesRetirandoAsLinhasEAsColunas() throws Exception {
        comBaseMigrada(ligacao -> {
            int outras = contar(ligacao, "lista_valores WHERE tipo NOT LIKE '%_SRUP'");
            // A classificação vem logo a seguir ao CHECK da categoria: revertem-se também os changesets posteriores.
            int posteriores = CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES;

            reverterOsUltimos(ligacao, posteriores + CHANGESETS_DA_CLASSIFICACAO_SRUP);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "lista_valores WHERE tipo LIKE '%_SRUP'")).isZero();
            assertThat(contar(ligacao, "lista_valores WHERE tipo NOT LIKE '%_SRUP'")).isLessThanOrEqualTo(outras);
            assertThat(colunaExiste(ligacao, "lista_valores", "pai_id")).isFalse();
            assertThat(colunaExiste(ligacao, "instrumento", "tipologia_srup_id")).isFalse();
            assertThat(contar(ligacao, "pg_indexes WHERE indexname IN ('idx_lista_valores_pai', "
                    + "'idx_instrumento_tipologia_srup')")).isZero();

            migrarOsProximos(ligacao, CHANGESETS_DA_CLASSIFICACAO_SRUP);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "lista_valores WHERE tipo = 'TIPOLOGIA_SRUP'")).isEqualTo(74);
        });
    }

    /** Os Procedimentos do PDM da lista de documentos da DGT de 04/11, sem os da RERAE (212 e 213). */
    private static final String PDM_DA_LISTA_DGT = "instrumento_procedimento_config_id BETWEEN 201 AND 220 "
            + "AND instrumento_procedimento_config_id NOT IN (212, 213) AND pagina = 'DOCUMENTOS'";

    private List<String> codigosAtivos(Connection ligacao, int procedimento) throws SQLException {
        return linhas(ligacao, "SELECT codigo FROM documento_config WHERE ativo AND pagina = 'DOCUMENTOS' "
                + "AND instrumento_procedimento_config_id = " + procedimento + " ORDER BY ordem");
    }

    @Test
    void deveDarACadaProcedimentoDoPdmAsLinhasAtivasDaListaDaDgtPelaOrdemDaLista() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || ':' || count(*) FROM documento_config "
                    + "WHERE ativo AND " + PDM_DA_LISTA_DGT + " GROUP BY instrumento_procedimento_config_id "
                    + "ORDER BY instrumento_procedimento_config_id")).containsExactly("201:4", "202:4", "203:4", "204:29",
                    "205:4", "206:4", "207:4", "208:29", "209:4", "210:4", "211:19", "214:4", "215:17", "216:4", "217:17",
                    "218:14", "219:13", "220:4");
            assertThat(texto(ligacao, "SELECT count(*) FROM (SELECT 1 FROM documento_config WHERE ativo AND "
                    + PDM_DA_LISTA_DGT + " GROUP BY instrumento_procedimento_config_id "
                    + "HAVING min(ordem) <> 1 OR max(ordem) <> count(*) OR count(DISTINCT ordem) <> count(*)) irregulares"))
                    .isEqualTo("0");

            List<String> textoEDeliberacao = List.of("TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "DELIBERACAO_PUB", "DELIBERACAO_DEP");
            for (int procedimento : new int[] {201, 203, 205, 207, 209, 210, 214, 216, 220}) {
                assertThat(codigosAtivos(ligacao, procedimento)).as("procedimento %d", procedimento)
                        .containsExactlyElementsOf(textoEDeliberacao);
            }
            List<String> publicacaoDoPlano = List.of("TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "DELIBERACAO_PUB", "DELIBERACAO_DEP",
                    "REGULAMENTO_PUB", "REGULAMENTO_DEP", "PLANTA_ORDENAMENTO_PUB", "PLANTA_ORDENAMENTO_DEP",
                    "PLANTA_CONDICIONANTES_PUB", "PLANTA_CONDICIONANTES_DEP", "OUTRAS_PLANTAS_PUB", "OUTRAS_PLANTAS_DEP",
                    "CARTOGRAMA_PUB", "CARTOGRAMA_DEP", "ESTUDOS_CARACTERIZACAO_DEP", "RELATORIO_PLANO_DEP",
                    "RELATORIO_AMBIENTAL_DEP", "PROGRAMA_EXECUCAO_DEP", "PLANTA_ENQUADRAMENTO_DEP",
                    "PLANTA_SITUACAO_EXISTENTE_DEP", "COMPROMISSOS_URBANISTICOS_DEP", "ESTRUTURA_ECOLOGICA_DEP",
                    "CARTA_EDUCATIVA_DEP", "MAPA_RUIDO_DEP", "RELATORIO_PONDERACAO_DEP", "PARECERES_ATA_DEP",
                    "DELIBERACAO_CM_DEP", "PARECER_CA_DEP", "PARECER_CCDR_DEP");
            assertThat(codigosAtivos(ligacao, 204)).containsExactlyElementsOf(publicacaoDoPlano);
            assertThat(codigosAtivos(ligacao, 208)).containsExactlyElementsOf(publicacaoDoPlano);
            assertThat(codigosAtivos(ligacao, 211)).containsExactly("TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "DELIBERACAO_PUB",
                    "DELIBERACAO_DEP", "ALTERACOES_REGULAMENTO_PUB", "ALTERACOES_REGULAMENTO_DEP", "REGULAMENTO_INTEGRAL_PUB",
                    "REGULAMENTO_INTEGRAL_DEP", "PLANTA_ORDENAMENTO_PUB", "PLANTA_ORDENAMENTO_DEP",
                    "PLANTA_CONDICIONANTES_PUB", "PLANTA_CONDICIONANTES_DEP", "RELATORIO_FUNDAMENTACAO_DEP",
                    "DEMAIS_ELEMENTOS_DEP", "RELATORIO_AMBIENTAL_DEP", "RELATORIO_PONDERACAO_DEP", "DELIBERACAO_CM_DEP",
                    "PARECER_CCDR_DEP", "PARECERES_ATA_DEP");
        });
    }

    @Test
    void deveDesativarSoAsPlantasQueAListaDaDgtNaoDaAoPdm() throws Exception {
        comBaseMigrada(ligacao -> assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || ':' || codigo "
                + "FROM documento_config WHERE NOT ativo AND " + PDM_DA_LISTA_DGT
                + " ORDER BY instrumento_procedimento_config_id, ordem")).containsExactly(
                "201:PLANTA_AREA_INTERVENCAO_PUB", "201:PLANTA_AREA_INTERVENCAO_DEP", "204:PLANTAS_INFRAESTRUTURAS_DEP",
                "205:PLANTA_AREA_INTERVENCAO_PUB", "205:PLANTA_AREA_INTERVENCAO_DEP", "208:PLANTAS_INFRAESTRUTURAS_DEP",
                "209:PLANTA_LOCALIZACAO_PUB", "209:PLANTA_LOCALIZACAO_DEP", "210:PLANTA_LOCALIZACAO_PUB",
                "210:PLANTA_LOCALIZACAO_DEP"));
    }

    @Test
    void deveExigirNaDecisaoDeInicioDaElaboracaoOTextoEADeliberacaoNasDuasCaixasSemAPlanta() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT codigo || '|' || separador || '|' || categoria || '|' || obrigatorio::text "
                    + "FROM documento_config WHERE ativo AND instrumento_procedimento_config_id = 201 "
                    + "AND pagina = 'DOCUMENTOS' ORDER BY ordem")).containsExactly(
                    "TEXTO_ATO_PUB|Texto do Ato|PUBLICACAO|true", "TEXTO_ATO_DEP|Texto do Ato|DEPOSITO|true",
                    "DELIBERACAO_PUB|Deliberação|PUBLICACAO|true", "DELIBERACAO_DEP|Deliberação|DEPOSITO|true");
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE ativo "
                    + "AND instrumento_procedimento_config_id = 201 AND codigo LIKE 'PLANTA_AREA_INTERVENCAO%'")).isEqualTo("0");
            assertThat(texto(ligacao, "SELECT categoria || '|' || tipo_elemento || '|' || obrigatorio::text || '|' "
                    + "|| ativo::text FROM documento_config WHERE instrumento_procedimento_config_id = 211 "
                    + "AND codigo = 'RELATORIO_AMBIENTAL_DEP'")).isEqualTo("DEPOSITO|PECA_ESCRITA|true|true");
        });
    }

    @Test
    void deveAcrescentarADeliberacaoObrigatoriaComInformacaoBaseNaAberturaDaDiscussaoPublica() throws Exception {
        comBaseMigrada(ligacao -> assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || '|' || codigo "
                + "|| '|' || separador || '|' || nome || '|' || tipo_elemento || '|' || obrigatorio::text || '|' "
                + "|| elemento_informacao_base FROM documento_config WHERE codigo LIKE 'DELIBERACAO\\_%' ESCAPE '\\' "
                + "AND instrumento_procedimento_config_id IN (203, 207) ORDER BY instrumento_procedimento_config_id, ordem"))
                .containsExactly("203|DELIBERACAO_PUB|Deliberação|Deliberação|PECA_ESCRITA|true|DELIBERACAO",
                        "203|DELIBERACAO_DEP|Deliberação|Deliberação|PECA_ESCRITA|true|DELIBERACAO",
                        "207|DELIBERACAO_PUB|Deliberação|Deliberação|PECA_ESCRITA|true|DELIBERACAO",
                        "207|DELIBERACAO_DEP|Deliberação|Deliberação|PECA_ESCRITA|true|DELIBERACAO"));
    }

    @Test
    void deveAcrescentarAsOutrasPlantasEOCartogramaNaPublicacaoDoPlanoSemMexerNaPaginaDasPlantas() throws Exception {
        comBaseMigrada(ligacao -> {
            String regrasDeOmissao = texto(ligacao, "SELECT DISTINCT regras_tiff::text FROM documento_config "
                    + "WHERE instrumento_procedimento_config_id = 204 AND codigo = 'PLANTA_ORDENAMENTO_PUB'");
            assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || '|' || codigo || '|' || separador "
                    + "|| '|' || nome || '|' || tipo_elemento || '|' || extensoes_aceites::text || '|' "
                    + "|| coalesce((regras_tiff::text = '" + regrasDeOmissao + "')::text, '-') FROM documento_config "
                    + "WHERE codigo IN ('OUTRAS_PLANTAS_PUB', 'CARTOGRAMA_PUB', 'CARTOGRAMA_DEP') "
                    + "AND instrumento_procedimento_config_id IN (204, 208) ORDER BY instrumento_procedimento_config_id, ordem"))
                    .containsExactly("204|OUTRAS_PLANTAS_PUB|Outras Plantas|Outras plantas|PECA_GRAFICA|[\"zip\"]|true",
                            "204|CARTOGRAMA_PUB|Cartograma|Cartograma|OUTROS|[\"pdf\", \"jpg\", \"png\"]|-",
                            "204|CARTOGRAMA_DEP|Cartograma|Cartograma|OUTROS|[\"pdf\", \"jpg\", \"png\"]|-",
                            "208|OUTRAS_PLANTAS_PUB|Outras Plantas|Outras plantas|PECA_GRAFICA|[\"zip\"]|true",
                            "208|CARTOGRAMA_PUB|Cartograma|Cartograma|OUTROS|[\"pdf\", \"jpg\", \"png\"]|-",
                            "208|CARTOGRAMA_DEP|Cartograma|Cartograma|OUTROS|[\"pdf\", \"jpg\", \"png\"]|-");
            for (int procedimento : new int[] {204, 208}) {
                assertThat(linhas(ligacao, "SELECT codigo || '|' || ordem || '|' || ativo::text FROM documento_config "
                        + "WHERE pagina = 'PLANTAS' AND instrumento_procedimento_config_id = " + procedimento
                        + " ORDER BY ordem")).as("procedimento %d", procedimento).containsExactly(
                        "PLANTAS_ZIP_ORDENAMENTO_DEP|28|true", "PLANTAS_ZIP_CONDICIONANTES_DEP|29|true",
                        "PLANTAS_TXT_NOMES_DEP|30|true", "PLANTAS_CARTOGRAMA_DEP|31|true");
            }
        });
    }

    private static final String CARTOGRAMAS_DOS_DOCUMENTOS =
            "documento_config WHERE ativo AND codigo IN ('CARTOGRAMA_PUB', 'CARTOGRAMA_DEP')";

    private List<String> estadoDosCartogramas(Connection ligacao) throws Exception {
        return linhas(ligacao, "SELECT tipo_elemento || '|' || extensoes_aceites::text || '|' "
                + "|| coalesce((regras_tiff = " + regrasTiffIniciaisGerais() + ")::text, '-') || '|' || count(*) FROM "
                + CARTOGRAMAS_DOS_DOCUMENTOS + " GROUP BY tipo_elemento, extensoes_aceites::text, regras_tiff");
    }

    @Test
    void deveDarAoCartogramaDosDocumentosOutrosEmPdfJpgPngSemRegrasTiffERecusarOZip() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT id || ':' || n FROM (SELECT instrumento_procedimento_config_id AS id, "
                    + "count(*) AS n FROM " + CARTOGRAMAS_DOS_DOCUMENTOS + " GROUP BY instrumento_procedimento_config_id) p "
                    + "ORDER BY id")).containsExactly("204:2", "208:2", "2203:2", "2206:2", "3203:2", "3206:2", "3222:2",
                    "3226:2", "3239:2", "3242:2", "3255:2", "3258:2");
            assertThat(estadoDosCartogramas(ligacao)).containsExactly("OUTROS|[\"pdf\", \"jpg\", \"png\"]|-|24");
            assertThat(linhas(ligacao, "SELECT DISTINCT codigo || '|' || categoria || '|' || formato || '|' || separador "
                    + "|| '|' || nome || '|' || obrigatorio::text || '|' || link_publico_permitido::text FROM "
                    + CARTOGRAMAS_DOS_DOCUMENTOS + " ORDER BY 1")).containsExactly(
                    "CARTOGRAMA_DEP|DEPOSITO|NAO_EDITAVEL|Cartograma|Cartograma|true|false",
                    "CARTOGRAMA_PUB|PUBLICACAO|EDITAVEL|Cartograma|Cartograma|true|true");
            // As plantas de publicação continuam a pedir a ficha MOTU no procedimento final da Elaboração do PDM.
            assertThat(contar(ligacao, "documento_config WHERE ativo AND instrumento_procedimento_config_id = 204 "
                    + "AND tipo_elemento = 'PECA_GRAFICA' AND categoria = 'PUBLICACAO'")).isPositive();

            DocumentoConfig cartograma;
            try (Statement sql = ligacao.createStatement(); ResultSet linha = sql.executeQuery(
                    "SELECT codigo, categoria, tipo_elemento, formato, extensoes_aceites::text, ordem FROM documento_config "
                            + "WHERE codigo = 'CARTOGRAMA_PUB' AND instrumento_procedimento_config_id = 204")) {
                assertThat(linha.next()).isTrue();
                cartograma = new DocumentoConfig(null, linha.getString(1), linha.getString(1), linha.getString(1),
                        CategoriaDocumento.valueOf(linha.getString(2)), TipoElemento.valueOf(linha.getString(3)),
                        FormatoDocumento.valueOf(linha.getString(4)), linha.getString(5), linha.getInt(6));
            }
            assertThatThrownBy(() -> RegrasExtensaoDocumento.validarFicheiro("cartograma.zip", cartograma))
                    .isInstanceOf(ExtensaoNaoAceiteException.class).hasMessage("Extensão não aceite para este documento.");
            for (String ficheiro : List.of("cartograma.pdf", "cartograma.jpg", "cartograma.png")) {
                assertThatCode(() -> RegrasExtensaoDocumento.validarFicheiro(ficheiro, cartograma))
                        .as(ficheiro).doesNotThrowAnyException();
            }
        });
    }

    @Test
    void deveReverterOCartogramaDosDocumentosParaPecaGraficaEmZipComAsRegrasTiffDeOmissao() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA);
            ligacao.setAutoCommit(true);

            assertThat(estadoDosCartogramas(ligacao)).containsExactly("PECA_GRAFICA|[\"zip\"]|true|24");
            assertThat(contar(ligacao, "databasechangelog WHERE id = '2026-11-16-3-cartograma-sem-ficha-pdf-jpg-png'"))
                    .isZero();
        });
    }

    @Test
    void deveChamarPublicacaoFinalAoTipoDeProcedimentoElaboracaoSemMudarADinamicaEReverterOnome() throws Exception {
        comBaseMigrada(ligacao -> {
            String nomeDoProcedimento = "SELECT nome FROM tipo_procedimento WHERE codigo = 'ELABORACAO'";
            assertThat(texto(ligacao, nomeDoProcedimento)).isEqualTo("Publicação final");
            assertThat(texto(ligacao, "SELECT nome FROM tipo_dinamica WHERE codigo = 'ELABORACAO'")).isEqualTo("Elaboração");
            assertThat(texto(ligacao, "SELECT nome FROM tipo_procedimento WHERE codigo = 'REVISAO'")).isEqualTo("Revisão");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, nomeDoProcedimento)).isEqualTo("Elaboração");
            assertThat(contar(ligacao, "databasechangelog WHERE id = '2026-11-16-4-renomeia-elaboracao-publicacao-final'"))
                    .isZero();
        });
    }

    @Test
    void deveDarAAlteracaoSimplificadaDl10OsItensDaAlteracaoSimplificada() throws Exception {
        comBaseMigrada(ligacao -> {
            String semProcedimento = "SELECT codigo || '|' || separador || '|' || nome || '|' || categoria || '|' "
                    + "|| tipo_elemento || '|' || formato || '|' || obrigatorio::text || '|' || extensoes_aceites::text "
                    + "|| '|' || ordem || '|' || ativo::text || '|' || coalesce(elemento_informacao_base, '-') || '|' "
                    + "|| link_publico_permitido::text || '|' || tamanho_maximo_ficheiro || '|' || tamanho_maximo_total "
                    + "|| '|' || numero_maximo_ficheiros || '|' || coalesce(regras_tiff::text, '-') || '|' "
                    + "|| regra_nomenclatura FROM documento_config WHERE pagina = 'DOCUMENTOS' "
                    + "AND instrumento_procedimento_config_id = ";
            assertThat(linhas(ligacao, semProcedimento + "216 ORDER BY ordem")).hasSize(4)
                    .isEqualTo(linhas(ligacao, semProcedimento + "214 ORDER BY ordem"));
            assertThat(linhas(ligacao, semProcedimento + "217 ORDER BY ordem")).hasSize(17)
                    .isEqualTo(linhas(ligacao, semProcedimento + "215 ORDER BY ordem"));
        });
    }

    @Test
    void deveExigirOsElementosXEQaNasDuasCaixasEDeixarOpcionalSoORegulamentoIntegral() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || ':' || codigo FROM documento_config "
                    + "WHERE ativo AND NOT obrigatorio AND " + PDM_DA_LISTA_DGT
                    + " ORDER BY instrumento_procedimento_config_id, ordem")).containsExactly(
                    "215:REGULAMENTO_INTEGRAL_PUB", "215:REGULAMENTO_INTEGRAL_DEP",
                    "217:REGULAMENTO_INTEGRAL_PUB", "217:REGULAMENTO_INTEGRAL_DEP",
                    "218:REGULAMENTO_INTEGRAL_PUB", "218:REGULAMENTO_INTEGRAL_DEP",
                    "219:REGULAMENTO_INTEGRAL_PUB", "219:REGULAMENTO_INTEGRAL_DEP");
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE ativo AND obrigatorio "
                    + "AND categoria = 'DEPOSITO' AND " + PDM_DA_LISTA_DGT)).isEqualTo("114");
        });
    }

    @Test
    void deveGerarAsLinhasDosItensDoPdmPelasRegrasDaListaDaDgt() throws Exception {
        comBaseMigrada(ligacao -> {
            String publicacaoEscrita = "'[\"doc\", \"docx\", \"rtf\", \"odf\", \"odt\", \"xls\", \"xlsx\", \"csv\", \"ods\", "
                    + "\"pdf\", \"txt\"]'::jsonb";
            assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || ':' || codigo FROM documento_config "
                    + "WHERE ativo AND " + PDM_DA_LISTA_DGT + " AND NOT ("
                    + "categoria = CASE WHEN codigo LIKE '%\\_PUB' ESCAPE '\\' THEN 'PUBLICACAO' ELSE 'DEPOSITO' END "
                    + "AND formato = CASE WHEN categoria = 'PUBLICACAO' THEN 'EDITAVEL' ELSE 'NAO_EDITAVEL' END "
                    + "AND extensoes_aceites = CASE WHEN tipo_elemento = 'PECA_GRAFICA' THEN '[\"zip\"]'::jsonb "
                    + "WHEN codigo LIKE 'CARTOGRAMA\\_%' ESCAPE '\\' THEN '[\"pdf\", \"jpg\", \"png\"]'::jsonb "
                    + "WHEN categoria = 'PUBLICACAO' THEN " + publicacaoEscrita + " ELSE '[\"pdf\", \"jpg\", \"png\"]'::jsonb END "
                    + "AND (regras_tiff IS NOT NULL) = (tipo_elemento = 'PECA_GRAFICA') "
                    + "AND tamanho_maximo_ficheiro = 524288000 AND tamanho_maximo_total = 2147483648 "
                    + "AND numero_maximo_ficheiros = 10 AND regra_nomenclatura = 'GERAL' "
                    + "AND link_publico_permitido = (categoria = 'PUBLICACAO' OR codigo LIKE 'DELIBERACAO%' "
                    + "OR codigo IN ('DESPACHO_CCDR_DEP', 'RCM_DEP')))")).isEmpty();
            assertThat(texto(ligacao, "SELECT count(DISTINCT regras_tiff) FROM documento_config "
                    + "WHERE ativo AND tipo_elemento = 'PECA_GRAFICA' AND " + PDM_DA_LISTA_DGT)).isEqualTo("1");
        });
    }

    @Test
    void deveManterOsDocumentosLigadosAUmItemQueALinhaDaDgtDesativa() throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            inserirProcessoComDocumento(ligacao);
            String item = "SELECT id FROM documento_config WHERE instrumento_procedimento_config_id = 209 "
                    + "AND codigo = 'PLANTA_LOCALIZACAO_DEP'";
            executar(ligacao, "UPDATE processo_documento SET documento_config_id = (" + item + ") "
                    + "WHERE processo_id = '" + PROCESSO_ID + "'");

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT ativo::text FROM documento_config WHERE id = (" + item + ")"))
                    .isEqualTo("false");
            assertThat(texto(ligacao, "SELECT count(*) FROM processo_documento WHERE documento_config_id = (" + item + ")"))
                    .isEqualTo("1");
        });
    }

    @Test
    void deveReporOsItensDoPdmAoReverterEVoltarAAplicarALista() throws Exception {
        String todosOsItens = "SELECT instrumento_procedimento_config_id || '|' || " + ITEM_COMPLETO
                + " || '|' || coalesce(regras_tiff::text, '-') FROM documento_config "
                + "ORDER BY instrumento_procedimento_config_id, pagina, codigo";
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT);
            ligacao.setAutoCommit(true);
            List<String> comALista = linhas(ligacao, todosOsItens);

            reverterOsUltimos(ligacao, CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT);
            ligacao.setAutoCommit(true);

            List<String> semALista = linhas(ligacao, todosOsItens);
            assertThat(semALista).hasSize(comALista.size() - 31);
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config "
                    + "WHERE instrumento_procedimento_config_id IN (216, 217)")).isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE NOT ativo AND " + PDM_DA_LISTA_DGT))
                    .isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE obrigatorio AND categoria = 'DEPOSITO' "
                    + "AND " + PDM_DA_LISTA_DGT)).isEqualTo("0");
            assertThat(codigosPorOrdem(ligacao, 209)).startsWith("DELIBERACAO_PUB", "DELIBERACAO_DEP", "TEXTO_ATO_PUB",
                    "TEXTO_ATO_DEP", "PLANTA_LOCALIZACAO_PUB", "PLANTA_LOCALIZACAO_DEP");

            migrarOsProximos(ligacao, CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, todosOsItens)).isEqualTo(comALista);

            reverterOsUltimos(ligacao, CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, todosOsItens)).isEqualTo(semALista);
        });
    }

    /** Os Procedimentos das Dinâmicas temporárias e da Ratificação, cujos itens vêm do seed 2026-11-13-7. */
    private static final String PROCEDIMENTOS_TEMPORARIOS_E_DA_RATIFICACAO = "('PUBLICACAO_SUSPENSAO', "
            + "'PRORROGACAO_SUSPENSAO', 'PUBLICACAO_MP', 'PRORROGACAO_MP', 'PUBLICACAO_NP', 'PRORROGACAO_NP', "
            + "'PUBLICACAO_SUSPENSAO_GOVERNO', 'PRORROGACAO_SUSPENSAO_GOVERNO', 'RATIFICACAO')";

    /**
     * Os itens da página dos documentos de PU, PP, PDIM, PUIM e PPIM, com o tipo, a Dinâmica e o Procedimento, fora das
     * Dinâmicas temporárias e da Ratificação. Sem os atos pontuais, que a lista da DGT não cobre (a Prorrogação do ato
     * tem seed próprio no build).
     */
    private static final String ITENS_DE_PU_PP_E_INTERMUNICIPAIS = "FROM documento_config d "
            + "JOIN instrumento_procedimento_config ipc ON ipc.id = d.instrumento_procedimento_config_id "
            + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
            + "WHERE d.pagina = 'DOCUMENTOS' AND ti.codigo IN ('PU', 'PP', 'PDIM', 'PUIM', 'PPIM') AND NOT ipc.ato_pontual "
            + "AND tp.codigo NOT IN " + PROCEDIMENTOS_TEMPORARIOS_E_DA_RATIFICACAO;

    private List<String> codigosDe(Connection ligacao, String tipo, String dinamica, String procedimento)
            throws SQLException {
        return linhas(ligacao, "SELECT d.codigo " + ITENS_DE_PU_PP_E_INTERMUNICIPAIS + " AND ti.codigo = '" + tipo
                + "' AND td.codigo = '" + dinamica + "' AND tp.codigo = '" + procedimento + "' ORDER BY d.ordem");
    }

    @Test
    void deveDarAPuPpEIntermunicipaisOsTotaisDaListaDaDgtPorTipoEDinamica() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || count(*) " + ITENS_DE_PU_PP_E_INTERMUNICIPAIS
                    + " GROUP BY ti.codigo ORDER BY ti.codigo"))
                    .containsExactly("PDIM:161", "PP:179", "PPIM:158", "PU:175", "PUIM:154");
            List<String> esperado = new ArrayList<>();
            for (String tipo : List.of("PDIM", "PP", "PPIM", "PU", "PUIM")) {
                int planoCompleto = switch (tipo) {
                    case "PDIM" -> 41;
                    case "PP", "PPIM" -> 39;
                    default -> 37;
                };
                esperado.add(tipo + ":ALTERACAO:" + (tipo.equals("PDIM") ? 27 : 28));
                esperado.add(tipo + ":ALTERACAO_ADAPTACAO:14");
                esperado.add(tipo + ":ALTERACAO_SIMPLIFICADA_ART123:21");
                if (List.of("PU", "PP").contains(tipo)) {
                    esperado.add(tipo + ":ALTERACAO_SIMPLIFICADA_DL10_2024:21");
                }
                esperado.add(tipo + ":CORRECAO_MATERIAL:13");
                esperado.add(tipo + ":ELABORACAO:" + planoCompleto);
                esperado.add(tipo + ":REVISAO:" + planoCompleto);
                esperado.add(tipo + ":REVOGACAO:4");
            }
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || td.codigo || ':' || count(*) "
                    + ITENS_DE_PU_PP_E_INTERMUNICIPAIS + " GROUP BY ti.codigo, td.codigo ORDER BY ti.codigo COLLATE \"C\", td.codigo COLLATE \"C\""))
                    .containsExactlyElementsOf(esperado);
            assertThat(texto(ligacao, "SELECT count(*) FROM (SELECT 1 " + ITENS_DE_PU_PP_E_INTERMUNICIPAIS
                    + " GROUP BY d.instrumento_procedimento_config_id "
                    + "HAVING min(d.ordem) <> 1 OR max(d.ordem) <> count(*) OR count(DISTINCT d.ordem) <> count(*)) irregulares"))
                    .isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(*) FROM (SELECT 1 " + ITENS_DE_PU_PP_E_INTERMUNICIPAIS
                    + " GROUP BY d.instrumento_procedimento_config_id, d.codigo HAVING count(*) > 1) duplicados"))
                    .isEqualTo("0");
        });
    }

    @Test
    void deveOrdenarAPublicacaoDoPlanoDePuEPpPelaListaDaDgt() throws Exception {
        comBaseMigrada(ligacao -> {
            List<String> soDeposito = List.of("ESTUDOS_CARACTERIZACAO_DEP", "RELATORIO_PLANO_DEP", "RELATORIO_AMBIENTAL_DEP",
                    "PROGRAMA_EXECUCAO_DEP", "PLANTA_ENQUADRAMENTO_DEP", "PLANTA_SITUACAO_EXISTENTE_DEP",
                    "COMPROMISSOS_URBANISTICOS_DEP", "PLANTAS_INFRAESTRUTURAS_DEP", "ESTRUTURA_ECOLOGICA_AGLOMERADOS_DEP",
                    "MAPA_RUIDO_DEP", "RELATORIO_PONDERACAO_DEP", "PARECERES_ATA_DEP", "DELIBERACAO_CM_DEP");
            List<String> pu = new ArrayList<>(List.of("TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "DELIBERACAO_PUB",
                    "DELIBERACAO_DEP", "REGULAMENTO_PUB", "REGULAMENTO_DEP", "PLANTA_ZONAMENTO_PUB", "PLANTA_ZONAMENTO_DEP",
                    "PLANTA_CONDICIONANTES_PUB", "PLANTA_CONDICIONANTES_DEP", "OUTRAS_PLANTAS_PUB", "OUTRAS_PLANTAS_DEP",
                    "CARTOGRAMA_PUB", "CARTOGRAMA_DEP"));
            pu.addAll(soDeposito);
            List<String> pp = new ArrayList<>(List.of("TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "DELIBERACAO_PUB",
                    "DELIBERACAO_DEP", "REGULAMENTO_PUB", "REGULAMENTO_DEP", "PLANTA_IMPLANTACAO_PUB",
                    "PLANTA_IMPLANTACAO_DEP", "PLANTA_CONDICIONANTES_PUB", "PLANTA_CONDICIONANTES_DEP",
                    "PLANTA_TRANSFORMACAO_FUNDIARIA_PUB", "PLANTA_TRANSFORMACAO_FUNDIARIA_DEP", "OUTRAS_PLANTAS_PUB",
                    "OUTRAS_PLANTAS_DEP", "CARTOGRAMA_PUB", "CARTOGRAMA_DEP"));
            pp.addAll(soDeposito);

            for (String tipo : List.of("PU", "PUIM")) {
                assertThat(codigosDe(ligacao, tipo, "ELABORACAO", "ELABORACAO")).as(tipo).containsExactlyElementsOf(pu);
                assertThat(codigosDe(ligacao, tipo, "REVISAO", "REVISAO")).as(tipo).containsExactlyElementsOf(pu);
            }
            for (String tipo : List.of("PP", "PPIM")) {
                assertThat(codigosDe(ligacao, tipo, "ELABORACAO", "ELABORACAO")).as(tipo).containsExactlyElementsOf(pp);
                assertThat(codigosDe(ligacao, tipo, "REVISAO", "REVISAO")).as(tipo).containsExactlyElementsOf(pp);
            }
            assertThat(codigosDe(ligacao, "PU", "ALTERACAO", "ALTERACAO")).containsExactly("TEXTO_ATO_PUB",
                    "TEXTO_ATO_DEP", "DELIBERACAO_PUB", "DELIBERACAO_DEP", "ALTERACOES_REGULAMENTO_PUB",
                    "ALTERACOES_REGULAMENTO_DEP", "REGULAMENTO_INTEGRAL_PUB", "REGULAMENTO_INTEGRAL_DEP",
                    "PLANTA_ZONAMENTO_PUB", "PLANTA_ZONAMENTO_DEP", "PLANTA_CONDICIONANTES_PUB", "PLANTA_CONDICIONANTES_DEP",
                    "RELATORIO_FUNDAMENTACAO_DEP", "DEMAIS_ELEMENTOS_DEP", "RELATORIO_AMBIENTAL_DEP",
                    "RELATORIO_PONDERACAO_DEP", "DELIBERACAO_CM_DEP", "PARECERES_ATA_DEP");
        });
    }

    @Test
    void deveDarSoAoPdimOsItensDoPlanoDiretorEAosOutrosOsDosPlanosDeUrbanizacaoEPormenor() throws Exception {
        comBaseMigrada(ligacao -> assertThat(linhas(ligacao, "SELECT d.codigo || ':' || string_agg(ti.codigo, ',' "
                + "ORDER BY ti.codigo) " + ITENS_DE_PU_PP_E_INTERMUNICIPAIS + " AND td.codigo = 'ELABORACAO' "
                + "AND tp.codigo = 'ELABORACAO' AND d.codigo IN ('PLANTA_ORDENAMENTO_PUB', 'PLANTA_ORDENAMENTO_DEP', "
                + "'ESTRUTURA_ECOLOGICA_DEP', 'CARTA_EDUCATIVA_DEP', 'PARECER_CA_DEP', 'PARECER_CCDR_DEP', "
                + "'PLANTAS_INFRAESTRUTURAS_DEP', 'ESTRUTURA_ECOLOGICA_AGLOMERADOS_DEP') GROUP BY d.codigo ORDER BY d.codigo COLLATE \"C\""))
                .containsExactly("CARTA_EDUCATIVA_DEP:PDIM", "ESTRUTURA_ECOLOGICA_AGLOMERADOS_DEP:PP,PPIM,PU,PUIM",
                        "ESTRUTURA_ECOLOGICA_DEP:PDIM", "PARECER_CA_DEP:PDIM", "PARECER_CCDR_DEP:PDIM",
                        "PLANTAS_INFRAESTRUTURAS_DEP:PP,PPIM,PU,PUIM", "PLANTA_ORDENAMENTO_DEP:PDIM",
                        "PLANTA_ORDENAMENTO_PUB:PDIM"));
    }

    @Test
    void deveDarAConstituicaoDaComissaoConsultivaSoAoPdimComOTextoEODespachoObrigatorios() throws Exception {
        comBaseMigrada(ligacao -> {
            List<String> esperado = new ArrayList<>();
            for (String dinamica : List.of("ELABORACAO", "REVISAO")) {
                for (String codigo : List.of("TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "DESPACHO_CCDR_PUB", "DESPACHO_CCDR_DEP")) {
                    esperado.add("PDIM:" + dinamica + ":" + codigo + ":true:-");
                }
            }
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || td.codigo || ':' || d.codigo || ':' "
                    + "|| d.obrigatorio::text || ':' || coalesce(d.elemento_informacao_base, '-') "
                    + ITENS_DE_PU_PP_E_INTERMUNICIPAIS + " AND tp.codigo = 'CONSTITUICAO_CC' "
                    + "ORDER BY ti.codigo, td.codigo, d.ordem")).containsExactlyElementsOf(esperado);
        });
    }

    @Test
    void deveExigirNaDecisaoDeInicioDaAlteracaoDePuEPpOTextoEADeliberacaoNasDuasCaixasSemAPlanta() throws Exception {
        comBaseMigrada(ligacao -> {
            for (String procedimento : List.of(PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PU,
                    PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PP)) {
                assertThat(linhas(ligacao, "SELECT codigo || '|' || ordem || '|' || obrigatorio::text || '|' || separador "
                        + "FROM documento_config WHERE instrumento_procedimento_config_id = " + procedimento
                        + " ORDER BY ordem")).as(procedimento).containsExactly(
                        "TEXTO_ATO_PUB|1|true|Texto do Ato", "TEXTO_ATO_DEP|2|true|Texto do Ato",
                        "DELIBERACAO_PUB|3|true|Deliberação", "DELIBERACAO_DEP|4|true|Deliberação",
                        "PLANTA_LOCALIZACAO_PUB|5|false|Planta de Localização",
                        "PLANTA_LOCALIZACAO_DEP|6|false|Planta de Localização");
            }
            assertThat(codigosDe(ligacao, "PUIM", "ALTERACAO", "DECISAO_INICIO")).containsExactly("TEXTO_ATO_PUB",
                    "TEXTO_ATO_DEP", "DELIBERACAO_PUB", "DELIBERACAO_DEP", "PLANTA_LOCALIZACAO_PUB",
                    "PLANTA_LOCALIZACAO_DEP");
            assertThat(codigosDe(ligacao, "PDIM", "ALTERACAO", "DECISAO_INICIO")).containsExactly("TEXTO_ATO_PUB",
                    "TEXTO_ATO_DEP", "DELIBERACAO_PUB", "DELIBERACAO_DEP");
        });
    }

    @Test
    void deveDarAAlteracaoSimplificadaDl10DePuEPpOsItensDaAlteracaoSimplificada() throws Exception {
        comBaseMigrada(ligacao -> {
            String itens = "SELECT d.codigo || '|' || d.nome || '|' || d.obrigatorio::text "
                    + ITENS_DE_PU_PP_E_INTERMUNICIPAIS + " AND ti.codigo = '%s' AND tp.codigo = '%s' ORDER BY d.ordem";
            for (String tipo : List.of("PU", "PP")) {
                List<String> publicitacao = linhas(ligacao, itens.formatted(tipo, "DECISAO_INICIO")
                        .replace("AND tp.codigo", "AND td.codigo = 'ALTERACAO_SIMPLIFICADA_ART123' AND tp.codigo"));
                List<String> publicacao = linhas(ligacao, itens.formatted(tipo, "ALTERACAO_SIMPLIFICADA"));
                assertThat(publicitacao).as(tipo).hasSize(4);
                assertThat(publicacao).as(tipo).hasSize(17);
                assertThat(linhas(ligacao, itens.formatted(tipo, "ABERTURA_DISCUSSAO_PUBLICA_DL10"))).as(tipo)
                        .isEqualTo(publicitacao);
                assertThat(linhas(ligacao, itens.formatted(tipo, "ALTERACAO_SIMPLIFICADA_DL10"))).as(tipo)
                        .isEqualTo(publicacao);
            }
        });
    }

    @Test
    void deveExigirOsElementosXEQaNasDuasCaixasEDeixarOpcionaisSoOsElementosO() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT DISTINCT left(d.codigo, length(d.codigo) - 4) || ':' || tp.codigo "
                    + ITENS_DE_PU_PP_E_INTERMUNICIPAIS + " AND NOT d.obrigatorio ORDER BY 1")).containsExactlyInAnyOrder(
                    "PLANTA_AREA_INTERVENCAO:DECISAO_INICIO", "PLANTA_LOCALIZACAO:DECISAO_INICIO",
                    "REGULAMENTO_INTEGRAL:ALTERACAO_ADAPTACAO", "REGULAMENTO_INTEGRAL:ALTERACAO_SIMPLIFICADA",
                    "REGULAMENTO_INTEGRAL:ALTERACAO_SIMPLIFICADA_DL10", "REGULAMENTO_INTEGRAL:CORRECAO_MATERIAL");
            assertThat(texto(ligacao, "SELECT count(*) " + ITENS_DE_PU_PP_E_INTERMUNICIPAIS + " AND NOT d.obrigatorio"))
                    .isEqualTo("58");
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || td.codigo " + ITENS_DE_PU_PP_E_INTERMUNICIPAIS
                    + " AND d.codigo LIKE 'PLANTA\\_AREA\\_INTERVENCAO%' ESCAPE '\\' AND d.obrigatorio")).isEmpty();
            assertThat(texto(ligacao, "SELECT count(*) FROM (SELECT 1 " + ITENS_DE_PU_PP_E_INTERMUNICIPAIS
                    + " GROUP BY d.instrumento_procedimento_config_id, left(d.codigo, length(d.codigo) - 4) "
                    + "HAVING bool_or(d.obrigatorio) <> bool_and(d.obrigatorio)) caixas_diferentes")).isEqualTo("0");
        });
    }

    @Test
    void deveDarInformacaoBaseSoAoTextoEADeliberacaoDasDinamicasQueAAlimentam() throws Exception {
        comBaseMigrada(ligacao -> {
            List<String> esperado = new ArrayList<>();
            for (String[] dinamica : new String[][] {{"ALTERACAO", "ALTERACAO"}, {"ELABORACAO", "ELABORACAO"},
                    {"REVISAO", "REVISAO"}}) {
                for (String procedimento : new java.util.TreeSet<>(List.of("ABERTURA_DISCUSSAO_PUBLICA", "DECISAO_INICIO",
                        dinamica[1]))) {
                    for (String codigo : List.of("DELIBERACAO_DEP", "DELIBERACAO_PUB", "TEXTO_ATO_DEP", "TEXTO_ATO_PUB")) {
                        esperado.add(dinamica[0] + ":" + procedimento + ":" + codigo + ":"
                                + codigo.substring(0, codigo.length() - 4));
                    }
                }
            }
            assertThat(linhas(ligacao, "SELECT DISTINCT td.codigo || ':' || tp.codigo || ':' || d.codigo || ':' "
                    + "|| d.elemento_informacao_base " + ITENS_DE_PU_PP_E_INTERMUNICIPAIS
                    + " AND d.elemento_informacao_base IS NOT NULL ORDER BY 1")).containsExactlyInAnyOrderElementsOf(esperado);
            assertThat(texto(ligacao, "SELECT count(*) " + ITENS_DE_PU_PP_E_INTERMUNICIPAIS
                    + " AND d.elemento_informacao_base IS NOT NULL")).isEqualTo("180");
        });
    }

    @Test
    void deveGerarAsLinhasDePuPpEIntermunicipaisPelasRegrasDaListaDaDgt() throws Exception {
        comBaseMigrada(ligacao -> {
            String publicacaoEscrita = "'[\"doc\", \"docx\", \"rtf\", \"odf\", \"odt\", \"xls\", \"xlsx\", \"csv\", \"ods\", "
                    + "\"pdf\", \"txt\"]'::jsonb";
            String inseridas = ITENS_DE_PU_PP_E_INTERMUNICIPAIS + " AND d.instrumento_procedimento_config_id NOT IN ("
                    + PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PU + ", " + PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PP + ")";
            assertThat(texto(ligacao, "SELECT count(*) " + inseridas)).isEqualTo("815");
            assertThat(linhas(ligacao, "SELECT d.instrumento_procedimento_config_id || ':' || d.codigo " + inseridas
                    + " AND NOT (d.categoria = CASE WHEN d.codigo LIKE '%\\_PUB' ESCAPE '\\' THEN 'PUBLICACAO' "
                    + "ELSE 'DEPOSITO' END "
                    + "AND d.formato = CASE WHEN d.categoria = 'PUBLICACAO' THEN 'EDITAVEL' ELSE 'NAO_EDITAVEL' END "
                    + "AND d.extensoes_aceites = CASE WHEN d.tipo_elemento = 'PECA_GRAFICA' THEN '[\"zip\"]'::jsonb "
                    + "WHEN d.codigo LIKE 'CARTOGRAMA\\_%' ESCAPE '\\' THEN '[\"pdf\", \"jpg\", \"png\"]'::jsonb "
                    + "WHEN d.categoria = 'PUBLICACAO' THEN " + publicacaoEscrita
                    + " ELSE '[\"pdf\", \"jpg\", \"png\"]'::jsonb END "
                    + "AND (d.regras_tiff IS NOT NULL) = (d.tipo_elemento = 'PECA_GRAFICA') "
                    + "AND d.tamanho_maximo_ficheiro = 524288000 AND d.tamanho_maximo_total = 2147483648 "
                    + "AND d.numero_maximo_ficheiros = 10 AND d.regra_nomenclatura = 'GERAL' AND d.ativo "
                    + "AND d.link_publico_permitido = (d.categoria = 'PUBLICACAO' OR d.codigo LIKE 'DELIBERACAO%' "
                    + "OR d.codigo = 'DESPACHO_CCDR_DEP'))")).isEmpty();
            assertThat(texto(ligacao, "SELECT count(DISTINCT d.regras_tiff) " + inseridas
                    + " AND d.tipo_elemento = 'PECA_GRAFICA'")).isEqualTo("1");
            assertThat(linhas(ligacao, "SELECT DISTINCT d.codigo " + inseridas + " AND d.descricao IS NOT NULL "
                    + "ORDER BY 1")).containsExactlyInAnyOrder("COMPROMISSOS_URBANISTICOS_DEP", "ESTUDOS_CARACTERIZACAO_DEP",
                    "PLANTAS_INFRAESTRUTURAS_DEP", "PLANTA_ENQUADRAMENTO_DEP", "PLANTA_SITUACAO_EXISTENTE_DEP",
                    "PROGRAMA_EXECUCAO_DEP", "RELATORIO_AMBIENTAL_DEP", "RELATORIO_PLANO_DEP", "TEXTO_ATO_DEP",
                    "TEXTO_ATO_PUB");
            assertThat(linhas(ligacao, "SELECT DISTINCT td.codigo || ':' || d.nome " + inseridas
                    + " AND tp.codigo = 'DECISAO_INICIO' AND d.codigo = 'DELIBERACAO_PUB' AND td.codigo IN "
                    + "('ELABORACAO', 'REVISAO') ORDER BY 1")).containsExactly(
                    "ELABORACAO:Deliberação do início de elaboração de plano territorial (CM/CIM)",
                    "REVISAO:Deliberação do início de revisão de plano territorial (CM/CIM)");
        });
    }

    @Test
    void deveDarADecisaoDeInicioDaElaboracaoDePuTresSeparadoresEQuatroItensObrigatorios() throws Exception {
        comBaseMigrada(ligacao -> {
            String decisao = ITENS_DE_PU_PP_E_INTERMUNICIPAIS
                    + " AND ti.codigo = 'PU' AND td.codigo = 'ELABORACAO' AND tp.codigo = 'DECISAO_INICIO'";
            assertThat(linhas(ligacao, "SELECT d.separador " + decisao + " GROUP BY d.separador ORDER BY min(d.ordem)"))
                    .containsExactly("Texto do Ato", "Deliberação", "Planta com Área de Intervenção");
            assertThat(linhas(ligacao, "SELECT d.codigo " + decisao + " AND d.obrigatorio ORDER BY d.ordem"))
                    .containsExactly("TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "DELIBERACAO_PUB", "DELIBERACAO_DEP");
        });
    }

    @Test
    void deveReporPuPpEIntermunicipaisAoReverterEVoltarAAplicarALista() throws Exception {
        String todosOsItens = "SELECT instrumento_procedimento_config_id || '|' || " + ITEM_COMPLETO
                + " || '|' || coalesce(descricao, '-') || '|' || coalesce(regras_tiff::text, '-') FROM documento_config "
                + "ORDER BY instrumento_procedimento_config_id, pagina, codigo";
        String doPdm = "SELECT instrumento_procedimento_config_id || '|' || " + ITEM_COMPLETO
                + " FROM documento_config WHERE " + PDM_DA_LISTA_DGT + " ORDER BY instrumento_procedimento_config_id, codigo";
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT);
            ligacao.setAutoCommit(true);
            List<String> comALista = linhas(ligacao, todosOsItens);
            List<String> pdm = linhas(ligacao, doPdm);

            reverterOsUltimos(ligacao, CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT);
            ligacao.setAutoCommit(true);

            List<String> semALista = linhas(ligacao, todosOsItens);
            assertThat(semALista).hasSize(comALista.size() - 815);
            assertThat(texto(ligacao, "SELECT count(*) " + ITENS_DE_PU_PP_E_INTERMUNICIPAIS)).isEqualTo("12");
            assertThat(linhas(ligacao, doPdm)).isEqualTo(pdm);
            for (String procedimento : List.of(PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PU,
                    PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PP)) {
                assertThat(linhas(ligacao, "SELECT codigo || '|' || ordem || '|' || obrigatorio::text "
                        + "FROM documento_config WHERE instrumento_procedimento_config_id = " + procedimento
                        + " ORDER BY ordem")).as(procedimento).containsExactly("DELIBERACAO_PUB|1|true",
                        "DELIBERACAO_DEP|2|false", "TEXTO_ATO_PUB|3|true", "TEXTO_ATO_DEP|4|false",
                        "PLANTA_LOCALIZACAO_PUB|5|true", "PLANTA_LOCALIZACAO_DEP|6|false");
            }

            migrarOsProximos(ligacao, CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, todosOsItens)).isEqualTo(comALista);
        });
    }

    /** Os itens da página dos documentos das Dinâmicas temporárias e da Ratificação dos seis planos territoriais. */
    private static final String ITENS_TEMPORARIOS_E_DA_RATIFICACAO = "FROM documento_config d "
            + "JOIN instrumento_procedimento_config ipc ON ipc.id = d.instrumento_procedimento_config_id "
            + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
            + "WHERE d.pagina = 'DOCUMENTOS' AND ti.codigo IN ('PDM', 'PU', 'PP', 'PDIM', 'PUIM', 'PPIM') "
            + "AND tp.codigo IN " + PROCEDIMENTOS_TEMPORARIOS_E_DA_RATIFICACAO;

    private List<String> itensTemporarios(Connection ligacao, String tipo, String procedimento, String colunas)
            throws SQLException {
        return linhas(ligacao, "SELECT " + colunas + " " + ITENS_TEMPORARIOS_E_DA_RATIFICACAO + " AND ti.codigo = '"
                + tipo + "' AND tp.codigo = '" + procedimento + "' ORDER BY d.ordem");
    }

    @Test
    void deveDarAosSeisPlanosOsTotaisDaListaDaDgtNasDinamicasTemporariasENaRatificacao() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || count(*) " + ITENS_TEMPORARIOS_E_DA_RATIFICACAO
                    + " GROUP BY ti.codigo")).containsExactlyInAnyOrder("PDM:63", "PDIM:63", "PU:49", "PP:49", "PUIM:49",
                    "PPIM:49");
            List<String> esperado = new ArrayList<>();
            for (String tipo : List.of("PDM", "PU", "PP", "PDIM", "PUIM", "PPIM")) {
                esperado.addAll(List.of(tipo + ":SUSPENSAO:PUBLICACAO_SUSPENSAO:12",
                        tipo + ":SUSPENSAO:PRORROGACAO_SUSPENSAO:4", tipo + ":MEDIDAS_PREVENTIVAS:PUBLICACAO_MP:9",
                        tipo + ":MEDIDAS_PREVENTIVAS:PRORROGACAO_MP:4", tipo + ":NORMAS_PROVISORIAS:PUBLICACAO_NP:8",
                        tipo + ":NORMAS_PROVISORIAS:PRORROGACAO_NP:4",
                        tipo + ":SUSPENSAO_GOVERNO:PUBLICACAO_SUSPENSAO_GOVERNO:6",
                        tipo + ":SUSPENSAO_GOVERNO:PRORROGACAO_SUSPENSAO_GOVERNO:2"));
            }
            esperado.addAll(List.of("PDM:RATIFICACAO:RATIFICACAO:14", "PDIM:RATIFICACAO:RATIFICACAO:14"));
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || td.codigo || ':' || tp.codigo || ':' || count(*) "
                    + ITENS_TEMPORARIOS_E_DA_RATIFICACAO + " GROUP BY ti.codigo, td.codigo, tp.codigo"))
                    .containsExactlyInAnyOrderElementsOf(esperado);
            assertThat(linhas(ligacao, "SELECT tp.codigo || ':' || count(DISTINCT ti.codigo) || ':' "
                    + "|| count(DISTINCT d.ordem || '|' || d.codigo || '|' || d.separador || '|' || d.nome || '|' "
                    + "|| d.obrigatorio::text || '|' || d.tipo_elemento) "
                    + ITENS_TEMPORARIOS_E_DA_RATIFICACAO + " GROUP BY tp.codigo")).containsExactlyInAnyOrder(
                    "PUBLICACAO_SUSPENSAO:6:12", "PRORROGACAO_SUSPENSAO:6:4", "PUBLICACAO_MP:6:9", "PRORROGACAO_MP:6:4",
                    "PUBLICACAO_NP:6:8", "PRORROGACAO_NP:6:4", "PUBLICACAO_SUSPENSAO_GOVERNO:6:6",
                    "PRORROGACAO_SUSPENSAO_GOVERNO:6:2", "RATIFICACAO:2:14");
        });
    }

    @Test
    void deveOrdenarAsMedidasPreventivasDoPdmPelaListaDaDgtTodasObrigatorias() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(itensTemporarios(ligacao, "PDM", "PUBLICACAO_MP", "d.codigo || ':' || d.obrigatorio::text"))
                    .containsExactly("TEXTO_ATO_PUB:true", "TEXTO_ATO_DEP:true", "DELIBERACAO_PUB:true",
                            "DELIBERACAO_DEP:true", "TEXTO_MEDIDAS_PREVENTIVAS_PUB:true",
                            "TEXTO_MEDIDAS_PREVENTIVAS_DEP:true", "PLANTA_DELIMITACAO_PUB:true",
                            "PLANTA_DELIMITACAO_DEP:true", "FUNDAMENTACAO_DEP:true");
            assertThat(itensTemporarios(ligacao, "PDM", "PUBLICACAO_MP", "d.ordem::text"))
                    .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
        });
    }

    @Test
    void deveDarASuspensaoDoMunicipioOsOitoElementosComQuatroSoEmDeposito() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(itensTemporarios(ligacao, "PU", "PUBLICACAO_SUSPENSAO", "d.codigo")).containsExactly(
                    "TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "DELIBERACAO_PUB", "DELIBERACAO_DEP",
                    "TEXTO_MEDIDAS_PREVENTIVAS_PUB", "TEXTO_MEDIDAS_PREVENTIVAS_DEP", "PLANTA_DELIMITACAO_PUB",
                    "PLANTA_DELIMITACAO_DEP", "FUNDAMENTACAO_DEP", "DELIBERACAO_CM_ELABORACAO_DEP", "PARECER_CCDR_DEP",
                    "DELIBERACAO_CM_SUSPENSAO_DEP");
            assertThat(linhas(ligacao, "SELECT DISTINCT d.separador " + ITENS_TEMPORARIOS_E_DA_RATIFICACAO
                    + " AND ti.codigo = 'PU' AND tp.codigo = 'PUBLICACAO_SUSPENSAO'")).containsExactlyInAnyOrder(
                    "Texto do Ato", "Deliberação", "Texto das Medidas Preventivas", "Planta de Delimitação",
                    "Fundamentação", "Deliberação da CM", "Parecer da CCDR");
            assertThat(texto(ligacao, "SELECT count(*) " + ITENS_TEMPORARIOS_E_DA_RATIFICACAO
                    + " AND tp.codigo = 'PUBLICACAO_SUSPENSAO' AND NOT d.obrigatorio")).isEqualTo("0");
        });
    }

    @Test
    void deveDarAsProrrogacoesOTextoEADeliberacaoEAProrrogacaoDoGovernoSoARcm() throws Exception {
        comBaseMigrada(ligacao -> {
            for (String tipo : List.of("PDM", "PU", "PP", "PDIM", "PUIM", "PPIM")) {
                for (String procedimento : List.of("PRORROGACAO_SUSPENSAO", "PRORROGACAO_MP", "PRORROGACAO_NP")) {
                    assertThat(itensTemporarios(ligacao, tipo, procedimento, "d.codigo")).as(tipo + ":" + procedimento)
                            .containsExactly("TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "DELIBERACAO_PUB", "DELIBERACAO_DEP");
                }
                assertThat(itensTemporarios(ligacao, tipo, "PRORROGACAO_SUSPENSAO_GOVERNO", "d.codigo")).as(tipo)
                        .containsExactly("RCM_PUB", "RCM_DEP");
            }
            assertThat(itensTemporarios(ligacao, "PDM", "PRORROGACAO_MP", "d.nome")).contains(
                    "Deliberação de aprovação da prorrogação das Medidas Preventivas");
        });
    }

    @Test
    void deveDarARatificacaoSoAoPdmEAoPdimComORegulamentoIntegralOpcional() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(itensTemporarios(ligacao, "PDM", "RATIFICACAO",
                    "d.codigo || ':' || d.categoria || ':' || d.obrigatorio::text")).containsExactly(
                    "TEXTO_ATO_PUB:PUBLICACAO:true", "TEXTO_ATO_DEP:DEPOSITO:true", "RCM_PUB:PUBLICACAO:true",
                    "RCM_DEP:DEPOSITO:true", "ALTERACOES_REGULAMENTO_PUB:PUBLICACAO:true",
                    "ALTERACOES_REGULAMENTO_DEP:DEPOSITO:true", "REGULAMENTO_INTEGRAL_PUB:PUBLICACAO:false",
                    "REGULAMENTO_INTEGRAL_DEP:DEPOSITO:false", "PLANTA_ORDENAMENTO_PUB:PUBLICACAO:true",
                    "PLANTA_ORDENAMENTO_DEP:DEPOSITO:true", "PLANTA_CONDICIONANTES_PUB:PUBLICACAO:true",
                    "PLANTA_CONDICIONANTES_DEP:DEPOSITO:true", "DEMAIS_ELEMENTOS_DEP:DEPOSITO:true",
                    "DELIBERACAO_DEP:DEPOSITO:true");
            assertThat(itensTemporarios(ligacao, "PDM", "RATIFICACAO", "d.tipo_elemento")).contains("OUTROS");
            assertThat(texto(ligacao, "SELECT count(*) " + ITENS_TEMPORARIOS_E_DA_RATIFICACAO
                    + " AND tp.codigo = 'RATIFICACAO' AND ti.codigo NOT IN ('PDM', 'PDIM')")).isEqualTo("0");
        });
    }

    @Test
    void deveDeixarSemItensASuspensaoDaCcdrEAsMedidasPreventivasDoGovernoDosPlanos() throws Exception {
        comBaseMigrada(ligacao -> assertThat(texto(ligacao, "SELECT count(*) FROM documento_config d "
                + "JOIN instrumento_procedimento_config ipc ON ipc.id = d.instrumento_procedimento_config_id "
                + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                + "WHERE ti.codigo IN ('PDM', 'PU', 'PP', 'PDIM', 'PUIM', 'PPIM') "
                + "AND td.codigo IN ('SUSPENSAO_CCDR', 'MEDIDAS_PREVENTIVAS_GOVERNO')")).isEqualTo("0"));
    }

    @Test
    void deveGerarAsLinhasTemporariasEDaRatificacaoPelasRegrasDaListaDaDgt() throws Exception {
        comBaseMigrada(ligacao -> {
            String publicacaoEscrita = "'[\"doc\", \"docx\", \"rtf\", \"odf\", \"odt\", \"xls\", \"xlsx\", \"csv\", \"ods\", "
                    + "\"pdf\", \"txt\"]'::jsonb";
            assertThat(texto(ligacao, "SELECT count(*) " + ITENS_TEMPORARIOS_E_DA_RATIFICACAO)).isEqualTo("322");
            assertThat(texto(ligacao, "SELECT count(*) " + ITENS_TEMPORARIOS_E_DA_RATIFICACAO
                    + " AND d.elemento_informacao_base IS NOT NULL")).isEqualTo("0");
            assertThat(linhas(ligacao, "SELECT d.instrumento_procedimento_config_id || ':' || d.codigo "
                    + ITENS_TEMPORARIOS_E_DA_RATIFICACAO
                    + " AND NOT (d.categoria = CASE WHEN d.codigo LIKE '%\\_PUB' ESCAPE '\\' THEN 'PUBLICACAO' "
                    + "ELSE 'DEPOSITO' END "
                    + "AND d.formato = CASE WHEN d.categoria = 'PUBLICACAO' THEN 'EDITAVEL' ELSE 'NAO_EDITAVEL' END "
                    + "AND d.extensoes_aceites = CASE WHEN d.tipo_elemento = 'PECA_GRAFICA' THEN '[\"zip\"]'::jsonb "
                    + "WHEN d.categoria = 'PUBLICACAO' THEN " + publicacaoEscrita
                    + " ELSE '[\"pdf\", \"jpg\", \"png\"]'::jsonb END "
                    + "AND (d.regras_tiff IS NOT NULL) = (d.tipo_elemento = 'PECA_GRAFICA') "
                    + "AND d.tamanho_maximo_ficheiro = 524288000 AND d.tamanho_maximo_total = 2147483648 "
                    + "AND d.numero_maximo_ficheiros = 10 AND d.regra_nomenclatura = 'GERAL' AND d.ativo "
                    + "AND d.link_publico_permitido = (d.categoria = 'PUBLICACAO' OR d.codigo LIKE 'DELIBERACAO%' "
                    + "OR d.codigo = 'RCM_DEP'))")).isEmpty();
            assertThat(linhas(ligacao, "SELECT DISTINCT tp.codigo " + ITENS_TEMPORARIOS_E_DA_RATIFICACAO
                    + " AND d.codigo = 'RCM_DEP' AND d.link_publico_permitido")).containsExactlyInAnyOrder(
                    "RATIFICACAO", "PUBLICACAO_SUSPENSAO_GOVERNO", "PRORROGACAO_SUSPENSAO_GOVERNO");
            assertThat(texto(ligacao, "SELECT count(DISTINCT d.regras_tiff) " + ITENS_TEMPORARIOS_E_DA_RATIFICACAO
                    + " AND d.tipo_elemento = 'PECA_GRAFICA'")).isEqualTo("1");
            assertThat(linhas(ligacao, "SELECT DISTINCT d.codigo " + ITENS_TEMPORARIOS_E_DA_RATIFICACAO
                    + " AND d.descricao IS NOT NULL")).containsExactlyInAnyOrder("DELIBERACAO_CM_ELABORACAO_DEP",
                    "DELIBERACAO_CM_SUSPENSAO_DEP");
        });
    }

    @Test
    void deveReporOsItensTemporariosEDaRatificacaoAoReverterEVoltarAAplicarALista() throws Exception {
        String todosOsItens = "SELECT instrumento_procedimento_config_id || '|' || " + ITEM_COMPLETO
                + " || '|' || coalesce(descricao, '-') || '|' || coalesce(regras_tiff::text, '-') FROM documento_config "
                + "ORDER BY instrumento_procedimento_config_id, pagina, codigo";
        comBaseMigrada(ligacao -> {
            // Os changesets do build de 13/11 vêm a seguir à lista das temporárias: revertem-se antes dela.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE);
            ligacao.setAutoCommit(true);
            List<String> comALista = linhas(ligacao, todosOsItens);

            reverterOsUltimos(ligacao, CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT);
            ligacao.setAutoCommit(true);

            List<String> semALista = linhas(ligacao, todosOsItens);
            assertThat(semALista).hasSize(comALista.size() - 322);
            assertThat(texto(ligacao, "SELECT count(*) " + ITENS_TEMPORARIOS_E_DA_RATIFICACAO)).isEqualTo("0");
            assertThat(comALista).containsAll(semALista);

            migrarOsProximos(ligacao, CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, todosOsItens)).isEqualTo(comALista);
        });
    }

    /**
     * Os itens da página dos documentos dos programas e dos programas intermunicipais, do seed 2026-11-14-1, sem os atos
     * pontuais, que a lista da DGT não cobre.
     */
    private static final String ITENS_DOS_PROGRAMAS = "FROM documento_config d "
            + "JOIN instrumento_procedimento_config ipc ON ipc.id = d.instrumento_procedimento_config_id "
            + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
            + "WHERE d.pagina = 'DOCUMENTOS' AND ti.codigo IN ('PNPOT', 'PS', 'PROT', 'PEOT', 'PIMOT') AND NOT ipc.ato_pontual";

    private List<String> itensDoPrograma(Connection ligacao, String tipo, String dinamica, String procedimento,
            String colunas) throws SQLException {
        return linhas(ligacao, "SELECT " + colunas + " " + ITENS_DOS_PROGRAMAS + " AND ti.codigo = '" + tipo
                + "' AND td.codigo = '" + dinamica + "' AND tp.codigo = '" + procedimento + "' ORDER BY d.ordem");
    }

    @Test
    void deveDarAosProgramasOsTotaisDaListaDaDgtPorTipoEPorDinamica() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || count(*) " + ITENS_DOS_PROGRAMAS + " GROUP BY ti.codigo"))
                    .containsExactlyInAnyOrder("PNPOT:64", "PS:84", "PROT:82", "PEOT:96", "PIMOT:96");
            List<String> esperado = new ArrayList<>(List.of("PNPOT:REVISAO:15", "PNPOT:ALTERACAO:15",
                    "PS:ELABORACAO:17", "PS:REVISAO:17", "PS:ALTERACAO:16", "PROT:ELABORACAO:16", "PROT:REVISAO:16",
                    "PROT:ALTERACAO:16", "PEOT:ELABORACAO:23", "PEOT:REVISAO:23", "PEOT:ALTERACAO:16",
                    "PIMOT:ELABORACAO:30", "PIMOT:REVISAO:26", "PIMOT:ALTERACAO:16", "PIMOT:ALTERACAO_ADAPTACAO:10",
                    "PIMOT:CORRECAO_MATERIAL:10", "PIMOT:REVOGACAO:4"));
            for (String tipo : List.of("PNPOT", "PS", "PROT", "PEOT")) {
                esperado.addAll(List.of(tipo + ":ALTERACAO_ADAPTACAO:8", tipo + ":CORRECAO_MATERIAL:8",
                        tipo + ":REVOGACAO:2", tipo + ":SUSPENSAO_GOVERNO:8", tipo + ":MEDIDAS_PREVENTIVAS_GOVERNO:8"));
            }
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || td.codigo || ':' || count(*) " + ITENS_DOS_PROGRAMAS
                    + " GROUP BY ti.codigo, td.codigo")).containsExactlyInAnyOrderElementsOf(esperado);
        });
    }

    @Test
    void deveDarAPublicacaoDaRevisaoDoPnpotOsOnzeItensTodosObrigatorios() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(itensDoPrograma(ligacao, "PNPOT", "REVISAO", "REVISAO", "d.codigo || ':' || d.obrigatorio::text"))
                    .containsExactly("TEXTO_ATO_PUB:true", "TEXTO_ATO_DEP:true", "RELATORIO_PUB:true",
                            "RELATORIO_DEP:true", "PECAS_GRAFICAS_PUB:true", "PECAS_GRAFICAS_DEP:true",
                            "PROGRAMA_ACAO_DEP:true", "RELATORIO_PONDERACAO_DEP:true", "LEI_AR_DEP:true",
                            "RCM_ELABORACAO_DEP:true", "PARECER_CC_DEP:true");
            assertThat(itensDoPrograma(ligacao, "PNPOT", "REVISAO", "REVISAO", "d.ordem::text"))
                    .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
            assertThat(texto(ligacao, "SELECT count(*) " + ITENS_DOS_PROGRAMAS
                    + " AND ti.codigo = 'PNPOT' AND td.codigo = 'ELABORACAO'")).isEqualTo("0");
        });
    }

    @Test
    void deveDarAoPeotOsPlanosDePraiaEAoProtARcmDaElaboracaoEOParecerDaComissaoConsultiva() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(itensDoPrograma(ligacao, "PEOT", "ELABORACAO", "ELABORACAO", "d.codigo")).containsExactly(
                    "TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "RELATORIO_PUB", "RELATORIO_DEP", "PECAS_GRAFICAS_PUB",
                    "PECAS_GRAFICAS_DEP", "RELATORIO_PONDERACAO_DEP", "RELATORIO_AMBIENTAL_DEP", "RCM_APROVACAO_DEP",
                    "DESPACHO_ELABORACAO_DEP", "PARECER_CA_DEP", "ESQUEMA_MODELO_TERRITORIAL_DEP",
                    "PLANTA_CONDICIONANTES_DEP", "PLANTA_ENQUADRAMENTO_DEP", "PROGRAMA_EXECUCAO_DEP",
                    "ESTUDOS_CARACTERIZACAO_DEP", "PLANTA_SITUACAO_EXISTENTE_DEP", "ELEMENTOS_GRAFICOS_DETALHE_DEP",
                    "PLANOS_PRAIA_DEP");
            assertThat(itensDoPrograma(ligacao, "PROT", "ELABORACAO", "ELABORACAO", "d.codigo")).containsExactly(
                    "TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "RELATORIO_PUB", "RELATORIO_DEP", "PECAS_GRAFICAS_PUB",
                    "PECAS_GRAFICAS_DEP", "RELATORIO_PONDERACAO_DEP", "RELATORIO_AMBIENTAL_DEP",
                    "OPCOES_OBJETIVOS_NORMAS_DEP", "RCM_APROVACAO_DEP", "RCM_ELABORACAO_DEP", "PARECER_CC_DEP");
            assertThat(itensDoPrograma(ligacao, "PROT", "ELABORACAO", "ELABORACAO",
                    "d.nome || '|' || coalesce(d.descricao, '-')")).contains(
                    "Resolução do Conselho de Ministros que determina a elaboração do programa regional|Resolução do "
                            + "Conselho de Ministros que determina a elaboração do plano regional de ordenamento do território");
            assertThat(itensDoPrograma(ligacao, "PEOT", "ELABORACAO", "ELABORACAO",
                    "d.codigo || ':' || d.tipo_elemento || ':' || d.obrigatorio::text")).contains(
                    "PLANOS_PRAIA_DEP:PECA_GRAFICA:true", "ESQUEMA_MODELO_TERRITORIAL_DEP:PECA_GRAFICA:true",
                    "PARECER_CA_DEP:PECA_ESCRITA:true");
        });
    }

    @Test
    void deveTornarOpcionalSoORelatorioIntegralNaPublicacaoDaAlteracaoDosProgramas() throws Exception {
        comBaseMigrada(ligacao -> {
            for (String tipo : List.of("PNPOT", "PS", "PROT", "PEOT")) {
                assertThat(itensDoPrograma(ligacao, tipo, "ALTERACAO", "ALTERACAO",
                        "d.codigo || ':' || d.obrigatorio::text")).as(tipo).contains("ALTERACOES_RELATORIO_PUB:true",
                        "ALTERACOES_RELATORIO_DEP:true", "PECAS_GRAFICAS_PUB:true", "PECAS_GRAFICAS_DEP:true",
                        "RELATORIO_INTEGRAL_PUB:false", "RELATORIO_INTEGRAL_DEP:false", "PARECER_CC_DEP:true");
                assertThat(linhas(ligacao, "SELECT d.codigo " + ITENS_DOS_PROGRAMAS + " AND ti.codigo = '" + tipo
                        + "' AND td.codigo = 'ALTERACAO' AND tp.codigo = 'ALTERACAO' AND NOT d.obrigatorio ORDER BY d.ordem"))
                        .as(tipo).containsExactly("RELATORIO_INTEGRAL_PUB", "RELATORIO_INTEGRAL_DEP");
            }
            assertThat(itensDoPrograma(ligacao, "PNPOT", "ALTERACAO", "ALTERACAO", "d.codigo"))
                    .doesNotContain("RELATORIO_AMBIENTAL_DEP");
        });
    }

    @Test
    void deveDarAElaboracaoDoPimotOsQuatroProcedimentosComOsSoDeDepositoOpcionais() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT tp.codigo || ':' || count(*) " + ITENS_DOS_PROGRAMAS
                    + " AND ti.codigo = 'PIMOT' AND td.codigo = 'ELABORACAO' GROUP BY tp.codigo, ipc.ordem ORDER BY ipc.ordem"))
                    .containsExactly("DECISAO_INICIO:4", "CONSTITUICAO_CC:4", "ABERTURA_DISCUSSAO_PUBLICA:2",
                            "ELABORACAO:20");
            assertThat(itensDoPrograma(ligacao, "PIMOT", "ELABORACAO", "CONSTITUICAO_CC", "d.codigo"))
                    .containsExactly("TEXTO_ATO_PUB", "TEXTO_ATO_DEP", "DESPACHO_CCDR_PUB", "DESPACHO_CCDR_DEP");
            List<String> publicacao = itensDoPrograma(ligacao, "PIMOT", "ELABORACAO", "ELABORACAO",
                    "d.codigo || ':' || d.obrigatorio::text");
            assertThat(publicacao.subList(0, 8)).allMatch(item -> item.endsWith(":true"));
            assertThat(publicacao.subList(8, 20)).hasSize(12).allMatch(item -> item.endsWith("_DEP:false"));
            assertThat(publicacao).contains("PARECER_CCDR_DEP:false", "DELIBERACAO_ELABORACAO_DEP:false");
        });
    }

    @Test
    void deveDeixarSemItensASuspensaoEAsMedidasPreventivasDoGovernoDoPimot() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT count(*) " + ITENS_DOS_PROGRAMAS + " AND ti.codigo = 'PIMOT' "
                    + "AND td.codigo IN ('SUSPENSAO_GOVERNO', 'MEDIDAS_PREVENTIVAS_GOVERNO')")).isEqualTo("0");
            assertThat(itensDoPrograma(ligacao, "PS", "SUSPENSAO_GOVERNO", "PUBLICACAO_SUSPENSAO_GOVERNO", "d.codigo"))
                    .containsExactly("RCM_PUB", "RCM_DEP", "TEXTO_MEDIDAS_PREVENTIVAS_PUB",
                            "TEXTO_MEDIDAS_PREVENTIVAS_DEP", "PLANTA_DELIMITACAO_PUB", "PLANTA_DELIMITACAO_DEP");
        });
    }

    @Test
    void deveGerarAsLinhasDosProgramasPelasRegrasDaListaDaDgt() throws Exception {
        comBaseMigrada(ligacao -> {
            String publicacaoEscrita = "'[\"doc\", \"docx\", \"rtf\", \"odf\", \"odt\", \"xls\", \"xlsx\", \"csv\", \"ods\", "
                    + "\"pdf\", \"txt\"]'::jsonb";
            assertThat(texto(ligacao, "SELECT count(*) " + ITENS_DOS_PROGRAMAS)).isEqualTo("422");
            assertThat(texto(ligacao, "SELECT count(*) " + ITENS_DOS_PROGRAMAS
                    + " AND d.elemento_informacao_base IS NOT NULL")).isEqualTo("0");
            assertThat(linhas(ligacao, "SELECT d.instrumento_procedimento_config_id || ':' || d.codigo "
                    + ITENS_DOS_PROGRAMAS
                    + " AND NOT (d.categoria = CASE WHEN d.codigo LIKE '%\\_PUB' ESCAPE '\\' THEN 'PUBLICACAO' "
                    + "ELSE 'DEPOSITO' END "
                    + "AND d.formato = CASE WHEN d.categoria = 'PUBLICACAO' THEN 'EDITAVEL' ELSE 'NAO_EDITAVEL' END "
                    + "AND d.extensoes_aceites = CASE WHEN d.tipo_elemento = 'PECA_GRAFICA' THEN '[\"zip\"]'::jsonb "
                    + "WHEN d.categoria = 'PUBLICACAO' THEN " + publicacaoEscrita
                    + " ELSE '[\"pdf\", \"jpg\", \"png\"]'::jsonb END "
                    + "AND (d.regras_tiff IS NOT NULL) = (d.tipo_elemento = 'PECA_GRAFICA') "
                    + "AND d.tamanho_maximo_ficheiro = 524288000 AND d.tamanho_maximo_total = 2147483648 "
                    + "AND d.numero_maximo_ficheiros = 10 AND d.regra_nomenclatura = 'GERAL' AND d.ativo "
                    + "AND d.link_publico_permitido = (d.categoria = 'PUBLICACAO' OR d.codigo LIKE 'DELIBERACAO%' "
                    + "OR d.codigo IN ('DESPACHO_CCDR_DEP', 'RCM_DEP')))")).isEmpty();
            assertThat(linhas(ligacao, "SELECT DISTINCT d.codigo " + ITENS_DOS_PROGRAMAS
                    + " AND d.categoria = 'DEPOSITO' AND d.link_publico_permitido")).containsExactlyInAnyOrder(
                    "DELIBERACAO_DEP", "DELIBERACAO_ELABORACAO_DEP", "DESPACHO_CCDR_DEP", "RCM_DEP");
            assertThat(texto(ligacao, "SELECT count(DISTINCT d.regras_tiff) " + ITENS_DOS_PROGRAMAS
                    + " AND d.tipo_elemento = 'PECA_GRAFICA'")).isEqualTo("1");
            assertThat(itensDoPrograma(ligacao, "PNPOT", "REVISAO", "REVISAO", "d.codigo || ':' || (d.descricao IS NOT NULL)"))
                    .contains("LEI_AR_DEP:true", "RCM_ELABORACAO_DEP:true", "RELATORIO_DEP:false");
        });
    }

    @Test
    void deveReporOsItensDosProgramasAoReverterEVoltarAAplicarALista() throws Exception {
        String todosOsItens = "SELECT instrumento_procedimento_config_id || '|' || " + ITEM_COMPLETO
                + " || '|' || coalesce(descricao, '-') || '|' || coalesce(regras_tiff::text, '-') FROM documento_config "
                + "ORDER BY instrumento_procedimento_config_id, pagina, codigo";
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE);
            ligacao.setAutoCommit(true);
            List<String> comALista = linhas(ligacao, todosOsItens);

            reverterOsUltimos(ligacao, CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT);
            ligacao.setAutoCommit(true);

            List<String> semALista = linhas(ligacao, todosOsItens);
            assertThat(semALista).hasSize(comALista.size() - 422);
            assertThat(texto(ligacao, "SELECT count(*) " + ITENS_DOS_PROGRAMAS)).isEqualTo("0");
            assertThat(comALista).containsAll(semALista);

            migrarOsProximos(ligacao, CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, todosOsItens)).isEqualTo(comALista);
        });
    }

    /** As configurações da Alteração RERAE antes do OI-545: PDM, PP, PU, PDIM, PUIM e PPIM. */
    private static final String CONFIGURACOES_DA_RERAE = "(104, 2104, 3104, 3124, 3132, 3140)";

    @Test
    void deveRetirarAAlteracaoReraeComTudoOQueLhePertenceENadaMais() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(contar(ligacao, "tipo_dinamica WHERE codigo = 'ALTERACAO_RERAE'")).isZero();
            assertThat(contar(ligacao, "tipo_procedimento WHERE codigo IN ('ABERTURA_DISCUSSAO_PUBLICA_RERAE', "
                    + "'ALTERACAO_RERAE')")).isZero();
            assertThat(contar(ligacao, "instrumento_dinamica_config WHERE id IN " + CONFIGURACOES_DA_RERAE)).isZero();
            assertThat(contar(ligacao, "instrumento_procedimento_config WHERE id IN (212, 213) "
                    + "OR instrumento_dinamica_config_id IN " + CONFIGURACOES_DA_RERAE)).isZero();
            assertThat(contar(ligacao, "regra WHERE instrumento_dinamica_config_id IN " + CONFIGURACOES_DA_RERAE
                    + " OR instrumento_procedimento_config_id IN (212, 213)")).isZero();
            assertThat(contar(ligacao, "permissao_acao WHERE instrumento_procedimento_config_id IN (212, 213)")).isZero();
            assertThat(contar(ligacao, "documento_config WHERE instrumento_procedimento_config_id IN (212, 213)")).isZero();
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || count(*) FROM instrumento_dinamica_config c "
                    + "JOIN tipo_instrumento ti ON ti.id = c.tipo_instrumento_id "
                    + "WHERE ti.codigo IN ('PDM', 'PU', 'PP', 'PDIM', 'PUIM', 'PPIM') GROUP BY ti.codigo"))
                    .containsExactlyInAnyOrder("PDM:15", "PU:14", "PP:14", "PDIM:14", "PUIM:13", "PPIM:13");
        });
    }

    @Test
    void deveReporOsTiposEAsConfiguracoesDaReraeAoReverterEPararSeHouverDinamicasDelaAoMigrar() throws Exception {
        String configuracao = "SELECT md5(string_agg(linha, ',' ORDER BY linha)) FROM ("
                + "SELECT 'idc' || to_jsonb(c)::text AS linha FROM instrumento_dinamica_config c "
                + "UNION ALL SELECT 'ipc' || to_jsonb(p)::text FROM instrumento_procedimento_config p "
                + "WHERE p.tipo_procedimento_id NOT IN (SELECT id FROM tipo_procedimento WHERE codigo = 'PUBLICACAO_REN') "
                + "UNION ALL SELECT 'regra' || to_jsonb(r)::text FROM regra r "
                + "UNION ALL SELECT 'condicao' || to_jsonb(k)::text FROM condicao k "
                + "UNION ALL SELECT 'permissao' || to_jsonb(a)::text FROM permissao_acao a "
                + "WHERE a.instrumento_procedimento_config_id NOT IN (SELECT ipc.id FROM instrumento_procedimento_config ipc "
                + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE tp.codigo = 'PUBLICACAO_REN') "
                + "UNION ALL SELECT 'documento' || to_jsonb(d)::text FROM documento_config d WHERE "
                + SEM_ITENS_DA_PRORROGACAO_DO_ATO + ") AS todas";
        // O seed da Prorrogação do ato (depois da retirada) volta a correr e dá outros ids aos mesmos itens.
        String procedimento = "213";
        comBaseMigrada(ligacao -> {
            String semARerae = texto(ligacao, configuracao);

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE);
            ligacao.setAutoCommit(true);

            assertThat(linhas(ligacao, "SELECT id || ':' || codigo || ':' || nome FROM tipo_dinamica WHERE id = 4"))
                    .containsExactly("4:ALTERACAO_RERAE:Alteração no âmbito RERAE");
            assertThat(linhas(ligacao, "SELECT id || ':' || codigo FROM tipo_procedimento WHERE id IN (9, 10) ORDER BY id"))
                    .containsExactly("9:ABERTURA_DISCUSSAO_PUBLICA_RERAE", "10:ALTERACAO_RERAE");
            assertThat(linhas(ligacao, "SELECT id || ':' || tipo_instrumento_id FROM instrumento_dinamica_config "
                    + "WHERE tipo_dinamica_id = 4 ORDER BY id")).containsExactly("104:1", "2104:2", "3104:3");
            assertThat(linhas(ligacao, "SELECT id || ':' || instrumento_dinamica_config_id || ':' || tipo_procedimento_id "
                    + "|| ':' || coalesce(codigo_idus, '-') FROM instrumento_procedimento_config "
                    + "WHERE tipo_procedimento_id IN (9, 10) ORDER BY id")).containsExactly("212:104:9:-", "213:104:10:040",
                    "2210:2104:9:-", "2211:2104:10:040", "3210:3104:9:-", "3211:3104:10:040");
            assertThat(contar(ligacao, "documento_config WHERE instrumento_procedimento_config_id IN (212, 213)")).isZero();
            assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || ':' || string_agg(tipo_entidade, ',' "
                    + "ORDER BY tipo_entidade) FROM permissao_acao WHERE ativo AND instrumento_procedimento_config_id "
                    + "IN (212, 213, 2210, 2211, 3210, 3211) GROUP BY instrumento_procedimento_config_id "
                    + "ORDER BY instrumento_procedimento_config_id")).containsExactly("212:CM,DGT", "213:CM,DGT",
                    "2210:CM,DGT", "2211:CM,DGT", "3210:CM,DGT", "3211:CM,DGT");

            // Um item e uma regra da RERAE, e uma Dinâmica iniciada dela.
            executar(ligacao, "INSERT INTO documento_config (instrumento_procedimento_config_id, codigo, nome, separador, "
                    + "categoria, tipo_elemento, formato, extensoes_aceites, ordem) VALUES (" + procedimento
                    + ", 'TEXTO_ATO_DEP', 'Texto do ato', 'Texto do Ato', 'DEPOSITO', 'PECA_ESCRITA', 'NAO_EDITAVEL', "
                    + "'[\"pdf\"]', 1)");
            executar(ligacao, "INSERT INTO regra (instrumento_dinamica_config_id, operador) VALUES (104, 'AND')");
            executar(ligacao, "INSERT INTO condicao (regra_id, avaliador, parametros) "
                    + "SELECT id, 'INSTRUMENTO_EM_VIGOR', '{}' FROM regra WHERE instrumento_dinamica_config_id = 104");
            inserirProcessoComDocumento(ligacao);
            executar(ligacao, "INSERT INTO dinamica (id, instrumento_id, instrumento_dinamica_config_id, criado_por, "
                    + "descritivo, numero_dinamica) VALUES ('" + OUTRA_DINAMICA_ID + "', "
                    + "'00000000-0000-0000-0000-000000000002', 104, '00000000-0000-0000-0000-000000000001', "
                    + "'Alteração RERAE', '00002')");

            assertThatThrownBy(() -> migrar(ligacao)).hasStackTraceContaining("Há Dinâmicas iniciadas de Alteração RERAE");
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "tipo_dinamica WHERE codigo = 'ALTERACAO_RERAE'")).isEqualTo(1);
            assertThat(contar(ligacao, "documento_config WHERE instrumento_procedimento_config_id = " + procedimento))
                    .isEqualTo(1);
            assertThat(contar(ligacao, "condicao WHERE regra_id IN (SELECT id FROM regra "
                    + "WHERE instrumento_dinamica_config_id = 104)")).isEqualTo(1);

            executar(ligacao, "DELETE FROM dinamica WHERE id = '" + OUTRA_DINAMICA_ID + "'");
            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "tipo_dinamica WHERE codigo = 'ALTERACAO_RERAE'")).isZero();
            assertThat(contar(ligacao, "tipo_procedimento WHERE id IN (9, 10)")).isZero();
            assertThat(texto(ligacao, configuracao)).isEqualTo(semARerae);
        });
    }

    /**
     * Reverte os itens pela lista da DGT — do PDM, de PU, PP e intermunicipais, das Dinâmicas temporárias e da
     * Ratificação e dos programas — para ver o que os seeds anteriores deixaram.
     */
    private void semAListaDaDgt(Connection ligacao) throws Exception {
        reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT);
        ligacao.setAutoCommit(true);
    }

    private void comBaseMigrada(Verificacao verificacao) throws Exception {
        try (Connection administracao = ligar("postgres")) {
            executar(administracao, "CREATE DATABASE " + BASE_DO_TESTE + " TEMPLATE " + BASE_MODELO);
            try (Connection ligacao = ligar(BASE_DO_TESTE)) {
                verificacao.executar(ligacao);
            } finally {
                executar(administracao, "DROP DATABASE " + BASE_DO_TESTE + " WITH (FORCE)");
            }
        }
    }

    private static Connection ligar(String base) throws SQLException {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(PORTA_POSTGRES) + "/" + base;
        return DriverManager.getConnection(url, "postgres", "teste");
    }

    @FunctionalInterface
    private interface Verificacao {
        void executar(Connection ligacao) throws Exception;
    }

    @SuppressWarnings("deprecation")
    private static void migrar(Connection ligacao) throws Exception {
        Database bd = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(ligacao));
        new Liquibase("db/master.xml", new ClassLoaderResourceAccessor(), bd).update("");
    }

    /** A tabela planta, imediatamente antes da auditoria das descargas. */
    private static final int CHANGESETS_DA_PLANTA = 1;

    /** A auditoria das descargas, depois da planta. */
    private static final int CHANGESETS_DA_DESCARGA = 1;

    /** A NUT II do instrumento e as Plantas e Metadados, antes da localização. */
    private static final int CHANGESETS_DA_NUT_II_E_PLANTAS = 2;

    /** A localização e o motivo da alteração, antes dos IDs do SNIT. */
    private static final int CHANGESETS_DA_LOCALIZACAO = 2;

    /** As colunas e o seed dos IDs do SNIT nos catálogos, antes do n.º de depósito. */
    private static final int CHANGESETS_DO_SNIT_CODIGO = 2;

    /** O n.º de depósito e o seed dos códigos dos concelhos, antes do manifesto do depósito no SNIT. */
    private static final int CHANGESETS_DO_NUMERO_DEPOSITO = 2;

    /** O manifesto do depósito no SNIT, antes das plantas por folha. */
    private static final int CHANGESETS_DO_MANIFESTO_SNIT = 1;

    /** O seed das freguesias e dos motivos da Alteração de PDM, antes das plantas por folha. */
    private static final int CHANGESETS_DAS_LISTAS_DA_ALTERACAO = 1;

    /** As plantas por folha, antes dos links públicos: sem unique, migração e fim da processo_planta. */
    private static final int CHANGESETS_DA_PLANTA_POR_FOLHA = 3;

    /** Os limites de tamanho por item documental, antes da ficha MOTU. */
    private static final int CHANGESETS_DOS_LIMITES_DOCUMENTAIS = 1;
    
    private static final int CHANGESETS_DA_FICHA_MOTU = 1;

    private static final int CHANGESETS_DO_ESTADO_SUBSTITUIDO = 1;

    /** A coluna `linha_txt` da planta, antes dos códigos da PCGT. */
    private static final int CHANGESETS_DA_LINHA_TXT = 1;

    /** A coluna `dados` da ficha MOTU, antes da `linha_txt`. */
    private static final int CHANGESETS_DA_SERIE_MOTU = 1;

    /** O seed da página das Plantas nos procedimentos finais do PDM (US-375), logo a seguir à ficha MOTU e antes do estado SUBSTITUIDO. */
    private static final int CHANGESETS_DAS_PLANTAS_FINAIS = 1;

    /** Os códigos da PCGT dos catálogos (coluna e seed), antes da sessão única. */
    private static final int CHANGESETS_DO_PCGT_CODIGO = 2;

    /** A sessão única (revogação das sessões antigas e índice), antes das notificações. */
    private static final int CHANGESETS_DA_SESSAO_UNICA = 1;

    /** As tabelas das notificações, das mensagens e dos anexos, antes da `permissao_acao` por tipo de entidade. */
    private static final int CHANGESETS_DAS_NOTIFICACOES = 1;

    /** A `permissao_acao` por tipo de entidade, antes da entidade no processo. */
    private static final int CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE = 1;

    /** A entidade no processo, a saída da entidade do Instrumento e a `visivel_na_dinamica`, antes da CC da CCDR. */
    private static final int CHANGESETS_DA_ENTIDADE_NO_PROCESSO = 3;

    /** A Constituição da CC passa da Câmara para a CCDR, antes do âmbito territorial. */
    private static final int CHANGESETS_DA_CC_DA_CCDR = 1;

    /** O âmbito territorial do tipo de Instrumento, antes do CHECK da categoria. */
    private static final int CHANGESETS_DO_AMBITO_TERRITORIAL = 1;

    /** O CHECK da categoria do tipo de Instrumento sem a ARU, antes da matriz de permissões. */
    private static final int CHANGESETS_DA_CATEGORIA_SEM_ARU = 1;

    /** A migração das colunas `pai_id` e `tipologia_srup_id` e o seed da classificação das servidões, logo a seguir ao CHECK da categoria. */
    private static final int CHANGESETS_DA_CLASSIFICACAO_SRUP = 2;

    /** O seed da matriz de permissões por tipo de entidade, antes do nome de autenticação das contas AD. */
    private static final int CHANGESETS_DA_MATRIZ_DE_PERMISSOES = 1;

    /** O email opcional e o nome de autenticação das contas AD_DGT, antes da conta por origem. */
    private static final int CHANGESETS_DO_NOME_AD = 2;

    /** Uma conta por origem e a saída de `desativado_em`: antes do contacto principal único. */
    private static final int CHANGESETS_DA_CONTA_POR_ORIGEM = 1;
    /** O índice do contacto principal ativo único por entidade, antes do perfil do utilizador. */
    private static final int CHANGESETS_DO_CONTACTO_PRINCIPAL = 1;
    /** `telemovel` → `telefone` e a `morada` do utilizador: antes das redes de defesa do SGIFR. */
    private static final int CHANGESETS_DO_PERFIL_DO_UTILIZADOR = 1;

    /** As tabelas das redes de defesa do SGIFR e o seed das 8 redes, antes do catálogo SRUP. */
    private static final int CHANGESETS_DAS_REDES_DE_DEFESA = 2;

    /** O seed dos tipos e Dinâmicas da REN e do SGIFR, antes dos itens documentais da Alteração de PU. */
    private static final int CHANGESETS_DO_CATALOGO_SRUP = 1;

    /** O seed dos itens documentais da Decisão de início da Alteração de PU, antes do da Alteração de PP. */
    private static final int CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU = 1;

    /** O seed dos itens documentais da Decisão de início da Alteração de PP, antes da tabela das tags. */
    private static final int CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP = 1;

    /** A tabela das tags de depósito obrigatório dos ficheiros, antes da regra da REN sem Dinâmicas em simultâneo. */
    private static final int CHANGESETS_DA_TAG_DOCUMENTAL = 1;

    /** O seed da regra da REN sem Dinâmicas em simultâneo, antes dos planos intermunicipais. */
    private static final int CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS = 1;

    /** O seed dos planos intermunicipais PDIM, PUIM e PPIM, um changeset por tabela, antes dos Programas. */
    private static final int CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS = 6;

    /** Os Procedimentos de PDIM (18), PUIM (16) e PPIM (16), que os rollbacks até antes deles levam. */
    private static final int PROCEDIMENTOS_DOS_PLANOS_INTERMUNICIPAIS = 50;

    /** O seed dos Programas PNPOT, PS, PROT, PEOT e PIMOT, um changeset por tabela, antes das regras de precedência da REN. */
    private static final int CHANGESETS_DOS_PROGRAMAS = 6;

    /** O seed das regras de elegibilidade das três Dinâmicas da REN, antes das regras TIFF. */
    private static final int CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN = 1;

    /** A coluna `regras_tiff` dos itens documentais, com a configuração inicial, antes da entidade instrutora. */
    private static final int CHANGESETS_DAS_REGRAS_TIFF = 1;

    /** A coluna `entidade_instrutora` do processo, antes da configuração do SGIFR. */
    private static final int CHANGESETS_DA_ENTIDADE_INSTRUTORA = 1;

    /** A configuração do PRA e do PSA (Procedimento, permissões, regras e ativação), antes da regra de nomenclatura. */
    private static final int CHANGESETS_DA_CONFIGURACAO_DO_SGIFR = 1;

    /** A coluna `regra_nomenclatura` dos itens documentais, antes do limite do SGIFR. */
    private static final int CHANGESETS_DA_REGRA_DE_NOMENCLATURA = 1;

    /** O limite de 5 GB dos Procedimentos de publicação do PRA e do PSA, antes das MP, NP e Suspensão. */
    private static final int CHANGESETS_DO_LIMITE_DO_SGIFR = 1;

    /**
     * Medidas Preventivas, Normas Provisórias e Suspensão do município (Dinâmicas dos intermunicipais, Procedimentos,
     * permissões, regras e condições): antes da justificação do desbloqueio.
     */
    private static final int CHANGESETS_DE_MP_NP_E_SUSPENSAO = 5;

    /** A coluna `justificacao_desbloqueio` dos ficheiros do Processo, antes da localização das Dinâmicas municipais. */
    private static final int CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO = 1;

    /** `pede_localizacao` no primeiro Procedimento das Dinâmicas do município, antes do motivo da alteração. */
    private static final int CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS = 1;

    /** `pede_motivo_alteracao` no primeiro Procedimento das Alterações, antes do índice dos motivos. */
    private static final int CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS = 1;

    /** O índice GIN `idx_processo_motivos_alteracao`, antes da coluna `inicio_sem_igt`. */
    private static final int CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO = 1;

    /** A coluna `tipo_dinamica.inicio_sem_igt`, antes das Dinâmicas transversais. */
    private static final int CHANGESETS_DO_INICIO_SEM_IGT = 1;

    /**
     * As Dinâmicas transversais (tipos de Dinâmica e de Procedimento, configurações, Procedimentos, permissões, regras e
     * condições): as últimas do master.xml.
     */
    private static final int CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS = 7;

    /** `dinamica.instrumento_id` anulável (Dinâmicas iniciadas sem IGT): o último do master.xml. */
    private static final int CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO = 1;

    /** `tipo_dinamica.designacao_unica`. */
    private static final int CHANGESETS_DA_DESIGNACAO_UNICA = 1;

    /** `codigo_idus` da REN e dos intermunicipais e `tipo_instrumento.entidade_instrutora_obrigatoria`, antes da matriz nos Programas. */
    private static final int CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA = 2;

    /** As linhas da matriz de permissões que o seed dos Programas não dá. */
    private static final int CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS = 1;

    /** A tabela das preferências do dashboard de KPIs. */
    private static final int CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD = 1;

    /** Os 5 atos pontuais nas Dinâmicas com Decisão de início ou Abertura de discussão pública. */
    private static final int CHANGESETS_DOS_ATOS_PONTUAIS = 1;

    /** A linha `PCM` em cada Procedimento de 1.ª série. */
    private static final int CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE = 1;

    /**
     * A coluna dos itens independentes dos elementos afetados. As provas de notificação da Correção Material saíram: a
     * lista da DGT prevalece nesse Procedimento (FR-193).
     */
    private static final int CHANGESETS_DA_CORRECAO_MATERIAL = 1;

    /** A coluna `dinamica.url_geoconform`. */
    private static final int CHANGESETS_DO_URL_GEOCONFORM = 1;

    /** A coluna `instrumento.instrumento_origem_id` (servidão de origem). */
    private static final int CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO = 1;

    /** `processo.dinamica_associada_id`, `tipo_instrumento_associavel` e `admite_associacao_igt`, e o seed da REN. */
    private static final int CHANGESETS_DA_DINAMICA_ASSOCIADA = 2;

    /** A coluna `processo.destino`. */
    private static final int CHANGESETS_DO_DESTINO_DO_PROCESSO = 1;

    /** O `tipo_procedimento` `PUBLICACAO_SRUP`, sem configuração: o último do master.xml. */
    private static final int CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP = 1;

    /** Os itens documentais do SGIFR (PRA e PSA, Delimitação e Alteração): o último do master.xml. */
    private static final int CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR = 1;

    /** 6 itens em cada Procedimento do PRA e 16 em cada um do PSA, nos quatro `PUBLICACAO_SGIFR`. */
    private static final int ITENS_DO_SGIFR = 44;

    private static final String SEM_ITENS_DO_SGIFR = "instrumento_procedimento_config_id NOT IN (SELECT c.id "
            + "FROM instrumento_procedimento_config c JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id "
            + "WHERE t.codigo = 'PUBLICACAO_SGIFR')";

    /**
     * Os itens do seed documental posterior ao SGIFR, o da Prorrogação do ato, que as cadeias até ele revertem. A lista
     * da DGT não cobre os atos pontuais, por isso o seed fica (FR-193).
     */
    private static final String SEM_ITENS_DA_PRORROGACAO_DO_ATO = "instrumento_procedimento_config_id NOT IN (SELECT c.id "
            + "FROM instrumento_procedimento_config c JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id "
            + "WHERE t.codigo = 'PRORROGACAO_ATO')";

    /** Texto do ato e Deliberação, duas caixas, nas 41 configurações ativas (as 6 da RERAE saíram com o OI-545). */
    private static final int ITENS_DA_PRORROGACAO_DO_ATO = 164;
    /** Só o Texto do ato da Prorrogação leva a Informação Base. */
    private static final int ITENS_DA_PRORROGACAO_DO_ATO_COM_INFORMACAO_BASE = ITENS_DA_PRORROGACAO_DO_ATO / 2;

    /** A coluna `serie_dr` anulável e a configuração das restantes servidões: os dois últimos do master.xml. */
    private static final int CHANGESETS_DA_SERIE_DR_OPCIONAL = 1;
    private static final int CHANGESETS_DO_SEED_DAS_RESTANTES_SERVIDOES = 1;
    private static final int CHANGESETS_DAS_RESTANTES_SERVIDOES = CHANGESETS_DA_SERIE_DR_OPCIONAL
            + CHANGESETS_DO_SEED_DAS_RESTANTES_SERVIDOES;

    /** Um `PUBLICACAO_SRUP` em cada Dinâmica do tipo SERVIDAO, com CM, SEC_ESTADO, IP e DGT; 3 regras e 4 condições. */
    private static final int PROCEDIMENTOS_DAS_RESTANTES_SERVIDOES = 3;
    private static final int PERMISSOES_DAS_RESTANTES_SERVIDOES = 12;

    /** A SEC_ESTADO desativada no PROT (só da CCDR). */
    private static final int CHANGESETS_DO_PROT_SO_DA_CCDR = 1;

    /** `processo.tentativa`, `processo.aproveita_atos_anteriores` e a chave única com a Tentativa. */
    private static final int CHANGESETS_DA_TENTATIVA_DO_PROCESSO = 1;

    /** `processo.processo_referido_id`, a chave estrangeira e o índice. */
    private static final int CHANGESETS_DO_PROCESSO_REFERIDO = 1;


    /** Os itens documentais da Prorrogação do ato, com a Informação Base no Texto do ato. */
    private static final int CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO = 1;

    /** `processo.serie_dr` com o CHECK das três séries: o último do master.xml. */
    private static final int CHANGESETS_DA_SERIE_DO_PROCESSO = 1;
    private static final int CHANGESETS_DO_RESUMO_VALIDADOS = 1;
    private static final int CHANGESETS_DO_PCM_DEVOLVIDO_EM = 1;
    private static final String CONFIGS_DA_REN = "SELECT td.codigo || '|' || idc.ativo || '|' || idc.pede_diploma_corrigido || '|' "
            + "|| coalesce(idc.tipo_informacao_fixo, '-') || '|' || coalesce(ipc.serie_dr, '-') || '|' || coalesce(ipc.ordem::text, '-') "
            + "|| '|' || coalesce(ipc.obrigatorio::text, '-') || '|' || coalesce(tp.codigo, '-') "
            + "|| '|' || coalesce((SELECT string_agg(pa.tipo_entidade, ',' ORDER BY pa.tipo_entidade) FROM permissao_acao pa "
            + "WHERE pa.instrumento_procedimento_config_id = ipc.id AND pa.ativo), '-') "
            + "FROM instrumento_dinamica_config idc "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id AND ti.codigo = 'REN' "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "LEFT JOIN instrumento_procedimento_config ipc ON ipc.instrumento_dinamica_config_id = idc.id "
            + "LEFT JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id ORDER BY td.codigo";

    @Test
    void deveSemearOsProcedimentosDaRenComOFormularioPorDinamicaEReverterTudo() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, CONFIGS_DA_REN)).containsExactly(
                    "ALTERACAO|true|false|-|SEGUNDA_C|1|true|PUBLICACAO_REN|CCDR_DR,DGT",
                    "CORRECAO_MATERIAL_RETIFICACAO|true|true|-|SEGUNDA_C|1|true|PUBLICACAO_REN|CCDR_DR,DGT",
                    "DELIMITACAO|true|false|TEXTO_INTEGRAL|SEGUNDA_C|1|true|PUBLICACAO_REN|CCDR_DR,DGT");
            assertThat(contar(ligacao, "lista_valores WHERE tipo = 'MOTIVO_PROCEDIMENTO_REN'")).isZero();
            assertThat(contar(ligacao, "instrumento_dinamica_config WHERE pede_diploma_corrigido OR tipo_informacao_fixo IS NOT NULL"))
                    .isEqualTo(2);

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "instrumento_dinamica_config", "pede_diploma_corrigido")).isFalse();
            assertThat(colunaExiste(ligacao, "instrumento_dinamica_config", "tipo_informacao_fixo")).isFalse();
            assertThat(contar(ligacao, "tipo_procedimento WHERE codigo = 'PUBLICACAO_REN'")).isZero();
            assertThat(contar(ligacao, "instrumento_dinamica_config idc JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "WHERE ti.codigo = 'REN' AND idc.ativo")).isZero();
        });
    }

    /** As colunas do formulário da Dinâmica e os Procedimentos da REN (2026-11-16-1 e 2026-11-16-2). */
    private static final int CHANGESETS_DO_FORMULARIO_REN = 2;
    /** O Cartograma da página dos Documentos em OUTROS, pdf, jpg e png, sem regras TIFF (2026-11-16-3). */
    private static final int CHANGESETS_DO_CARTOGRAMA_SEM_FICHA = 1;
    /** O nome «Publicação final» do tipo de Procedimento ELABORACAO (2026-11-16-4). */
    private static final int CHANGESETS_DA_PUBLICACAO_FINAL = 1;
    /** Um Procedimento PUBLICACAO_REN por Dinâmica da REN, cada um com CCDR_DR e DGT. */
    private static final int PROCEDIMENTOS_DA_REN = 3;
    private static final int PERMISSOES_DA_REN = 6;

    /** As linhas SEC_ESTADO do PROT fora das Dinâmicas do Governo: 6 Dinâmicas, atos pontuais incluídos. */
    private static final int PERMISSOES_DO_SEC_ESTADO_NO_PROT = 27;
    private static final int CONDICOES_DAS_RESTANTES_SERVIDOES = 4;

    /** Uma linha `PCM` por Procedimento de 1.ª série, atos pontuais dos Programas incluídos. */
    private static final int PERMISSOES_DA_PCM_NA_PRIMEIRA_SERIE = 142;

    /** Os 5 atos pontuais em cada uma das 47 Dinâmicas com Decisão de início ou Abertura de discussão pública. */
    private static final int PROCEDIMENTOS_DOS_ATOS_PONTUAIS = 235;

    /** As permissões ativas da Decisão de início (ou do primeiro Procedimento) copiadas para cada ato pontual. */
    private static final int PERMISSOES_DOS_ATOS_PONTUAIS = 560;

    /**
     * Feedback de 13/11, antes do drop da `nut`: a Decisão de elaborar sem Plantas, os dados do ValidateUser na
     * entidade e as observações da secção Outros.
     */
    private static final int CHANGESETS_DO_FEEDBACK_DE_13_11 = 3;

    /** O drop da tabela `nut` e de `instrumento.nut_ii_codigo`, antes dos itens do PDM pela lista da DGT. */
    private static final int CHANGESETS_DA_NUT_SO_DA_REPLICA = 1;

    /** Os itens documentais do PDM pela lista da DGT de 04/11, antes dos de PU, PP e intermunicipais. */
    private static final int CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT = 1;

    /** Os itens documentais de PU, PP, PDIM, PUIM e PPIM pela lista da DGT de 04/11: o último do master.xml. */
    private static final int CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT = 1;
    private static final int CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT = 1;

    /** O seed dos itens dos programas e dos programas intermunicipais pela lista da DGT (2026-11-14-1). */
    private static final int CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT = 1;

    /** A retirada da Alteração RERAE (2026-11-14-2, OI-545). */
    private static final int CHANGESETS_DA_RETIRADA_DA_RERAE = 1;

    /** A Lagoa do Algarve com o código 5126 do extrato v6 (2026-11-14-3, OI-546). */
    private static final int CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 = 1;

    /**
     * Os Procedimentos da RERAE que a base migrada já não tem: os 6 dos intermunicipais, que as contas dos planos
     * intermunicipais ainda descontam, e os 6 de PDM, PP e PU, que o rollback da retirada repõe. Cada um tinha duas
     * permissões, da Câmara e da DGT.
     */
    private static final int PROCEDIMENTOS_DA_RERAE = 12;

    /** As permissões da Câmara e da DGT dos 6 Procedimentos da RERAE de PDM, PP e PU, que o rollback da retirada repõe. */
    private static final int PERMISSOES_DA_RERAE_DE_PDM_PP_E_PU = 12;

    /**
     * Os 5 atos pontuais de cada uma das 6 Dinâmicas da RERAE (PDM, PP, PU e intermunicipais), que o seed dos atos
     * pontuais cria e a retirada da RERAE leva com os outros Procedimentos da Dinâmica.
     */
    private static final int ATOS_PONTUAIS_DA_RERAE = 30;

    /** Os atos pontuais das 3 Dinâmicas RERAE dos intermunicipais, que o rollback da retirada não repõe. */
    private static final int ATOS_PONTUAIS_DA_RERAE_DOS_INTERMUNICIPAIS = 15;

    /** As permissões da Câmara e da DGT copiadas para cada um dos {@link #ATOS_PONTUAIS_DA_RERAE}. */
    private static final int PERMISSOES_DOS_ATOS_PONTUAIS_DA_RERAE = 2 * ATOS_PONTUAIS_DA_RERAE;

    /**
     * Os itens que os seeds documentais do ramo da Sprint 4 acrescentam à lista da DGT, só nos Procedimentos que ela não
     * cobre: o SGIFR e a Prorrogação do ato. Nos que cobre, a lista prevalece (FR-193) e os seeds do build saíram.
     */
    private static final int ITENS_DOS_SEEDS_DOCUMENTAIS_DO_BUILD = ITENS_DO_SGIFR + ITENS_DA_PRORROGACAO_DO_ATO;


    /** Os itens da página das Plantas da Decisão de elaborar, que o feedback de 13/11 desativa e o revert reativa. */
    private static final String FORA_DAS_PLANTAS_DA_DECISAO_DE_ELABORAR =
            "NOT (instrumento_procedimento_config_id IN (201, 205) AND pagina = 'PLANTAS')";

    /** Os códigos das Dinâmicas transversais, para uma lista SQL. */
    private static final String DINAMICAS_TRANSVERSAIS = "'SUSPENSAO_CCDR', 'SUSPENSAO_GOVERNO', 'MEDIDAS_PREVENTIVAS_GOVERNO', 'RATIFICACAO'";

    /** 1 na Suspensão da CCDR e na Ratificação, 2 nas do Governo. */
    private static final int PROCEDIMENTOS_DAS_DINAMICAS_TRANSVERSAIS = 52;

    /** 2 por Procedimento. */
    private static final int PERMISSOES_DAS_DINAMICAS_TRANSVERSAIS = 104;

    /** Uma condição de instrumento em vigor em cada configuração da Suspensão da CCDR e das do Governo. */
    private static final int CONDICOES_DAS_DINAMICAS_TRANSVERSAIS = 28;

    /** Publicação e Prorrogação em cada uma das três Dinâmicas dos seis planos. */
    private static final int PROCEDIMENTOS_DE_MP_NP_E_SUSPENSAO = 36;

    /** CM e DGT em cada um desses Procedimentos. */
    private static final int PERMISSOES_DE_MP_NP_E_SUSPENSAO = 72;

    /** Plano em vigor nas três; MP publicadas também na Suspensão. */
    private static final int CONDICOES_DE_MP_NP_E_SUSPENSAO = 24;

    /** Os Procedimentos de publicação do PRA e do PSA, um por Dinâmica. */
    private static final int PROCEDIMENTOS_DO_SGIFR = 4;

    /** As permissões do SGIFR: CCDR_DR e DGT nos 2 do PRA, CIM, AM e DGT nos 2 do PSA. */
    private static final int PERMISSOES_DO_SGIFR = 10;

    /** Os Procedimentos de PNPOT (9), PS, PROT e PEOT (12 cada) e PIMOT (13). */
    private static final int PROCEDIMENTOS_DOS_PROGRAMAS = 58;

    /** As permissões dos Programas: 2 por Procedimento no PNPOT, PROT e PIMOT, 3 no PS e no PEOT. */
    private static final int PERMISSOES_DOS_PROGRAMAS = 140;

    /** As que a matriz pede e o seed dos Programas não dá: CCDR_DR no PROT, ICNF no PS, ICNF e DGPC no PEOT (12 Procedimentos cada). */
    private static final int PERMISSOES_DA_MATRIZ_NOS_PROGRAMAS = 48;

    /**
     * A tabela dos links públicos, o seed de `link_publico_permitido`, o drop de `token_publico` e a auditoria das
     * descargas que sobrevive ao documento, antes dos limites documentais.
     */
    private static final int CHANGESETS_DOS_LINKS_PUBLICOS = 4;

    /**
     * Os últimos changesets do master.xml são os da entidade conforme o LLD, o seed dos itens documentais dos
     * procedimentos de PDM, a planta, a auditoria das descargas, a NUT II do instrumento e as Plantas e Metadados, a
     * localização e o motivo da alteração, os IDs do SNIT, o n.º de depósito, o seed das listas da Alteração, as
     * plantas por folha, os links públicos, os limites documentais, a ficha MOTU, as plantas dos procedimentos
     * finais, o estado SUBSTITUIDO, os dados da ficha MOTU, a `linha_txt`, os códigos da PCGT, a sessão única, as
     * notificações, a `permissao_acao` por tipo de entidade, a entidade no processo, a CC da CCDR, o âmbito territorial,
     * o CHECK da categoria sem ARU, a matriz de permissões, o nome de autenticação das contas AD, a conta por origem,
     * o contacto principal único, o perfil do utilizador, as redes de defesa do SGIFR, o catálogo SRUP da REN e do SGIFR e, por fim, os itens
     * documentais da Decisão de início da Alteração de PU e de PP, a tabela das tags dos ficheiros, a regra da REN
     * sem Dinâmicas em simultâneo, os planos intermunicipais, os Programas, as regras de precedência da REN, as regras
     * TIFF dos itens documentais, a entidade instrutora do processo, a configuração do SGIFR, a regra de
     * nomenclatura dos itens, o limite do SGIFR, as MP, NP e Suspensão do município, a justificação do desbloqueio, a localização das Dinâmicas municipais, o motivo da alteração das Dinâmicas, o índice dos motivos, a coluna `inicio_sem_igt`, as Dinâmicas transversais, as preferências do dashboard e os atos pontuais: os `reverter(n)` contam para
     * trás deles.
     */
    private static final int CHANGESETS_FINAIS = 7 + CHANGESETS_DA_PLANTA + CHANGESETS_DA_DESCARGA + CHANGESETS_DA_NUT_II_E_PLANTAS
            + CHANGESETS_DA_LOCALIZACAO + CHANGESETS_DO_SNIT_CODIGO + CHANGESETS_DO_NUMERO_DEPOSITO + CHANGESETS_DO_MANIFESTO_SNIT
            + CHANGESETS_DAS_LISTAS_DA_ALTERACAO + CHANGESETS_DA_PLANTA_POR_FOLHA + CHANGESETS_DOS_LINKS_PUBLICOS
            + CHANGESETS_DOS_LIMITES_DOCUMENTAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DA_SERIE_MOTU
            + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_SESSAO_UNICA
            + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DA_ENTIDADE_NO_PROCESSO
            + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU
            + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_CONTA_POR_ORIGEM
            + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DO_PERFIL_DO_UTILIZADOR
            + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU
            + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS
            + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN
            + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR
            + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DE_MP_NP_E_SUSPENSAO
            + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS
            + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO
            + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO
            + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS;

    /** Reverte {@code changesets} anteriores aos finais do master.xml (que revertem primeiro). */
    private void reverter(Connection ligacao, int changesets) throws Exception {
        reverterOsUltimos(ligacao, changesets + CHANGESETS_FINAIS);
    }

    @SuppressWarnings("deprecation")
    private void reverterOsUltimos(Connection ligacao, int changesets) throws Exception {
        Database bd = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(ligacao));
        new Liquibase("db/master.xml", new ClassLoaderResourceAccessor(), bd).rollback(changesets, "");
    }

    @SuppressWarnings("deprecation")
    private void migrarOsProximos(Connection ligacao, int changesets) throws Exception {
        Database bd = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(ligacao));
        new Liquibase("db/master.xml", new ClassLoaderResourceAccessor(), bd).update(changesets, "");
    }

    private String texto(Connection ligacao, String consulta) throws SQLException {
        try (Statement sql = ligacao.createStatement(); ResultSet resultado = sql.executeQuery(consulta)) {
            resultado.next();
            return resultado.getString(1);
        }
    }

    private List<String> linhas(Connection ligacao, String consulta) throws SQLException {
        List<String> linhas = new ArrayList<>();
        try (Statement sql = ligacao.createStatement(); ResultSet resultado = sql.executeQuery(consulta)) {
            while (resultado.next()) {
                linhas.add(resultado.getString(1));
            }
        }
        return linhas;
    }

    private static void executar(Connection ligacao, String comando) throws SQLException {
        try (Statement sql = ligacao.createStatement()) {
            sql.execute(comando);
        }
    }

    private boolean colunaExiste(Connection ligacao, String tabela, String coluna) throws SQLException {
        try (Statement sql = ligacao.createStatement();
             ResultSet resultado = sql.executeQuery("SELECT count(*) FROM information_schema.columns "
                     + "WHERE table_schema = current_schema() AND table_name = '" + tabela + "' AND column_name = '" + coluna + "'")) {
            resultado.next();
            return resultado.getInt(1) == 1;
        }
    }

    private String definicaoDoIndice(Connection ligacao, String indice) throws SQLException {
        try (Statement sql = ligacao.createStatement();
             ResultSet resultado = sql.executeQuery("SELECT indexdef FROM pg_indexes WHERE indexname = '" + indice + "'")) {
            return resultado.next() ? resultado.getString(1) : null;
        }
    }

    private int contar(Connection ligacao, String tabela) throws SQLException {
        try (Statement sql = ligacao.createStatement();
             ResultSet resultado = sql.executeQuery("SELECT count(*) FROM " + tabela)) {
            resultado.next();
            return resultado.getInt(1);
        }
    }

    private static final String CODIGOS_ATO_PONTUAL = "'ALTERACAO_ATO_ANEXOS', 'RETIFICACAO_ATO_ANEXOS', 'REVOGACAO_ATO', "
            + "'PRORROGACAO_ATO', 'DECLARACAO_NULIDADE_JUDICIAL'";

    @Test
    void deveAcrescentarAsColunasV15ComOsValoresPorOmissaoDoLld() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT count(*) FROM instrumento_dinamica_config "
                    + "WHERE associacao_instrumentos <> 'UM' AND tipo_dinamica_id NOT IN (SELECT id FROM tipo_dinamica "
                    + "WHERE inicio_sem_igt)")).isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(*) FROM instrumento_procedimento_config WHERE (ato_pontual "
                    + "OR tamanho_maximo_processo IS DISTINCT FROM 5368709120) AND tipo_procedimento_id NOT IN "
                    + "(SELECT id FROM tipo_procedimento WHERE codigo IN (" + CODIGOS_ATO_PONTUAL + "))")).isEqualTo("0");
            assertThat(texto(ligacao, "SELECT string_agg(column_name || ':' || data_type || ':' || is_nullable, ',' "
                    + "ORDER BY column_name) FROM information_schema.columns "
                    + "WHERE table_name = 'instrumento_procedimento_config' "
                    + "AND column_name IN ('tamanho_maximo_processo', 'repetivel', 'ato_pontual')"))
                    .isEqualTo("ato_pontual:boolean:NO,repetivel:boolean:NO,tamanho_maximo_processo:bigint:YES");

            for (String associacao : new String[] {"UM", "UM_OU_MAIS", "OPCIONAL"}) {
                executar(ligacao, "UPDATE instrumento_dinamica_config SET associacao_instrumentos = '" + associacao + "'");
            }
            assertThatThrownBy(() -> executar(ligacao,
                    "UPDATE instrumento_dinamica_config SET associacao_instrumentos = 'VARIOS'"))
                    .hasMessageContaining("ck_instrumento_dinamica_config_associacao");
            assertThatThrownBy(() -> executar(ligacao,
                    "UPDATE instrumento_procedimento_config SET tamanho_maximo_processo = 0"))
                    .hasMessageContaining("ck_instrumento_procedimento_config_tamanho");
            executar(ligacao, "UPDATE instrumento_procedimento_config SET tamanho_maximo_processo = NULL");
        });
    }

    @Test
    void deveSemearRepetivelSoNaAberturaDeDiscussaoPublicaEReporFalsoNoRollback() throws Exception {
        comBaseMigrada(ligacao -> {
            String procedimentos = "FROM instrumento_procedimento_config c JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id ";
            assertThat(texto(ligacao, "SELECT string_agg(DISTINCT t.codigo, ',') " + procedimentos + "WHERE c.repetivel"))
                    .isEqualTo("ABERTURA_DISCUSSAO_PUBLICA");
            assertThat(texto(ligacao, "SELECT count(*) " + procedimentos
                    + "WHERE t.codigo = 'ABERTURA_DISCUSSAO_PUBLICA' AND NOT c.repetivel")).isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(*) " + procedimentos + "WHERE t.codigo IN "
                    + "('ABERTURA_DISCUSSAO_PUBLICA_DL10') AND c.repetivel"))
                    .isEqualTo("0");

            reverter(ligacao, 9);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT count(*) FROM instrumento_procedimento_config WHERE repetivel")).isEqualTo("0");
        });
    }

    @Test
    void deveRetirarOutrosAtosEAnexosDasDinamicasMantendoOsTiposDeAtoPontual() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT count(*) FROM tipo_dinamica WHERE codigo = 'OUTROS_ATOS_ANEXOS'"))
                    .isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(*) FROM instrumento_dinamica_config WHERE id IN (113, 2113, 3113)"))
                    .isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(*) FROM tipo_procedimento WHERE ativo AND codigo IN ("
                    + CODIGOS_ATO_PONTUAL + ")")).isEqualTo("5");
        });
    }

    @Test
    void deveRecusarRetirarOutrosAtosEAnexosEnquantoHouverDinamicasIniciadas() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 15);
            ligacao.setAutoCommit(true);
            inserirProcessoComDocumento(ligacao);
            executar(ligacao, "UPDATE dinamica SET instrumento_dinamica_config_id = 113");

            assertThatThrownBy(() -> migrar(ligacao)).hasStackTraceContaining(
                    "Há Dinâmicas iniciadas de Outros atos e anexos");
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT count(*) FROM tipo_dinamica WHERE codigo = 'OUTROS_ATOS_ANEXOS'"))
                    .isEqualTo("1");
            assertThat(texto(ligacao, "SELECT count(*) FROM instrumento_dinamica_config WHERE id IN (113, 2113, 3113)"))
                    .isEqualTo("3");
        });
    }

    @Test
    void deveReverterAsColunasV15EReporOutrosAtosEAnexos() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 15);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "instrumento_dinamica_config", "associacao_instrumentos")).isFalse();
            for (String coluna : new String[] {"tamanho_maximo_processo", "repetivel", "ato_pontual"}) {
                assertThat(colunaExiste(ligacao, "instrumento_procedimento_config", coluna)).isFalse();
            }
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_constraint WHERE conname IN "
                    + "('ck_instrumento_dinamica_config_associacao', 'ck_instrumento_procedimento_config_tamanho')"))
                    .isEqualTo("0");
            assertThat(texto(ligacao, "SELECT nome || '|' || ativo::text FROM tipo_dinamica WHERE id = 13"))
                    .isEqualTo("Outros atos e anexos|true");
            assertThat(linhas(ligacao, "SELECT id || ':' || tipo_instrumento_id || ':' || tipo_dinamica_id || ':' || ativo::text "
                    + "FROM instrumento_dinamica_config WHERE tipo_dinamica_id = 13 ORDER BY id"))
                    .containsExactly("113:1:13:true", "2113:2:13:true", "3113:3:13:true");

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "tipo_dinamica")).isEqualTo(17);
            assertThat(colunaExiste(ligacao, "instrumento_procedimento_config", "ato_pontual")).isTrue();
        });
    }

    @Test
    void deveImporUnicidadeAoIgtDoSnitSemImpedirInstrumentosSemIgt() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirAutorEEntidade(ligacao);
            inserirInstrumento(ligacao, "1", "PDM sem IGT", null);
            inserirInstrumento(ligacao, "2", "Outro PDM sem IGT", null);
            inserirInstrumento(ligacao, "3", "PDM de Sintra", "1001");

            assertThatThrownBy(() -> inserirInstrumento(ligacao, "4", "PDM de Sintra outra vez", "1001"))
                    .hasMessageContaining("uq_instrumento_id_igt_snit");
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_instrumento_id_igt_snit'"))
                    .isEqualTo("0");
        });
    }

    @Test
    void deveGuardarDesignacoesDeAte255CaracteresNoInstrumento() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirAutorEEntidade(ligacao);
            inserirInstrumento(ligacao, "1", "x".repeat(255), null);

            assertThatThrownBy(() -> inserirInstrumento(ligacao, "2", "x".repeat(256), null))
                    .hasMessageContaining("255");
            assertThat(tamanhoDaDesignacao(ligacao)).isEqualTo("255");
        });
    }

    @Test
    void deveReverterAUnicidadeEOTamanhoDaDesignacaoCortandoAsDesignacoesLongas() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirAutorEEntidade(ligacao);
            inserirInstrumento(ligacao, "1", "y".repeat(80), "1001");

            reverter(ligacao, 31);
            ligacao.setAutoCommit(true);

            assertThat(tamanhoDaDesignacao(ligacao)).isEqualTo("50");
            assertThat(texto(ligacao, "SELECT designacao FROM instrumento")).isEqualTo("y".repeat(50));
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_constraint WHERE conname = 'uq_instrumento_id_igt_snit'"))
                    .isEqualTo("0");
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_instrumento_id_igt_snit'"))
                    .isEqualTo("1");
            inserirInstrumento(ligacao, "2", "Repetido", "1001");
        });
    }

    @Test
    void deveAcrescentarOContactoDeTramitacaoAoProcessoEReverteloSemTocarNosElementosAfetados() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT data_type || '|' || character_maximum_length || '|' || is_nullable "
                    + "FROM information_schema.columns WHERE table_schema = current_schema() "
                    + "AND table_name = 'processo' AND column_name = 'contacto_tramitacao'"))
                    .isEqualTo("character varying|254|YES");

            reverter(ligacao, 29);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "processo", "contacto_tramitacao")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "elementos_afetados")).isTrue();
        });
    }

    @Test
    void deveAcrescentarOsCamposDesbloqueadosAoProcessoEReverteloSemTocarNoContacto() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT data_type || '|' || is_nullable "
                    + "FROM information_schema.columns WHERE table_schema = current_schema() "
                    + "AND table_name = 'processo' AND column_name = 'campos_desbloqueados'"))
                    .isEqualTo("jsonb|YES");

            reverter(ligacao, 28);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "processo", "campos_desbloqueados")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "contacto_tramitacao")).isTrue();
        });
    }

    @Test
    void deveAcrescentarAEntidadeEmitenteEOContactoDePublicacaoAoProcessoEReverteLosSemTocarNosCamposDesbloqueados() throws Exception {
        comBaseMigrada(ligacao -> {
            for (String coluna : List.of("entidade_emitente", "contacto_publicacao")) {
                assertThat(texto(ligacao, "SELECT data_type || '|' || character_maximum_length || '|' || is_nullable "
                        + "FROM information_schema.columns WHERE table_schema = current_schema() "
                        + "AND table_name = 'processo' AND column_name = '" + coluna + "'"))
                        .isEqualTo("character varying|500|YES");
            }

            reverter(ligacao, 27);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "processo", "entidade_emitente")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "contacto_publicacao")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "campos_desbloqueados")).isTrue();
        });
    }

    @Test
    void devePreencherAsDescricoesDaEntidadeAoMigrarDeixandoCodigoETipoNulos() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 26);
            ligacao.setAutoCommit(true);
            inserirEntidade(ligacao, "500000004");

            migrarOsProximos(ligacao, 22);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT descricao_incm || '|' || descricao_saigt || '|' || coalesce(codigo, 'nulo') "
                    + "|| '|' || coalesce(tipo_entidade, 'nulo') FROM entidade WHERE nif = '500000004'"))
                    .isEqualTo("Câmara Municipal|Câmara Municipal|nulo|nulo");
            assertThat(colunaExiste(ligacao, "entidade", "nome")).isFalse();
            assertThat(texto(ligacao, "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'entidade' "
                    + "AND column_name = 'descricao_saigt'")).isEqualTo("NO");
        });
    }

    @Test
    void deveRecusarTipoDeEntidadeForaDaListaCodigoRepetidoECodigoOuTipoNulos() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirEntidade(ligacao, "500000005");
            inserirEntidade(ligacao, "500000006");

            assertThatThrownBy(() -> executar(ligacao, "UPDATE entidade SET tipo_entidade = 'XPTO' WHERE nipc = '500000005'"))
                    .hasMessageContaining("ck_entidade_tipo_entidade");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE entidade SET tipo_entidade = NULL WHERE nipc = '500000005'"))
                    .hasMessageContaining("tipo_entidade");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE entidade SET codigo = NULL WHERE nipc = '500000005'"))
                    .hasMessageContaining("codigo");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE entidade SET codigo = 'T500000005' WHERE nipc = '500000006'"))
                    .hasMessageContaining("uq_entidade_codigo");
            assertThat(definicaoDoIndice(ligacao, "idx_entidade_tipo_entidade")).contains("(tipo_entidade)");
        });
    }

    @Test
    void deveReverterAEntidadeDevolvendoONomeSemTocarNoContactoDePublicacao() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirEntidade(ligacao, "500000007");

            reverter(ligacao, 26);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT nome FROM entidade WHERE nif = '500000007'")).isEqualTo("Câmara Municipal");
            for (String coluna : List.of("descricao_incm", "descricao_saigt", "codigo", "tipo_entidade")) {
                assertThat(colunaExiste(ligacao, "entidade", coluna)).isFalse();
            }
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_entidade_tipo_entidade'"))
                    .isEqualTo("0");
            assertThat(colunaExiste(ligacao, "processo", "contacto_publicacao")).isTrue();
        });
    }

    @Test
    void deveMarcarAInformacaoBaseNosItensSemeadosDoProcedimento() throws Exception {
        comBaseMigrada(ligacao -> {
            semAListaDaDgt(ligacao);
            assertThat(linhas(ligacao, "SELECT codigo || '|' || coalesce(elemento_informacao_base, 'NULL') "
                    + "FROM documento_config WHERE instrumento_procedimento_config_id = "
                    + PROCEDIMENTO_DECISAO_INICIO_ALTERACAO_PDM + " ORDER BY ordem")).containsExactly(
                    "DELIBERACAO_PUB|DELIBERACAO", "DELIBERACAO_DEP|DELIBERACAO",
                    "TEXTO_ATO_PUB|TEXTO_ATO", "TEXTO_ATO_DEP|TEXTO_ATO",
                    "PLANTA_LOCALIZACAO_PUB|NULL", "PLANTA_LOCALIZACAO_DEP|NULL");
        });
    }

    @Test
    void deveRecusarElementoDeInformacaoBaseForaDoDominio() throws Exception {
        comBaseMigrada(ligacao -> {
            String insercao = "INSERT INTO documento_config (instrumento_procedimento_config_id, codigo, nome, separador, "
                    + "categoria, tipo_elemento, formato, extensoes_aceites, ordem, elemento_informacao_base) "
                    + "SELECT min(id), '%s', 'Item', 'Item', 'PUBLICACAO', 'PECA_ESCRITA', 'EDITAVEL', '[\"pdf\"]', 1, %s "
                    + "FROM instrumento_procedimento_config";

            executar(ligacao, insercao.formatted("NULO", "NULL"));
            executar(ligacao, insercao.formatted("VALIDO", "'TEXTO_ATO'"));
            assertThatThrownBy(() -> executar(ligacao, insercao.formatted("INVALIDO", "'OUTRO'")))
                    .hasMessageContaining("ck_documento_config_elemento_informacao_base");
        });
    }

    @Test
    void naoDeveTerUmSeparadorComDoisElementosDeInformacaoBaseDiferentes() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT instrumento_procedimento_config_id || '|' || separador "
                    + "FROM documento_config WHERE elemento_informacao_base IS NOT NULL "
                    + "GROUP BY instrumento_procedimento_config_id, separador "
                    + "HAVING count(DISTINCT elemento_informacao_base) > 1")).isEmpty();
        });
    }

    @Test
    void deveReverterOElementoDeInformacaoBaseSemTocarNosItens() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 25);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "documento_config", "elemento_informacao_base")).isFalse();
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_constraint "
                    + "WHERE conname = 'ck_documento_config_elemento_informacao_base'")).isEqualTo("0");
            assertThat(contar(ligacao, "documento_config")).isEqualTo(6);

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE elemento_informacao_base IS NOT NULL"))
                    .isEqualTo(String.valueOf(216 + ITENS_DA_PRORROGACAO_DO_ATO_COM_INFORMACAO_BASE));
        });
    }

    @Test
    void deveCriarASnitReferenciaComAsColunasOCheckDasSeisListasEAUnicidade() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT column_name || '|' || data_type || '|' || is_nullable "
                    + "FROM information_schema.columns WHERE table_schema = current_schema() "
                    + "AND table_name = 'snit_referencia' ORDER BY ordinal_position")).containsExactly(
                    "id|bigint|NO",
                    "lista|character varying|NO",
                    "codigo|character varying|NO",
                    "designacao|character varying|NO",
                    "dados_origem|jsonb|NO",
                    "ativo|boolean|NO",
                    "sincronizado_em|timestamp with time zone|NO");

            String insercao = "INSERT INTO snit_referencia (lista, codigo, designacao, dados_origem, ativo, "
                    + "sincronizado_em) VALUES ('%s', '%s', 'Designação', '{\"codigo\": \"1\"}', true, now())";
            try (Connection app = DriverManager.getConnection(ligacao.getMetaData().getURL(), "saigt_app", "teste")) {
                for (String lista : new String[] {"INSTRUMENT_TYPES", "PROCEDURES", "PROCEDURE_TYPES", "CLASSES",
                        "NUT2", "MUNICIPALITIES"}) {
                    executar(app, insercao.formatted(lista, "1"));
                }
                executar(app, "UPDATE snit_referencia SET ativo = false WHERE lista = 'NUT2'");
            }
            assertThat(texto(ligacao, "SELECT count(*) || '|' || count(*) FILTER (WHERE ativo) FROM snit_referencia"))
                    .isEqualTo("6|5");
            assertThatThrownBy(() -> executar(ligacao, insercao.formatted("SRUP", "1")))
                    .hasMessageContaining("ck_snit_referencia_lista");
            assertThatThrownBy(() -> executar(ligacao, insercao.formatted("NUT2", "1")))
                    .hasMessageContaining("uq_snit_referencia_lista_codigo");
        });
    }

    @Test
    void deveReverterASnitReferenciaSemTocarNoElementoDeInformacaoBase() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 23);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "snit_referencia")).isFalse();
            assertThat(colunaExiste(ligacao, "documento_config", "elemento_informacao_base")).isTrue();
        });
    }

    @Test
    void deveRetirarDoProcessoAsColunasDoDesenhoAnteriorEReporVaziasNoRollback() throws Exception {
        List<String> retiradas = List.of("pcgt_confirmado", "codigo_estado_incm", "idus_sistema", "idus_id_tmp",
                "idus_id_definitivo", "idus_ordem_tmp", "idus_ordem_definitiva", "idus_tmp", "idus_definitivo",
                "id_snit", "proc_pcgt", "proc_geoconforme");
        List<String> mantidas = List.of("incm_enrollment_id", "incm_act_identifier", "campos_desbloqueados");
        comBaseMigrada(ligacao -> {
            for (String coluna : retiradas) {
                assertThat(colunaExiste(ligacao, "processo", coluna)).as(coluna).isFalse();
            }
            for (String coluna : mantidas) {
                assertThat(colunaExiste(ligacao, "processo", coluna)).as(coluna).isTrue();
            }

            reverter(ligacao, 22);
            ligacao.setAutoCommit(true);

            assertThat(linhas(ligacao, "SELECT column_name || '|' || data_type || '|' "
                    + "|| coalesce(character_maximum_length::text, '') || '|' || is_nullable "
                    + "|| '|' || coalesce(column_default, '') FROM information_schema.columns "
                    + "WHERE table_schema = current_schema() AND table_name = 'processo' "
                    + "AND column_name IN ('" + String.join("', '", retiradas) + "') ORDER BY column_name"))
                    .containsExactly(
                            "codigo_estado_incm|integer||YES|",
                            "id_snit|character varying|50|YES|",
                            "idus_definitivo|character varying|200|YES|",
                            "idus_id_definitivo|character varying|50|YES|",
                            "idus_id_tmp|character varying|50|YES|",
                            "idus_ordem_definitiva|character varying|50|YES|",
                            "idus_ordem_tmp|character varying|50|YES|",
                            "idus_sistema|character varying|2|YES|",
                            "idus_tmp|character varying|200|YES|",
                            "pcgt_confirmado|boolean||NO|false",
                            "proc_geoconforme|character varying|50|YES|",
                            "proc_pcgt|character varying|50|YES|");
            for (String coluna : mantidas) {
                assertThat(colunaExiste(ligacao, "processo", coluna)).as(coluna).isTrue();
            }
        });
    }

    @Test
    void deveCriarOsConcelhosDoInstrumentoComPkEFkAoInstrumento() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT column_name || '|' || data_type || '|' "
                    + "|| coalesce(character_maximum_length::text, '') || '|' || is_nullable FROM information_schema.columns "
                    + "WHERE table_schema = current_schema() AND table_name = 'instrumento_concelho' ORDER BY ordinal_position"))
                    .containsExactly("instrumento_id|uuid||NO", "municipio_codigo|character varying|50|NO");
            assertThat(definicaoDoIndice(ligacao, "idx_instrumento_concelho_municipio")).contains("municipio_codigo");

            inserirProcessoComDocumento(ligacao);
            String insercao = "INSERT INTO instrumento_concelho (instrumento_id, municipio_codigo) VALUES ('%s', '%s')";
            try (Connection app = DriverManager.getConnection(ligacao.getMetaData().getURL(), "saigt_app", "teste")) {
                executar(app, insercao.formatted("00000000-0000-0000-0000-000000000002", "1111"));
                executar(app, insercao.formatted("00000000-0000-0000-0000-000000000002", "1105"));
            }
            assertThat(texto(ligacao, "SELECT count(*) FROM instrumento_concelho")).isEqualTo("2");
            assertThatThrownBy(() -> executar(ligacao, insercao.formatted("00000000-0000-0000-0000-000000000002", "1111")))
                    .hasMessageContaining("pk_instrumento_concelho");
            assertThatThrownBy(() -> executar(ligacao, insercao.formatted(UUID_SEM_PROCESSO, "1111")))
                    .hasMessageContaining("fk_instrumento_concelho_instrumento");
        });
    }

    @Test
    void deveReverterOsConcelhosDoInstrumentoSemTocarNoEmVigor() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 20);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "instrumento_concelho")).isFalse();
            assertThat(colunaExiste(ligacao, "instrumento", "em_vigor")).isTrue();
            assertThat(tabelaExiste(ligacao, "snit_referencia")).isTrue();
        });
    }

    @Test
    void deveMudarALagoaDoAlgarvePara5126MantendoOIdEAsReferenciasEVoltarA5128AoReverter() throws Exception {
        comBaseMigrada(ligacao -> {
            String id = texto(ligacao, "SELECT id FROM entidade WHERE codigo = '5126'");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "entidade WHERE codigo = '5126'")).isZero();
            assertThat(texto(ligacao, "SELECT id FROM entidade WHERE codigo = '5128'")).isEqualTo(id);
            executar(ligacao, "WITH u AS (INSERT INTO utilizador (id, perfil_interno) VALUES (gen_random_uuid(), 'VISITANTE') RETURNING id) "
                    + "INSERT INTO entidade_contacto (entidade_id, email, principal, criado_por) SELECT " + id
                    + ", 'lagoa@exemplo.pt', TRUE, u.id FROM u");

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "entidade WHERE codigo = '5128'")).isZero();
            assertThat(texto(ligacao, "SELECT id || '|' || nipc || '|' || dico || '|' || descricao_incm FROM entidade "
                    + "WHERE codigo = '5126'")).isEqualTo(id + "|506804240|0806|Município de Lagoa (Algarve)");
            assertThat(texto(ligacao, "SELECT e.codigo FROM entidade_contacto c JOIN entidade e ON e.id = c.entidade_id "
                    + "WHERE c.email = 'lagoa@exemplo.pt'")).isEqualTo("5126");
        });
    }

    @Test
    void deveMarcarAMudancaDaLagoaComoCorridaSemAlterarNadaQuandoJaHaUmaEntidade5126() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126);
            ligacao.setAutoCommit(true);
            executar(ligacao, INSERCAO_ENTIDADE_COM_DICO.formatted("5126", "DGT", "NULL"));

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, "SELECT nipc FROM entidade WHERE codigo = '5128'")).isEqualTo("506804240");
            assertThat(texto(ligacao, "SELECT nipc FROM entidade WHERE codigo = '5126'")).isEqualTo("500000099");
            assertThat(texto(ligacao, "SELECT exectype FROM databasechangelog WHERE id = '2026-11-14-3-lagoa-algarve-5126'"))
                    .isEqualTo("MARK_RAN");
        });
    }

    private static final String INSERCAO_ENTIDADE_COM_DICO = "INSERT INTO entidade (codigo, descricao_incm, "
            + "descricao_saigt, tipo_entidade, nipc, dico) VALUES ('%s', 'Entidade', 'Entidade', '%s', '500000099', %s)";

    @Test
    void deveCarregarAs366EntidadesDoExtratoV5ComATipologiaEOsDicoDasCm() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(contar(ligacao, "entidade")).isEqualTo(366);
            assertThat(linhas(ligacao, "SELECT tipo_entidade || ' ' || count(*) FROM entidade GROUP BY tipo_entidade"))
                    .containsExactlyInAnyOrder("CM 308", "CCDR_DR 7", "AM 2", "CIM 21", "PCM 3", "SEC_ESTADO 20", "DGT 1",
                            "ICNF 1", "APA 1", "DGPC 1", "IP 1");

            assertThat(contar(ligacao, "entidade WHERE codigo = '5128'")).isZero();
            assertThat(texto(ligacao, "SELECT codigo || '|' || nipc || '|' || dico FROM entidade WHERE nipc = '506804240'"))
                    .isEqualTo("5126|506804240|0806");
            assertThat(contar(ligacao, "entidade WHERE nipc = '506804240'")).isEqualTo(1);
            assertThat(texto(ligacao, "SELECT string_agg(codigo, ',' ORDER BY codigo) FROM entidade "
                    + "WHERE nipc = '600025861'")).isEqualTo("3001,3007");
            assertThat(texto(ligacao, "SELECT nipc || '|' || descricao_incm FROM entidade WHERE codigo = '5085'"))
                    .isEqualTo("505187531|Município de Cascais");

            assertThat(contar(ligacao, "entidade WHERE dico IS NOT NULL")).isEqualTo(308);
            assertThat(contar(ligacao, "entidade WHERE tipo_entidade = 'CM' AND dico ~ '^[0-9]{4}$'")).isEqualTo(308);
            assertThat(contar(ligacao, "entidade WHERE tipo_entidade <> 'CM' AND dico IS NOT NULL")).isZero();
            assertThat(linhas(ligacao, "SELECT codigo || '=' || dico FROM entidade "
                    + "WHERE codigo IN ('5245', '5003', '5078', '5127', '5161') ORDER BY codigo"))
                    .containsExactly("5003=3101", "5078=4501", "5127=4201", "5161=0706", "5245=1111");
            assertThat(tabelaExiste(ligacao, "entidade_carga")).isFalse();
        });
    }

    @Test
    void deveDesativarAsPlantasDaDecisaoDeElaborarGuardarODoValidateUserEAsObservacoesDosOutrosEReverter() throws Exception {
        comBaseMigrada(ligacao -> {
            String plantasAtivas = "documento_config WHERE instrumento_procedimento_config_id IN (201, 205) "
                    + "AND pagina = 'PLANTAS' AND ativo";
            assertThat(contar(ligacao, plantasAtivas)).isZero();
            assertThat(contar(ligacao, "documento_config WHERE instrumento_procedimento_config_id IN (201, 205) "
                    + "AND codigo LIKE 'PLANTA_AREA_INTERVENCAO_%' AND ativo")).isZero();
            assertThat(colunaExiste(ligacao, "entidade", "dados_incm")).isTrue();
            assertThat(colunaExiste(ligacao, "processo", "observacoes_outros")).isTrue();

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, plantasAtivas)).isEqualTo(8);
            assertThat(contar(ligacao, "documento_config WHERE instrumento_procedimento_config_id IN (201, 205) "
                    + "AND codigo LIKE 'PLANTA_AREA_INTERVENCAO_%' AND ativo")).isEqualTo(4);
            assertThat(colunaExiste(ligacao, "entidade", "dados_incm")).isFalse();
            assertThat(colunaExiste(ligacao, "entidade", "morada_arteria")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "observacoes_outros")).isFalse();
        });
    }

    @Test
    void deveTerATabelaEntidadeConformeOLld() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT column_name || '|' || data_type || '|' || is_nullable || '|' "
                    + "|| coalesce(character_maximum_length::text, '') FROM information_schema.columns "
                    + "WHERE table_schema = current_schema() AND table_name = 'entidade'")).containsExactlyInAnyOrder(
                    "id|bigint|NO|", "codigo|character varying|NO|20", "descricao_incm|character varying|NO|200",
                    "descricao_saigt|character varying|NO|200", "tipo_entidade|character varying|NO|20",
                    "nipc|character varying|YES|20", "dico|character varying|YES|4",
                    "telefone|character varying|YES|30", "email|character varying|YES|200",
                    "responsavel_nome|character varying|YES|200", "responsavel_email|character varying|YES|200",
                    "responsavel_cargo|character varying|YES|200", "atualizado_em|timestamp with time zone|YES|",
                    "ativo|boolean|NO|", "morada_arteria|character varying|YES|200", "morada_numero|character varying|YES|20",
                    "morada_piso|character varying|YES|20", "morada_codigo_postal|character varying|YES|20",
                    "morada_designacao_postal|character varying|YES|200", "morada_localidade|character varying|YES|200",
                    "incm_enrollment_id|character varying|YES|100", "dr_parte_id|bigint|YES|",
                    "dr_parte_letra|character varying|YES|10", "dr_parte_descricao|character varying|YES|200",
                    "atos_serie1|jsonb|YES|", "atos_serie2|jsonb|YES|", "dados_incm|jsonb|YES|");
            assertThat(colunaExiste(ligacao, "entidade", "nif")).isFalse();
            assertThat(colunaExiste(ligacao, "entidade", "utilizador_email")).isFalse();
            assertThat(definicaoDoIndice(ligacao, "idx_entidade_nipc")).contains("(nipc)").doesNotContain("UNIQUE");
            assertThat(definicaoDoIndice(ligacao, "idx_entidade_nif")).isNull();
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_constraint WHERE conrelid = 'entidade'::regclass "
                    + "AND conname IN ('uq_entidade_nif', 'uq_entidade_codigo', 'ck_entidade_dico', "
                    + "'ck_entidade_dico_cm', 'ck_entidade_tipo_entidade')")).isEqualTo("4");
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'ck_entidade_tipo_entidade'")).doesNotContain("IS NULL");
        });
    }

    @Test
    void deveRecusarDicoSemQuatroAlgarismosDicoForaDasCmETipoForaDaLista() throws Exception {
        comBaseMigrada(ligacao -> {
            executar(ligacao, INSERCAO_ENTIDADE_COM_DICO.formatted("X1", "CM", "'0412'"));
            executar(ligacao, INSERCAO_ENTIDADE_COM_DICO.formatted("X2", "DGT", "NULL"));

            assertThatThrownBy(() -> executar(ligacao, INSERCAO_ENTIDADE_COM_DICO.formatted("X3", "CM", "'412'")))
                    .hasMessageContaining("ck_entidade_dico");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_ENTIDADE_COM_DICO.formatted("X4", "CM", "'04a2'")))
                    .hasMessageContaining("ck_entidade_dico");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_ENTIDADE_COM_DICO.formatted("X5", "DGT", "'0412'")))
                    .hasMessageContaining("ck_entidade_dico_cm");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_ENTIDADE_COM_DICO.formatted("X6", "XPTO", "NULL")))
                    .hasMessageContaining("ck_entidade_tipo_entidade");
            assertThatThrownBy(() -> executar(ligacao, INSERCAO_ENTIDADE_COM_DICO.formatted("X7", null, "NULL")
                    .replace("'null'", "NULL"))).hasMessageContaining("tipo_entidade");
        });
    }

    @Test
    void deveCriarAsPlantasEOsMetadadosComOsItensDaDecisaoDeInicioEReverteLosSemTocarNaNutII() throws Exception {
        comBaseMigrada(ligacao -> {
            for (int procedimento : new int[] {201, 205}) {
                assertThat(linhas(ligacao, "SELECT codigo || ':' || pagina || ':' || obrigatorio::text FROM documento_config "
                        + "WHERE instrumento_procedimento_config_id = " + procedimento + " AND ordem > 4 ORDER BY ordem"))
                        .containsExactly("PLANTA_AREA_INTERVENCAO_PUB:DOCUMENTOS:false",
                                "PLANTA_AREA_INTERVENCAO_DEP:DOCUMENTOS:false", "PLANTAS_ZIP_ORDENAMENTO_DEP:PLANTAS:false",
                                "PLANTAS_ZIP_CONDICIONANTES_DEP:PLANTAS:false", "PLANTAS_TXT_NOMES_DEP:PLANTAS:false",
                                "PLANTAS_CARTOGRAMA_DEP:PLANTAS:false");
            }
            assertThat(texto(ligacao, "SELECT count(*) FROM documento_config WHERE pagina = 'DOCUMENTOS'"))
                    .isEqualTo(String.valueOf(1763 + ITENS_DOS_SEEDS_DOCUMENTAIS_DO_BUILD));
            assertThat(tabelaExiste(ligacao, "processo_planta")).isFalse();
            assertThat(colunaExiste(ligacao, "processo_metadados", "dados")).isTrue();

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DOS_LIMITES_DOCUMENTAIS + CHANGESETS_DOS_LINKS_PUBLICOS + CHANGESETS_DA_PLANTA_POR_FOLHA + CHANGESETS_DAS_LISTAS_DA_ALTERACAO + CHANGESETS_DO_MANIFESTO_SNIT + CHANGESETS_DO_NUMERO_DEPOSITO + CHANGESETS_DO_SNIT_CODIGO + CHANGESETS_DA_LOCALIZACAO + 1);

            assertThat(colunaExiste(ligacao, "documento_config", "pagina")).isFalse();
            assertThat(colunaExiste(ligacao, "processo_planta", "nome_tiff")).isFalse();
            assertThat(colunaExiste(ligacao, "processo_metadados", "dados")).isFalse();
            assertThat(colunaExiste(ligacao, "instrumento", "nut_ii_codigo")).isTrue();
            assertThat(contar(ligacao, "documento_config")).isEqualTo(157);
        });
    }

    @Test
    void deveMigrarAsLinhasDeProcessoPlantaParaPlantaEReverterRepondoAProcessoPlanta() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DOS_LIMITES_DOCUMENTAIS + CHANGESETS_DOS_LINKS_PUBLICOS + CHANGESETS_DA_PLANTA_POR_FOLHA);
            ligacao.setAutoCommit(true);
            inserirProcessoComDocumento(ligacao);
            String documento = "INSERT INTO processo_documento (id, processo_id, documento_config_id, categoria, extensao, "
                    + "nome_ficheiro, caminho_ficheiro, hash_sha256, versao_objeto, tamanho_bytes, criado_por) VALUES "
                    + "(gen_random_uuid(), '" + PROCESSO_ID + "', (SELECT id FROM documento_config WHERE codigo = '%s' "
                    + "AND instrumento_procedimento_config_id = 201), 'DEPOSITO', 'zip', '%s', 'processos/00001/DEPOSITO/%s', "
                    + "'" + "a".repeat(64) + "', 'v1', 10, '00000000-0000-0000-0000-000000000001')";
            executar(ligacao, documento.formatted("PLANTAS_ZIP_ORDENAMENTO_DEP", "ordenamento.zip", "ord"));
            executar(ligacao, documento.formatted("PLANTAS_ZIP_CONDICIONANTES_DEP", "condicionantes.zip", "cond"));
            String planta = "INSERT INTO planta (id, processo_id, processo_documento_id, nome_zip, criado_em, atualizado_em) "
                    + "SELECT gen_random_uuid(), '" + PROCESSO_ID + "', id, nome_ficheiro, now(), now() "
                    + "FROM processo_documento WHERE nome_ficheiro = '%s'";
            executar(ligacao, planta.formatted("ordenamento.zip"));
            executar(ligacao, planta.formatted("texto.pdf"));
            String associacao = "INSERT INTO processo_planta (processo_id, ordem, tipo, nome, folha, nome_tiff) "
                    + "VALUES ('" + PROCESSO_ID + "', %d, '%s', '%s', '%s', '%s')";
            executar(ligacao, associacao.formatted(1, "Ordenamento", "Qualificação do Solo", "1", "o1.tif"));
            executar(ligacao, associacao.formatted(2, "Ordenamento", "Qualificação do Solo", "2", "o2.tif"));
            executar(ligacao, associacao.formatted(3, "Condicionantes", "Servidões", "1", "c1.tif"));

            migrarOsProximos(ligacao, CHANGESETS_DA_PLANTA_POR_FOLHA);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "processo_planta")).isFalse();
            assertThat(linhas(ligacao, "SELECT nome_regulamento || '|' || numero_folha || '|' || nome_tiff || '|' || nome_zip "
                    + "FROM planta WHERE nome_regulamento IS NOT NULL ORDER BY nome_regulamento, numero_folha"))
                    .containsExactly("Qualificação do Solo|1|o1.tif|ordenamento.zip",
                            "Qualificação do Solo|2|o2.tif|ordenamento.zip", "Servidões|1|c1.tif|condicionantes.zip");
            assertThat(contar(ligacao, "planta WHERE nome_tiff IS NULL AND nome_zip = 'ordenamento.zip'")).isZero();
            assertThat(contar(ligacao, "planta WHERE nome_zip = 'texto.pdf'")).isEqualTo(1);
            assertThat(contar(ligacao, "planta p JOIN processo_documento d ON d.id = p.processo_documento_id "
                    + "WHERE p.nome_tiff = 'c1.tif' AND d.nome_ficheiro = 'condicionantes.zip'")).isEqualTo(1);

            // Só as plantas por folha voltaram a migrar-se: contam elas e não as constantes dos changesets seguintes.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PLANTA_POR_FOLHA);
            ligacao.setAutoCommit(true);

            assertThat(linhas(ligacao, "SELECT ordem || '|' || tipo || '|' || nome || '|' || folha || '|' || nome_tiff "
                    + "FROM processo_planta ORDER BY ordem"))
                    .containsExactly("1|Ordenamento|Qualificação do Solo|1|o1.tif",
                            "2|Ordenamento|Qualificação do Solo|2|o2.tif", "3|Condicionantes|Servidões|1|c1.tif");
            assertThat(contar(ligacao, "planta")).isEqualTo(1);
        });
    }

    @Test
    void deveApagarATabelaNutEANutIIDoInstrumentoERepolasVaziasNoRollback() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(tabelaExiste(ligacao, "nut")).isFalse();
            assertThat(colunaExiste(ligacao, "instrumento", "nut_ii_codigo")).isFalse();

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "nut")).isTrue();
            assertThat(contar(ligacao, "nut")).isZero();
            assertThat(colunaExiste(ligacao, "instrumento", "nut_ii_codigo")).isTrue();
        });
    }

    @Test
    void deveAcrescentarANutIIAoInstrumentoERevertelaSemTocarNoSeedDosItensDocumentais() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "instrumento", "nut_ii_codigo")).isTrue();
            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA);
            ligacao.setAutoCommit(true);

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DOS_LIMITES_DOCUMENTAIS + CHANGESETS_DOS_LINKS_PUBLICOS + CHANGESETS_DA_PLANTA_POR_FOLHA + CHANGESETS_DAS_LISTAS_DA_ALTERACAO + CHANGESETS_DO_MANIFESTO_SNIT + CHANGESETS_DO_NUMERO_DEPOSITO + CHANGESETS_DO_SNIT_CODIGO + CHANGESETS_DA_LOCALIZACAO + 2);

            assertThat(colunaExiste(ligacao, "instrumento", "nut_ii_codigo")).isFalse();
            assertThat(contar(ligacao, "documento_config")).isEqualTo(157);
        });
    }

    @Test
    void deveManterOIdDaEntidadeCriadaPeloLoginAoAtribuirLheOCodigoERemoverAsRestantesSemCodigo() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_FINAIS);
            ligacao.setAutoCommit(true);
            executar(ligacao, "INSERT INTO entidade (descricao_incm, descricao_saigt, nif) VALUES "
                    + "('Câmara Municipal de Sintra', 'Câmara Municipal de Sintra', '500051062'), "
                    + "('Sem correspondência', 'Sem correspondência', '501111222'), "
                    + "('Dois códigos possíveis', 'Dois códigos possíveis', '600025861')");
            String idSintra = texto(ligacao, "SELECT id FROM entidade WHERE nif = '500051062'");

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT id || '|' || codigo || '|' || descricao_incm || '|' || descricao_saigt "
                    + "|| '|' || tipo_entidade || '|' || dico FROM entidade WHERE nipc = '500051062'"))
                    .isEqualTo(idSintra + "|5245|Município de Sintra|Município de Sintra|CM|1111");
            assertThat(contar(ligacao, "entidade")).isEqualTo(366);
            assertThat(contar(ligacao, "entidade WHERE codigo = '5245'")).isEqualTo(1);
            assertThat(contar(ligacao, "entidade WHERE nipc = '501111222'")).isZero();
            assertThat(contar(ligacao, "entidade WHERE nipc = '600025861'")).isEqualTo(2);
        });
    }

    @Test
    void deveFalharComOIdQuandoUmaEntidadeSemCodigoEstaReferenciadaPorOutraTabela() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_FINAIS);
            ligacao.setAutoCommit(true);
            inserirAutor(ligacao);
            inserirEntidade(ligacao, "500000003");
            inserirInstrumento(ligacao, "391-1", "PDM", null);
            String id = texto(ligacao, "SELECT id FROM entidade WHERE nif = '500000003'");

            assertThatThrownBy(() -> migrar(ligacao))
                    .hasStackTraceContaining("A entidade com id " + id + " não tem código")
                    .hasStackTraceContaining("scripts/reset-all.sh");
        });
    }

    @Test
    void deveReverterAEntidadeParaAFormaAnteriorSemAsLinhasDaCarga() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirEntidade(ligacao, "500000008");

            reverterOsUltimos(ligacao, CHANGESETS_FINAIS);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "entidade")).isEqualTo(1);
            assertThat(texto(ligacao, "SELECT nif FROM entidade")).isEqualTo("500000008");
            for (String coluna : List.of("nif", "utilizador_nome", "utilizador_email", "utilizador_cargo")) {
                assertThat(colunaExiste(ligacao, "entidade", coluna)).isTrue();
            }
            for (String coluna : List.of("nipc", "dico", "telefone", "email", "responsavel_nome", "responsavel_email",
                    "responsavel_cargo")) {
                assertThat(colunaExiste(ligacao, "entidade", coluna)).isFalse();
            }
            assertThat(texto(ligacao, "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'entidade' "
                    + "AND column_name = 'nif'")).isEqualTo("NO");
            assertThat(texto(ligacao, "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'entidade' "
                    + "AND column_name = 'codigo'")).isEqualTo("YES");
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_constraint WHERE conname = 'uq_entidade_nif'"))
                    .isEqualTo("1");
            assertThat(definicaoDoIndice(ligacao, "idx_entidade_nif")).contains("(nif)");
            assertThat(definicaoDoIndice(ligacao, "idx_entidade_nipc")).isNull();
            assertThat(tabelaExiste(ligacao, "entidade_carga")).isFalse();
        });
    }

    @Test
    void deveExigirVersaoObjetoEGuardarAVersaoDoDocumento() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);

            assertThat(texto(ligacao, "SELECT is_nullable FROM information_schema.columns WHERE table_name = "
                    + "'processo_documento' AND column_name = 'versao_objeto'")).isEqualTo("NO");
            assertThat(texto(ligacao, "SELECT is_nullable || '|' || column_default FROM information_schema.columns "
                    + "WHERE table_name = 'processo_documento' AND column_name = 'versao'")).isEqualTo("NO|0");
            assertThat(texto(ligacao, "SELECT versao::text FROM processo_documento")).isEqualTo("0");
            assertThat(colunaExiste(ligacao, "processo_documento", "token_publico")).isFalse();
            assertThat(colunaExiste(ligacao, "processo_documento", "exposto_em")).isFalse();
            assertThatThrownBy(() -> executar(ligacao, "UPDATE processo_documento SET versao_objeto = NULL"))
                    .hasMessageContaining("versao_objeto");
        });
    }

    @Test
    void deveCriarAsIntencoesDeArmazenamentoComIndiceEOperacoesValidas() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT column_name || '|' || is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'armazenamento_intencao' ORDER BY column_name"))
                    .containsExactly("chave_objeto|NO", "id|NO", "operacao|NO", "registada_em|NO");
            assertThat(definicaoDoIndice(ligacao, "idx_armazenamento_intencao_registada_em")).contains("registada_em");

            String insercao = "INSERT INTO armazenamento_intencao (chave_objeto, operacao, registada_em) "
                    + "VALUES ('processos/00001/DEPOSITO/x', '%s', now())";
            for (String operacao : List.of("GRAVACAO", "COPIA", "APAGAMENTO")) {
                executar(ligacao, insercao.formatted(operacao));
            }
            assertThatThrownBy(() -> executar(ligacao, insercao.formatted("LEITURA")))
                    .hasMessageContaining("ck_armazenamento_intencao_operacao");
        });
    }

    @Test
    void deveCriarLinkPublicoPermitidoFalsoPorOmissaoForaDoSeed() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT is_nullable || '|' || column_default FROM information_schema.columns "
                    + "WHERE table_name = 'documento_config' AND column_name = 'link_publico_permitido'"))
                    .isEqualTo("NO|false");
            assertThat(texto(ligacao, "SELECT count(*)::text FROM documento_config WHERE link_publico_permitido "
                    + "AND categoria = 'DEPOSITO' AND codigo NOT IN ('DESPACHO_CCDR_DEP', 'DELIBERACAO_CM_DEP', "
                    + "'DELIBERACAO_AM_DEP', 'DELIBERACAO_DEP', 'DELIBERACAO_ELABORACAO_DEP', 'DELIBERACAO_CM_ELABORACAO_DEP', 'DELIBERACAO_CM_SUSPENSAO_DEP', "
                    + "'RCM_DEP')"))
                    .isEqualTo("0");
        });
    }

    @Test
    void devePreencherAVersaoDoObjetoDasLinhasExistentesERevertEmCasoDeRollback() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirProcessoComDocumento(ligacao);
            reverter(ligacao, 3);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "armazenamento_intencao")).isFalse();
            assertThat(colunaExiste(ligacao, "processo_documento", "versao")).isFalse();
            assertThat(colunaExiste(ligacao, "documento_config", "link_publico_permitido")).isFalse();
            executar(ligacao, "UPDATE processo_documento SET versao_objeto = NULL");

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT versao_objeto FROM processo_documento")).isEqualTo("null");
        });
    }

    private void inserirEntidade(Connection ligacao, String nif) throws SQLException {
        if (colunaExiste(ligacao, "entidade", "nipc")) {
            executar(ligacao, "INSERT INTO entidade (codigo, descricao_incm, descricao_saigt, tipo_entidade, nipc) "
                    + "VALUES ('T" + nif + "', 'Câmara Municipal', 'Câmara Municipal', 'CM', '" + nif + "')");
            return;
        }
        boolean migrada = colunaExiste(ligacao, "entidade", "descricao_incm");
        executar(ligacao, "INSERT INTO entidade (" + (migrada ? "descricao_incm, descricao_saigt" : "nome")
                + ", nif) VALUES ('Câmara Municipal', " + (migrada ? "'Câmara Municipal', " : "") + "'" + nif + "')");
    }

    /** NIPC de uma entidade da carga com um só código (5245): numa base antiga o login ainda a criava sem código. */
    private static final String NIPC_DA_CARGA = "500051062";

    private void inserirAutorEEntidade(Connection ligacao) throws SQLException {
        inserirAutor(ligacao);
        inserirEntidade(ligacao, NIPC_DA_CARGA);
    }

    private void inserirAutor(Connection ligacao) throws SQLException {
        executar(ligacao, "INSERT INTO utilizador (id, email, origem_autenticacao) "
                + "VALUES ('00000000-0000-0000-0000-000000000001', 'autor@cm-igt.pt', 'INCM')");
    }

    private void inserirInstrumento(Connection ligacao, String numero, String designacao, String idIgtSnit)
            throws SQLException {
        boolean comEntidade = colunaExiste(ligacao, "instrumento", "entidade_id");
        executar(ligacao, "INSERT INTO instrumento (id, tipo_instrumento_id, " + (comEntidade ? "entidade_id, " : "")
                + "numero, designacao, id_igt_snit, responsavel_id, criado_por) VALUES (gen_random_uuid(), "
                + "(SELECT min(id) FROM tipo_instrumento), " + (comEntidade ? "(SELECT min(id) FROM entidade), " : "")
                + "'" + numero + "', '" + designacao + "', "
                + (idIgtSnit == null ? "NULL" : "'" + idIgtSnit + "'") + ", "
                + "'00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001')");
    }

    @Test
    void deveMigrarAEntidadeDoInstrumentoParaOProcessoERetirarlhaAoInstrumento() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(colunaExiste(ligacao, "processo", "entidade_id")).isTrue();
            assertThat(colunaExiste(ligacao, "instrumento", "entidade_id")).isFalse();
            assertThat(texto(ligacao, "SELECT is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'processo' AND column_name = 'entidade_id'")).isEqualTo("NO");
            assertThat(contar(ligacao, "pg_indexes WHERE indexname = 'idx_processo_entidade'")).isEqualTo(1);
            assertThat(contar(ligacao, "pg_indexes WHERE indexname = 'idx_instrumento_entidade'")).isZero();
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'fk_processo_entidade'")).isEqualTo(1);
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'fk_instrumento_entidade'")).isZero();

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "entidade_id")).isFalse();
            assertThat(colunaExiste(ligacao, "instrumento", "entidade_id")).isTrue();
            assertThat(colunaExiste(ligacao, "instrumento_procedimento_config", "visivel_na_dinamica")).isFalse();

            inserirAutor(ligacao);
            inserirEntidade(ligacao, "500000001");
            inserirEntidade(ligacao, "500000002");
            inserirInstrumentoDaEntidade(ligacao, "1", "500000001");
            inserirInstrumentoDaEntidade(ligacao, "2", "500000002");
            inserirInstrumentoDaEntidade(ligacao, "3", "500000001");
            inserirProcessoDoInstrumento(ligacao, "1");
            inserirProcessoDoInstrumento(ligacao, "2");

            migrarOsProximos(ligacao, CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO);
            ligacao.setAutoCommit(true);

            assertThat(linhas(ligacao, "SELECT i.numero || ':' || e.nipc FROM processo p "
                    + "JOIN dinamica d ON d.id = p.dinamica_id JOIN instrumento i ON i.id = d.instrumento_id "
                    + "JOIN entidade e ON e.id = p.entidade_id ORDER BY i.numero"))
                    .containsExactly("1:500000001", "2:500000002");
            assertThat(texto(ligacao, "SELECT is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'processo' AND column_name = 'entidade_id'")).isEqualTo("NO");
            assertThat(colunaExiste(ligacao, "instrumento", "entidade_id")).isFalse();
            assertThat(contar(ligacao, "instrumento")).isEqualTo(3);
        });
    }

    @Test
    void deveRecusarUmProcessoSemEntidadeEUmaEntidadeInexistente() throws Exception {
        comBaseMigrada(ligacao -> {
            inserirAutor(ligacao);
            inserirEntidade(ligacao, "500000001");
            inserirInstrumento(ligacao, "1", "PDM", null);
            inserirDinamicaDoInstrumento(ligacao, "1");

            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO processo (id, dinamica_id, "
                    + "instrumento_procedimento_config_id, criado_por, numero_processo) SELECT gen_random_uuid(), d.id, "
                    + "(SELECT min(id) FROM instrumento_procedimento_config), "
                    + "'00000000-0000-0000-0000-000000000001', '00001' FROM dinamica d"))
                    .hasMessageContaining("entidade_id");
            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO processo (id, dinamica_id, "
                    + "instrumento_procedimento_config_id, criado_por, entidade_id, numero_processo) SELECT gen_random_uuid(), d.id, "
                    + "(SELECT min(id) FROM instrumento_procedimento_config), "
                    + "'00000000-0000-0000-0000-000000000001', -1, '00001' FROM dinamica d"))
                    .hasMessageContaining("fk_processo_entidade");
        });
    }

    @Test
    void deveMarcarAConstituicaoDaComissaoConsultivaComoVisivelNaDinamicaEMaisNenhumProcedimento() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT data_type || ':' || is_nullable || ':' || column_default FROM information_schema.columns "
                    + "WHERE table_name = 'instrumento_procedimento_config' AND column_name = 'visivel_na_dinamica'"))
                    .isEqualTo("boolean:NO:false");
            assertThat(linhas(ligacao, "SELECT ti.codigo || '/' || tp.codigo FROM instrumento_procedimento_config ipc "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                    + "WHERE ipc.visivel_na_dinamica ORDER BY ipc.id"))
                    .containsExactly("PDM/CONSTITUICAO_CC", "PDM/CONSTITUICAO_CC", "PDIM/CONSTITUICAO_CC", "PDIM/CONSTITUICAO_CC",
                            "PIMOT/CONSTITUICAO_CC");
            assertThat(contar(ligacao, "instrumento_procedimento_config WHERE NOT visivel_na_dinamica"))
                    .isEqualTo(486 - PROCEDIMENTOS_DA_RERAE - ATOS_PONTUAIS_DA_RERAE + PROCEDIMENTOS_DAS_RESTANTES_SERVIDOES + PROCEDIMENTOS_DA_REN);
        });
    }

    private static final String PERMISSOES_DA_CONSTITUICAO_DA_CC = "SELECT ipc.id || ':' || pa.tipo_entidade || ':' || pa.ativo "
            + "FROM permissao_acao pa JOIN instrumento_procedimento_config ipc ON ipc.id = pa.instrumento_procedimento_config_id "
            + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE tp.codigo = 'CONSTITUICAO_CC' ORDER BY 1";

    @Test
    void deveDarAConstituicaoDaCcACcdrEADgtESoAsDuasEDevolverACmNoRollback() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, PERMISSOES_DA_CONSTITUICAO_DA_CC))
                    .containsExactly("202:CCDR_DR:true", "202:DGT:true", "206:CCDR_DR:true", "206:DGT:true",
                            "3220:CCDR_DR:true", "3220:DGT:true", "3224:CCDR_DR:true", "3224:DGT:true",
                            "3315:CCDR_DR:true", "3315:DGT:true");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, PERMISSOES_DA_CONSTITUICAO_DA_CC))
                    .containsExactly("202:CM:true", "202:DGT:true", "206:CM:true", "206:DGT:true");

            migrarOsProximos(ligacao, CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, PERMISSOES_DA_CONSTITUICAO_DA_CC))
                    .containsExactly("202:CCDR_DR:true", "202:DGT:true", "206:CCDR_DR:true", "206:DGT:true");
        });
    }

    private static final String AMBITO_DOS_TIPOS = "SELECT codigo || ':' || coalesce(ambito_territorial, '-') "
            + "FROM tipo_instrumento ORDER BY codigo";

    @Test
    void deveDarOAmbitoMunicipalAosPlanosMunicipaisEIntermunicipalAosIntermunicipaisERetirarACunaNoRollback() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, AMBITO_DOS_TIPOS))
                    .containsExactly("PDIM:INTERMUNICIPAL", "PDM:MUNICIPAL", "PEOT:-", "PIMOT:INTERMUNICIPAL", "PNPOT:-", "PP:MUNICIPAL",
                            "PPIM:INTERMUNICIPAL", "PRA:-", "PROT:-", "PS:-", "PSA:-", "PU:MUNICIPAL", "PUIM:INTERMUNICIPAL", "REN:-",
                            "SERVIDAO:-");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE tipo_instrumento SET ambito_territorial = 'LOCAL' WHERE codigo = 'PDM'"))
                    .hasMessageContaining("ck_tipo_instrumento_ambito_territorial");
            ligacao.setAutoCommit(true);

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "tipo_instrumento", "ambito_territorial")).isFalse();
            executar(ligacao, "INSERT INTO tipo_instrumento (codigo, nome, categoria, ativo) "
                    + "VALUES ('PDIM', 'Plano Diretor Intermunicipal', 'PLANO', true)");

            migrarOsProximos(ligacao, CHANGESETS_DO_AMBITO_TERRITORIAL);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, AMBITO_DOS_TIPOS))
                    .containsExactly("PDIM:INTERMUNICIPAL", "PDM:MUNICIPAL", "PEOT:-", "PP:MUNICIPAL", "PROT:-", "PU:MUNICIPAL");
        });
    }

    @Test
    void cadaConfiguracaoDaConstituicaoDaCcDeveSerVisivelNaDinamicaESoComLinhasCcdrDrEDgt() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(contar(ligacao, "instrumento_procedimento_config ipc JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                    + "WHERE tp.codigo = 'CONSTITUICAO_CC'")).isEqualTo(5);
            assertThat(contar(ligacao, "instrumento_procedimento_config ipc JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                    + "WHERE tp.codigo = 'CONSTITUICAO_CC' AND NOT ipc.visivel_na_dinamica")).isZero();
            assertThat(linhas(ligacao, "SELECT DISTINCT pa.tipo_entidade FROM permissao_acao pa "
                    + "JOIN instrumento_procedimento_config ipc ON ipc.id = pa.instrumento_procedimento_config_id "
                    + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
                    + "WHERE tp.codigo = 'CONSTITUICAO_CC' AND pa.ativo ORDER BY 1")).containsExactly("CCDR_DR", "DGT");
        });
    }

    @Test
    void deveMarcarAConstituicaoDaCcDePdimEPimotQuandoOsTiposTiveremConfiguracao() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO);
            ligacao.setAutoCommit(true);
            executar(ligacao, "INSERT INTO tipo_instrumento (codigo, nome, categoria, ativo) "
                    + "VALUES ('PDIM', 'Plano Diretor Intermunicipal', 'PLANO', true), "
                    + "('PIMOT', 'Programa Intermunicipal', 'PROGRAMA', true)");
            executar(ligacao, "INSERT INTO instrumento_dinamica_config (tipo_instrumento_id, tipo_dinamica_id) "
                    + "SELECT ti.id, (SELECT id FROM tipo_dinamica WHERE codigo = 'ELABORACAO') FROM tipo_instrumento ti "
                    + "WHERE ti.codigo IN ('PDIM', 'PIMOT')");
            executar(ligacao, "INSERT INTO instrumento_procedimento_config (instrumento_dinamica_config_id, tipo_procedimento_id, "
                    + "ordem, serie_dr) SELECT idc.id, (SELECT id FROM tipo_procedimento WHERE codigo = 'CONSTITUICAO_CC'), 1, "
                    + "'SEGUNDA_C' FROM instrumento_dinamica_config idc JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "WHERE ti.codigo IN ('PDIM', 'PIMOT')");

            migrarOsProximos(ligacao, CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO);
            ligacao.setAutoCommit(true);

            assertThat(linhas(ligacao, "SELECT DISTINCT ti.codigo FROM instrumento_procedimento_config ipc "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ipc.visivel_na_dinamica ORDER BY 1"))
                    .containsExactly("PDIM", "PDM", "PIMOT");
        });
    }

    @Test
    void deveReverterAEntidadeDoProcessoRepondoAEntidadeDoInstrumentoPeloPrimeiroProcesso() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO);
            ligacao.setAutoCommit(true);
            inserirAutor(ligacao);
            inserirEntidade(ligacao, "500000001");
            inserirEntidade(ligacao, "500000002");
            inserirInstrumentoDaEntidade(ligacao, "1", "500000001");
            inserirProcessoDoInstrumento(ligacao, "1");
            migrarOsProximos(ligacao, CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO);
            ligacao.setAutoCommit(true);

            // Um segundo processo, de outra entidade, não muda o que o Instrumento tinha: conta o primeiro.
            executar(ligacao, "INSERT INTO processo (id, dinamica_id, instrumento_procedimento_config_id, criado_por, entidade_id, "
                    + "criado_em, numero_processo, ocorrencia) SELECT gen_random_uuid(), d.id, (SELECT min(id) FROM instrumento_procedimento_config), "
                    + "'00000000-0000-0000-0000-000000000001', (SELECT id FROM entidade WHERE nipc = '500000002'), "
                    + "now() + interval '1 day', '00099', 2 FROM dinamica d");
            // Um Instrumento sem processos, criado por um utilizador com vínculo a uma entidade.
            inserirInstrumento(ligacao, "2", "Sem processos", null);
            executar(ligacao, "INSERT INTO utilizador_entidade (utilizador_id, entidade_id, sincronizado_em) "
                    + "VALUES ('00000000-0000-0000-0000-000000000001', (SELECT id FROM entidade WHERE nipc = '500000002'), now())");
            // E um sem processos nem vínculo: a coluna não volta a NOT NULL e o Instrumento fica por preencher.
            executar(ligacao, "INSERT INTO utilizador (id, email, origem_autenticacao) "
                    + "VALUES ('00000000-0000-0000-0000-000000000002', 'outro@cm-igt.pt', 'INCM')");
            executar(ligacao, "INSERT INTO instrumento (id, tipo_instrumento_id, numero, designacao, responsavel_id, criado_por) "
                    + "VALUES (gen_random_uuid(), (SELECT min(id) FROM tipo_instrumento), '3', 'Sem dono', "
                    + "'00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002')");

            reverterOsUltimos(ligacao, CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "processo", "entidade_id")).isFalse();
            assertThat(colunaExiste(ligacao, "instrumento_procedimento_config", "visivel_na_dinamica")).isFalse();
            assertThat(texto(ligacao, "SELECT coalesce(string_agg(i.numero || ':' || coalesce(e.nipc, '-'), ',' ORDER BY i.numero), '') "
                    + "FROM instrumento i LEFT JOIN entidade e ON e.id = i.entidade_id"))
                    .isEqualTo("1:500000001,2:500000002,3:-");
            assertThat(contar(ligacao, "pg_indexes WHERE indexname = 'idx_instrumento_entidade'")).isEqualTo(1);
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'fk_instrumento_entidade'")).isEqualTo(1);
            assertThat(texto(ligacao, "SELECT is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'instrumento' AND column_name = 'entidade_id'")).isEqualTo("YES");

            executar(ligacao, "DELETE FROM instrumento WHERE numero = '3'");
            migrarOsProximos(ligacao, CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "instrumento", "entidade_id")).isFalse();
            assertThat(texto(ligacao, "SELECT is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'processo' AND column_name = 'entidade_id'")).isEqualTo("NO");
        });
    }

    private void inserirInstrumentoDaEntidade(Connection ligacao, String numero, String nipc) throws SQLException {
        executar(ligacao, "INSERT INTO instrumento (id, tipo_instrumento_id, entidade_id, numero, designacao, responsavel_id, "
                + "criado_por) VALUES (gen_random_uuid(), (SELECT min(id) FROM tipo_instrumento), "
                + "(SELECT id FROM entidade WHERE nipc = '" + nipc + "'), '" + numero + "', 'IGT " + numero + "', "
                + "'00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001')");
        inserirDinamicaDoInstrumento(ligacao, numero);
    }

    private void inserirDinamicaDoInstrumento(Connection ligacao, String numero) throws SQLException {
        boolean comNumeroDinamica = semValorPorOmissao(ligacao, "dinamica", "numero_dinamica");
        executar(ligacao, "INSERT INTO dinamica (id, instrumento_id, instrumento_dinamica_config_id, criado_por, descritivo, "
                + "estado" + (comNumeroDinamica ? ", numero_dinamica" : "") + ") SELECT gen_random_uuid(), i.id, "
                + "(SELECT min(id) FROM instrumento_dinamica_config), '00000000-0000-0000-0000-000000000001', 'Dinâmica', "
                + "'EM_CURSO'" + (comNumeroDinamica ? ", lpad(i.numero, 5, '0')" : "") + " FROM instrumento i WHERE i.numero = '"
                + numero + "'");
    }

    private void inserirProcessoDoInstrumento(Connection ligacao, String numero) throws SQLException {
        boolean comNumeroProcesso = semValorPorOmissao(ligacao, "processo", "numero_processo");
        executar(ligacao, "INSERT INTO processo (id, dinamica_id, instrumento_procedimento_config_id, criado_por"
                + (comNumeroProcesso ? ", numero_processo" : "") + ") SELECT gen_random_uuid(), d.id, "
                + "(SELECT min(id) FROM instrumento_procedimento_config), '00000000-0000-0000-0000-000000000001'"
                + (comNumeroProcesso ? ", lpad(i.numero, 5, '0')" : "")
                + " FROM dinamica d JOIN instrumento i ON i.id = d.instrumento_id WHERE i.numero = '" + numero + "'");
    }

    @Test
    void devePreencherEmVigorPelasDinamicasTerminadasEPeloIgtDoSnitAoMigrar() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 21);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "instrumento", "estado")).isTrue();
            inserirAutorEEntidade(ligacao);
            executar(ligacao, "INSERT INTO instrumento_dinamica_config (tipo_instrumento_id, tipo_dinamica_id, ordem) "
                    + "SELECT (SELECT min(id) FROM tipo_instrumento), id, 90 FROM tipo_dinamica "
                    + "WHERE codigo IN ('ELABORACAO', 'REVISAO', 'REVOGACAO') ON CONFLICT DO NOTHING");
            inserirInstrumentoComDinamicas(ligacao, "1", null, "ELABORACAO:TERMINADA");
            inserirInstrumentoComDinamicas(ligacao, "2", null, "REVISAO:TERMINADA", "REVOGACAO:TERMINADA");
            inserirInstrumentoComDinamicas(ligacao, "3", "99");
            inserirInstrumentoComDinamicas(ligacao, "4", null, "ELABORACAO:EM_CURSO");
            inserirInstrumentoComDinamicas(ligacao, "5", "98", "REVOGACAO:TERMINADA");

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT string_agg(numero || ':' || em_vigor::text, ',' ORDER BY numero) FROM instrumento"))
                    .isEqualTo("1:true,2:false,3:true,4:false,5:false");
            assertThat(colunaExiste(ligacao, "instrumento", "estado")).isFalse();
        });
    }

    @Test
    void deveRecusarMigrarEmVigorEnquantoHouverInstrumentosCancelados() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 21);
            ligacao.setAutoCommit(true);
            inserirAutorEEntidade(ligacao);
            inserirInstrumento(ligacao, "1", "PDM", null);
            executar(ligacao, "UPDATE instrumento SET estado = 'CANCELADO'");

            assertThatThrownBy(() -> migrar(ligacao)).hasStackTraceContaining("Há Instrumentos CANCELADO");
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "instrumento", "estado")).isTrue();
            assertThat(colunaExiste(ligacao, "instrumento", "em_vigor")).isFalse();

            executar(ligacao, "UPDATE instrumento SET estado = 'ATIVO'");
            migrar(ligacao);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "instrumento", "estado")).isFalse();
        });
    }

    @Test
    void deveReverterEmVigorRepondoOEstadoAtivo() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(colunaExiste(ligacao, "instrumento", "em_vigor")).isTrue();
            assertThat(colunaExiste(ligacao, "instrumento", "estado")).isFalse();
            inserirAutorEEntidade(ligacao);
            inserirInstrumento(ligacao, "1", "PDM", null);

            reverter(ligacao, 21);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "instrumento", "em_vigor")).isFalse();
            assertThat(texto(ligacao, "SELECT estado FROM instrumento")).isEqualTo("ATIVO");
        });
    }

    /** Cada `codigo:estado` cria uma Dinâmica do tipo e estado indicados, ligada ao Instrumento. */
    private void inserirInstrumentoComDinamicas(Connection ligacao, String numero, String idIgtSnit,
                                                String... codigosEEstados) throws SQLException {
        inserirInstrumento(ligacao, numero, "PDM " + numero, idIgtSnit);
        for (String codigoEEstado : codigosEEstados) {
            String[] partes = codigoEEstado.split(":");
            UUID dinamica = UUID.randomUUID();
            executar(ligacao, "INSERT INTO dinamica (id, instrumento_id, instrumento_dinamica_config_id, criado_por, "
                    + "descritivo, estado) SELECT '" + dinamica + "'::uuid, i.id, c.id, "
                    + "'00000000-0000-0000-0000-000000000001', 'Dinâmica', '" + partes[1] + "' "
                    + "FROM instrumento i CROSS JOIN instrumento_dinamica_config c "
                    + "JOIN tipo_dinamica t ON t.id = c.tipo_dinamica_id "
                    + "WHERE i.numero = '" + numero + "' AND t.codigo = '" + partes[0] + "' "
                    + "AND c.tipo_instrumento_id = i.tipo_instrumento_id");
            executar(ligacao, "INSERT INTO dinamica_instrumento (dinamica_id, instrumento_id) "
                    + "SELECT '" + dinamica + "'::uuid, id FROM instrumento WHERE numero = '" + numero + "'");
        }
    }

    private String tamanhoDaDesignacao(Connection ligacao) throws SQLException {
        return texto(ligacao, "SELECT character_maximum_length FROM information_schema.columns "
                + "WHERE table_name = 'instrumento' AND column_name = 'designacao'");
    }
    private static final String PROCEDIMENTOS_COM_CODIGO_IDUS = "SELECT td.codigo || '/' || tp.codigo || '=' "
            + "|| coalesce(ipc.codigo_idus, '-') FROM instrumento_procedimento_config ipc "
            + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE ti.codigo = 'PDM' AND NOT ipc.ato_pontual";

    @Test
    void deveAcrescentarOsCodigosDoIdusAosCatalogosComOsValoresDoSeed() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT table_name || ':' || data_type || ':' || character_maximum_length || ':' "
                    + "|| is_nullable FROM information_schema.columns WHERE table_schema = current_schema() "
                    + "AND column_name = 'codigo_idus' ORDER BY table_name"))
                    .containsExactly("instrumento_procedimento_config:character varying:3:YES",
                            "tipo_dinamica:character varying:2:YES", "tipo_instrumento:character varying:2:YES");

            assertThat(linhas(ligacao, "SELECT codigo || ':' || coalesce(codigo_idus, '-') FROM tipo_instrumento"))
                    .containsExactlyInAnyOrder("PDM:01", "PP:06", "PU:07", "PROT:-", "PEOT:-", "REN:37", "PRA:-", "PSA:-",
                            "PDIM:53", "PUIM:54", "PPIM:55",
                            "PNPOT:-", "PS:-", "PIMOT:-", "SERVIDAO:-");
            assertThat(linhas(ligacao, "SELECT codigo || ':' || coalesce(codigo_idus, '-') FROM tipo_dinamica"))
                    .containsExactlyInAnyOrder("ELABORACAO:01", "ALTERACAO:02", "REVISAO:03", "ALTERACAO_ADAPTACAO:04",
                            "ALTERACAO_SIMPLIFICADA_ART123:06", "ALTERACAO_SIMPLIFICADA_DL10_2024:06",
                            "CORRECAO_MATERIAL:10", "REVOGACAO:15", "MEDIDAS_PREVENTIVAS:-",
                            "NORMAS_PROVISORIAS:-", "SUSPENSAO:-", "DELIMITACAO:-", "CORRECAO_MATERIAL_RETIFICACAO:-",
                            "SUSPENSAO_CCDR:-", "SUSPENSAO_GOVERNO:-", "MEDIDAS_PREVENTIVAS_GOVERNO:-", "RATIFICACAO:-");
            assertThat(linhas(ligacao, PROCEDIMENTOS_COM_CODIGO_IDUS + " AND td.codigo NOT IN (" + DINAMICAS_TRANSVERSAIS + ")")).containsExactlyInAnyOrder(
                    "ELABORACAO/DECISAO_INICIO=029", "ELABORACAO/CONSTITUICAO_CC=038",
                    "ELABORACAO/ABERTURA_DISCUSSAO_PUBLICA=033", "ELABORACAO/ELABORACAO=001",
                    "REVISAO/DECISAO_INICIO=030", "REVISAO/CONSTITUICAO_CC=039",
                    "REVISAO/ABERTURA_DISCUSSAO_PUBLICA=060", "REVISAO/REVISAO=003",
                    "ALTERACAO/DECISAO_INICIO=031", "ALTERACAO/ABERTURA_DISCUSSAO_PUBLICA=034", "ALTERACAO/ALTERACAO=002",
                    "ALTERACAO_SIMPLIFICADA_ART123/DECISAO_INICIO=032",
                    "ALTERACAO_SIMPLIFICADA_ART123/ALTERACAO_SIMPLIFICADA=006",
                    "ALTERACAO_SIMPLIFICADA_DL10_2024/ABERTURA_DISCUSSAO_PUBLICA_DL10=035",
                    "ALTERACAO_SIMPLIFICADA_DL10_2024/ALTERACAO_SIMPLIFICADA_DL10=006",
                    "ALTERACAO_ADAPTACAO/ALTERACAO_ADAPTACAO=004", "CORRECAO_MATERIAL/CORRECAO_MATERIAL=010",
                    "REVOGACAO/REVOGACAO=015",
                    "MEDIDAS_PREVENTIVAS/PUBLICACAO_MP=-", "MEDIDAS_PREVENTIVAS/PRORROGACAO_MP=-",
                    "NORMAS_PROVISORIAS/PUBLICACAO_NP=-", "NORMAS_PROVISORIAS/PRORROGACAO_NP=-",
                    "SUSPENSAO/PUBLICACAO_SUSPENSAO=-", "SUSPENSAO/PRORROGACAO_SUSPENSAO=-");

            assertThat(texto(ligacao, "SELECT count(*) FROM (SELECT idc.tipo_dinamica_id, ipc.tipo_procedimento_id "
                    + "FROM instrumento_procedimento_config ipc "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                    + "GROUP BY idc.tipo_dinamica_id, ipc.tipo_procedimento_id "
                    + "HAVING count(DISTINCT coalesce(ipc.codigo_idus, '-')) > 1) AS divergentes")).isEqualTo("0");
            assertThat(linhas(ligacao, "SELECT DISTINCT td.codigo || '/' || tp.codigo "
                    + "FROM instrumento_procedimento_config ipc "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                    + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE ipc.codigo_idus IS NULL AND NOT ipc.ato_pontual"))
                    .containsExactlyInAnyOrder("ALTERACAO/PUBLICACAO_SGIFR",
                            "DELIMITACAO/PUBLICACAO_SGIFR", "MEDIDAS_PREVENTIVAS/PUBLICACAO_MP",
                            "MEDIDAS_PREVENTIVAS/PRORROGACAO_MP", "NORMAS_PROVISORIAS/PUBLICACAO_NP",
                            "NORMAS_PROVISORIAS/PRORROGACAO_NP", "SUSPENSAO/PUBLICACAO_SUSPENSAO",
                            "SUSPENSAO/PRORROGACAO_SUSPENSAO", "SUSPENSAO_CCDR/SUSPENSAO_CCDR",
                            "SUSPENSAO_GOVERNO/PUBLICACAO_SUSPENSAO_GOVERNO", "SUSPENSAO_GOVERNO/PRORROGACAO_SUSPENSAO_GOVERNO",
                            "MEDIDAS_PREVENTIVAS_GOVERNO/PUBLICACAO_MP_GOVERNO",
                            "MEDIDAS_PREVENTIVAS_GOVERNO/PRORROGACAO_MP_GOVERNO", "RATIFICACAO/RATIFICACAO",
                            "DELIMITACAO/PUBLICACAO_SRUP", "ALTERACAO/PUBLICACAO_SRUP", "REVOGACAO/PUBLICACAO_SRUP",
                            "ALTERACAO/PUBLICACAO_REN", "CORRECAO_MATERIAL_RETIFICACAO/PUBLICACAO_REN", "DELIMITACAO/PUBLICACAO_REN");
        });
    }

    @Test
    void deveAcrescentarOsIdsDoSnitAosCatalogosSemMexerNosCodigosDoIdusEReverterAsColunas() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT table_name || ':' || data_type || ':' || character_maximum_length || ':' "
                    + "|| is_nullable FROM information_schema.columns WHERE table_schema = current_schema() "
                    + "AND column_name = 'snit_codigo' ORDER BY table_name"))
                    .containsExactly("tipo_dinamica:character varying:20:YES",
                            "tipo_instrumento:character varying:20:YES", "tipo_procedimento:character varying:20:YES");

            assertThat(linhas(ligacao, "SELECT codigo || ':' || coalesce(snit_codigo, '-') || ':' "
                    + "|| coalesce(codigo_idus, '-') FROM tipo_instrumento"))
                    .containsExactlyInAnyOrder("PDM:1:01", "PP:6:06", "PU:7:07", "PROT:51:-", "PEOT:-:-", "REN:37:37", "PRA:-:-",
                            "PSA:-:-", "PDIM:53:53", "PUIM:54:54", "PPIM:55:55",
                            "PNPOT:17:-", "PS:46:-", "PIMOT:52:-", "SERVIDAO:-:-");
            assertThat(linhas(ligacao, "SELECT codigo || ':' || coalesce(snit_codigo, '-') || ':' "
                    + "|| coalesce(codigo_idus, '-') FROM tipo_dinamica"))
                    .containsExactlyInAnyOrder("ELABORACAO:1:01", "ALTERACAO:2:02", "REVISAO:7:03",
                            "ALTERACAO_ADAPTACAO:3:04", "ALTERACAO_SIMPLIFICADA_ART123:4:06",
                            "ALTERACAO_SIMPLIFICADA_DL10_2024:4:06", "CORRECAO_MATERIAL:6:10", "REVOGACAO:11:15",
                            "MEDIDAS_PREVENTIVAS:14:-", "NORMAS_PROVISORIAS:24:-", "SUSPENSAO:19:-",
                            "DELIMITACAO:-:-", "CORRECAO_MATERIAL_RETIFICACAO:-:-", "SUSPENSAO_CCDR:-:-",
                            "SUSPENSAO_GOVERNO:18:-", "MEDIDAS_PREVENTIVAS_GOVERNO:15:-", "RATIFICACAO:-:-");
            assertThat(contar(ligacao, "tipo_procedimento WHERE snit_codigo IS NOT NULL")).isZero();

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DOS_LIMITES_DOCUMENTAIS + CHANGESETS_DOS_LINKS_PUBLICOS + CHANGESETS_DA_PLANTA_POR_FOLHA + CHANGESETS_DAS_LISTAS_DA_ALTERACAO + CHANGESETS_DO_MANIFESTO_SNIT + CHANGESETS_DO_NUMERO_DEPOSITO + 1);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "tipo_instrumento WHERE snit_codigo IS NOT NULL")).isZero();
            assertThat(contar(ligacao, "tipo_dinamica WHERE snit_codigo IS NOT NULL")).isZero();

            reverterOsUltimos(ligacao, 1);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "tipo_instrumento", "snit_codigo")).isFalse();
            assertThat(colunaExiste(ligacao, "tipo_dinamica", "snit_codigo")).isFalse();
            assertThat(colunaExiste(ligacao, "tipo_procedimento", "snit_codigo")).isFalse();
            assertThat(texto(ligacao, "SELECT codigo_idus FROM tipo_dinamica WHERE codigo = 'REVISAO'")).isEqualTo("03");
        });
    }

    @Test
    void deveCriarONumeroDeDepositoComOsCodigosDosConcelhosEReverterTudo() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(colunaExiste(ligacao, "processo", "numero_deposito")).isTrue();
            assertThat(texto(ligacao, "SELECT indexdef FROM pg_indexes WHERE indexname = 'uk_processo_numero_deposito'"))
                    .contains("UNIQUE").contains("(numero_deposito)").contains("WHERE (numero_deposito IS NOT NULL)");
            assertThat(linhas(ligacao, "SELECT DISTINCT serie_numero_deposito FROM tipo_instrumento ORDER BY 1"))
                    .containsExactly("GERAL", "REN");
            assertThatThrownBy(() -> executar(ligacao,
                    "UPDATE tipo_instrumento SET serie_numero_deposito = 'OUTRA' WHERE codigo = 'PDM'"))
                    .hasMessageContaining("ck_tipo_instrumento_serie_numero_deposito");
            assertThatThrownBy(() -> executar(ligacao,
                    "INSERT INTO sequencia_numero_deposito (serie, ano, ultimo_sequencial) VALUES ('GERAL', 2026, -1)"))
                    .hasMessageContaining("ck_sequencia_numero_deposito_ultimo_sequencial");

            assertThat(contar(ligacao, "municipio_codigo_deposito")).isEqualTo(308);
            assertThat(texto(ligacao, "SELECT codigo_regiao || ':' || codigo_municipio FROM municipio_codigo_deposito "
                    + "WHERE dtcc = '0501'")).isEqualTo("02:02.05.01");
            assertThat(texto(ligacao, "SELECT codigo_municipio FROM municipio_codigo_deposito WHERE dtcc = '1111'"))
                    .isEqualTo("03.11.11");
            assertThat(texto(ligacao, "SELECT codigo_regiao FROM municipio_codigo_deposito WHERE dtcc = '4101'"))
                    .isEqualTo("01");
            assertThat(contar(ligacao, "municipio_codigo_deposito WHERE dtcc = '0000'")).isZero();

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DOS_LIMITES_DOCUMENTAIS + CHANGESETS_DOS_LINKS_PUBLICOS + CHANGESETS_DA_PLANTA_POR_FOLHA + CHANGESETS_DAS_LISTAS_DA_ALTERACAO + CHANGESETS_DO_MANIFESTO_SNIT + 1);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "municipio_codigo_deposito")).isZero();

            reverterOsUltimos(ligacao, 1);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "numero_deposito")).isFalse();
            assertThat(colunaExiste(ligacao, "tipo_instrumento", "serie_numero_deposito")).isFalse();
            assertThat(contar(ligacao, "information_schema.tables WHERE table_name IN "
                    + "('sequencia_numero_deposito', 'municipio_codigo_deposito')")).isZero();
            assertThat(colunaExiste(ligacao, "tipo_instrumento", "snit_codigo")).isTrue();
        });
    }

    @Test
    void deveRecusarCodigosDoIdusComOutroNumeroDeAlgarismos() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThatThrownBy(() -> executar(ligacao, "UPDATE tipo_instrumento SET codigo_idus = '1' WHERE codigo = 'PDM'"))
                    .hasStackTraceContaining("ck_tipo_instrumento_codigo_idus");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE tipo_instrumento SET codigo_idus = '1A' WHERE codigo = 'PDM'"))
                    .hasStackTraceContaining("ck_tipo_instrumento_codigo_idus");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE tipo_dinamica SET codigo_idus = '001' WHERE codigo = 'ELABORACAO'"))
                    .hasStackTraceContaining("value too long for type character varying(2)");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE instrumento_procedimento_config SET codigo_idus = '29'"))
                    .hasStackTraceContaining("ck_instrumento_procedimento_config_codigo_idus");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE instrumento_procedimento_config SET codigo_idus = '0291'"))
                    .hasStackTraceContaining("value too long for type character varying(3)");
        });
    }

    @Test
    void deveReverterOSeedEDepoisAsColunasDosCodigosDoIdus() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 10);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT (SELECT count(codigo_idus) FROM tipo_instrumento) "
                    + "+ (SELECT count(codigo_idus) FROM tipo_dinamica) "
                    + "+ (SELECT count(codigo_idus) FROM instrumento_procedimento_config)")).isEqualTo("0");

            // Já revertido até ao seed: só falta o changeset das colunas (não se somam os finais outra vez).
            reverterOsUltimos(ligacao, 1);
            ligacao.setAutoCommit(true);

            for (String tabela : new String[] {"tipo_instrumento", "tipo_dinamica", "instrumento_procedimento_config"}) {
                assertThat(colunaExiste(ligacao, tabela, "codigo_idus")).isFalse();
            }
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_constraint WHERE conname LIKE 'ck_%codigo_idus'"))
                    .isEqualTo("0");

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(texto(ligacao, "SELECT codigo_idus FROM tipo_instrumento WHERE codigo = 'PDM'")).isEqualTo("01");
            assertThat(texto(ligacao, "SELECT count(codigo_idus) FROM instrumento_procedimento_config"))
                    .isNotEqualTo("0");
        });
    }

    private static final String PERGUNTAS_GEOCONFORM = "SELECT ti.codigo || '|' || td.codigo || '|' || tp.codigo || '|' "
            + "|| ipc.pergunta_geoconform FROM instrumento_procedimento_config ipc "
            + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
            + "WHERE ipc.pergunta_geoconform <> 'NAO_APLICAVEL' ORDER BY 1";

    @Test
    void deveConfigurarAPerguntaGeoconformSoNaDecisaoDeInicioDoPdmPeloCodigo() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, PERGUNTAS_GEOCONFORM)).containsExactly(
                    "PDM|ALTERACAO|DECISAO_INICIO|SE_PECAS_GRAFICAS",
                    "PDM|ELABORACAO|DECISAO_INICIO|OBRIGATORIA",
                    "PDM|REVISAO|DECISAO_INICIO|OBRIGATORIA");
            assertThat(texto(ligacao, "SELECT string_agg(id::text, ',' ORDER BY id) FROM instrumento_procedimento_config "
                    + "WHERE pergunta_geoconform <> 'NAO_APLICAVEL'")).isEqualTo("201,205,209");
        });
    }

    @Test
    void deveAcrescentarAComGeoconformNulaNaDinamicaEAPerguntaObrigatoriaNaConfiguracao() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT data_type || '|' || is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'dinamica' AND column_name = 'com_geoconform'")).isEqualTo("boolean|YES");
            assertThat(texto(ligacao, "SELECT data_type || '|' || is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'instrumento_procedimento_config' AND column_name = 'pergunta_geoconform'"))
                    .isEqualTo("character varying|NO");

            assertThatThrownBy(() -> executar(ligacao, "UPDATE instrumento_procedimento_config SET pergunta_geoconform = 'TALVEZ'"))
                    .hasStackTraceContaining("ck_instrumento_procedimento_config_pergunta_geoconform");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE instrumento_procedimento_config SET pergunta_geoconform = NULL"))
                    .hasStackTraceContaining("pergunta_geoconform");

            inserirProcessoComDocumento(ligacao);
            assertThat(texto(ligacao, "SELECT count(*) || '|' || count(com_geoconform) FROM dinamica")).isEqualTo("1|0");
        });
    }

    @Test
    void deveReverterAPerguntaGeoconformSemTocarNaProcessoRejeicaoItemEVoltarAAplicar() throws Exception {
        comBaseMigrada(ligacao -> {
            reverter(ligacao, 5);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "dinamica", "com_geoconform")).isFalse();
            assertThat(colunaExiste(ligacao, "instrumento_procedimento_config", "pergunta_geoconform")).isFalse();
            assertThat(texto(ligacao, "SELECT count(*) FROM pg_constraint "
                    + "WHERE conname = 'ck_instrumento_procedimento_config_pergunta_geoconform'")).isEqualTo("0");
            assertThat(tabelaExiste(ligacao, "processo_rejeicao_item")).isTrue();

            migrar(ligacao);
            ligacao.setAutoCommit(true);

            assertThat(linhas(ligacao, PERGUNTAS_GEOCONFORM)).hasSize(3);
        });
    }
    @Test
    void deveCriarOManifestoDoDepositoNoSnitComAsChavesEReverter() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT column_name || ':' || data_type || ':' || is_nullable || ':' "
                    + "|| coalesce(column_default, '-') FROM information_schema.columns "
                    + "WHERE table_schema = current_schema() AND table_name = 'deposito_snit_manifesto_linha' "
                    + "ORDER BY ordinal_position"))
                    .containsExactly("manifesto_id:uuid:NO:-", "processo_documento_id:uuid:NO:-",
                            "chave_objeto:character varying:NO:-", "versao_objeto:character varying:NO:-",
                            "tamanho_bytes:bigint:NO:-", "hash_sha256:character varying:NO:-",
                            "comunicado_um_a_um:boolean:NO:false");
            assertThat(linhas(ligacao, "SELECT column_name || ':' || data_type || ':' || is_nullable "
                    + "FROM information_schema.columns WHERE table_schema = current_schema() "
                    + "AND table_name = 'deposito_snit_manifesto' ORDER BY ordinal_position"))
                    .containsExactly("id:uuid:NO", "processo_id:uuid:NO", "caixas_incluidas:character varying:NO",
                            "criado_em:timestamp with time zone:NO");
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'pk_deposito_snit_manifesto_linha'"))
                    .isEqualTo("PRIMARY KEY (manifesto_id, processo_documento_id)");
            assertThat(linhas(ligacao, "SELECT conname || ':' || confrelid::regclass FROM pg_constraint "
                    + "WHERE contype = 'f' AND conrelid::regclass::text LIKE 'deposito_snit_manifesto%' ORDER BY conname"))
                    .containsExactly("fk_deposito_snit_manifesto_linha_manifesto:deposito_snit_manifesto",
                            "fk_deposito_snit_manifesto_linha_processo_documento:processo_documento",
                            "fk_deposito_snit_manifesto_processo:processo");
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'ck_deposito_snit_manifesto_caixas_incluidas'"))
                    .contains("'DEPOSITO'", "'PUBLICACAO'", "'PUBLICACAO,DEPOSITO'");
            assertThat(texto(ligacao, "SELECT indexdef FROM pg_indexes "
                    + "WHERE indexname = 'idx_deposito_snit_manifesto_processo_id'"))
                    .contains("(processo_id, criado_em)");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DOS_LIMITES_DOCUMENTAIS + CHANGESETS_DOS_LINKS_PUBLICOS + CHANGESETS_DA_PLANTA_POR_FOLHA + CHANGESETS_DAS_LISTAS_DA_ALTERACAO + CHANGESETS_DO_MANIFESTO_SNIT);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "information_schema.tables WHERE table_name IN "
                    + "('deposito_snit_manifesto', 'deposito_snit_manifesto_linha')")).isZero();
            assertThat(colunaExiste(ligacao, "processo", "numero_deposito")).isTrue();
        });
    }

    @Test
    void deveSemearAsFreguesiasDeSintraEOsMotivosDaAlteracaoEReverterSoEssasLinhas() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(contar(ligacao, "lista_valores WHERE tipo = 'FREGUESIA' AND codigo LIKE '1111%' AND ativo")).isEqualTo(15);
            assertThat(contar(ligacao, "lista_valores WHERE tipo = 'MOTIVO_ALTERACAO_LEI'")).isEqualTo(3);
            assertThat(contar(ligacao, "lista_valores WHERE tipo = 'MOTIVO_ALTERACAO_PROGRAMA'")).isEqualTo(3);
            assertThat(contar(ligacao, "lista_valores WHERE tipo = 'MOTIVO_ALTERACAO_PLANO'")).isEqualTo(1);
            assertThat(contar(ligacao, "lista_valores WHERE tipo = 'TOPONIMO'")).isZero();
            assertThat(texto(ligacao, "SELECT descricao FROM lista_valores WHERE tipo = 'FREGUESIA' AND codigo = '111133'"))
                    .isEqualTo("Queluz");
            int outras = contar(ligacao, "lista_valores WHERE tipo NOT IN ('FREGUESIA', 'MOTIVO_ALTERACAO_LEI', "
                    + "'MOTIVO_ALTERACAO_PROGRAMA', 'MOTIVO_ALTERACAO_PLANO') AND tipo NOT LIKE '%_SRUP'");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA + CHANGESETS_DO_PERFIL_DO_UTILIZADOR + CHANGESETS_DO_CONTACTO_PRINCIPAL + CHANGESETS_DA_CONTA_POR_ORIGEM + CHANGESETS_DO_NOME_AD + CHANGESETS_DA_MATRIZ_DE_PERMISSOES + CHANGESETS_DA_CLASSIFICACAO_SRUP + CHANGESETS_DA_CATEGORIA_SEM_ARU + CHANGESETS_DO_AMBITO_TERRITORIAL + CHANGESETS_DA_CC_DA_CCDR + CHANGESETS_DA_ENTIDADE_NO_PROCESSO + CHANGESETS_DA_PERMISSAO_POR_TIPO_ENTIDADE + CHANGESETS_DAS_NOTIFICACOES + CHANGESETS_DA_SESSAO_UNICA + CHANGESETS_DO_PCGT_CODIGO + CHANGESETS_DA_LINHA_TXT + CHANGESETS_DA_SERIE_MOTU + CHANGESETS_DO_ESTADO_SUBSTITUIDO + CHANGESETS_DAS_PLANTAS_FINAIS + CHANGESETS_DA_FICHA_MOTU + CHANGESETS_DOS_LIMITES_DOCUMENTAIS + CHANGESETS_DOS_LINKS_PUBLICOS + CHANGESETS_DA_PLANTA_POR_FOLHA + CHANGESETS_DAS_LISTAS_DA_ALTERACAO);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "lista_valores WHERE tipo IN ('FREGUESIA', 'MOTIVO_ALTERACAO_LEI', "
                    + "'MOTIVO_ALTERACAO_PROGRAMA', 'MOTIVO_ALTERACAO_PLANO')")).isZero();
            assertThat(contar(ligacao, "lista_valores")).isEqualTo(outras);
        });
    }

    @Test
    void deveSemearAsOitoRedesDeDefesaLigarAoProcessoEReverterSemTocarNoProcesso() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT codigo || ':' || aplica_pra || ':' || aplica_psa || ':' || publicavel "
                    + "FROM sgifr_rede_defesa ORDER BY codigo"))
                    .containsExactly("AEMGC:false:true:true", "APPS:false:true:true", "RPA:false:true:true",
                            "RPFGC:true:false:true", "RSFGC:false:true:true", "RTFGC:false:false:false",
                            "RVDI:false:true:true", "RVF:false:true:true");
            assertThat(texto(ligacao, "SELECT nome FROM sgifr_rede_defesa WHERE codigo = 'RVDI'"))
                    .isEqualTo("Rede nacional de postos de vigia - rede de Vigilância e Deteção de Incêndios");
            assertThat(linhas(ligacao, "SELECT column_name || ':' || data_type || ':' || is_nullable "
                    + "FROM information_schema.columns WHERE table_schema = current_schema() "
                    + "AND table_name = 'processo_rede_defesa' ORDER BY ordinal_position"))
                    .containsExactly("processo_id:uuid:NO", "rede_defesa_id:bigint:NO");
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'pk_processo_rede_defesa'")).isEqualTo("PRIMARY KEY (processo_id, rede_defesa_id)");
            assertThat(linhas(ligacao, "SELECT conname || ':' || confrelid::regclass FROM pg_constraint "
                    + "WHERE contype = 'f' AND conrelid::regclass::text = 'processo_rede_defesa' ORDER BY conname"))
                    .containsExactly("fk_processo_rede_defesa_processo:processo", "fk_processo_rede_defesa_rede:sgifr_rede_defesa");
            assertThat(texto(ligacao, "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_processo_rede_defesa_rede'"))
                    .contains("(rede_defesa_id)");

            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO sgifr_rede_defesa "
                    + "(codigo, nome, aplica_pra, aplica_psa, publicavel) VALUES ('RVF', 'Duplicada', false, true, true)"))
                    .hasMessageContaining("uq_sgifr_rede_defesa_codigo");
            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO processo_rede_defesa (processo_id, rede_defesa_id) "
                    + "SELECT '" + UUID.randomUUID() + "', id FROM sgifr_rede_defesa WHERE codigo = 'RVF'"))
                    .hasMessageContaining("fk_processo_rede_defesa_processo");
            int processos = contar(ligacao, "processo");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP + CHANGESETS_DAS_REDES_DE_DEFESA);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "sgifr_rede_defesa")).isFalse();
            assertThat(tabelaExiste(ligacao, "processo_rede_defesa")).isFalse();
            assertThat(contar(ligacao, "processo")).isEqualTo(processos);

            migrar(ligacao);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "sgifr_rede_defesa")).isEqualTo(8);
        });
    }

    private static final String CONFIGURACOES_SRUP = "SELECT ti.codigo || ':' || td.codigo || ':' || idc.ativo || ':' "
            + "|| idc.associacao_instrumentos FROM instrumento_dinamica_config idc "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "WHERE ti.codigo IN ('REN', 'PRA', 'PSA') ORDER BY ti.codigo, td.codigo";

    private static final String DINAMICAS_ATIVAS_DO_PDM = "SELECT td.codigo FROM instrumento_dinamica_config idc "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "WHERE ti.codigo = 'PDM' AND idc.ativo ORDER BY td.id";

    @Test
    void deveSemearOsTiposEAsDinamicasDaRenEDoSgifrInativosESemProcedimentos() throws Exception {
        comBaseMigrada(ligacao -> {
            // O estado do catálogo antes da configuração do SGIFR, que ativa o PRA e o PSA.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, "SELECT codigo || ':' || nome || ':' || ativo FROM tipo_dinamica "
                    + "WHERE codigo IN ('DELIMITACAO', 'CORRECAO_MATERIAL_RETIFICACAO', 'ALTERACAO', 'REVOGACAO') "
                    + "ORDER BY codigo"))
                    .containsExactly("ALTERACAO:Alteração:true",
                            "CORRECAO_MATERIAL_RETIFICACAO:Correção Material ou Retificação:true",
                            "DELIMITACAO:Delimitação:true", "REVOGACAO:Revogação:true");
            assertThat(linhas(ligacao, "SELECT codigo || ':' || nome || ':' || ativo || ':' || coalesce(snit_codigo, '-') "
                    + "|| ':' || serie_numero_deposito FROM tipo_instrumento WHERE categoria = 'SRUP' ORDER BY codigo"))
                    .containsExactly("PRA:Programa Regional de Ação:true:-:GERAL",
                            "PSA:Programa Sub-Regional de Ação:true:-:GERAL",
                            "REN:Reserva Ecológica Nacional:true:37:REN");
            assertThat(linhas(ligacao, CONFIGURACOES_SRUP)).containsExactly(
                    "PRA:ALTERACAO:false:UM", "PRA:DELIMITACAO:false:UM",
                    "PSA:ALTERACAO:false:UM", "PSA:DELIMITACAO:false:UM",
                    "REN:ALTERACAO:false:UM", "REN:CORRECAO_MATERIAL_RETIFICACAO:false:UM", "REN:DELIMITACAO:false:UM");
            assertThat(contar(ligacao, "instrumento_procedimento_config ipc "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ti.categoria = 'SRUP'")).isZero();
            // O que GET /tipos-instrumento lista: tipos ativos com pelo menos uma configuração ativa.
            assertThat(linhas(ligacao, "SELECT DISTINCT ti.codigo FROM instrumento_dinamica_config idc "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "WHERE idc.ativo AND ti.ativo ORDER BY ti.codigo"))
                    .containsExactly("PDIM", "PDM", "PEOT", "PIMOT", "PNPOT", "PP", "PPIM", "PROT", "PS", "PU",
                            "PUIM");
        });
    }

    @Test
    void deveReverterSoAsDozeLinhasDoCatalogoSrupSemMudarAsDinamicasDoPdm() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS);
            ligacao.setAutoCommit(true);
            String restantesTipos = texto(ligacao, "SELECT md5(string_agg(t::text, ',' ORDER BY t.id)) "
                    + "FROM tipo_instrumento t WHERE codigo NOT IN ('REN', 'PRA', 'PSA')");
            String restantesDinamicas = texto(ligacao, "SELECT md5(string_agg(t::text, ',' ORDER BY t.id)) "
                    + "FROM tipo_dinamica t WHERE codigo NOT IN ('DELIMITACAO', 'CORRECAO_MATERIAL_RETIFICACAO')");
            String restantesConfiguracoes = texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) "
                    + "FROM instrumento_dinamica_config c WHERE tipo_instrumento_id NOT IN "
                    + "(SELECT id FROM tipo_instrumento WHERE codigo IN ('REN', 'PRA', 'PSA'))");
            int tipos = contar(ligacao, "tipo_instrumento");
            int dinamicas = contar(ligacao, "tipo_dinamica");
            int configuracoes = contar(ligacao, "instrumento_dinamica_config");
            List<String> dinamicasDoPdm = linhas(ligacao, DINAMICAS_ATIVAS_DO_PDM);

            reverterOsUltimos(ligacao, CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "tipo_instrumento")).isEqualTo(tipos - 3);
            assertThat(contar(ligacao, "tipo_dinamica")).isEqualTo(dinamicas - 2);
            assertThat(contar(ligacao, "instrumento_dinamica_config")).isEqualTo(configuracoes - 7);
            assertThat(contar(ligacao, "tipo_instrumento WHERE codigo IN ('REN', 'PRA', 'PSA')")).isZero();
            assertThat(contar(ligacao, "tipo_dinamica WHERE codigo IN ('DELIMITACAO', 'CORRECAO_MATERIAL_RETIFICACAO')"))
                    .isZero();
            assertThat(texto(ligacao, "SELECT md5(string_agg(t::text, ',' ORDER BY t.id)) FROM tipo_instrumento t"))
                    .isEqualTo(restantesTipos);
            assertThat(texto(ligacao, "SELECT md5(string_agg(t::text, ',' ORDER BY t.id)) FROM tipo_dinamica t"))
                    .isEqualTo(restantesDinamicas);
            assertThat(texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM instrumento_dinamica_config c"))
                    .isEqualTo(restantesConfiguracoes);
            assertThat(linhas(ligacao, DINAMICAS_ATIVAS_DO_PDM)).isEqualTo(dinamicasDoPdm);
        });
    }

    @Test
    void deveResolverAsConfiguracoesPeloCodigoQuandoOutroSeedJaOcupouOsIdsDosTipos() throws Exception {
        comBaseMigrada(ligacao -> {
            long idDaRen = Long.parseLong(texto(ligacao, "SELECT id FROM tipo_instrumento WHERE codigo = 'REN'"));
            long idDaDelimitacao = Long.parseLong(texto(ligacao, "SELECT id FROM tipo_dinamica WHERE codigo = 'DELIMITACAO'"));
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PP + CHANGESETS_DOS_ITENS_DA_ALTERACAO_DE_PU + CHANGESETS_DO_CATALOGO_SRUP);
            ligacao.setAutoCommit(true);

            // Como um seed de planos com ids explícitos e setval, a correr antes deste.
            executar(ligacao, "INSERT INTO tipo_instrumento (id, codigo, nome, categoria) VALUES (" + idDaRen
                    + ", 'PDIM', 'Plano Diretor Intermunicipal', 'PLANO')");
            executar(ligacao, "SELECT setval(pg_get_serial_sequence('tipo_instrumento', 'id'), "
                    + "(SELECT MAX(id) FROM tipo_instrumento))");
            executar(ligacao, "INSERT INTO tipo_dinamica (id, codigo, nome) VALUES (" + idDaDelimitacao
                    + ", 'OUTRA_DINAMICA', 'Outra Dinâmica')");
            executar(ligacao, "SELECT setval(pg_get_serial_sequence('tipo_dinamica', 'id'), (SELECT MAX(id) FROM tipo_dinamica))");

            migrarOsProximos(ligacao, CHANGESETS_DO_CATALOGO_SRUP);
            ligacao.setAutoCommit(true);

            assertThat(Long.parseLong(texto(ligacao, "SELECT id FROM tipo_instrumento WHERE codigo = 'REN'")))
                    .isGreaterThan(idDaRen);
            assertThat(linhas(ligacao, CONFIGURACOES_SRUP)).hasSize(7);
            assertThat(contar(ligacao, "instrumento_dinamica_config WHERE tipo_instrumento_id = " + idDaRen)).isZero();
            assertThat(contar(ligacao, "instrumento_dinamica_config WHERE tipo_dinamica_id = " + idDaDelimitacao)).isZero();
        });
    }

    @Test
    void deveCriarATabelaDasTagsComChaveCompostaFkEmCascataEIndiceEReverterSoEla() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT column_name || ':' || data_type || ':' || is_nullable "
                    + "FROM information_schema.columns WHERE table_schema = current_schema() "
                    + "AND table_name = 'processo_documento_tag' ORDER BY ordinal_position"))
                    .containsExactly("processo_documento_id:uuid:NO", "documento_config_id:bigint:NO");
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'pk_processo_documento_tag'"))
                    .isEqualTo("PRIMARY KEY (processo_documento_id, documento_config_id)");
            // confdeltype: 'c' = ON DELETE CASCADE, 'a' = NO ACTION.
            assertThat(linhas(ligacao, "SELECT conname || ':' || confrelid::regclass || ':' || confdeltype::text FROM pg_constraint "
                    + "WHERE contype = 'f' AND conrelid::regclass::text = 'processo_documento_tag' ORDER BY conname"))
                    .containsExactly("fk_processo_documento_tag_config:documento_config:a",
                            "fk_processo_documento_tag_documento:processo_documento:c");
            assertThat(texto(ligacao, "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_processo_documento_tag_config'"))
                    .contains("(documento_config_id)");
            int documentos = contar(ligacao, "processo_documento");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS + CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS + CHANGESETS_DA_TAG_DOCUMENTAL);
            ligacao.setAutoCommit(true);

            assertThat(tabelaExiste(ligacao, "processo_documento_tag")).isFalse();
            assertThat(tabelaExiste(ligacao, "processo_documento")).isTrue();
            assertThat(contar(ligacao, "processo_documento")).isEqualTo(documentos);

            migrar(ligacao);
            ligacao.setAutoCommit(true);
            assertThat(tabelaExiste(ligacao, "processo_documento_tag")).isTrue();
        });
    }

    private static final String REGRA_SEM_SIMULTANEAS = "Sem Dinâmicas em simultâneo no mesmo Instrumento";

    private static final String REGRAS_SEM_SIMULTANEAS_DA_REN = "SELECT td.codigo || ':' || r.operador || ':' "
            + "|| r.substitui_sequenciamento || ':' || r.ativo || ':' || coalesce(r.instrumento_procedimento_config_id::text, '-') "
            + "|| ':' || (SELECT count(*) FROM condicao c WHERE c.regra_id = r.id) || ':' "
            + "|| (SELECT string_agg(c.avaliador || '|' || c.parametros::text || '|' || c.grupo || '|' || c.ativo, ',') "
            + "FROM condicao c WHERE c.regra_id = r.id) FROM regra r "
            + "JOIN instrumento_dinamica_config idc ON idc.id = r.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "WHERE ti.codigo = 'REN' AND r.descricao = '" + REGRA_SEM_SIMULTANEAS + "' ORDER BY td.codigo";

    @Test
    void deveSemearUmaRegraSemDinamicasEmSimultaneoComUmaCondicaoPorConfiguracaoDaRen() throws Exception {
        comBaseMigrada(ligacao -> {
            String condicao = ":1:OCORRENCIA_NAO_EXISTE|{\"estado\": \"EM_CURSO\", \"tipo_ocorrencia\": \"DINAMICA\"}|1|true";
            assertThat(linhas(ligacao, REGRAS_SEM_SIMULTANEAS_DA_REN)).containsExactly(
                    "ALTERACAO:AND:false:true:-" + condicao,
                    "CORRECAO_MATERIAL_RETIFICACAO:AND:false:true:-" + condicao,
                    "DELIMITACAO:AND:false:true:-" + condicao);
            assertThat(contar(ligacao, "instrumento_dinamica_config idc JOIN tipo_instrumento ti "
                    + "ON ti.id = idc.tipo_instrumento_id WHERE ti.codigo = 'REN'")).isEqualTo(3);
            assertThat(contar(ligacao, "regra WHERE descricao = '" + REGRA_SEM_SIMULTANEAS + "'")).isEqualTo(3);
        });
    }

    @Test
    void deveReverterSoAsTresRegrasEAsTresCondicoesDaRenSemDinamicasEmSimultaneo() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS);
            ligacao.setAutoCommit(true);
            String restantesRegras = texto(ligacao, "SELECT md5(string_agg(r::text, ',' ORDER BY r.id)) FROM regra r "
                    + "WHERE descricao IS DISTINCT FROM '" + REGRA_SEM_SIMULTANEAS + "'");
            String restantesCondicoes = texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM condicao c "
                    + "WHERE regra_id IN (SELECT id FROM regra WHERE descricao IS DISTINCT FROM '" + REGRA_SEM_SIMULTANEAS + "')");
            int regras = contar(ligacao, "regra");
            int condicoes = contar(ligacao, "condicao");

            reverterOsUltimos(ligacao, CHANGESETS_DA_REGRA_REN_SEM_SIMULTANEAS);
            ligacao.setAutoCommit(true);

            assertThat(linhas(ligacao, REGRAS_SEM_SIMULTANEAS_DA_REN)).isEmpty();
            assertThat(contar(ligacao, "regra")).isEqualTo(regras - 3);
            assertThat(contar(ligacao, "condicao")).isEqualTo(condicoes - 3);
            assertThat(texto(ligacao, "SELECT md5(string_agg(r::text, ',' ORDER BY r.id)) FROM regra r"))
                    .isEqualTo(restantesRegras);
            assertThat(texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM condicao c"))
                    .isEqualTo(restantesCondicoes);
            assertThat(contar(ligacao, "instrumento_dinamica_config idc JOIN tipo_instrumento ti "
                    + "ON ti.id = idc.tipo_instrumento_id WHERE ti.codigo = 'REN'")).isEqualTo(3);

            migrar(ligacao);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, REGRAS_SEM_SIMULTANEAS_DA_REN)).hasSize(3);
        });
    }

    private static final String DOS_PLANOS_INTERMUNICIPAIS = "(SELECT id FROM tipo_instrumento WHERE codigo IN ('PDIM', 'PUIM', 'PPIM'))";

    /** Impressão digital de cada tabela do seed, sem as linhas dos planos intermunicipais. */
    private List<String> configuracaoForaDosPlanosIntermunicipais(Connection ligacao) throws SQLException {
        String configuracoes = "SELECT id FROM instrumento_dinamica_config WHERE tipo_instrumento_id IN " + DOS_PLANOS_INTERMUNICIPAIS;
        String procedimentos = "SELECT id FROM instrumento_procedimento_config WHERE instrumento_dinamica_config_id IN (" + configuracoes + ")";
        String regras = "SELECT id FROM regra WHERE instrumento_dinamica_config_id IN (" + configuracoes + ")";
        return List.of(
                texto(ligacao, "SELECT md5(string_agg(t::text, ',' ORDER BY t.id)) FROM tipo_instrumento t WHERE id NOT IN "
                        + DOS_PLANOS_INTERMUNICIPAIS),
                texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM instrumento_dinamica_config c "
                        + "WHERE id NOT IN (" + configuracoes + ")"),
                texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM instrumento_procedimento_config p "
                        + "WHERE id NOT IN (" + procedimentos + ")"),
                texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM permissao_acao p "
                        + "WHERE instrumento_procedimento_config_id NOT IN (" + procedimentos + ")"),
                texto(ligacao, "SELECT md5(string_agg(r::text, ',' ORDER BY r.id)) FROM regra r WHERE id NOT IN (" + regras + ")"),
                texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM condicao c "
                        + "WHERE regra_id NOT IN (" + regras + ")"));
    }

    @Test
    void deveReverterSoAsLinhasDosPlanosIntermunicipaisEVoltarASemeaLasIguais() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS);
            ligacao.setAutoCommit(true);
            List<String> restantes = configuracaoForaDosPlanosIntermunicipais(ligacao);
            String dosPlanos = "SELECT ti.codigo || ':' || count(DISTINCT idc.id) || ':' || count(DISTINCT ipc.id) || ':' "
                    + "|| count(DISTINCT pa.id) || ':' || count(DISTINCT r.id) || ':' || count(DISTINCT c.id) FROM tipo_instrumento ti "
                    + "JOIN instrumento_dinamica_config idc ON idc.tipo_instrumento_id = ti.id "
                    + "JOIN instrumento_procedimento_config ipc ON ipc.instrumento_dinamica_config_id = idc.id "
                    + "JOIN permissao_acao pa ON pa.instrumento_procedimento_config_id = ipc.id "
                    + "JOIN regra r ON r.instrumento_dinamica_config_id = idc.id JOIN condicao c ON c.regra_id = r.id "
                    + "WHERE ti.codigo IN ('PDIM', 'PUIM', 'PPIM') GROUP BY ti.id, ti.codigo ORDER BY ti.id";
            assertThat(linhas(ligacao, dosPlanos)).containsExactly("PDIM:7:16:32:7:9", "PUIM:7:14:28:7:9", "PPIM:7:14:28:7:9");

            reverterOsUltimos(ligacao, CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "tipo_instrumento WHERE codigo IN ('PDIM', 'PUIM', 'PPIM')")).isZero();
            assertThat(contar(ligacao, "tipo_instrumento WHERE codigo IN ('PDM', 'PU', 'PP', 'PROT', 'PEOT', 'REN', 'PRA', 'PSA')"))
                    .isEqualTo(8);
            assertThat(configuracaoForaDosPlanosIntermunicipais(ligacao)).isEqualTo(restantes);

            migrarOsProximos(ligacao, CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, dosPlanos)).containsExactly("PDIM:8:18:36:8:10", "PUIM:8:16:32:8:10", "PPIM:8:16:32:8:10");
            assertThat(configuracaoForaDosPlanosIntermunicipais(ligacao)).isEqualTo(restantes);
        });
    }

    @Test
    void naoDeveColidirComTiposEConfiguracoesQueOutroSeedJaAcrescentouPelaSequencia() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN + CHANGESETS_DOS_PROGRAMAS + CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS);
            ligacao.setAutoCommit(true);
            executar(ligacao, "INSERT INTO tipo_instrumento (codigo, nome, categoria) VALUES ('PIMOT', 'Programa Intermunicipal', 'PROGRAMA')");
            executar(ligacao, "INSERT INTO instrumento_dinamica_config (tipo_instrumento_id, tipo_dinamica_id) "
                    + "SELECT (SELECT id FROM tipo_instrumento WHERE codigo = 'PIMOT'), id FROM tipo_dinamica WHERE codigo = 'ELABORACAO'");
            executar(ligacao, "INSERT INTO instrumento_procedimento_config (instrumento_dinamica_config_id, tipo_procedimento_id, "
                    + "ordem, serie_dr) SELECT idc.id, (SELECT id FROM tipo_procedimento WHERE codigo = 'DECISAO_INICIO'), 1, "
                    + "'SEGUNDA_H' FROM instrumento_dinamica_config idc JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "WHERE ti.codigo = 'PIMOT'");
            executar(ligacao, "INSERT INTO permissao_acao (tipo_entidade, instrumento_procedimento_config_id) "
                    + "SELECT 'CIM', max(id) FROM instrumento_procedimento_config");
            executar(ligacao, "INSERT INTO regra (instrumento_dinamica_config_id, operador) SELECT max(id), 'AND' "
                    + "FROM instrumento_dinamica_config");
            executar(ligacao, "INSERT INTO condicao (regra_id, avaliador, parametros) SELECT max(id), 'INSTRUMENTO_EM_VIGOR', '{}' "
                    + "FROM regra");

            assertThatCode(() -> migrarOsProximos(ligacao, CHANGESETS_DOS_PLANOS_INTERMUNICIPAIS)).doesNotThrowAnyException();
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "instrumento_procedimento_config WHERE instrumento_dinamica_config_id IN "
                    + "(SELECT id FROM instrumento_dinamica_config WHERE tipo_instrumento_id IN " + DOS_PLANOS_INTERMUNICIPAIS + ")"))
                    .isEqualTo(PROCEDIMENTOS_DOS_PLANOS_INTERMUNICIPAIS);
            assertThat(contar(ligacao, "instrumento_procedimento_config ipc JOIN instrumento_dinamica_config idc "
                    + "ON idc.id = ipc.instrumento_dinamica_config_id JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "WHERE ti.codigo = 'PIMOT'")).isEqualTo(1);
        });
    }

    private static final String DOS_PROGRAMAS = "(SELECT id FROM tipo_instrumento WHERE codigo IN ('PNPOT', 'PS', 'PROT', 'PEOT', 'PIMOT'))";

    /** Impressão digital de cada tabela do seed, sem as linhas dos Programas (PROT e PEOT ficam em `tipo_instrumento`). */
    private List<String> configuracaoForaDosProgramas(Connection ligacao) throws SQLException {
        String configuracoes = "SELECT id FROM instrumento_dinamica_config WHERE tipo_instrumento_id IN " + DOS_PROGRAMAS;
        String procedimentos = "SELECT id FROM instrumento_procedimento_config WHERE instrumento_dinamica_config_id IN (" + configuracoes + ")";
        String regras = "SELECT id FROM regra WHERE instrumento_dinamica_config_id IN (" + configuracoes + ")";
        return List.of(
                texto(ligacao, "SELECT md5(string_agg(t::text, ',' ORDER BY t.id)) FROM tipo_instrumento t "
                        + "WHERE codigo NOT IN ('PNPOT', 'PS', 'PIMOT')"),
                texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM instrumento_dinamica_config c "
                        + "WHERE id NOT IN (" + configuracoes + ")"),
                texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM instrumento_procedimento_config p "
                        + "WHERE id NOT IN (" + procedimentos + ")"),
                texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM permissao_acao p "
                        + "WHERE instrumento_procedimento_config_id NOT IN (" + procedimentos + ")"),
                texto(ligacao, "SELECT md5(string_agg(r::text, ',' ORDER BY r.id)) FROM regra r WHERE id NOT IN (" + regras + ")"),
                texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM condicao c "
                        + "WHERE regra_id NOT IN (" + regras + ")"));
    }

    @Test
    void deveReverterAConfiguracaoDosCincoProgramasEOsTresTiposNovosMantendoOProtEOPeot() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN);
            ligacao.setAutoCommit(true);
            String protEPeot ="SELECT id || ':' || codigo || ':' || nome FROM tipo_instrumento WHERE codigo IN ('PROT', 'PEOT') ORDER BY id";
            List<String> protEPeotAntes = linhas(ligacao, protEPeot);
            List<String> restantes = configuracaoForaDosProgramas(ligacao);
            String dosProgramas = "SELECT ti.codigo || ':' || count(DISTINCT idc.id) || ':' || count(DISTINCT ipc.id) || ':' "
                    + "|| count(DISTINCT pa.id) || ':' || count(DISTINCT r.id) || ':' || count(DISTINCT c.id) FROM tipo_instrumento ti "
                    + "JOIN instrumento_dinamica_config idc ON idc.tipo_instrumento_id = ti.id "
                    + "JOIN instrumento_procedimento_config ipc ON ipc.instrumento_dinamica_config_id = idc.id "
                    + "JOIN permissao_acao pa ON pa.instrumento_procedimento_config_id = ipc.id "
                    + "JOIN regra r ON r.instrumento_dinamica_config_id = idc.id JOIN condicao c ON c.regra_id = r.id "
                    + "WHERE ti.id IN " + DOS_PROGRAMAS + " GROUP BY ti.codigo ORDER BY ti.codigo";
            List<String> contagens = List.of("PEOT:6:12:36:6:8", "PIMOT:6:13:26:6:8", "PNPOT:5:9:18:5:7", "PROT:6:12:24:6:8",
                    "PS:6:12:36:6:8");
            assertThat(linhas(ligacao, dosProgramas)).containsExactlyElementsOf(contagens);
            assertThat(protEPeotAntes).containsExactly("4:PROT:Programa Regional de Ordenamento do Território",
                    "5:PEOT:Programa Especial de Ordenamento do Território");

            reverterOsUltimos(ligacao, CHANGESETS_DOS_PROGRAMAS);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "tipo_instrumento WHERE codigo IN ('PNPOT', 'PS', 'PIMOT')")).isZero();
            assertThat(linhas(ligacao, protEPeot)).isEqualTo(protEPeotAntes);
            assertThat(contar(ligacao, "instrumento_dinamica_config WHERE tipo_instrumento_id IN " + DOS_PROGRAMAS)).isZero();
            assertThat(configuracaoForaDosProgramas(ligacao)).isEqualTo(restantes);

            migrarOsProximos(ligacao, CHANGESETS_DOS_PROGRAMAS);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, dosProgramas)).containsExactlyElementsOf(contagens);
            assertThat(linhas(ligacao, protEPeot)).isEqualTo(protEPeotAntes);
            assertThat(configuracaoForaDosProgramas(ligacao)).isEqualTo(restantes);
        });
    }

    private static final String REGRAS_DA_REN = "SELECT td.codigo || ':' || r.descricao || ':' || r.operador || ':' "
            + "|| r.substitui_sequenciamento || ':' || r.ativo || ':' || coalesce(r.instrumento_procedimento_config_id::text, '-') "
            + "|| ':' || (SELECT string_agg(c.avaliador || '|' || c.parametros::text || '|' || c.grupo || '|' || c.ativo, ',') "
            + "FROM condicao c WHERE c.regra_id = r.id) FROM regra r "
            + "JOIN instrumento_dinamica_config idc ON idc.id = r.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "WHERE ti.codigo = 'REN' ORDER BY td.codigo, r.descricao";

    private static final String SEM_SIMULTANEAS = REGRA_SEM_SIMULTANEAS + ":AND:false:true:-:OCORRENCIA_NAO_EXISTE|"
            + "{\"estado\": \"EM_CURSO\", \"tipo_ocorrencia\": \"DINAMICA\"}|1|true";

    @Test
    void deveSemearAsRegrasDePrecedenciaDaRenAlemDaRegraSemDinamicasEmSimultaneoMantendoAsConfiguracoesInativas() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, REGRAS_DA_REN)).containsExactly(
                    "ALTERACAO:Requer REN já delimitada:AND:false:true:-:INSTRUMENTO_EM_VIGOR|{}|1|true",
                    "ALTERACAO:" + SEM_SIMULTANEAS,
                    "CORRECAO_MATERIAL_RETIFICACAO:Requer REN já delimitada:AND:false:true:-:INSTRUMENTO_EM_VIGOR|{}|1|true",
                    "CORRECAO_MATERIAL_RETIFICACAO:" + SEM_SIMULTANEAS,
                    "DELIMITACAO:Delimitação é a Dinâmica inicial e única:AND:false:true:-:OCORRENCIA_NAO_EXISTE|"
                            + "{\"codigo\": \"DELIMITACAO\", \"tipo_ocorrencia\": \"DINAMICA\"}|1|true",
                    "DELIMITACAO:" + SEM_SIMULTANEAS);
            // O seed dos Procedimentos da REN, posterior, ativa-as: antes dele estavam inativas e sem Procedimentos.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, "SELECT td.codigo || ':' || idc.ativo || ':' || (SELECT count(*) "
                    + "FROM instrumento_procedimento_config ipc WHERE ipc.instrumento_dinamica_config_id = idc.id) "
                    + "FROM instrumento_dinamica_config idc JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id WHERE ti.codigo = 'REN' ORDER BY td.codigo"))
                    .containsExactly("ALTERACAO:false:0", "CORRECAO_MATERIAL_RETIFICACAO:false:0", "DELIMITACAO:false:0");
        });
    }

    @Test
    void deveReverterSoAsTresRegrasDePrecedenciaDaRenEAsSuasCondicoes() throws Exception {
        comBaseMigrada(ligacao -> {
            // As regras do SGIFR vêm depois e saem no mesmo rollback: revertem-se antes de guardar as restantes.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR);
            ligacao.setAutoCommit(true);
            String regrasDePrecedencia = "regra WHERE descricao IN ('Delimitação é a Dinâmica inicial e única', "
                    + "'Requer REN já delimitada')";
            String restantesRegras = "SELECT md5(string_agg(r::text, ',' ORDER BY r.id)) FROM regra r WHERE id NOT IN "
                    + "(SELECT id FROM " + regrasDePrecedencia + ")";
            String restantesCondicoes = "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM condicao c WHERE regra_id "
                    + "NOT IN (SELECT id FROM " + regrasDePrecedencia + ")";
            String regrasAntes = texto(ligacao, restantesRegras);
            String condicoesAntes = texto(ligacao, restantesCondicoes);
            int regras = contar(ligacao, "regra");
            int condicoes = contar(ligacao, "condicao");
            assertThat(contar(ligacao, regrasDePrecedencia)).isEqualTo(3);

            reverterOsUltimos(ligacao, CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF + CHANGESETS_DAS_REGRAS_DE_PRECEDENCIA_DA_REN);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, regrasDePrecedencia)).isZero();
            assertThat(contar(ligacao, "regra")).isEqualTo(regras - 3);
            assertThat(contar(ligacao, "condicao")).isEqualTo(condicoes - 3);
            assertThat(texto(ligacao, "SELECT md5(string_agg(r::text, ',' ORDER BY r.id)) FROM regra r")).isEqualTo(regrasAntes);
            assertThat(texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM condicao c"))
                    .isEqualTo(condicoesAntes);
            assertThat(linhas(ligacao, REGRAS_DA_REN)).containsExactly("ALTERACAO:" + SEM_SIMULTANEAS,
                    "CORRECAO_MATERIAL_RETIFICACAO:" + SEM_SIMULTANEAS, "DELIMITACAO:" + SEM_SIMULTANEAS);

            migrar(ligacao);
            ligacao.setAutoCommit(true);
            // A Delimitação das restantes servidões, semeada depois, tem a mesma descrição.
            assertThat(contar(ligacao, regrasDePrecedencia)).isEqualTo(3 + 1);
            assertThat(contar(ligacao, "condicao")).isEqualTo(condicoes + 8 + CONDICOES_DE_MP_NP_E_SUSPENSAO
                    + CONDICOES_DAS_DINAMICAS_TRANSVERSAIS + CONDICOES_DAS_RESTANTES_SERVIDOES);
        });
    }

    private static final String PECAS_GRAFICAS = "documento_config WHERE tipo_elemento = 'PECA_GRAFICA'";

    /** O JSON que a aplicação grava para {@link RegrasTiff#inicialGeral()}, para comparar com o do changeset. */
    private static String regrasTiffIniciaisGerais() throws Exception {
        return "'" + new ObjectMapper().writeValueAsString(RegrasTiff.inicialGeral()) + "'::jsonb";
    }

    /** O JSON que a aplicação grava para {@link RegrasTiff#inicialSgifr()}. */
    private static String regrasTiffIniciaisSgifr() throws Exception {
        return "'" + new ObjectMapper().writeValueAsString(RegrasTiff.inicialSgifr()) + "'::jsonb";
    }

    @Test
    void deveDarATodasAsPecasGraficasAsRegrasTiffIniciaisGeraisESoAElas() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(contar(ligacao, PECAS_GRAFICAS)).isPositive();
            assertThat(contar(ligacao, PECAS_GRAFICAS + " AND " + SEM_ITENS_DO_SGIFR + " AND regras_tiff IS DISTINCT FROM "
                    + regrasTiffIniciaisGerais())).isZero();
            assertThat(contar(ligacao, "documento_config WHERE tipo_elemento <> 'PECA_GRAFICA' AND regras_tiff IS NOT NULL"))
                    .isZero();
            assertThat(texto(ligacao, "SELECT data_type FROM information_schema.columns WHERE table_schema = "
                    + "current_schema() AND table_name = 'documento_config' AND column_name = 'regras_tiff'"))
                    .isEqualTo("jsonb");
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'ck_documento_config_regras_tiff'"))
                    .contains("regras_tiff IS NULL").contains("'PECA_GRAFICA'");
        });
    }

    @Test
    void deveRecusarRegrasTiffNumItemQueNaoEPecaGraficaEAceitarVazioNumaPecaGrafica() throws Exception {
        comBaseMigrada(ligacao -> {
            for (String tipo : new String[] {"PECA_ESCRITA", "OUTROS"}) {
                assertThatThrownBy(() -> executar(ligacao, "UPDATE documento_config SET regras_tiff = "
                        + regrasTiffIniciaisGerais() + " WHERE id = (SELECT min(id) FROM documento_config "
                        + "WHERE tipo_elemento = '" + tipo + "')"))
                        .hasMessageContaining("ck_documento_config_regras_tiff");
            }
            assertThatCode(() -> executar(ligacao, "UPDATE documento_config SET regras_tiff = NULL WHERE id = "
                    + "(SELECT min(id) FROM " + PECAS_GRAFICAS + ")")).doesNotThrowAnyException();
        });
    }

    @Test
    void deveReverterAsRegrasTiffSemTocarNoRestoDosItensEVoltarAPreencherAsPecasGraficas() throws Exception {
        comBaseMigrada(ligacao -> {
            String itensSemRegras = "SELECT md5(string_agg((to_jsonb(d) - 'regras_tiff' - 'regra_nomenclatura' "
                    + "- 'independente_elementos_afetados')::text, ',' ORDER BY d.id)) FROM documento_config d WHERE "
                    + FORA_DAS_PLANTAS_DA_DECISAO_DE_ELABORAR + " AND " + SEM_ITENS_DO_SGIFR + " AND " + SEM_ITENS_DA_PRORROGACAO_DO_ATO;
            int pecasGraficas = contar(ligacao, PECAS_GRAFICAS + " AND " + SEM_ITENS_DO_SGIFR);
            semAListaDaDgt(ligacao);
            String itensAntes = texto(ligacao, itensSemRegras);

            // O semAListaDaDgt já reverteu os changesets até à lista do PDM: daqui só os anteriores.
            reverterOsUltimos(ligacao, CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA + CHANGESETS_DAS_REGRAS_TIFF);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "documento_config", "regras_tiff")).isFalse();
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'ck_documento_config_regras_tiff'")).isZero();
            assertThat(texto(ligacao, itensSemRegras)).isEqualTo(itensAntes);

            migrar(ligacao);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, PECAS_GRAFICAS + " AND regras_tiff = " + regrasTiffIniciaisGerais()))
                    .isEqualTo(pecasGraficas);
            assertThat(contar(ligacao, PECAS_GRAFICAS + " AND regras_tiff = " + regrasTiffIniciaisSgifr()))
                    .isEqualTo(PECAS_GRAFICAS_DO_SGIFR);
        });
    }

    @Test
    void deveAcrescentarAEntidadeInstrutoraAnulavelAoProcessoEReverteLaSemTocarNaEntidadeEmitente() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(texto(ligacao, "SELECT data_type || '|' || character_maximum_length || '|' || is_nullable "
                    + "FROM information_schema.columns WHERE table_schema = current_schema() "
                    + "AND table_name = 'processo' AND column_name = 'entidade_instrutora'"))
                    .isEqualTo("character varying|500|YES");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO + CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR + CHANGESETS_DA_ENTIDADE_INSTRUTORA);
            ligacao.setAutoCommit(true);

            assertThat(colunaExiste(ligacao, "processo", "entidade_instrutora")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "entidade_emitente")).isTrue();
            assertThat(colunaExiste(ligacao, "documento_config", "regras_tiff")).isTrue();
        });
    }

    private static final String DO_SGIFR = "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE ti.codigo IN ('PRA', 'PSA')";

    private static final String CONSULTA_PROCEDIMENTOS_DO_SGIFR = "SELECT ti.codigo || ':' || td.codigo || ':' || tp.codigo || ':' "
            + "|| ipc.ordem || ':' || ipc.obrigatorio || ':' || ipc.serie_dr || ':' || ipc.ativo || ':' "
            + "|| coalesce(ipc.prazo_dias::text, '-') || ':' || coalesce(ipc.tipo_prazo, '-') || ':' || ipc.repetivel || ':' "
            + "|| ipc.ato_pontual || ':' || coalesce(ipc.codigo_idus, '-') FROM instrumento_procedimento_config ipc " + DO_SGIFR
            + " ORDER BY ti.codigo, td.codigo";

    private static final String DINAMICAS_DO_SGIFR = "SELECT ti.codigo || ':' || td.codigo || ':' || idc.ativo "
            + "FROM instrumento_dinamica_config idc JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id WHERE ti.codigo IN ('PRA', 'PSA') ORDER BY ti.codigo, td.codigo";

    private static final String REGRAS_DO_SGIFR = "SELECT ti.codigo || ':' || td.codigo || ':' || r.descricao || ':' || r.operador "
            + "|| ':' || r.substitui_sequenciamento || ':' || r.ativo || ':' || coalesce(r.instrumento_procedimento_config_id::text, '-') "
            + "|| ':' || (SELECT string_agg(c.avaliador || '|' || c.parametros::text || '|' || c.grupo || '|' || c.ativo, ',' "
            + "ORDER BY c.id) FROM condicao c WHERE c.regra_id = r.id) FROM regra r "
            + "JOIN instrumento_dinamica_config idc ON idc.id = r.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "WHERE ti.codigo IN ('PRA', 'PSA') ORDER BY ti.codigo, td.codigo";

    private static final String SGIFR_DELIMITACAO = "Delimitação é única; sem Dinâmicas em simultâneo:AND:false:true:-:"
            + "OCORRENCIA_NAO_EXISTE|{\"codigo\": \"DELIMITACAO\", \"tipo_ocorrencia\": \"DINAMICA\"}|1|true,"
            + "OCORRENCIA_NAO_EXISTE|{\"estado\": \"EM_CURSO\", \"tipo_ocorrencia\": \"DINAMICA\"}|1|true";

    private static final String SGIFR_ALTERACAO = "Requer programa em vigor; sem Dinâmicas em simultâneo:AND:false:true:-:"
            + "INSTRUMENTO_EM_VIGOR|{}|1|true,"
            + "OCORRENCIA_NAO_EXISTE|{\"estado\": \"EM_CURSO\", \"tipo_ocorrencia\": \"DINAMICA\"}|1|true";

    @Test
    void deveConfigurarOPraNaSegundaSerieCEOPsaNaSegundaSerieHComPermissoesRegrasEDinamicasAtivas() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, "SELECT codigo || ':' || nome || ':' || ativo FROM tipo_procedimento "
                    + "WHERE codigo = 'PUBLICACAO_SGIFR'")).containsExactly("PUBLICACAO_SGIFR:Publicação do programa SGIFR:true");

            assertThat(linhas(ligacao, CONSULTA_PROCEDIMENTOS_DO_SGIFR)).containsExactly(
                    "PRA:ALTERACAO:PUBLICACAO_SGIFR:1:true:SEGUNDA_C:true:-:-:false:false:-",
                    "PRA:DELIMITACAO:PUBLICACAO_SGIFR:1:true:SEGUNDA_C:true:-:-:false:false:-",
                    "PSA:ALTERACAO:PUBLICACAO_SGIFR:1:true:SEGUNDA_H:true:-:-:false:false:-",
                    "PSA:DELIMITACAO:PUBLICACAO_SGIFR:1:true:SEGUNDA_H:true:-:-:false:false:-");

            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || td.codigo || ':' "
                    + "|| string_agg(pa.tipo_entidade || '/' || pa.ativo, ',' ORDER BY pa.tipo_entidade) "
                    + "FROM instrumento_procedimento_config ipc JOIN permissao_acao pa ON pa.instrumento_procedimento_config_id = ipc.id "
                    + DO_SGIFR + " GROUP BY ti.codigo, td.codigo ORDER BY ti.codigo, td.codigo")).containsExactly(
                    "PRA:ALTERACAO:CCDR_DR/true,DGT/true", "PRA:DELIMITACAO:CCDR_DR/true,DGT/true",
                    "PSA:ALTERACAO:AM/true,CIM/true,DGT/true", "PSA:DELIMITACAO:AM/true,CIM/true,DGT/true");

            assertThat(linhas(ligacao, DINAMICAS_DO_SGIFR))
                    .containsExactly("PRA:ALTERACAO:true", "PRA:DELIMITACAO:true", "PSA:ALTERACAO:true", "PSA:DELIMITACAO:true");

            assertThat(linhas(ligacao, REGRAS_DO_SGIFR)).containsExactly(
                    "PRA:ALTERACAO:" + SGIFR_ALTERACAO, "PRA:DELIMITACAO:" + SGIFR_DELIMITACAO,
                    "PSA:ALTERACAO:" + SGIFR_ALTERACAO, "PSA:DELIMITACAO:" + SGIFR_DELIMITACAO);
        });
    }

    @Test
    void deveReverterAConfiguracaoDoSgifrDevolvendoAsDinamicasAInativasSemTocarNoResto() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO);
            ligacao.setAutoCommit(true);
            String sgifr = "(SELECT ipc.id FROM instrumento_procedimento_config ipc " + DO_SGIFR + ")";
            String regrasSgifr = "(SELECT r.id FROM regra r JOIN instrumento_dinamica_config idc ON idc.id = r.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ti.codigo IN ('PRA', 'PSA'))";
            List<String> restantesAntes = List.of(
                    texto(ligacao, "SELECT md5(string_agg(t::text, ',' ORDER BY t.id)) FROM tipo_procedimento t "
                            + "WHERE codigo <> 'PUBLICACAO_SGIFR'"),
                    texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM instrumento_procedimento_config p "
                            + "WHERE id NOT IN " + sgifr),
                    texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM permissao_acao p "
                            + "WHERE instrumento_procedimento_config_id NOT IN " + sgifr),
                    texto(ligacao, "SELECT md5(string_agg(r::text, ',' ORDER BY r.id)) FROM regra r WHERE id NOT IN " + regrasSgifr),
                    texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM condicao c "
                            + "WHERE regra_id NOT IN " + regrasSgifr),
                    texto(ligacao, "SELECT md5(string_agg(i::text, ',' ORDER BY i.id)) FROM instrumento_dinamica_config i "
                            + "WHERE tipo_instrumento_id NOT IN (SELECT id FROM tipo_instrumento WHERE codigo IN ('PRA', 'PSA'))"));
            int procedimentos = contar(ligacao, "instrumento_procedimento_config");
            int permissoes = contar(ligacao, "permissao_acao");
            int regras = contar(ligacao, "regra");
            int condicoes = contar(ligacao, "condicao");

            reverterOsUltimos(ligacao, CHANGESETS_DO_LIMITE_DO_SGIFR + CHANGESETS_DA_REGRA_DE_NOMENCLATURA + CHANGESETS_DA_CONFIGURACAO_DO_SGIFR);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "tipo_procedimento WHERE codigo = 'PUBLICACAO_SGIFR'")).isZero();
            assertThat(contar(ligacao, "instrumento_procedimento_config")).isEqualTo(procedimentos - 4);
            assertThat(contar(ligacao, "permissao_acao")).isEqualTo(permissoes - 10);
            assertThat(contar(ligacao, "regra")).isEqualTo(regras - 4);
            assertThat(contar(ligacao, "condicao")).isEqualTo(condicoes - 8);
            assertThat(linhas(ligacao, REGRAS_DO_SGIFR)).isEmpty();
            assertThat(linhas(ligacao, DINAMICAS_DO_SGIFR)).containsExactly(
                    "PRA:ALTERACAO:false", "PRA:DELIMITACAO:false", "PSA:ALTERACAO:false", "PSA:DELIMITACAO:false");
            assertThat(List.of(
                    texto(ligacao, "SELECT md5(string_agg(t::text, ',' ORDER BY t.id)) FROM tipo_procedimento t"),
                    texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM instrumento_procedimento_config p"),
                    texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM permissao_acao p"),
                    texto(ligacao, "SELECT md5(string_agg(r::text, ',' ORDER BY r.id)) FROM regra r"),
                    texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM condicao c"),
                    texto(ligacao, "SELECT md5(string_agg(i::text, ',' ORDER BY i.id)) FROM instrumento_dinamica_config i "
                            + "WHERE tipo_instrumento_id NOT IN (SELECT id FROM tipo_instrumento WHERE codigo IN ('PRA', 'PSA'))")))
                    .isEqualTo(restantesAntes);

            migrar(ligacao);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, CONSULTA_PROCEDIMENTOS_DO_SGIFR)).hasSize(4);
            // As permissões dos Procedimentos da RERAE de PDM, PP e PU, que o rollback da retirada repôs, saem outra vez.
            assertThat(contar(ligacao, "permissao_acao")).isEqualTo(permissoes + PERMISSOES_DE_MP_NP_E_SUSPENSAO
                    + PERMISSOES_DAS_DINAMICAS_TRANSVERSAIS + PERMISSOES_DA_MATRIZ_NOS_PROGRAMAS + PERMISSOES_DOS_ATOS_PONTUAIS
                    + PERMISSOES_DA_PCM_NA_PRIMEIRA_SERIE + PERMISSOES_DAS_RESTANTES_SERVIDOES
                    - PERMISSOES_DA_RERAE_DE_PDM_PP_E_PU - PERMISSOES_DOS_ATOS_PONTUAIS_DA_RERAE + PERMISSOES_DA_REN);
            assertThat(contar(ligacao, "condicao")).isEqualTo(condicoes + CONDICOES_DE_MP_NP_E_SUSPENSAO
                    + CONDICOES_DAS_DINAMICAS_TRANSVERSAIS + CONDICOES_DAS_RESTANTES_SERVIDOES);
            assertThat(linhas(ligacao, DINAMICAS_DO_SGIFR)).containsOnly(
                    "PRA:ALTERACAO:true", "PRA:DELIMITACAO:true", "PSA:ALTERACAO:true", "PSA:DELIMITACAO:true");
        });
    }

    private static final String LIMITES_DO_SGIFR = "SELECT ti.codigo || ':' || td.codigo || ':' "
            + "|| coalesce(ipc.tamanho_maximo_processo::text, '-') FROM instrumento_procedimento_config ipc " + DO_SGIFR
            + " ORDER BY ti.codigo, td.codigo";

    @Test
    void deveDarATodosOsItensARegraDeNomenclaturaGeralObrigatoriaERecusarOutroValor() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(contar(ligacao, "documento_config")).isPositive();
            assertThat(contar(ligacao, "documento_config WHERE regra_nomenclatura IS DISTINCT FROM 'GERAL' AND "
                    + SEM_ITENS_DO_SGIFR)).isZero();
            assertThat(texto(ligacao, "SELECT data_type || '|' || character_maximum_length || '|' || is_nullable || '|' "
                    + "|| column_default FROM information_schema.columns WHERE table_schema = current_schema() "
                    + "AND table_name = 'documento_config' AND column_name = 'regra_nomenclatura'"))
                    .isEqualTo("character varying|10|NO|'GERAL'::character varying");

            String primeiro = " WHERE id = (SELECT min(id) FROM documento_config)";
            assertThatThrownBy(() -> executar(ligacao, "UPDATE documento_config SET regra_nomenclatura = 'OUTRA'" + primeiro))
                    .hasMessageContaining("ck_documento_config_regra_nomenclatura");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE documento_config SET regra_nomenclatura = NULL" + primeiro))
                    .hasMessageContaining("regra_nomenclatura");
            assertThatCode(() -> executar(ligacao, "UPDATE documento_config SET regra_nomenclatura = 'SGIFR'" + primeiro))
                    .doesNotThrowAnyException();
        });
    }

    @Test
    void deveLimitarACincoGigabytesOsQuatroProcedimentosDePublicacaoDoSgifr() throws Exception {
        comBaseMigrada(ligacao -> assertThat(linhas(ligacao, LIMITES_DO_SGIFR)).containsExactly(
                "PRA:ALTERACAO:5368709120", "PRA:DELIMITACAO:5368709120",
                "PSA:ALTERACAO:5368709120", "PSA:DELIMITACAO:5368709120"));
    }

    @Test
    void deveReverterOLimiteDoSgifrEARegraDeNomenclaturaSemTocarNoRestoEVoltarAAplicaLos() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO);
            ligacao.setAutoCommit(true);
            String procedimentosForaDoSgifr = "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) "
                    + "FROM instrumento_procedimento_config p WHERE id NOT IN "
                    + "(SELECT ipc.id FROM instrumento_procedimento_config ipc " + DO_SGIFR + ")";
            String itensSemRegra = "SELECT md5(string_agg((to_jsonb(d) - 'regra_nomenclatura')::text, ',' ORDER BY d.id)) "
                    + "FROM documento_config d";
            String procedimentosAntes = texto(ligacao, procedimentosForaDoSgifr);
            String itensAntes = texto(ligacao, itensSemRegra);

            reverterOsUltimos(ligacao, CHANGESETS_DO_LIMITE_DO_SGIFR);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, LIMITES_DO_SGIFR)).containsExactly(
                    "PRA:ALTERACAO:-", "PRA:DELIMITACAO:-", "PSA:ALTERACAO:-", "PSA:DELIMITACAO:-");
            assertThat(texto(ligacao, procedimentosForaDoSgifr)).isEqualTo(procedimentosAntes);
            assertThat(colunaExiste(ligacao, "documento_config", "regra_nomenclatura")).isTrue();

            reverterOsUltimos(ligacao, CHANGESETS_DA_REGRA_DE_NOMENCLATURA);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "documento_config", "regra_nomenclatura")).isFalse();
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'ck_documento_config_regra_nomenclatura'")).isZero();
            assertThat(texto(ligacao, itensSemRegra)).isEqualTo(itensAntes);
            assertThat(linhas(ligacao, CONSULTA_PROCEDIMENTOS_DO_SGIFR)).hasSize(4);

            migrar(ligacao);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "documento_config WHERE regra_nomenclatura IS DISTINCT FROM 'GERAL' AND "
                    + SEM_ITENS_DO_SGIFR)).isZero();
            assertThat(contar(ligacao, "documento_config WHERE regra_nomenclatura = 'SGIFR'")).isEqualTo(ITENS_DO_SGIFR);
            assertThat(linhas(ligacao, LIMITES_DO_SGIFR)).containsOnly(
                    "PRA:ALTERACAO:5368709120", "PRA:DELIMITACAO:5368709120",
                    "PSA:ALTERACAO:5368709120", "PSA:DELIMITACAO:5368709120");
        });
    }

    private static final String CONFIGURACOES_DE_MP_NP_E_SUSPENSAO = "SELECT idc.id FROM instrumento_dinamica_config idc "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "WHERE ti.codigo IN ('PDM', 'PP', 'PU', 'PDIM', 'PUIM', 'PPIM') "
            + "AND td.codigo IN ('MEDIDAS_PREVENTIVAS', 'NORMAS_PROVISORIAS', 'SUSPENSAO')";

    /** Por tipo de Instrumento: configurações ativas, Procedimentos, permissões, regras e condições das três Dinâmicas. */
    private static final String RESUMO_DE_MP_NP_E_SUSPENSAO = "SELECT ti.codigo || ':' || count(DISTINCT idc.id) "
            + "FILTER (WHERE idc.ativo) || ':' || count(DISTINCT ipc.id) || ':' || count(DISTINCT pa.id) || ':' "
            + "|| count(DISTINCT r.id) || ':' || count(DISTINCT c.id) FROM tipo_instrumento ti "
            + "JOIN instrumento_dinamica_config idc ON idc.tipo_instrumento_id = ti.id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "AND td.codigo IN ('MEDIDAS_PREVENTIVAS', 'NORMAS_PROVISORIAS', 'SUSPENSAO') "
            + "LEFT JOIN instrumento_procedimento_config ipc ON ipc.instrumento_dinamica_config_id = idc.id "
            + "LEFT JOIN permissao_acao pa ON pa.instrumento_procedimento_config_id = ipc.id "
            + "LEFT JOIN regra r ON r.instrumento_dinamica_config_id = idc.id LEFT JOIN condicao c ON c.regra_id = r.id "
            + "WHERE ti.codigo IN ('PDM', 'PP', 'PU', 'PDIM', 'PUIM', 'PPIM') GROUP BY ti.codigo ORDER BY ti.codigo";

    private static final List<String> RESUMO_COM_MP_NP_E_SUSPENSAO = List.of("PDIM:3:6:12:3:4", "PDM:3:6:12:3:4",
            "PP:3:6:12:3:4", "PPIM:3:6:12:3:4", "PU:3:6:12:3:4", "PUIM:3:6:12:3:4");

    /** Impressão digital de cada tabela do seed, sem as linhas das três Dinâmicas do município. */
    private List<String> configuracaoForaDeMpNpESuspensao(Connection ligacao) throws SQLException {
        String procedimentos = "SELECT id FROM instrumento_procedimento_config WHERE instrumento_dinamica_config_id IN ("
                + CONFIGURACOES_DE_MP_NP_E_SUSPENSAO + ")";
        String regras = "SELECT id FROM regra WHERE instrumento_dinamica_config_id IN (" + CONFIGURACOES_DE_MP_NP_E_SUSPENSAO + ")";
        return List.of(
                texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM instrumento_dinamica_config c "
                        + "WHERE id NOT IN (" + CONFIGURACOES_DE_MP_NP_E_SUSPENSAO + ")"),
                texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM instrumento_procedimento_config p "
                        + "WHERE id NOT IN (" + procedimentos + ")"),
                texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM permissao_acao p "
                        + "WHERE instrumento_procedimento_config_id NOT IN (" + procedimentos + ")"),
                texto(ligacao, "SELECT md5(string_agg(r::text, ',' ORDER BY r.id)) FROM regra r WHERE id NOT IN (" + regras + ")"),
                texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM condicao c "
                        + "WHERE regra_id NOT IN (" + regras + ")"));
    }

    @Test
    void deveReverterAsMpNpESuspensaoDeixandoAsDoPdmPpEPuAtivasESemProcedimentosEVoltarAAplicaLas() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, RESUMO_DE_MP_NP_E_SUSPENSAO)).containsExactlyElementsOf(RESUMO_COM_MP_NP_E_SUSPENSAO);
            // A localização das Dinâmicas municipais, mais recente, muda outros Procedimentos: sai antes de se guardar o resto.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS);
            ligacao.setAutoCommit(true);
            List<String> restantes = configuracaoForaDeMpNpESuspensao(ligacao);

            reverterOsUltimos(ligacao, CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO);
            ligacao.setAutoCommit(true);

            assertThat(linhas(ligacao, RESUMO_DE_MP_NP_E_SUSPENSAO))
                    .containsExactly("PDM:3:0:0:0:0", "PP:3:0:0:0:0", "PU:3:0:0:0:0");
            assertThat(configuracaoForaDeMpNpESuspensao(ligacao)).isEqualTo(restantes);
            assertThat(contar(ligacao, "databasechangelog WHERE id LIKE '2026-11-12-5-%'")).isZero();

            migrarOsProximos(ligacao, CHANGESETS_DE_MP_NP_E_SUSPENSAO);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, RESUMO_DE_MP_NP_E_SUSPENSAO)).containsExactlyElementsOf(RESUMO_COM_MP_NP_E_SUSPENSAO);
            assertThat(configuracaoForaDeMpNpESuspensao(ligacao)).isEqualTo(restantes);
        });
    }

    @Test
    void naoDeveFalharNemDuplicarQuandoOBackofficeJaCriouLinhasNemQuandoCorreDuasVezes() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO + CHANGESETS_DE_MP_NP_E_SUSPENSAO);
            ligacao.setAutoCommit(true);
            String config = "(SELECT idc.id FROM instrumento_dinamica_config idc JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id WHERE ti.codigo = '%s' AND td.codigo = '%s')";
            executar(ligacao, "INSERT INTO instrumento_procedimento_config (instrumento_dinamica_config_id, tipo_procedimento_id, "
                    + "ordem, serie_dr, tamanho_maximo_processo) SELECT " + config.formatted("PDM", "MEDIDAS_PREVENTIVAS")
                    + ", id, 1, 'SEGUNDA_H', 5368709120 FROM tipo_procedimento WHERE codigo = 'PUBLICACAO_MP'");
            executar(ligacao, "INSERT INTO permissao_acao (tipo_entidade, instrumento_procedimento_config_id) "
                    + "SELECT 'CM', max(id) FROM instrumento_procedimento_config");
            executar(ligacao, "INSERT INTO instrumento_dinamica_config (tipo_instrumento_id, tipo_dinamica_id) "
                    + "SELECT (SELECT id FROM tipo_instrumento WHERE codigo = 'PDIM'), id FROM tipo_dinamica WHERE codigo = 'SUSPENSAO'");
            executar(ligacao, "INSERT INTO regra (instrumento_dinamica_config_id, operador, descricao) SELECT "
                    + config.formatted("PU", "NORMAS_PROVISORIAS") + ", 'AND', 'Regra do backoffice'");
            executar(ligacao, "INSERT INTO condicao (regra_id, avaliador, parametros) SELECT max(id), 'INSTRUMENTO_EM_VIGOR', '{}' FROM regra");

            assertThatCode(() -> migrarOsProximos(ligacao, CHANGESETS_DE_MP_NP_E_SUSPENSAO)).doesNotThrowAnyException();
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, RESUMO_DE_MP_NP_E_SUSPENSAO)).containsExactlyElementsOf(RESUMO_COM_MP_NP_E_SUSPENSAO);
            assertThat(linhas(ligacao, "SELECT r.descricao FROM regra r WHERE r.instrumento_dinamica_config_id = "
                    + config.formatted("PU", "NORMAS_PROVISORIAS"))).containsExactly("Regra do backoffice");

            executar(ligacao, "DELETE FROM databasechangelog WHERE id LIKE '2026-11-12-5-%'");
            assertThatCode(() -> migrarOsProximos(ligacao, CHANGESETS_DE_MP_NP_E_SUSPENSAO)).doesNotThrowAnyException();
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "databasechangelog WHERE id LIKE '2026-11-12-5-%'")).isEqualTo(CHANGESETS_DE_MP_NP_E_SUSPENSAO);
            assertThat(linhas(ligacao, RESUMO_DE_MP_NP_E_SUSPENSAO)).containsExactlyElementsOf(RESUMO_COM_MP_NP_E_SUSPENSAO);
        });
    }

    @Test
    void deveAcrescentarAJustificacaoDoDesbloqueioERemoveLaNoRollback() throws Exception {
        String coluna = "SELECT data_type || '|' || is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'processo_documento' AND column_name = 'justificacao_desbloqueio'";
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, coluna)).isEqualTo("text|YES");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS + CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS + CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "information_schema.columns WHERE table_name = 'processo_documento' "
                    + "AND column_name = 'justificacao_desbloqueio'")).isZero();
            assertThat(contar(ligacao, "databasechangelog WHERE id = '2026-11-12-6-add-justificacao-desbloqueio'")).isZero();

            migrarOsProximos(ligacao, CHANGESETS_DA_JUSTIFICACAO_DE_DESBLOQUEIO);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, coluna)).isEqualTo("text|YES");
        });
    }

    private static final String LOCALIZACAO_DOS_PRIMEIROS_PROCEDIMENTOS = "SELECT ti.codigo || ':' || td.codigo || ':' "
            + "|| ipc.pede_localizacao FROM instrumento_procedimento_config ipc "
            + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "WHERE ipc.ordem = 1 AND ti.codigo IN ('PDM', 'PP', 'PU') AND td.codigo NOT IN (" + DINAMICAS_TRANSVERSAIS + ")";

    private static List<String> localizacaoEsperada(boolean comOSeed, boolean comARerae) {
        List<String> obrigatoriaNoPdm = List.of("ALTERACAO_SIMPLIFICADA_ART123", "ALTERACAO_SIMPLIFICADA_DL10_2024", "ALTERACAO_ADAPTACAO");
        List<String> obrigatoriaNoPuEPp = List.of("ELABORACAO", "REVISAO", "ALTERACAO", "ALTERACAO_SIMPLIFICADA_ART123",
                "ALTERACAO_SIMPLIFICADA_DL10_2024", "ALTERACAO_ADAPTACAO");
        List<String> opcional = List.of("SUSPENSAO", "MEDIDAS_PREVENTIVAS", "NORMAS_PROVISORIAS", "CORRECAO_MATERIAL");
        List<String> todas = new ArrayList<>(List.of("ELABORACAO", "REVISAO", "ALTERACAO", "ALTERACAO_SIMPLIFICADA_ART123",
                "ALTERACAO_SIMPLIFICADA_DL10_2024", "ALTERACAO_ADAPTACAO", "CORRECAO_MATERIAL", "REVOGACAO",
                "SUSPENSAO", "MEDIDAS_PREVENTIVAS", "NORMAS_PROVISORIAS"));
        if (comARerae) {
            // Com a retirada da RERAE revertida, que repõe os seus Procedimentos.
            todas.add("ALTERACAO_RERAE");
        }
        List<String> esperada = new ArrayList<>();
        for (String tipo : List.of("PDM", "PP", "PU")) {
            for (String dinamica : todas) {
                String valor = "NAO";
                if ("PDM".equals(tipo) && "ALTERACAO".equals(dinamica)) {
                    valor = "OBRIGATORIA";
                } else if (comOSeed && ("PDM".equals(tipo) ? obrigatoriaNoPdm : obrigatoriaNoPuEPp).contains(dinamica)) {
                    valor = "OBRIGATORIA";
                } else if (comOSeed && opcional.contains(dinamica)) {
                    valor = "OPCIONAL";
                }
                esperada.add(tipo + ":" + dinamica + ":" + valor);
            }
        }
        return esperada;
    }

    @Test
    void deveLigarALocalizacaoNoPrimeiroProcedimentoDasDinamicasMunicipaisDoPdmPuEPp() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, LOCALIZACAO_DOS_PRIMEIROS_PROCEDIMENTOS))
                    .containsExactlyInAnyOrderElementsOf(localizacaoEsperada(true, false));
            assertThat(contar(ligacao, "instrumento_procedimento_config WHERE pede_localizacao = 'OBRIGATORIA'")).isEqualTo(16);
            assertThat(contar(ligacao, "instrumento_procedimento_config WHERE pede_localizacao = 'OPCIONAL'")).isEqualTo(12);
            assertThat(contar(ligacao, "instrumento_procedimento_config WHERE ordem > 1 AND pede_localizacao <> 'NAO'")).isZero();
        });
    }

    @Test
    void deveReverterALocalizacaoDasDinamicasMunicipaisMantendoAAlteracaoDoPdmEVoltarAAplicaLa() throws Exception {
        comBaseMigrada(ligacao -> {
            // O motivo da alteração vem depois e muda as mesmas linhas: sai antes do md5 dos restantes.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS);
            ligacao.setAutoCommit(true);
            String restantes = "SELECT md5(string_agg((to_jsonb(p) - 'pede_localizacao')::text, ',' ORDER BY p.id)) "
                    + "FROM instrumento_procedimento_config p";
            String antes = texto(ligacao, restantes);

            reverterOsUltimos(ligacao, CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, LOCALIZACAO_DOS_PRIMEIROS_PROCEDIMENTOS))
                    .containsExactlyInAnyOrderElementsOf(localizacaoEsperada(false, true));
            assertThat(contar(ligacao, "instrumento_procedimento_config WHERE pede_localizacao <> 'NAO'")).isEqualTo(1);
            assertThat(texto(ligacao, restantes)).isEqualTo(antes);
            assertThat(contar(ligacao, "databasechangelog WHERE id = '2026-11-12-7-seed-localizacao-dinamicas-municipais'")).isZero();

            migrarOsProximos(ligacao, CHANGESETS_DA_LOCALIZACAO_DAS_DINAMICAS_MUNICIPAIS);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, LOCALIZACAO_DOS_PRIMEIROS_PROCEDIMENTOS))
                    .containsExactlyInAnyOrderElementsOf(localizacaoEsperada(true, true));
        });
    }

    private static final String MOTIVO_DOS_PRIMEIROS_PROCEDIMENTOS = "SELECT ti.codigo || ':' || td.codigo || ':' "
            + "|| ipc.pede_motivo_alteracao FROM instrumento_procedimento_config ipc "
            + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "WHERE ipc.ordem = 1 AND ti.codigo IN ('PDM', 'PU', 'PP', 'PDIM', 'PUIM', 'PPIM')";

    /** Os primeiros Procedimentos que pedem o motivo; os intermunicipais não têm a Alteração Simplificada DL 10/2024. */
    private static List<String> motivoEsperado() {
        List<String> esperado = new ArrayList<>(List.of("PDM:ALTERACAO:COM_PERGUNTA",
                "PDM:ALTERACAO_SIMPLIFICADA_ART123:COM_PERGUNTA", "PDM:ALTERACAO_SIMPLIFICADA_DL10_2024:COM_PERGUNTA",
                "PDM:ALTERACAO_ADAPTACAO:OBRIGATORIO"));
        for (String tipo : List.of("PU", "PP", "PDIM", "PUIM", "PPIM")) {
            esperado.add(tipo + ":ALTERACAO:COM_PERGUNTA");
            esperado.add(tipo + ":ALTERACAO_SIMPLIFICADA_ART123:COM_PERGUNTA");
            if (List.of("PU", "PP").contains(tipo)) {
                esperado.add(tipo + ":ALTERACAO_SIMPLIFICADA_DL10_2024:COM_PERGUNTA");
            }
            esperado.add(tipo + ":ALTERACAO_ADAPTACAO:OBRIGATORIO");
        }
        return esperado;
    }

    @Test
    void deveLigarOMotivoDaAlteracaoNoPrimeiroProcedimentoDasAlteracoesDosSeisPlanos() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(linhas(ligacao, MOTIVO_DOS_PRIMEIROS_PROCEDIMENTOS + " AND ipc.pede_motivo_alteracao <> 'NAO'"))
                    .containsExactlyInAnyOrderElementsOf(motivoEsperado());
            assertThat(contar(ligacao, "instrumento_procedimento_config WHERE pede_motivo_alteracao <> 'NAO'")).isEqualTo(21);
            assertThat(linhas(ligacao, MOTIVO_DOS_PRIMEIROS_PROCEDIMENTOS + " AND td.codigo IN ("
                    + "'ELABORACAO', 'REVISAO', 'CORRECAO_MATERIAL') AND ipc.pede_motivo_alteracao <> 'NAO'")).isEmpty();
        });
    }

    @Test
    void deveReverterOMotivoDaAlteracaoMantendoAAlteracaoDoPdmEVoltarAAplicaLo() throws Exception {
        comBaseMigrada(ligacao -> {
            // As Dinâmicas transversais vêm depois e acrescentam Procedimentos: saem antes do md5 dos restantes.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT);
            ligacao.setAutoCommit(true);
            String restantes = "SELECT md5(string_agg((to_jsonb(p) - 'pede_motivo_alteracao')::text, ',' ORDER BY p.id)) "
                    + "FROM instrumento_procedimento_config p";
            String antes = texto(ligacao, restantes);

            reverterOsUltimos(ligacao, CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO + CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, MOTIVO_DOS_PRIMEIROS_PROCEDIMENTOS + " AND ipc.pede_motivo_alteracao <> 'NAO'"))
                    .containsExactly("PDM:ALTERACAO:COM_PERGUNTA");
            assertThat(contar(ligacao, "instrumento_procedimento_config WHERE pede_motivo_alteracao <> 'NAO'")).isEqualTo(1);
            assertThat(texto(ligacao, restantes)).isEqualTo(antes);
            assertThat(contar(ligacao, "databasechangelog WHERE id = '2026-11-12-8-seed-motivo-alteracao-dinamicas'")).isZero();

            migrarOsProximos(ligacao, CHANGESETS_DO_MOTIVO_DE_ALTERACAO_DAS_DINAMICAS);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, MOTIVO_DOS_PRIMEIROS_PROCEDIMENTOS + " AND ipc.pede_motivo_alteracao <> 'NAO'"))
                    .containsExactlyInAnyOrderElementsOf(motivoEsperado());
        });
    }

    @Test
    void deveCriarOIndiceGinDosMotivosDaAlteracaoUsadoPeloFiltroERemoveLoNoRollback() throws Exception {
        String consulta = "EXPLAIN SELECT id FROM processo WHERE (dados_formulario -> 'motivosAlteracao') "
                + "@> '[{\"tipo\":\"MOTIVO_ALTERACAO_PROGRAMA\",\"codigo\":\"PNEC\"}]'::jsonb";
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(definicaoDoIndice(ligacao, "idx_processo_motivos_alteracao"))
                    .contains("USING gin").contains("(dados_formulario -> 'motivosAlteracao'::text)");
            executar(ligacao, "SET enable_seqscan = off");
            assertThat(String.join("\n", linhas(ligacao, consulta))).contains("idx_processo_motivos_alteracao");
            executar(ligacao, "RESET enable_seqscan");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO);
            ligacao.setAutoCommit(true);
            assertThat(definicaoDoIndice(ligacao, "idx_processo_motivos_alteracao")).isNull();

            migrarOsProximos(ligacao, CHANGESETS_DO_INDICE_DOS_MOTIVOS_DE_ALTERACAO);
            ligacao.setAutoCommit(true);
            assertThat(definicaoDoIndice(ligacao, "idx_processo_motivos_alteracao")).contains("USING gin");
        });
    }

    /** Por tipo de Dinâmica iniciada sem IGT: snit_codigo, associação, configurações, Procedimentos, permissões, regras e condições. */
    private static final String RESUMO_DAS_DINAMICAS_SEM_IGT = "SELECT td.codigo || ':' || coalesce(td.snit_codigo, '-') || ':' "
            + "|| string_agg(DISTINCT idc.associacao_instrumentos, '/') || ':' || count(DISTINCT idc.id) || ':' "
            + "|| count(DISTINCT ipc.id) || ':' || count(DISTINCT pa.id) || ':' || count(DISTINCT r.id) || ':' || count(DISTINCT c.id) "
            + "FROM tipo_dinamica td JOIN instrumento_dinamica_config idc ON idc.tipo_dinamica_id = td.id "
            + "LEFT JOIN instrumento_procedimento_config ipc ON ipc.instrumento_dinamica_config_id = idc.id "
            + "LEFT JOIN permissao_acao pa ON pa.instrumento_procedimento_config_id = ipc.id AND pa.tipo_entidade <> 'PCM' "
            + "LEFT JOIN regra r ON r.instrumento_dinamica_config_id = idc.id LEFT JOIN condicao c ON c.regra_id = r.id "
            + "WHERE td.inicio_sem_igt GROUP BY td.codigo, td.snit_codigo ORDER BY td.codigo";

    private static final List<String> RESUMO_COM_AS_DINAMICAS_TRANSVERSAIS = List.of(
            "MEDIDAS_PREVENTIVAS:14:OPCIONAL:6:12:24:6:6", "MEDIDAS_PREVENTIVAS_GOVERNO:15:UM_OU_MAIS:11:22:44:11:11",
            "RATIFICACAO:-:UM:2:2:4:0:0", "SUSPENSAO_CCDR:-:OPCIONAL:6:6:12:6:6", "SUSPENSAO_GOVERNO:18:UM_OU_MAIS:11:22:44:11:11");

    /** Impressão digital do catálogo e da configuração sem as linhas das Dinâmicas transversais nem as colunas que o seed muda. */
    private List<String> configuracaoForaDasDinamicasTransversais(Connection ligacao) throws SQLException {
        String configuracoes = "SELECT idc.id FROM instrumento_dinamica_config idc JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                + "WHERE td.codigo IN (" + DINAMICAS_TRANSVERSAIS + ")";
        String procedimentos = "SELECT id FROM instrumento_procedimento_config WHERE instrumento_dinamica_config_id IN (" + configuracoes + ")";
        String regras = "SELECT id FROM regra WHERE instrumento_dinamica_config_id IN (" + configuracoes + ")";
        return List.of(
                texto(ligacao, "SELECT md5(string_agg((to_jsonb(t) - 'inicio_sem_igt' - 'designacao_unica')::text, ',' ORDER BY t.id)) FROM tipo_dinamica t "
                        + "WHERE codigo NOT IN (" + DINAMICAS_TRANSVERSAIS + ", 'MEDIDAS_PREVENTIVAS')"),
                texto(ligacao, "SELECT md5(string_agg(t::text, ',' ORDER BY t.id)) FROM tipo_procedimento t WHERE codigo NOT IN "
                        + "('PUBLICACAO_SUSPENSAO_GOVERNO', 'PRORROGACAO_SUSPENSAO_GOVERNO', 'PUBLICACAO_MP_GOVERNO', "
                        + "'PRORROGACAO_MP_GOVERNO', 'SUSPENSAO_CCDR', 'RATIFICACAO')"),
                texto(ligacao, "SELECT md5(string_agg((to_jsonb(c) - 'associacao_instrumentos')::text, ',' ORDER BY c.id)) "
                        + "FROM instrumento_dinamica_config c WHERE id NOT IN (" + configuracoes + ")"),
                texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM instrumento_procedimento_config p "
                        + "WHERE id NOT IN (" + procedimentos + ")"),
                texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM permissao_acao p "
                        + "WHERE instrumento_procedimento_config_id NOT IN (" + procedimentos + ")"),
                texto(ligacao, "SELECT md5(string_agg(r::text, ',' ORDER BY r.id)) FROM regra r WHERE id NOT IN (" + regras + ")"),
                texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM condicao c WHERE regra_id NOT IN (" + regras + ")"));
    }

    private static final String MEDIDAS_PREVENTIVAS_DO_MUNICIPIO = "SELECT td.nome || ':' || ti.codigo || ':' || idc.associacao_instrumentos "
            + "FROM instrumento_dinamica_config idc JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE td.codigo = 'MEDIDAS_PREVENTIVAS' ORDER BY ti.codigo";

    private static List<String> medidasPreventivasDoMunicipio(String nome, String associacao) {
        return List.of("PDIM", "PDM", "PP", "PPIM", "PU", "PUIM").stream().map(t -> nome + ":" + t + ":" + associacao).toList();
    }

    @Test
    void deveReverterAsDinamicasTransversaisEAColunaInicioSemIgtRepondoAsMedidasPreventivasDoMunicipioEVoltarAAplicaLas() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, RESUMO_DAS_DINAMICAS_SEM_IGT)).containsExactlyElementsOf(RESUMO_COM_AS_DINAMICAS_TRANSVERSAIS);
            // A matriz nos Programas, por cima destas, sai antes da fotografia: não volta com a migração.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS);
            ligacao.setAutoCommit(true);
            List<String> restantes = configuracaoForaDasDinamicasTransversais(ligacao);

            reverterOsUltimos(ligacao, CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS + CHANGESETS_DO_INICIO_SEM_IGT);
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, "information_schema.columns WHERE table_name = 'tipo_dinamica' "
                    + "AND column_name = 'inicio_sem_igt'")).isZero();
            assertThat(contar(ligacao, "tipo_dinamica WHERE codigo IN (" + DINAMICAS_TRANSVERSAIS + ")")).isZero();
            assertThat(contar(ligacao, "tipo_procedimento WHERE codigo LIKE '%\\_GOVERNO' OR codigo IN ('SUSPENSAO_CCDR', "
                    + "'RATIFICACAO')")).isZero();
            assertThat(linhas(ligacao, MEDIDAS_PREVENTIVAS_DO_MUNICIPIO))
                    .containsExactlyElementsOf(medidasPreventivasDoMunicipio("Medidas Preventivas", "UM"));
            assertThat(configuracaoForaDasDinamicasTransversais(ligacao)).isEqualTo(restantes);
            assertThat(contar(ligacao, "databasechangelog WHERE id LIKE '2026-11-12-10-%' OR id LIKE '2026-11-12-11-%'")).isZero();

            migrarOsProximos(ligacao, CHANGESETS_DO_INICIO_SEM_IGT + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, RESUMO_DAS_DINAMICAS_SEM_IGT)).containsExactlyElementsOf(RESUMO_COM_AS_DINAMICAS_TRANSVERSAIS);
            assertThat(linhas(ligacao, MEDIDAS_PREVENTIVAS_DO_MUNICIPIO)).containsExactlyElementsOf(
                    medidasPreventivasDoMunicipio("Medidas Preventivas (iniciativa do município)", "OPCIONAL"));
            assertThat(configuracaoForaDasDinamicasTransversais(ligacao)).isEqualTo(restantes);
        });
    }

    @Test
    void naoDeveFalharNemDuplicarAsDinamicasTransversaisQuandoOBackofficeJaCriouLinhasNemQuandoCorreDuasVezes() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO + CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS);
            ligacao.setAutoCommit(true);
            executar(ligacao, "INSERT INTO tipo_dinamica (codigo, nome, inicio_sem_igt, snit_codigo) "
                    + "VALUES ('SUSPENSAO_GOVERNO', 'Suspensão (Governo, backoffice)', true, '18')");
            executar(ligacao, "INSERT INTO tipo_procedimento (codigo, nome) VALUES ('RATIFICACAO', 'Ratificação (backoffice)')");
            executar(ligacao, "INSERT INTO instrumento_dinamica_config (tipo_instrumento_id, tipo_dinamica_id, associacao_instrumentos) "
                    + "SELECT (SELECT id FROM tipo_instrumento WHERE codigo = 'PROT'), id, 'UM_OU_MAIS' FROM tipo_dinamica "
                    + "WHERE codigo = 'SUSPENSAO_GOVERNO'");
            executar(ligacao, "INSERT INTO regra (instrumento_dinamica_config_id, operador, descricao) SELECT max(id), 'AND', "
                    + "'Regra do backoffice' FROM instrumento_dinamica_config");
            executar(ligacao, "INSERT INTO condicao (regra_id, avaliador, parametros) SELECT max(id), 'INSTRUMENTO_EM_VIGOR', '{}' FROM regra");

            assertThatCode(() -> migrarOsProximos(ligacao, CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS)).doesNotThrowAnyException();
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, RESUMO_DAS_DINAMICAS_SEM_IGT)).containsExactlyElementsOf(RESUMO_COM_AS_DINAMICAS_TRANSVERSAIS);
            assertThat(contar(ligacao, "tipo_dinamica WHERE codigo = 'SUSPENSAO_GOVERNO'")).isEqualTo(1);
            assertThat(contar(ligacao, "tipo_procedimento WHERE codigo = 'RATIFICACAO'")).isEqualTo(1);
            assertThat(linhas(ligacao, "SELECT r.descricao FROM regra r JOIN instrumento_dinamica_config idc "
                    + "ON idc.id = r.instrumento_dinamica_config_id JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id WHERE ti.codigo = 'PROT' AND td.codigo = 'SUSPENSAO_GOVERNO'"))
                    .containsExactly("Regra do backoffice");

            executar(ligacao, "DELETE FROM databasechangelog WHERE id LIKE '2026-11-12-11-%'");
            assertThatCode(() -> migrarOsProximos(ligacao, CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS)).doesNotThrowAnyException();
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "databasechangelog WHERE id LIKE '2026-11-12-11-%'")).isEqualTo(CHANGESETS_DAS_DINAMICAS_TRANSVERSAIS);
            assertThat(linhas(ligacao, RESUMO_DAS_DINAMICAS_SEM_IGT)).containsExactlyElementsOf(RESUMO_COM_AS_DINAMICAS_TRANSVERSAIS);
        });
    }

    private static final String INSTRUMENTO_DE_INICIO_ANULAVEL = "SELECT is_nullable FROM information_schema.columns "
            + "WHERE table_name = 'dinamica' AND column_name = 'instrumento_id'";

    @Test
    void deveTornarOInstrumentoDeInicioDaDinamicaOpcionalSemMudarAsDinamicasEReporONotNullNoRollback() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA + CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, INSTRUMENTO_DE_INICIO_ANULAVEL)).isEqualTo("NO");
            inserirProcessoComDocumento(ligacao);
            String dinamicas = "SELECT md5(string_agg(d::text, ',' ORDER BY d.id)) FROM dinamica d";
            String antes = texto(ligacao, dinamicas);

            migrarOsProximos(ligacao, CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, INSTRUMENTO_DE_INICIO_ANULAVEL)).isEqualTo("YES");
            assertThat(texto(ligacao, dinamicas)).isEqualTo(antes);

            // Sem Instrumento de início mas já com um abrangido: o rollback dá-lhe esse.
            executar(ligacao, "INSERT INTO dinamica (id, instrumento_dinamica_config_id, criado_por, descritivo, numero_dinamica) "
                    + "VALUES ('00000000-0000-0000-0000-000000000009', (SELECT min(id) FROM instrumento_dinamica_config), "
                    + "'00000000-0000-0000-0000-000000000001', 'Sem IGT de partida', '00009')");
            executar(ligacao, "INSERT INTO dinamica_instrumento (dinamica_id, instrumento_id) VALUES "
                    + "('00000000-0000-0000-0000-000000000009', '00000000-0000-0000-0000-000000000002')");
            reverterOsUltimos(ligacao, CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, INSTRUMENTO_DE_INICIO_ANULAVEL)).isEqualTo("NO");
            assertThat(texto(ligacao, "SELECT instrumento_id FROM dinamica WHERE id = '00000000-0000-0000-0000-000000000009'"))
                    .isEqualTo("00000000-0000-0000-0000-000000000002");

            migrarOsProximos(ligacao, CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO);
            ligacao.setAutoCommit(true);
            executar(ligacao, "INSERT INTO dinamica (id, instrumento_dinamica_config_id, criado_por, descritivo, numero_dinamica) "
                    + "VALUES ('00000000-0000-0000-0000-000000000010', (SELECT min(id) FROM instrumento_dinamica_config), "
                    + "'00000000-0000-0000-0000-000000000001', 'Sem nenhum Instrumento', '00010')");
            assertThatThrownBy(() -> reverterOsUltimos(ligacao, CHANGESETS_DA_DINAMICA_SEM_INSTRUMENTO_DE_INICIO))
                    .hasStackTraceContaining("Há Dinâmicas sem Instrumento de início nem Instrumentos abrangidos");
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, INSTRUMENTO_DE_INICIO_ANULAVEL)).isEqualTo("YES");
        });
    }

    @Test
    void deveGravarOCodigoIdusDaRenEDosIntermunicipaisEAEntidadeInstrutoraOpcionalNoPsERevertelos() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            String idus = "SELECT codigo || '=' || codigo_idus FROM tipo_instrumento WHERE codigo IN ('REN', 'PDIM', 'PUIM', 'PPIM') ORDER BY codigo";
            assertThat(linhas(ligacao, idus)).containsExactly("PDIM=53", "PPIM=55", "PUIM=54", "REN=37");
            assertThat(linhas(ligacao, "SELECT codigo FROM tipo_instrumento WHERE NOT entidade_instrutora_obrigatoria"))
                    .containsExactly("PS");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "tipo_instrumento WHERE codigo IN ('REN', 'PDIM', 'PUIM', 'PPIM') AND codigo_idus IS NOT NULL"))
                    .isZero();
            assertThat(contar(ligacao, "information_schema.columns WHERE table_name = 'tipo_instrumento' "
                    + "AND column_name = 'entidade_instrutora_obrigatoria'")).isZero();

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, idus)).containsExactly("PDIM=53", "PPIM=55", "PUIM=54", "REN=37");
        });
    }

    @Test
    void deveMarcarADesignacaoUnicaSoNasSuspensoesDaCcdrEDoGovernoENasMedidasPreventivasDoGovernoERemoverAColunaNoRollback()
            throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, "SELECT codigo FROM tipo_dinamica WHERE designacao_unica ORDER BY codigo"))
                    .containsExactly("MEDIDAS_PREVENTIVAS_GOVERNO", "SUSPENSAO_CCDR", "SUSPENSAO_GOVERNO");
            String outrasColunas = "SELECT md5(string_agg((to_jsonb(t) - 'designacao_unica')::text, ',' ORDER BY t.id)) "
                    + "FROM tipo_dinamica t WHERE t.codigo <> 'ALTERACAO_RERAE'";
            String antes = texto(ligacao, outrasColunas);

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD + CHANGESETS_DA_MATRIZ_NOS_PROGRAMAS + CHANGESETS_DO_IDUS_E_DA_ENTIDADE_INSTRUTORA_OBRIGATORIA + CHANGESETS_DA_DESIGNACAO_UNICA);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "information_schema.columns WHERE table_name = 'tipo_dinamica' "
                    + "AND column_name = 'designacao_unica'")).isZero();
            assertThat(texto(ligacao, outrasColunas)).isEqualTo(antes);

            migrarOsProximos(ligacao, CHANGESETS_DA_DESIGNACAO_UNICA);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "tipo_dinamica WHERE designacao_unica")).isEqualTo(3);
        });
    }

    @Test
    void deveCriarAsPreferenciasDoDashboardComAPkAFkAUnicaDaOrdemEOCheckEApagarANoRollback() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, "SELECT conname || ':' || contype::text FROM pg_constraint "
                    + "WHERE conrelid = 'dashboard_preferencia'::regclass ORDER BY conname")).containsExactly(
                    "ck_dashboard_preferencia_ordem:c", "fk_dashboard_preferencia_utilizador:f",
                    "pk_dashboard_preferencia:p", "uq_dashboard_preferencia_ordem:u");
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'pk_dashboard_preferencia'")).isEqualTo("PRIMARY KEY (utilizador_id, kpi_codigo)");
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'uq_dashboard_preferencia_ordem'")).isEqualTo("UNIQUE (utilizador_id, ordem)");
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'fk_dashboard_preferencia_utilizador'"))
                    .isEqualTo("FOREIGN KEY (utilizador_id) REFERENCES utilizador(id)");

            String utilizador = UUID.randomUUID().toString();
            executar(ligacao, "INSERT INTO utilizador (id, nome, email, origem_autenticacao, perfil_interno) "
                    + "VALUES ('" + utilizador + "', 'Master Dashboard', 'master.dashboard@dgt.pt', 'AD_DGT', 'MASTER')");
            String insercao = "INSERT INTO dashboard_preferencia (utilizador_id, kpi_codigo, visivel, ordem, atualizado_em) "
                    + "VALUES ('" + utilizador + "', ";
            executar(ligacao, insercao + "'TAXA_REJEICAO', true, 0, now())");
            assertThatThrownBy(() -> executar(ligacao, insercao + "'GRAFICO_MES', false, -1, now())"))
                    .hasMessageContaining("ck_dashboard_preferencia_ordem");
            assertThatThrownBy(() -> executar(ligacao, insercao + "'GRAFICO_MES', false, 0, now())"))
                    .hasMessageContaining("uq_dashboard_preferencia_ordem");
            assertThatThrownBy(() -> executar(ligacao, insercao + "'TAXA_REJEICAO', false, 1, now())"))
                    .hasMessageContaining("pk_dashboard_preferencia");
            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO dashboard_preferencia (utilizador_id, kpi_codigo, "
                    + "visivel, ordem, atualizado_em) VALUES (gen_random_uuid(), 'GRAFICO_MES', true, 1, now())"))
                    .hasMessageContaining("fk_dashboard_preferencia_utilizador");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS + CHANGESETS_DAS_PREFERENCIAS_DO_DASHBOARD);
            ligacao.setAutoCommit(true);
            assertThat(tabelaExiste(ligacao, "dashboard_preferencia")).isFalse();
        });
    }

    private static final String DOS_ATOS_PONTUAIS = "instrumento_procedimento_config ipc "
            + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE ipc.ato_pontual AND tp.codigo IN ("
            + CODIGOS_ATO_PONTUAL + ")";

    private static final String PERMISSOES_NOS_ATOS_PONTUAIS = "permissao_acao pa JOIN instrumento_procedimento_config ipc "
            + "ON ipc.id = pa.instrumento_procedimento_config_id JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id "
            + "WHERE ipc.ato_pontual AND tp.codigo IN (" + CODIGOS_ATO_PONTUAL + ")";

    private static final String RESTO_DA_CONFIGURACAO = "SELECT md5(concat_ws('|', "
            + "(SELECT string_agg(to_jsonb(ipc)::text, ',' ORDER BY ipc.id) FROM instrumento_procedimento_config ipc WHERE NOT ipc.ato_pontual), "
            + "(SELECT string_agg(to_jsonb(pa)::text, ',' ORDER BY pa.id) FROM permissao_acao pa "
            + "JOIN instrumento_procedimento_config ipc ON ipc.id = pa.instrumento_procedimento_config_id WHERE NOT ipc.ato_pontual), "
            + "(SELECT string_agg(to_jsonb(idc)::text, ',' ORDER BY idc.id) FROM instrumento_dinamica_config idc), "
            + "(SELECT string_agg(to_jsonb(tp)::text, ',' ORDER BY tp.id) FROM tipo_procedimento tp), "
            + "(SELECT string_agg(to_jsonb(d)::text, ',' ORDER BY d.id) FROM documento_config d), "
            + "(SELECT string_agg(to_jsonb(r)::text, ',' ORDER BY r.id) FROM regra r), "
            + "(SELECT string_agg(to_jsonb(c)::text, ',' ORDER BY c.id) FROM condicao c)))";

    @Test
    void deveReverterSoOsAtosPontuaisEVoltarASemeaLosComORestoDaConfiguracaoIgual() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11);
            ligacao.setAutoCommit(true);
            String antes = texto(ligacao, RESTO_DA_CONFIGURACAO);
            // O rollback da retirada da RERAE repõe as Dinâmicas RERAE de PDM, PP e PU, mas não os atos pontuais delas.
            assertThat(contar(ligacao, DOS_ATOS_PONTUAIS)).isEqualTo(PROCEDIMENTOS_DOS_ATOS_PONTUAIS - ATOS_PONTUAIS_DA_RERAE);
            assertThat(contar(ligacao, PERMISSOES_NOS_ATOS_PONTUAIS))
                    .isEqualTo(PERMISSOES_DOS_ATOS_PONTUAIS - PERMISSOES_DOS_ATOS_PONTUAIS_DA_RERAE);

            reverterOsUltimos(ligacao, CHANGESETS_DOS_ATOS_PONTUAIS);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, DOS_ATOS_PONTUAIS)).isZero();
            assertThat(contar(ligacao, PERMISSOES_NOS_ATOS_PONTUAIS)).isZero();
            // Os 6 Procedimentos RERAE dos intermunicipais (2 permissões cada) não voltam com o rollback da retirada.
            assertThat(contar(ligacao, "instrumento_procedimento_config")).isEqualTo(256 - 6);
            assertThat(contar(ligacao, "permissao_acao")).isEqualTo(586 - 12);
            assertThat(texto(ligacao, RESTO_DA_CONFIGURACAO)).isEqualTo(antes);

            migrarOsProximos(ligacao, CHANGESETS_DOS_ATOS_PONTUAIS);
            ligacao.setAutoCommit(true);
            // De novo, o seed dá-os também às três Dinâmicas RERAE repostas; as dos intermunicipais não voltam.
            assertThat(contar(ligacao, DOS_ATOS_PONTUAIS))
                    .isEqualTo(PROCEDIMENTOS_DOS_ATOS_PONTUAIS - ATOS_PONTUAIS_DA_RERAE_DOS_INTERMUNICIPAIS);
            assertThat(contar(ligacao, PERMISSOES_NOS_ATOS_PONTUAIS))
                    .isEqualTo(PERMISSOES_DOS_ATOS_PONTUAIS - 2 * ATOS_PONTUAIS_DA_RERAE_DOS_INTERMUNICIPAIS);
            assertThat(texto(ligacao, RESTO_DA_CONFIGURACAO)).isEqualTo(antes);
        });
    }

    @Test
    void deveSemearOsAtosPontuaisSemFalharNemDuplicarNemMexerNumParQueJaExistia() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE + CHANGESETS_DOS_ITENS_TEMPORARIOS_E_RATIFICACAO_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DE_PU_PP_E_INTERMUNICIPAIS_DA_LISTA_DGT + CHANGESETS_DOS_ITENS_DO_PDM_DA_LISTA_DGT + CHANGESETS_DA_NUT_SO_DA_REPLICA + CHANGESETS_DO_FEEDBACK_DE_13_11 + CHANGESETS_DOS_ATOS_PONTUAIS);
            ligacao.setAutoCommit(true);
            String doBackoffice = "(SELECT id FROM instrumento_procedimento_config WHERE instrumento_dinamica_config_id = 101 "
                    + "AND tipo_procedimento_id = (SELECT id FROM tipo_procedimento WHERE codigo = 'ALTERACAO_ATO_ANEXOS'))";
            executar(ligacao, "INSERT INTO instrumento_procedimento_config (instrumento_dinamica_config_id, tipo_procedimento_id, "
                    + "ordem, obrigatorio, serie_dr, ativo, tamanho_maximo_processo, repetivel, ato_pontual) SELECT 101, id, 50, "
                    + "false, 'SEGUNDA_H', true, 1000, true, true FROM tipo_procedimento WHERE codigo = 'ALTERACAO_ATO_ANEXOS'");
            executar(ligacao, "INSERT INTO permissao_acao (tipo_entidade, instrumento_procedimento_config_id, ativo) "
                    + "SELECT 'DGT', " + doBackoffice + ", true");

            assertThatCode(() -> migrarOsProximos(ligacao, CHANGESETS_DOS_ATOS_PONTUAIS)).doesNotThrowAnyException();
            ligacao.setAutoCommit(true);

            assertThat(linhas(ligacao, "SELECT tp.codigo || ':' || ipc.ordem || ':' || ipc.repetivel || ':' "
                    + "|| ipc.tamanho_maximo_processo FROM " + DOS_ATOS_PONTUAIS
                    + " AND ipc.instrumento_dinamica_config_id = 101 ORDER BY ipc.ordem"))
                    .containsExactly("ALTERACAO_ATO_ANEXOS:50:true:1000", "RETIFICACAO_ATO_ANEXOS:102:false:5368709120",
                            "REVOGACAO_ATO:103:false:5368709120", "PRORROGACAO_ATO:104:false:5368709120",
                            "DECLARACAO_NULIDADE_JUDICIAL:105:false:5368709120");
            assertThat(linhas(ligacao, "SELECT tipo_entidade FROM permissao_acao WHERE instrumento_procedimento_config_id = "
                    + doBackoffice)).containsExactly("DGT");
            // As Dinâmicas RERAE dos intermunicipais não voltam com o rollback da retirada.
            assertThat(contar(ligacao, DOS_ATOS_PONTUAIS))
                    .isEqualTo(PROCEDIMENTOS_DOS_ATOS_PONTUAIS - ATOS_PONTUAIS_DA_RERAE_DOS_INTERMUNICIPAIS);
            assertThat(contar(ligacao, PERMISSOES_NOS_ATOS_PONTUAIS))
                    .isEqualTo(PERMISSOES_DOS_ATOS_PONTUAIS - 2 * ATOS_PONTUAIS_DA_RERAE_DOS_INTERMUNICIPAIS - 1);
        });
    }

    private static final String PCM_NA_PRIMEIRA_SERIE = "permissao_acao pa JOIN instrumento_procedimento_config ipc "
            + "ON ipc.id = pa.instrumento_procedimento_config_id WHERE pa.tipo_entidade = 'PCM' AND ipc.serie_dr = 'PRIMEIRA'";

    private static final String INSERCAO_PCM_NA_PRIMEIRA_SERIE = "INSERT INTO permissao_acao (tipo_entidade, "
            + "instrumento_procedimento_config_id, ativo) SELECT 'PCM', c.id, true FROM instrumento_procedimento_config c "
            + "WHERE c.serie_dr = 'PRIMEIRA' AND NOT EXISTS (SELECT 1 FROM permissao_acao p "
            + "WHERE p.instrumento_procedimento_config_id = c.id AND p.tipo_entidade = 'PCM')";

    @Test
    void cadaProcedimentoDaPrimeiraSerieDeveTerExatamenteUmaLinhaPcmAtivaENenhumDeOutraSerie() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            int primeiraSerie = contar(ligacao, "instrumento_procedimento_config WHERE serie_dr = 'PRIMEIRA'");
            assertThat(primeiraSerie).isEqualTo(PERMISSOES_DA_PCM_NA_PRIMEIRA_SERIE);
            assertThat(contar(ligacao, "instrumento_procedimento_config c WHERE c.serie_dr = 'PRIMEIRA' AND (SELECT count(*) "
                    + "FROM permissao_acao p WHERE p.instrumento_procedimento_config_id = c.id AND p.tipo_entidade = 'PCM' "
                    + "AND p.ativo) <> 1")).isZero();
            assertThat(contar(ligacao, PCM_NA_PRIMEIRA_SERIE)).isEqualTo(primeiraSerie);
            assertThat(contar(ligacao, "permissao_acao pa JOIN instrumento_procedimento_config ipc "
                    + "ON ipc.id = pa.instrumento_procedimento_config_id WHERE pa.tipo_entidade = 'PCM' "
                    + "AND ipc.serie_dr IS DISTINCT FROM 'PRIMEIRA'")).isZero();
            assertThat(linhas(ligacao, "SELECT DISTINCT ti.codigo FROM " + PCM_NA_PRIMEIRA_SERIE.replace(" WHERE ",
                    " JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ") + " ORDER BY 1"))
                    .contains("PNPOT", "PS", "PROT", "PEOT", "PDM").doesNotContain("PRA", "PSA");

            executar(ligacao, INSERCAO_PCM_NA_PRIMEIRA_SERIE);
            assertThat(contar(ligacao, PCM_NA_PRIMEIRA_SERIE)).isEqualTo(primeiraSerie);
        });
    }

    @Test
    void deveSemearAsLinhasPcmSemFalharNemDuplicarNemMexerNumaQueJaExistia() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'PCM'")).isZero();
            String primeira = "(SELECT min(id) FROM instrumento_procedimento_config WHERE serie_dr = 'PRIMEIRA')";
            executar(ligacao, "INSERT INTO permissao_acao (tipo_entidade, instrumento_procedimento_config_id, ativo) "
                    + "SELECT 'PCM', " + primeira + ", false");
            String existente = texto(ligacao, "SELECT id || ':' || ativo FROM permissao_acao WHERE tipo_entidade = 'PCM'");

            assertThatCode(() -> migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE)).doesNotThrowAnyException();
            ligacao.setAutoCommit(true);

            assertThat(contar(ligacao, PCM_NA_PRIMEIRA_SERIE)).isEqualTo(PERMISSOES_DA_PCM_NA_PRIMEIRA_SERIE);
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'PCM' AND instrumento_procedimento_config_id = "
                    + primeira)).isEqualTo(1);
            assertThat(texto(ligacao, "SELECT id || ':' || ativo FROM permissao_acao WHERE tipo_entidade = 'PCM' "
                    + "AND instrumento_procedimento_config_id = " + primeira)).isEqualTo(existente);
        });
    }

    @Test
    void deveReverterSoAsLinhasPcmDaPrimeiraSerieEVoltarASemeaLas() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            executar(ligacao, "INSERT INTO permissao_acao (tipo_entidade, instrumento_procedimento_config_id, ativo) "
                    + "SELECT 'PCM', min(id), true FROM instrumento_procedimento_config WHERE serie_dr = 'SEGUNDA_H'");
            // A SEC_ESTADO desativada no PROT e a Tentativa do processo, posteriores, saem antes da fotografia.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR);
            ligacao.setAutoCommit(true);
            // As restantes servidões, semeadas depois, também saem no rollback.
            String restoAntes = texto(ligacao, "SELECT md5(string_agg(to_jsonb(pa)::text, ',' ORDER BY pa.id)) "
                    + "FROM permissao_acao pa WHERE pa.id NOT IN (SELECT pa.id FROM " + PCM_NA_PRIMEIRA_SERIE + ") "
                    + "AND pa.instrumento_procedimento_config_id NOT IN (SELECT ipc.id FROM instrumento_procedimento_config ipc "
                    + DO_TIPO_SERVIDAO + ")");
            int permissoes = contar(ligacao, "permissao_acao");

            reverterOsUltimos(ligacao, CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, PCM_NA_PRIMEIRA_SERIE)).isZero();
            assertThat(contar(ligacao, "permissao_acao WHERE tipo_entidade = 'PCM'")).isEqualTo(1);
            assertThat(contar(ligacao, "permissao_acao")).isEqualTo(permissoes - PERMISSOES_DA_PCM_NA_PRIMEIRA_SERIE
                    - PERMISSOES_DAS_RESTANTES_SERVIDOES);
            assertThat(texto(ligacao, "SELECT md5(string_agg(to_jsonb(pa)::text, ',' ORDER BY pa.id)) FROM permissao_acao pa"))
                    .isEqualTo(restoAntes);

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL + CHANGESETS_DA_PCM_NA_PRIMEIRA_SERIE);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, PCM_NA_PRIMEIRA_SERIE)).isEqualTo(PERMISSOES_DA_PCM_NA_PRIMEIRA_SERIE);
        });
    }


    /** Os itens da Correção Material de PDM, PP e PU, sem a coluna dos independentes, que o revert dela tira. */
    private static final String ITENS_DA_CORRECAO_MATERIAL_SEM_A_COLUNA = "SELECT md5(string_agg((to_jsonb(d) "
            + "- 'independente_elementos_afetados')::text, ',' ORDER BY d.id)) FROM documento_config d "
            + "JOIN instrumento_procedimento_config c ON c.id = d.instrumento_procedimento_config_id "
            + "JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id "
            + "JOIN instrumento_dinamica_config idc ON idc.id = c.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "WHERE t.codigo = 'CORRECAO_MATERIAL' AND ti.codigo IN ('PDM', 'PP', 'PU')";

    /** Os seeds AS-IS do build que a lista da DGT substitui nos Procedimentos que cobre (FR-193). */
    private static final String SEEDS_DO_BUILD_SUBSTITUIDOS_PELA_LISTA = "databasechangelog WHERE id IN ("
            + "'2026-11-13-3-seed-documento-config-correcao-material', '2026-11-13-10-seed-documento-config-ratificacao', "
            + "'2026-11-13-11-seed-documento-config-suspensao-ccdr', '2026-11-14-2-seed-documento-config-suspensao-mp-governo')";

    @Test
    void deveFicarSoComAListaDaDgtNaCorrecaoMaterialNaRatificacaoNaSuspensaoDaCcdrENoGovernoSemOsSeedsDoBuild()
            throws Exception {
        // A lista da DGT prevalece (FR-193): sem provas de notificação, sem as peças gráficas da Ratificação e sem os
        // itens AS-IS da Suspensão da CCDR e do Governo. A coluna dos independentes fica, falsa, para o backoffice.
        comBaseMigrada(ligacao -> {
            assertThat(contar(ligacao, SEEDS_DO_BUILD_SUBSTITUIDOS_PELA_LISTA)).isZero();
            assertThat(contar(ligacao, "documento_config WHERE codigo IN ('PROVA_NOTIFICACAO_AM_DEP', "
                    + "'PROVA_NOTIFICACAO_CCDR_DEP', 'DECLARACAO_SUSPENSAO_PUB', 'DECLARACAO_SUSPENSAO_DEP', "
                    + "'PLANTA_AREA_SUSPENSAO_PUB', 'PLANTA_AREA_SUSPENSAO_DEP', 'ANEXO_SUSPENSAO_PUB', 'ANEXO_SUSPENSAO_DEP') "
                    + "OR codigo LIKE '%\\_GOV\\_PUB' OR codigo LIKE '%\\_GOV\\_DEP'")).isZero();
            assertThat(contar(ligacao, "documento_config d JOIN instrumento_procedimento_config c "
                    + "ON c.id = d.instrumento_procedimento_config_id JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id "
                    + "WHERE t.codigo = 'RATIFICACAO' AND d.codigo LIKE 'PECAS\\_GRAFICAS%'")).isZero();
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || count(*) FROM documento_config d "
                    + "JOIN instrumento_procedimento_config c ON c.id = d.instrumento_procedimento_config_id "
                    + "JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = c.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE t.codigo = 'CORRECAO_MATERIAL' "
                    + "AND ti.codigo IN ('PDM', 'PP', 'PU') AND d.ativo AND d.pagina = 'DOCUMENTOS' GROUP BY ti.codigo"))
                    .containsExactlyInAnyOrder("PDM:13", "PP:13", "PU:13");
            assertThat(texto(ligacao, "SELECT data_type || ':' || is_nullable || ':' || column_default "
                    + "FROM information_schema.columns WHERE table_name = 'documento_config' "
                    + "AND column_name = 'independente_elementos_afetados'")).isEqualTo("boolean:NO:false");
            assertThat(contar(ligacao, "documento_config WHERE independente_elementos_afetados")).isZero();
        });
    }


    @Test
    void deveReverterEVoltarACriarAColunaUrlGeoconformDaDinamica() throws Exception {
        comBaseMigrada(ligacao -> {
            assertThat(colunaExiste(ligacao, "dinamica", "url_geoconform")).isTrue();

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "dinamica", "url_geoconform")).isFalse();
            assertThat(colunaExiste(ligacao, "dinamica", "com_geoconform")).isTrue();

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "dinamica", "url_geoconform")).isTrue();
            assertThat(texto(ligacao, "SELECT data_type FROM information_schema.columns WHERE table_name = 'dinamica' "
                    + "AND column_name = 'url_geoconform'")).isEqualTo("text");
            assertThat(texto(ligacao, "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'dinamica' "
                    + "AND column_name = 'url_geoconform'")).isEqualTo("YES");
        });
    }

    @Test
    void deveReverterAColunaDosIndependentesSemMexerNosItensDaCorrecaoMaterialEVoltarACriala() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            String itensAntes = texto(ligacao, ITENS_DA_CORRECAO_MATERIAL_SEM_A_COLUNA);

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "documento_config", "independente_elementos_afetados")).isFalse();
            assertThat(texto(ligacao, ITENS_DA_CORRECAO_MATERIAL_SEM_A_COLUNA)).isEqualTo(itensAntes);

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO + CHANGESETS_DO_URL_GEOCONFORM + CHANGESETS_DA_CORRECAO_MATERIAL);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "documento_config WHERE independente_elementos_afetados")).isZero();
            assertThat(texto(ligacao, ITENS_DA_CORRECAO_MATERIAL_SEM_A_COLUNA)).isEqualTo(itensAntes);
        });
    }


    @Test
    void deveCriarAServidaoDeOrigemNulaComFkIndiceEAutoReferenciaRecusadaERevertelaPorCompleto() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, "SELECT data_type || ':' || is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'instrumento' AND column_name = 'instrumento_origem_id'")).isEqualTo("uuid:YES");
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'fk_instrumento_instrumento_origem'"))
                    .isEqualTo("FOREIGN KEY (instrumento_origem_id) REFERENCES instrumento(id)");
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'ck_instrumento_origem_diferente'"))
                    .contains("instrumento_origem_id IS NULL").contains("instrumento_origem_id <> id");
            assertThat(definicaoDoIndice(ligacao, "idx_instrumento_instrumento_origem_id"))
                    .contains("ON public.instrumento").contains("(instrumento_origem_id)");

            executar(ligacao, "INSERT INTO utilizador (id, email, origem_autenticacao) "
                    + "VALUES ('00000000-0000-0000-0000-000000000265', 'autor265@cm-teste.pt', 'INCM')");
            for (String id : List.of("00000000-0000-0000-0000-000000002651", "00000000-0000-0000-0000-000000002652")) {
                executar(ligacao, "INSERT INTO instrumento (id, tipo_instrumento_id, numero, designacao, responsavel_id, "
                        + "criado_por) VALUES ('" + id + "', (SELECT min(id) FROM tipo_instrumento), '" + id.substring(30)
                        + "', 'Servidão', '00000000-0000-0000-0000-000000000265', '00000000-0000-0000-0000-000000000265')");
            }
            assertThat(contar(ligacao, "instrumento WHERE instrumento_origem_id IS NOT NULL")).isZero();
            assertThatThrownBy(() -> executar(ligacao, "UPDATE instrumento SET instrumento_origem_id = id "
                    + "WHERE id = '00000000-0000-0000-0000-000000002651'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_instrumento_origem_diferente");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE instrumento SET instrumento_origem_id = "
                    + "'00000000-0000-0000-0000-000000009999' WHERE id = '00000000-0000-0000-0000-000000002651'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("fk_instrumento_instrumento_origem");
            executar(ligacao, "UPDATE instrumento SET instrumento_origem_id = '00000000-0000-0000-0000-000000002652' "
                    + "WHERE id = '00000000-0000-0000-0000-000000002651'");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "instrumento", "instrumento_origem_id")).isFalse();
            assertThat(contar(ligacao, "pg_constraint WHERE conname IN ('fk_instrumento_instrumento_origem', "
                    + "'ck_instrumento_origem_diferente')")).isZero();
            assertThat(definicaoDoIndice(ligacao, "idx_instrumento_instrumento_origem_id")).isNull();
            assertThat(colunaExiste(ligacao, "dinamica", "url_geoconform")).isTrue();
            assertThat(contar(ligacao, "instrumento WHERE designacao = 'Servidão'")).isEqualTo(2);

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA + CHANGESETS_DA_ORIGEM_DO_INSTRUMENTO);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "instrumento", "instrumento_origem_id")).isTrue();
            assertThat(contar(ligacao, "instrumento WHERE instrumento_origem_id IS NOT NULL")).isZero();
        });
    }

    private static final String PARES_ASSOCIAVEIS = "SELECT string_agg(o.codigo || '>' || d.codigo, ',' ORDER BY d.codigo) "
            + "FROM tipo_instrumento_associavel a JOIN tipo_instrumento o ON o.id = a.tipo_instrumento_id "
            + "JOIN tipo_instrumento d ON d.id = a.tipo_instrumento_associavel_id";
    private static final String DINAMICAS_QUE_ADMITEM = "SELECT string_agg(ti.codigo || ':' || td.codigo, ',' ORDER BY td.codigo) "
            + "FROM instrumento_dinamica_config idc JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id WHERE idc.admite_associacao_igt";

    @Test
    void deveCriarAAssociacaoDaRenComFkIndiceETabelaSemeadaERevertelaPorCompleto() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, "SELECT data_type || ':' || is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'processo' AND column_name = 'dinamica_associada_id'")).isEqualTo("uuid:YES");
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'fk_processo_dinamica_associada'"))
                    .isEqualTo("FOREIGN KEY (dinamica_associada_id) REFERENCES dinamica(id)");
            assertThat(definicaoDoIndice(ligacao, "idx_processo_dinamica_associada"))
                    .contains("ON public.processo").contains("(dinamica_associada_id)");
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname = 'pk_tipo_instrumento_associavel'"))
                    .isEqualTo("PRIMARY KEY (tipo_instrumento_id, tipo_instrumento_associavel_id)");
            assertThat(contar(ligacao, "pg_constraint WHERE conname IN ('fk_tipo_instrumento_associavel_origem', "
                    + "'fk_tipo_instrumento_associavel_destino', 'ck_tipo_instrumento_associavel_distintos')")).isEqualTo(3);
            assertThat(texto(ligacao, "SELECT data_type || ':' || is_nullable || ':' || column_default "
                    + "FROM information_schema.columns WHERE table_name = 'instrumento_dinamica_config' "
                    + "AND column_name = 'admite_associacao_igt'")).isEqualTo("boolean:NO:false");
            assertThat(texto(ligacao, PARES_ASSOCIAVEIS)).isEqualTo("REN>PDM,REN>PEOT,REN>PP,REN>PU");
            assertThat(texto(ligacao, DINAMICAS_QUE_ADMITEM)).isEqualTo("REN:ALTERACAO,REN:DELIMITACAO");
            assertThatThrownBy(() -> executar(ligacao, "INSERT INTO tipo_instrumento_associavel VALUES "
                    + "((SELECT id FROM tipo_instrumento WHERE codigo = 'REN'), (SELECT id FROM tipo_instrumento WHERE codigo = 'REN'))"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_tipo_instrumento_associavel_distintos");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + 1);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "tipo_instrumento_associavel")).isZero();
            assertThat(contar(ligacao, "instrumento_dinamica_config WHERE admite_associacao_igt")).isZero();

            reverterOsUltimos(ligacao, 1);
            ligacao.setAutoCommit(true);
            assertThat(tabelaExiste(ligacao, "tipo_instrumento_associavel")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "dinamica_associada_id")).isFalse();
            assertThat(colunaExiste(ligacao, "instrumento_dinamica_config", "admite_associacao_igt")).isFalse();
            assertThat(definicaoDoIndice(ligacao, "idx_processo_dinamica_associada")).isNull();
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'fk_processo_dinamica_associada'")).isZero();
            assertThat(colunaExiste(ligacao, "instrumento", "instrumento_origem_id")).isTrue();

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO + CHANGESETS_DA_DINAMICA_ASSOCIADA);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, PARES_ASSOCIAVEIS)).isEqualTo("REN>PDM,REN>PEOT,REN>PP,REN>PU");
            assertThat(texto(ligacao, DINAMICAS_QUE_ADMITEM)).isEqualTo("REN:ALTERACAO,REN:DELIMITACAO");
        });
    }

    @Test
    void deveCriarODestinoComPublicacaoEDepositoNosProcessosExistentesConstraintERollback() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP + CHANGESETS_DO_DESTINO_DO_PROCESSO);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "destino")).isFalse();
            inserirProcessoComDocumento(ligacao);

            migrarOsProximos(ligacao, CHANGESETS_DO_DESTINO_DO_PROCESSO);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, "SELECT data_type || ':' || character_maximum_length || ':' || is_nullable "
                    + "FROM information_schema.columns WHERE table_name = 'processo' AND column_name = 'destino'"))
                    .isEqualTo("character varying:20:NO");
            assertThat(contar(ligacao, "processo")).isPositive();
            assertThat(contar(ligacao, "processo WHERE destino <> 'PUBLICACAO_DEPOSITO'")).isZero();
            assertThat(texto(ligacao, "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_processo_destino'"))
                    .contains("PUBLICACAO_DEPOSITO").contains("SO_DEPOSITO");
            executar(ligacao, "UPDATE processo SET destino = 'SO_DEPOSITO' WHERE id = '" + PROCESSO_ID + "'");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE processo SET destino = 'OUTRO' WHERE id = '" + PROCESSO_ID + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_processo_destino");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE processo SET destino = NULL WHERE id = '" + PROCESSO_ID + "'"))
                    .isInstanceOf(SQLException.class);

            reverterOsUltimos(ligacao, CHANGESETS_DO_DESTINO_DO_PROCESSO);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "destino")).isFalse();
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'ck_processo_destino'")).isZero();
            assertThat(colunaExiste(ligacao, "processo", "dinamica_associada_id")).isTrue();
        });
    }

    @Test
    void deveSemearOTipoDeProcedimentoDasRestantesServidoesSemConfiguracaoERevertelo() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, "SELECT codigo || ':' || nome || ':' || ativo FROM tipo_procedimento "
                    + "WHERE codigo = 'PUBLICACAO_SRUP'")).isEqualTo("PUBLICACAO_SRUP:Publicação da servidão:true");
            // Só as configurações do tipo SERVIDAO, semeadas depois, usam o Procedimento.
            assertThat(linhas(ligacao, "SELECT DISTINCT ti.codigo FROM instrumento_procedimento_config c JOIN tipo_procedimento t "
                    + "ON t.id = c.tipo_procedimento_id JOIN instrumento_dinamica_config idc ON idc.id = c.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE t.codigo = 'PUBLICACAO_SRUP'"))
                    .containsExactly("SERVIDAO");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "tipo_procedimento WHERE codigo = 'PUBLICACAO_SRUP'")).isZero();
            assertThat(contar(ligacao, "tipo_procedimento")).isEqualTo(32);
            assertThat(colunaExiste(ligacao, "processo", "destino")).isTrue();

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR + CHANGESETS_DO_TIPO_PROCEDIMENTO_SRUP);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "tipo_procedimento WHERE codigo = 'PUBLICACAO_SRUP' AND ativo")).isEqualTo(1);
        });
    }


    /** Os itens dos programas e dos programas intermunicipais pela lista da DGT (2026-11-14-1). */
    private static final int ITENS_DOS_PROGRAMAS_DA_LISTA_DGT = 422;

    private static final String SEM_ITENS_DOS_PROGRAMAS = "instrumento_procedimento_config_id NOT IN (SELECT c.id "
            + "FROM instrumento_procedimento_config c JOIN instrumento_dinamica_config idc ON idc.id = c.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ti.codigo IN ('PNPOT', 'PS', 'PROT', 'PEOT', 'PIMOT'))";

    private static final String ITENS_SGIFR = "documento_config d JOIN instrumento_procedimento_config c "
            + "ON c.id = d.instrumento_procedimento_config_id JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id "
            + "JOIN instrumento_dinamica_config idc ON idc.id = c.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id WHERE t.codigo = 'PUBLICACAO_SGIFR'";

    /** A carta RPFGC nos dois Procedimentos do PRA e as seis cartas do PSA nos dois do PSA, por caixa. */
    private static final int PECAS_GRAFICAS_DO_SGIFR = 28;

    private static final String CODIGOS_DO_PRA = "TEXTO_ATO_PUB,TEXTO_ATO_DEP,CARTA_RPFGC_PUB,CARTA_RPFGC_DEP,"
            + "PROGRAMA_PUB,PROGRAMA_DEP";

    private static final String CODIGOS_DO_PSA = "TEXTO_ATO_PUB,TEXTO_ATO_DEP,CARTA_RSFGC_PUB,CARTA_RSFGC_DEP,"
            + "CARTA_APPS_PUB,CARTA_APPS_DEP,CARTA_RVDI_PUB,CARTA_RVDI_DEP,CARTA_AEMGC_PUB,CARTA_AEMGC_DEP,"
            + "CARTA_RVF_PUB,CARTA_RVF_DEP,CARTA_RPA_PUB,CARTA_RPA_DEP,PROGRAMA_PUB,PROGRAMA_DEP";

    private static final String EXTENSOES_ESCRITA_PUBLICACAO = "[\"doc\", \"docx\", \"rtf\", \"odf\", \"odt\", \"xls\", "
            + "\"xlsx\", \"csv\", \"ods\", \"pdf\", \"txt\"]";

    @Test
    void deveSemearOsItensDoSgifrDepoisDosSeedsDeQueDependeComOsCodigosPelaOrdemDaEf() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || td.codigo || ':' || count(*) FROM " + ITENS_SGIFR
                    + " GROUP BY ti.codigo, td.codigo ORDER BY ti.codigo, td.codigo")).containsExactly(
                    "PRA:ALTERACAO:6", "PRA:DELIMITACAO:6", "PSA:ALTERACAO:16", "PSA:DELIMITACAO:16");
            assertThat(contar(ligacao, ITENS_SGIFR)).isEqualTo(ITENS_DO_SGIFR);
            assertThat(contar(ligacao, "documento_config WHERE regra_nomenclatura = 'SGIFR'")).isEqualTo(ITENS_DO_SGIFR);

            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || td.codigo || ':' || string_agg(d.codigo, ',' ORDER BY d.ordem) "
                    + "FROM " + ITENS_SGIFR + " GROUP BY ti.codigo, td.codigo ORDER BY ti.codigo, td.codigo")).containsExactly(
                    "PRA:ALTERACAO:" + CODIGOS_DO_PRA, "PRA:DELIMITACAO:" + CODIGOS_DO_PRA,
                    "PSA:ALTERACAO:" + CODIGOS_DO_PSA, "PSA:DELIMITACAO:" + CODIGOS_DO_PSA);
            // A Delimitação e a Alteração têm os mesmos itens por tipo de programa, com a ordem sequencial a partir de 1.
            assertThat(linhas(ligacao, "SELECT DISTINCT ti.codigo || ':' || string_agg(d.ordem || '/' || d.codigo || '/' "
                    + "|| d.separador || '/' || d.nome || '/' || d.categoria || '/' || d.tipo_elemento || '/' || d.formato, '|' "
                    + "ORDER BY d.ordem) FROM " + ITENS_SGIFR + " GROUP BY ti.codigo, td.codigo")).hasSize(2);
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || td.codigo || ':' || min(d.ordem) || '-' || max(d.ordem) "
                    + "|| '-' || count(DISTINCT d.ordem) FROM " + ITENS_SGIFR + " GROUP BY ti.codigo, td.codigo "
                    + "ORDER BY ti.codigo, td.codigo")).containsExactly("PRA:ALTERACAO:1-6-6", "PRA:DELIMITACAO:1-6-6",
                    "PSA:ALTERACAO:1-16-16", "PSA:DELIMITACAO:1-16-16");
            assertThat(linhas(ligacao, "SELECT DISTINCT d.separador || ':' || d.nome FROM " + ITENS_SGIFR
                    + " AND ti.codigo = 'PRA' AND d.categoria = 'PUBLICACAO' ORDER BY 1")).containsExactly(
                    "Carta do Programa — RPFGC:Carta do programa: rede primária de faixas de gestão de combustível",
                    "Programa:Documento do Programa Regional de Ação",
                    "Texto do Ato:Texto legal do ato que será publicado no Diário da República");

            String ordemDe = "SELECT orderexecuted FROM databasechangelog WHERE id = '%s'";
            int ordemDoSeed = Integer.parseInt(texto(ligacao, ordemDe.formatted("2026-11-13-12-seed-documento-config-sgifr")));
            for (String dependencia : List.of("2026-11-12-2-seed-config-sgifr", "2026-11-12-3-add-regra-nomenclatura-documento-config",
                    "2026-11-12-4-seed-limite-processo-sgifr", "2026-11-11-14-add-regras-tiff-documento-config")) {
                assertThat(Integer.parseInt(texto(ligacao, ordemDe.formatted(dependencia)))).isLessThan(ordemDoSeed);
            }
        });
    }

    @Test
    void deveTerUmaCartaPorRedePublicavelDoCatalogoSemARtfgc() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            String cartas = "SELECT DISTINCT ti.codigo || ':' || replace(replace(replace(d.codigo, 'CARTA_', ''), '_PUB', ''), "
                    + "'_DEP', '') FROM " + ITENS_SGIFR + " AND d.codigo LIKE 'CARTA%' ORDER BY 1";
            assertThat(linhas(ligacao, cartas)).containsExactly("PRA:RPFGC", "PSA:AEMGC", "PSA:APPS", "PSA:RPA",
                    "PSA:RSFGC", "PSA:RVDI", "PSA:RVF");
            assertThat(linhas(ligacao, "SELECT 'PRA:' || codigo FROM sgifr_rede_defesa WHERE aplica_pra UNION ALL "
                    + "SELECT 'PSA:' || codigo FROM sgifr_rede_defesa WHERE aplica_psa ORDER BY 1"))
                    .isEqualTo(linhas(ligacao, cartas));
            assertThat(contar(ligacao, ITENS_SGIFR + " AND d.codigo LIKE '%RTFGC%'")).isZero();
            assertThat(contar(ligacao, ITENS_SGIFR + " AND d.codigo LIKE 'CARTA%' AND d.tipo_elemento <> 'PECA_GRAFICA'"))
                    .isZero();
        });
    }

    @Test
    void deveExigirSoOTextoDoAtoACartaRpfgcEOProgramaNaPublicacao() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, "SELECT ti.codigo || ':' || td.codigo || ':' || string_agg(d.codigo, ',' ORDER BY d.ordem) "
                    + "FROM " + ITENS_SGIFR + " AND d.obrigatorio GROUP BY ti.codigo, td.codigo ORDER BY ti.codigo, td.codigo"))
                    .containsExactly("PRA:ALTERACAO:TEXTO_ATO_PUB,CARTA_RPFGC_PUB,PROGRAMA_PUB",
                            "PRA:DELIMITACAO:TEXTO_ATO_PUB,CARTA_RPFGC_PUB,PROGRAMA_PUB",
                            "PSA:ALTERACAO:TEXTO_ATO_PUB,PROGRAMA_PUB", "PSA:DELIMITACAO:TEXTO_ATO_PUB,PROGRAMA_PUB");
            assertThat(contar(ligacao, ITENS_SGIFR + " AND d.obrigatorio AND (d.categoria = 'DEPOSITO' "
                    + "OR (ti.codigo = 'PSA' AND d.codigo LIKE 'CARTA%'))")).isZero();
        });
    }

    @Test
    void deveDarAosItensDoSgifrARegraDeNomesOsLimitesEAsRegrasTiffDoSgifr() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, ITENS_SGIFR + " AND d.regra_nomenclatura = 'SGIFR' AND d.tamanho_maximo_ficheiro = 524288000 "
                    + "AND d.numero_maximo_ficheiros = 10 AND d.tamanho_maximo_total = 2147483648 "
                    + "AND d.elemento_informacao_base IS NULL AND d.ativo AND d.pagina = 'DOCUMENTOS'")).isEqualTo(ITENS_DO_SGIFR);

            assertThat(contar(ligacao, ITENS_SGIFR + " AND d.tipo_elemento = 'PECA_GRAFICA' AND d.regras_tiff = "
                    + regrasTiffIniciaisSgifr())).isEqualTo(PECAS_GRAFICAS_DO_SGIFR);
            assertThat(linhas(ligacao, "SELECT DISTINCT (d.regras_tiff ->> 'resolucaoDpi') || '|' || (d.regras_tiff ->> 'modeloCor') "
                    + "|| '|' || (d.regras_tiff ->> 'bitsPorBanda') || '|' || (d.regras_tiff ->> 'compressoesAceites') FROM "
                    + ITENS_SGIFR + " AND d.tipo_elemento = 'PECA_GRAFICA'")).containsExactly("300|RGB|8|[\"NONE\"]");
            assertThat(contar(ligacao, ITENS_SGIFR + " AND d.tipo_elemento <> 'PECA_GRAFICA' AND d.regras_tiff IS NOT NULL"))
                    .isZero();

            // Uma linha de cada combinação caixa × tipo: extensões, formato e link público.
            assertThat(linhas(ligacao, "SELECT DISTINCT d.categoria || ':' || d.tipo_elemento || ':' || d.formato || ':' "
                    + "|| d.link_publico_permitido || ':' || d.extensoes_aceites::text FROM " + ITENS_SGIFR + " ORDER BY 1"))
                    .containsExactly(
                            "DEPOSITO:PECA_ESCRITA:NAO_EDITAVEL:false:[\"pdf\", \"jpg\", \"png\"]",
                            "DEPOSITO:PECA_GRAFICA:NAO_EDITAVEL:false:[\"zip\"]",
                            "PUBLICACAO:PECA_ESCRITA:EDITAVEL:true:" + EXTENSOES_ESCRITA_PUBLICACAO,
                            "PUBLICACAO:PECA_GRAFICA:EDITAVEL:true:[\"zip\"]");
            assertThat(contar(ligacao, ITENS_SGIFR + " AND d.categoria = 'PUBLICACAO' AND right(d.codigo, 4) <> '_PUB'"))
                    .isZero();
            assertThat(contar(ligacao, ITENS_SGIFR + " AND d.categoria = 'DEPOSITO' AND right(d.codigo, 4) <> '_DEP'"))
                    .isZero();
        });
    }

    @Test
    void deveReverterSoOsItensDoSgifrSemDuplicarNemMexerNumItemJaCriadoEmBackoffice() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            // A cadeia até ao SGIFR também reverte a lista dos programas (2026-11-14-1) e o Cartograma em OUTROS
            // (2026-11-16-3), que vêm depois dele.
            String restantes = "SELECT md5(string_agg(to_jsonb(d)::text, ',' ORDER BY d.id)) FROM documento_config d WHERE "
                    + SEM_ITENS_DO_SGIFR + " AND " + SEM_ITENS_DA_PRORROGACAO_DO_ATO + " AND " + SEM_ITENS_DOS_PROGRAMAS
                    + " AND d.codigo NOT IN ('CARTOGRAMA_PUB', 'CARTOGRAMA_DEP')";
            String restantesAntes = texto(ligacao, restantes);
            int itens = contar(ligacao, "documento_config WHERE " + SEM_ITENS_DA_PRORROGACAO_DO_ATO);

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES + CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, ITENS_SGIFR)).isZero();
            assertThat(contar(ligacao, "documento_config")).isEqualTo(itens - ITENS_DO_SGIFR - ITENS_DOS_PROGRAMAS_DA_LISTA_DGT);
            assertThat(texto(ligacao, restantes)).isEqualTo(restantesAntes);
            assertThat(contar(ligacao, "instrumento_procedimento_config c JOIN tipo_procedimento t "
                    + "ON t.id = c.tipo_procedimento_id WHERE t.codigo = 'PUBLICACAO_SGIFR'")).isEqualTo(4);

            // Um item com um código do seed, criado em backoffice no PRA × Delimitação, fica como está.
            executar(ligacao, "INSERT INTO documento_config (instrumento_procedimento_config_id, codigo, separador, nome, "
                    + "categoria, tipo_elemento, formato, obrigatorio, extensoes_aceites, ordem, ativo) SELECT c.id, "
                    + "'TEXTO_ATO_PUB', 'Ato', 'Ato do backoffice', 'PUBLICACAO', 'PECA_ESCRITA', 'EDITAVEL', false, "
                    + "'[\"pdf\"]', 9, true FROM instrumento_procedimento_config c "
                    + "JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = c.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                    + "WHERE t.codigo = 'PUBLICACAO_SGIFR' AND ti.codigo = 'PRA' AND td.codigo = 'DELIMITACAO'");

            migrarOsProximos(ligacao, CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, ITENS_SGIFR)).isEqualTo(ITENS_DO_SGIFR);
            assertThat(linhas(ligacao, "SELECT d.nome || ':' || d.obrigatorio || ':' || d.regra_nomenclatura FROM " + ITENS_SGIFR
                    + " AND ti.codigo = 'PRA' AND td.codigo = 'DELIMITACAO' AND d.codigo = 'TEXTO_ATO_PUB'"))
                    .containsExactly("Ato do backoffice:false:GERAL");

            executar(ligacao, "DELETE FROM databasechangelog WHERE id = '2026-11-13-12-seed-documento-config-sgifr'");
            assertThatCode(() -> migrarOsProximos(ligacao, CHANGESETS_DO_DOCUMENTO_CONFIG_SGIFR)).doesNotThrowAnyException();
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, ITENS_SGIFR)).isEqualTo(ITENS_DO_SGIFR);
            assertThat(texto(ligacao, restantes)).isEqualTo(restantesAntes);
        });
    }

    private static final String DO_TIPO_SERVIDAO = "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "JOIN tipo_procedimento tp ON tp.id = ipc.tipo_procedimento_id WHERE ti.codigo = 'SERVIDAO'";

    private static final String DINAMICAS_DA_SERVIDAO = "SELECT td.codigo || ':' || idc.ativo || ':' || idc.associacao_instrumentos "
            + "FROM instrumento_dinamica_config idc JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id WHERE ti.codigo = 'SERVIDAO' ORDER BY idc.id";

    private static final String PROCEDIMENTOS_DA_SERVIDAO = "SELECT td.codigo || ':' || tp.codigo || ':' || ipc.ordem || ':' "
            + "|| ipc.obrigatorio || ':' || coalesce(ipc.serie_dr, '-') || ':' || ipc.repetivel || ':' || ipc.ato_pontual || ':' "
            + "|| ipc.tamanho_maximo_processo || ':' || ipc.ativo FROM instrumento_procedimento_config ipc " + DO_TIPO_SERVIDAO
            + " ORDER BY idc.id";

    private static final String PERMISSOES_DA_SERVIDAO = "SELECT td.codigo || ':' || string_agg(pa.tipo_entidade || '/' || pa.ativo, "
            + "',' ORDER BY pa.tipo_entidade) FROM permissao_acao pa JOIN instrumento_procedimento_config ipc "
            + "ON ipc.id = pa.instrumento_procedimento_config_id " + DO_TIPO_SERVIDAO + " GROUP BY idc.id, td.codigo ORDER BY idc.id";

    private static final String REGRAS_DA_SERVIDAO = "SELECT td.codigo || ':' || r.descricao || ':' || r.operador || ':' "
            + "|| r.substitui_sequenciamento || ':' || r.ativo || ':' || coalesce(r.instrumento_procedimento_config_id::text, '-') "
            + "|| ':' || (SELECT string_agg(c.avaliador || c.parametros::text || '/' || c.grupo || '/' || c.ativo, ';' ORDER BY c.id) "
            + "FROM condicao c WHERE c.regra_id = r.id) FROM regra r "
            + "JOIN instrumento_dinamica_config idc ON idc.id = r.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "WHERE ti.codigo = 'SERVIDAO' ORDER BY idc.id, r.id";

    private static final String SERIE_DR_ANULAVEL = "SELECT is_nullable FROM information_schema.columns "
            + "WHERE table_name = 'instrumento_procedimento_config' AND column_name = 'serie_dr'";

    /** As impressões digitais das seis tabelas do seed, sem as linhas do tipo SERVIDAO. */
    private List<String> configuracaoForaDasRestantesServidoes(Connection ligacao) throws SQLException {
        String tipo = "(SELECT id FROM tipo_instrumento WHERE codigo = 'SERVIDAO')";
        String dinamicas = "(SELECT id FROM instrumento_dinamica_config WHERE tipo_instrumento_id IN " + tipo + ")";
        String procedimentos = "(SELECT id FROM instrumento_procedimento_config WHERE instrumento_dinamica_config_id IN "
                + dinamicas + ")";
        String regras = "(SELECT id FROM regra WHERE instrumento_dinamica_config_id IN " + dinamicas + ")";
        return List.of(
                texto(ligacao, "SELECT md5(string_agg(t::text, ',' ORDER BY t.id)) FROM tipo_instrumento t WHERE id NOT IN " + tipo),
                texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM instrumento_dinamica_config c "
                        + "WHERE id NOT IN " + dinamicas),
                texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM instrumento_procedimento_config p "
                        + "WHERE id NOT IN " + procedimentos),
                texto(ligacao, "SELECT md5(string_agg(p::text, ',' ORDER BY p.id)) FROM permissao_acao p "
                        + "WHERE instrumento_procedimento_config_id NOT IN " + procedimentos),
                texto(ligacao, "SELECT md5(string_agg(r::text, ',' ORDER BY r.id)) FROM regra r WHERE id NOT IN " + regras),
                texto(ligacao, "SELECT md5(string_agg(c::text, ',' ORDER BY c.id)) FROM condicao c WHERE regra_id NOT IN " + regras));
    }

    private static final List<String> PROCEDIMENTOS_DA_SERVIDAO_ESPERADOS = List.of(
            "DELIMITACAO:PUBLICACAO_SRUP:1:true:-:false:false:5368709120:true",
            "ALTERACAO:PUBLICACAO_SRUP:1:true:-:false:false:5368709120:true",
            "REVOGACAO:PUBLICACAO_SRUP:1:true:-:false:false:5368709120:true");

    private static final List<String> REGRAS_DA_SERVIDAO_ESPERADAS = List.of(
            "DELIMITACAO:Delimitação é a Dinâmica inicial e única:AND:false:true:-:"
                    + "OCORRENCIA_NAO_EXISTE{\"codigo\": \"DELIMITACAO\", \"tipo_ocorrencia\": \"DINAMICA\"}/1/true",
            "ALTERACAO:Requer servidão já delimitada:AND:false:true:-:INSTRUMENTO_EM_VIGOR{}/1/true",
            "REVOGACAO:Requer servidão já delimitada e só uma Revogação:AND:false:true:-:INSTRUMENTO_EM_VIGOR{}/1/true;"
                    + "OCORRENCIA_NAO_EXISTE{\"codigo\": \"REVOGACAO\", \"tipo_ocorrencia\": \"DINAMICA\"}/1/true");

    private List<Integer> contagensDoSeed(Connection ligacao) throws SQLException {
        List<Integer> contagens = new ArrayList<>();
        for (String tabela : List.of("tipo_instrumento", "instrumento_dinamica_config", "instrumento_procedimento_config",
                "permissao_acao", "regra", "condicao")) {
            contagens.add(contar(ligacao, tabela));
        }
        return contagens;
    }

    @Test
    void deveSemearAsRestantesServidoesComUmTipoTresDinamicasUmProcedimentoCadaEAsPermissoesDaMatriz() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, "SELECT codigo || ':' || nome || ':' || categoria || ':' || ativo || ':' "
                    + "|| coalesce(codigo_idus, '-') || ':' || coalesce(snit_codigo, '-') || ':' || serie_numero_deposito "
                    + "FROM tipo_instrumento WHERE categoria = 'SRUP' AND codigo NOT IN ('REN', 'PRA', 'PSA')"))
                    .containsExactly("SERVIDAO:Servidão:SRUP:true:-:-:GERAL");
            assertThat(linhas(ligacao, DINAMICAS_DA_SERVIDAO))
                    .containsExactly("DELIMITACAO:true:UM", "ALTERACAO:true:UM", "REVOGACAO:true:UM");
            assertThat(linhas(ligacao, PROCEDIMENTOS_DA_SERVIDAO)).containsExactlyElementsOf(PROCEDIMENTOS_DA_SERVIDAO_ESPERADOS);
            assertThat(linhas(ligacao, PERMISSOES_DA_SERVIDAO)).containsExactly(
                    "DELIMITACAO:CM/true,DGT/true,IP/true,SEC_ESTADO/true",
                    "ALTERACAO:CM/true,DGT/true,IP/true,SEC_ESTADO/true",
                    "REVOGACAO:CM/true,DGT/true,IP/true,SEC_ESTADO/true");
            assertThat(linhas(ligacao, REGRAS_DA_SERVIDAO)).containsExactlyElementsOf(REGRAS_DA_SERVIDAO_ESPERADAS);
            assertThat(contar(ligacao, "condicao c JOIN regra r ON r.id = c.regra_id "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = r.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id WHERE ti.codigo = 'SERVIDAO'"))
                    .isEqualTo(CONDICOES_DAS_RESTANTES_SERVIDOES);
            assertThat(contar(ligacao, "permissao_acao pa JOIN instrumento_procedimento_config ipc "
                    + "ON ipc.id = pa.instrumento_procedimento_config_id " + DO_TIPO_SERVIDAO))
                    .isEqualTo(PERMISSOES_DAS_RESTANTES_SERVIDOES);
            // A consulta da Validation Checklist: as três Dinâmicas das restantes servidões, sem série do DR.
            assertThat(linhas(ligacao, "SELECT t.codigo || ':' || d.codigo || ':' || coalesce(p.serie_dr, 'NULL') || ':' "
                    + "|| p.obrigatorio FROM instrumento_procedimento_config p "
                    + "JOIN instrumento_dinamica_config c ON c.id = p.instrumento_dinamica_config_id "
                    + "JOIN tipo_dinamica d ON d.id = c.tipo_dinamica_id JOIN tipo_instrumento t ON t.id = c.tipo_instrumento_id "
                    + "WHERE t.categoria = 'SRUP' AND t.codigo NOT IN ('REN', 'PRA', 'PSA') ORDER BY c.id"))
                    .containsExactly("SERVIDAO:DELIMITACAO:NULL:true", "SERVIDAO:ALTERACAO:NULL:true",
                            "SERVIDAO:REVOGACAO:NULL:true");
            assertThat(contar(ligacao, "instrumento_procedimento_config WHERE serie_dr IS NULL"))
                    .isEqualTo(PROCEDIMENTOS_DAS_RESTANTES_SERVIDOES);
            assertThat(MatrizPermissaoAcao.desacordos(ligacao)).isEmpty();

            String ordemDe = "SELECT orderexecuted FROM databasechangelog WHERE id = '%s'";
            int ordemDoSeed = Integer.parseInt(texto(ligacao, ordemDe.formatted("2026-11-13-14-seed-config-restantes-servidoes")));
            for (String dependencia : List.of("2026-11-13-13-serie-dr-opcional-procedimento", "2026-11-13-9-seed-tipo-procedimento-srup",
                    "2026-11-11-6-seed-catalogo-srup-ren-sgifr", "2026-11-10-8-permissao-acao-por-tipo-entidade")) {
                assertThat(Integer.parseInt(texto(ligacao, ordemDe.formatted(dependencia)))).as(dependencia).isLessThan(ordemDoSeed);
            }
        });
    }

    @Test
    void deveTornarASerieDrOpcionalParaAsRestantesServidoesComOCheckEReporONotNullNoRollback() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, SERIE_DR_ANULAVEL)).isEqualTo("YES");
            String umaConfiguracao = "(SELECT min(id) FROM instrumento_procedimento_config WHERE serie_dr IS NOT NULL)";
            assertThatThrownBy(() -> executar(ligacao, "UPDATE instrumento_procedimento_config SET serie_dr = 'TERCEIRA' "
                    + "WHERE id = " + umaConfiguracao)).hasMessageContaining("ck_instrumento_procedimento_config_serie_dr");
            for (String serie : List.of("PRIMEIRA", "SEGUNDA_H", "SEGUNDA_C")) {
                assertThat(contar(ligacao, "pg_constraint WHERE conname = 'ck_instrumento_procedimento_config_serie_dr' "
                        + "AND pg_get_constraintdef(oid) LIKE '%''" + serie + "''%'")).isEqualTo(1);
            }

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DAS_RESTANTES_SERVIDOES);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, SERIE_DR_ANULAVEL)).isEqualTo("NO");
            assertThat(contar(ligacao, "tipo_instrumento WHERE codigo = 'SERVIDAO'")).isZero();

            migrarOsProximos(ligacao, CHANGESETS_DA_SERIE_DR_OPCIONAL);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, SERIE_DR_ANULAVEL)).isEqualTo("YES");
            String serieAntes = texto(ligacao, "SELECT id || ':' || serie_dr FROM instrumento_procedimento_config WHERE id = "
                    + umaConfiguracao);
            long id = Long.parseLong(serieAntes.split(":")[0]);
            executar(ligacao, "UPDATE instrumento_procedimento_config SET serie_dr = NULL WHERE id = " + id);
            assertThatThrownBy(() -> reverterOsUltimos(ligacao, CHANGESETS_DA_SERIE_DR_OPCIONAL))
                    .hasStackTraceContaining("Há configurações de Procedimento sem série do DR");
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, SERIE_DR_ANULAVEL)).isEqualTo("YES");

            executar(ligacao, "UPDATE instrumento_procedimento_config SET serie_dr = '" + serieAntes.split(":")[1]
                    + "' WHERE id = " + id);
            reverterOsUltimos(ligacao, CHANGESETS_DA_SERIE_DR_OPCIONAL);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, SERIE_DR_ANULAVEL)).isEqualTo("NO");
        });
    }

    @Test
    void deveSemearAsRestantesServidoesSemFalharNemDuplicarNemMexerNoQueOBackofficeJaCriou() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR + CHANGESETS_DO_SEED_DAS_RESTANTES_SERVIDOES);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "tipo_instrumento WHERE codigo = 'SERVIDAO'")).isZero();

            // O backoffice já criou o tipo, a Delimitação inativa, o Procedimento com série e a linha da CM inativa.
            executar(ligacao, "INSERT INTO tipo_instrumento (codigo, nome, categoria, ativo) "
                    + "VALUES ('SERVIDAO', 'Servidão do backoffice', 'SRUP', true)");
            executar(ligacao, "INSERT INTO instrumento_dinamica_config (tipo_instrumento_id, tipo_dinamica_id, ativo) "
                    + "SELECT ti.id, td.id, false FROM tipo_instrumento ti, tipo_dinamica td "
                    + "WHERE ti.codigo = 'SERVIDAO' AND td.codigo = 'DELIMITACAO'");
            executar(ligacao, "INSERT INTO instrumento_procedimento_config (instrumento_dinamica_config_id, tipo_procedimento_id, "
                    + "ordem, serie_dr) SELECT idc.id, tp.id, 1, 'SEGUNDA_H' FROM instrumento_dinamica_config idc "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id CROSS JOIN tipo_procedimento tp "
                    + "WHERE ti.codigo = 'SERVIDAO' AND tp.codigo = 'PUBLICACAO_SRUP'");
            executar(ligacao, "INSERT INTO permissao_acao (tipo_entidade, instrumento_procedimento_config_id, ativo) "
                    + "SELECT 'CM', ipc.id, false FROM instrumento_procedimento_config ipc " + DO_TIPO_SERVIDAO);
            String doBackoffice = "SELECT (SELECT nome FROM tipo_instrumento WHERE codigo = 'SERVIDAO') || '|' "
                    + "|| (SELECT string_agg(idc.id || ':' || idc.ativo, ',') FROM instrumento_dinamica_config idc "
                    + "JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id WHERE td.codigo = 'DELIMITACAO' "
                    + "AND idc.tipo_instrumento_id = (SELECT id FROM tipo_instrumento WHERE codigo = 'SERVIDAO')) || '|' "
                    + "|| (SELECT string_agg(ipc.id || ':' || ipc.serie_dr, ',') FROM instrumento_procedimento_config ipc "
                    + DO_TIPO_SERVIDAO + " AND td.codigo = 'DELIMITACAO') || '|' "
                    + "|| (SELECT string_agg(pa.id || ':' || pa.ativo, ',') FROM permissao_acao pa "
                    + "JOIN instrumento_procedimento_config ipc ON ipc.id = pa.instrumento_procedimento_config_id "
                    + DO_TIPO_SERVIDAO + " AND pa.tipo_entidade = 'CM' AND td.codigo = 'DELIMITACAO')";
            String antes = texto(ligacao, doBackoffice);

            assertThatCode(() -> migrarOsProximos(ligacao, CHANGESETS_DO_SEED_DAS_RESTANTES_SERVIDOES)).doesNotThrowAnyException();
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, doBackoffice)).isEqualTo(antes);
            assertThat(contar(ligacao, "tipo_instrumento WHERE codigo = 'SERVIDAO'")).isEqualTo(1);
            assertThat(linhas(ligacao, DINAMICAS_DA_SERVIDAO))
                    .containsExactly("DELIMITACAO:false:UM", "ALTERACAO:true:UM", "REVOGACAO:true:UM");
            assertThat(contar(ligacao, "instrumento_procedimento_config ipc " + DO_TIPO_SERVIDAO))
                    .isEqualTo(PROCEDIMENTOS_DAS_RESTANTES_SERVIDOES);
            assertThat(linhas(ligacao, PERMISSOES_DA_SERVIDAO)).containsExactly(
                    "DELIMITACAO:CM/false,DGT/true,IP/true,SEC_ESTADO/true",
                    "ALTERACAO:CM/true,DGT/true,IP/true,SEC_ESTADO/true",
                    "REVOGACAO:CM/true,DGT/true,IP/true,SEC_ESTADO/true");
            assertThat(linhas(ligacao, REGRAS_DA_SERVIDAO)).containsExactlyElementsOf(REGRAS_DA_SERVIDAO_ESPERADAS);
            List<Integer> depoisDaPrimeira = contagensDoSeed(ligacao);
            List<String> linhasDepoisDaPrimeira = new ArrayList<>(linhas(ligacao, PROCEDIMENTOS_DA_SERVIDAO));
            linhasDepoisDaPrimeira.addAll(linhas(ligacao, PERMISSOES_DA_SERVIDAO));
            linhasDepoisDaPrimeira.addAll(linhas(ligacao, REGRAS_DA_SERVIDAO));

            executar(ligacao, "DELETE FROM databasechangelog WHERE id = '2026-11-13-14-seed-config-restantes-servidoes'");
            assertThatCode(() -> migrarOsProximos(ligacao, CHANGESETS_DO_SEED_DAS_RESTANTES_SERVIDOES)).doesNotThrowAnyException();
            ligacao.setAutoCommit(true);
            assertThat(contagensDoSeed(ligacao)).isEqualTo(depoisDaPrimeira);
            List<String> linhasDepoisDaSegunda = new ArrayList<>(linhas(ligacao, PROCEDIMENTOS_DA_SERVIDAO));
            linhasDepoisDaSegunda.addAll(linhas(ligacao, PERMISSOES_DA_SERVIDAO));
            linhasDepoisDaSegunda.addAll(linhas(ligacao, REGRAS_DA_SERVIDAO));
            assertThat(linhasDepoisDaSegunda).isEqualTo(linhasDepoisDaPrimeira);
            assertThat(texto(ligacao, doBackoffice)).isEqualTo(antes);
        });
    }

    @Test
    void deveReverterSoAsRestantesServidoesSemMexerNaRenNoSgifrNemNosPlanosEVoltarASemeaLas() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            // A SEC_ESTADO desativada no PROT e a Tentativa do processo, posteriores, saem antes da fotografia.
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO + CHANGESETS_DO_PROT_SO_DA_CCDR);
            ligacao.setAutoCommit(true);
            List<String> restantesAntes = configuracaoForaDasRestantesServidoes(ligacao);
            List<Integer> contagens = contagensDoSeed(ligacao);

            reverterOsUltimos(ligacao, CHANGESETS_DO_SEED_DAS_RESTANTES_SERVIDOES);
            ligacao.setAutoCommit(true);
            assertThat(contagensDoSeed(ligacao)).containsExactly(contagens.get(0) - 1, contagens.get(1) - 3,
                    contagens.get(2) - PROCEDIMENTOS_DAS_RESTANTES_SERVIDOES, contagens.get(3) - PERMISSOES_DAS_RESTANTES_SERVIDOES,
                    contagens.get(4) - 3, contagens.get(5) - CONDICOES_DAS_RESTANTES_SERVIDOES);
            assertThat(contar(ligacao, "tipo_instrumento WHERE codigo = 'SERVIDAO'")).isZero();
            assertThat(contar(ligacao, "instrumento_procedimento_config WHERE serie_dr IS NULL")).isZero();
            assertThat(configuracaoForaDasRestantesServidoes(ligacao)).isEqualTo(restantesAntes);
            assertThat(texto(ligacao, SERIE_DR_ANULAVEL)).isEqualTo("YES");

            migrarOsProximos(ligacao, CHANGESETS_DO_SEED_DAS_RESTANTES_SERVIDOES);
            ligacao.setAutoCommit(true);
            assertThat(contagensDoSeed(ligacao)).isEqualTo(contagens);
            assertThat(linhas(ligacao, PROCEDIMENTOS_DA_SERVIDAO)).containsExactlyElementsOf(PROCEDIMENTOS_DA_SERVIDAO_ESPERADOS);
            assertThat(linhas(ligacao, REGRAS_DA_SERVIDAO)).containsExactlyElementsOf(REGRAS_DA_SERVIDAO_ESPERADAS);
            assertThat(configuracaoForaDasRestantesServidoes(ligacao)).isEqualTo(restantesAntes);
        });
    }

    private static final String SEC_ESTADO_NO_PROT = "permissao_acao pa JOIN instrumento_procedimento_config ipc "
            + "ON ipc.id = pa.instrumento_procedimento_config_id "
            + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
            + "WHERE ti.codigo = 'PROT' AND pa.tipo_entidade = 'SEC_ESTADO'";

    private static final String DINAMICAS_DO_GOVERNO = "td.codigo IN ('SUSPENSAO_GOVERNO', 'MEDIDAS_PREVENTIVAS_GOVERNO')";

    @Test
    void deveDesativarASecEstadoNoProtForaDasDinamicasDoGovernoComOProtSoDaCcdr() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, SEC_ESTADO_NO_PROT + " AND NOT " + DINAMICAS_DO_GOVERNO + " AND NOT pa.ativo"))
                    .isEqualTo(PERMISSOES_DO_SEC_ESTADO_NO_PROT);
            assertThat(contar(ligacao, SEC_ESTADO_NO_PROT + " AND NOT " + DINAMICAS_DO_GOVERNO + " AND pa.ativo")).isZero();
            assertThat(contar(ligacao, SEC_ESTADO_NO_PROT + " AND " + DINAMICAS_DO_GOVERNO + " AND pa.ativo")).isEqualTo(4);
            assertThat(linhas(ligacao, "SELECT DISTINCT pa.tipo_entidade FROM permissao_acao pa "
                    + "JOIN instrumento_procedimento_config ipc ON ipc.id = pa.instrumento_procedimento_config_id "
                    + "JOIN instrumento_dinamica_config idc ON idc.id = ipc.instrumento_dinamica_config_id "
                    + "JOIN tipo_instrumento ti ON ti.id = idc.tipo_instrumento_id JOIN tipo_dinamica td ON td.id = idc.tipo_dinamica_id "
                    + "WHERE ti.codigo = 'PROT' AND pa.ativo AND pa.tipo_entidade <> 'PCM' AND NOT " + DINAMICAS_DO_GOVERNO
                    + " ORDER BY 1")).containsExactly("CCDR_DR", "DGT");
            assertThat(MatrizPermissaoAcao.desacordos(ligacao)).isEmpty();
            assertThat(texto(ligacao, "SELECT id FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 1 OFFSET "
                    + (CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT + CHANGESETS_DA_TENTATIVA_DO_PROCESSO)))
                    .isEqualTo("2026-11-13-15-seed-matriz-permissao-acao-prot-so-ccdr");
        });
    }

    @Test
    void deveDesativarASecEstadoNoProtDuasVezesSemErroEReverterReativandoSoEssasLinhas() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            List<String> depois = linhas(ligacao, TODAS_AS_PERMISSOES);
            List<String> desativadas = linhas(ligacao, "SELECT pa.id::text FROM " + SEC_ESTADO_NO_PROT
                    + " AND NOT pa.ativo ORDER BY pa.id");
            assertThat(desativadas).hasSize(PERMISSOES_DO_SEC_ESTADO_NO_PROT);

            executar(ligacao, "DELETE FROM databasechangelog WHERE id = '2026-11-13-15-seed-matriz-permissao-acao-prot-so-ccdr'");
            assertThatCode(() -> migrarOsProximos(ligacao, CHANGESETS_DO_PROT_SO_DA_CCDR)).doesNotThrowAnyException();
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, TODAS_AS_PERMISSOES)).isEqualTo(depois);

            reverterOsUltimos(ligacao, CHANGESETS_DO_PROT_SO_DA_CCDR);
            ligacao.setAutoCommit(true);
            List<String> esperadoNoRollback = depois.stream()
                    .map(linha -> desativadas.contains(linha.split(":")[0]) ? linha.replace(":false", ":true") : linha)
                    .toList();
            assertThat(linhas(ligacao, TODAS_AS_PERMISSOES)).isEqualTo(esperadoNoRollback);
            assertThat(MatrizPermissaoAcao.desacordosDaMatrizInicial(ligacao)).isEmpty();

            migrarOsProximos(ligacao, CHANGESETS_DO_PROT_SO_DA_CCDR);
            ligacao.setAutoCommit(true);
            assertThat(linhas(ligacao, TODAS_AS_PERMISSOES)).isEqualTo(depois);
            assertThat(MatrizPermissaoAcao.desacordos(ligacao)).isEmpty();
        });
    }

    private static final String COLUNA_DO_PROCESSO = "SELECT data_type || ':' || is_nullable || ':' || coalesce(column_default, '') "
            + "FROM information_schema.columns WHERE table_name = 'processo' AND column_name = ";

    private static final String CHAVE_DA_OCORRENCIA = "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conname = 'uq_processo_ocorrencia'";

    private static final String PROCESSO_NA_TENTATIVA = "INSERT INTO processo (id, dinamica_id, "
            + "instrumento_procedimento_config_id, criado_por, ocorrencia, numero_processo, entidade_id, tentativa, "
            + "aproveita_atos_anteriores) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000003', "
            + "(SELECT min(id) FROM instrumento_procedimento_config), '00000000-0000-0000-0000-000000000001', 1, '%s', "
            + "(SELECT min(id) FROM entidade), %d, %s)";

    @Test
    void deveCriarATentativaDoProcessoComUmNosExistentesOsChecksEAChaveComATentativaERevertelos() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO + CHANGESETS_DO_PROCESSO_REFERIDO + CHANGESETS_DA_LAGOA_DO_ALGARVE_5126 + CHANGESETS_DA_RETIRADA_DA_RERAE + CHANGESETS_DOS_ITENS_DOS_PROGRAMAS_DA_LISTA_DGT);
            reverterOsUltimos(ligacao, CHANGESETS_DA_TENTATIVA_DO_PROCESSO);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "tentativa")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "aproveita_atos_anteriores")).isFalse();
            assertThat(texto(ligacao, CHAVE_DA_OCORRENCIA))
                    .isEqualTo("UNIQUE (dinamica_id, instrumento_procedimento_config_id, ocorrencia)");
            inserirProcessoComDocumento(ligacao);
            inserirOutroProcesso(ligacao);

            migrarOsProximos(ligacao, CHANGESETS_DA_TENTATIVA_DO_PROCESSO);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, COLUNA_DO_PROCESSO + "'tentativa'")).isEqualTo("integer:NO:1");
            assertThat(texto(ligacao, COLUNA_DO_PROCESSO + "'aproveita_atos_anteriores'")).isEqualTo("boolean:YES:");
            assertThat(contar(ligacao, "processo")).isEqualTo(2);
            assertThat(contar(ligacao, "processo WHERE tentativa = 1 AND aproveita_atos_anteriores IS NULL")).isEqualTo(2);
            assertThat(texto(ligacao, CHAVE_DA_OCORRENCIA))
                    .isEqualTo("UNIQUE (dinamica_id, tentativa, instrumento_procedimento_config_id, ocorrencia)");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE processo SET tentativa = 0 WHERE id = '" + PROCESSO_ID + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_processo_tentativa");
            assertThatThrownBy(() -> executar(ligacao, "UPDATE processo SET tentativa = NULL WHERE id = '" + PROCESSO_ID + "'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> executar(ligacao, "UPDATE processo SET aproveita_atos_anteriores = false WHERE id = '"
                    + PROCESSO_ID + "'")).isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_processo_aproveita_atos_anteriores");

            executar(ligacao, String.format(PROCESSO_NA_TENTATIVA, "00010", 2, "true"));
            assertThat(contar(ligacao, "processo WHERE tentativa = 2 AND ocorrencia = 1 AND aproveita_atos_anteriores")).isEqualTo(1);
            assertThatThrownBy(() -> executar(ligacao, String.format(PROCESSO_NA_TENTATIVA, "00011", 2, "NULL")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uq_processo_ocorrencia");
            assertThatThrownBy(() -> executar(ligacao, String.format(PROCESSO_NA_TENTATIVA, "00012", 1, "NULL")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uq_processo_ocorrencia");

            // Com a mesma ocorrência em duas Tentativas, a chave anterior não volta: o rollback falha sem mexer em nada.
            assertThatThrownBy(() -> reverterOsUltimos(ligacao, CHANGESETS_DA_TENTATIVA_DO_PROCESSO))
                    .hasStackTraceContaining("uq_processo_ocorrencia");
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "tentativa")).isTrue();
            executar(ligacao, "DELETE FROM processo WHERE tentativa = 2");

            reverterOsUltimos(ligacao, CHANGESETS_DA_TENTATIVA_DO_PROCESSO);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "tentativa")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "aproveita_atos_anteriores")).isFalse();
            assertThat(contar(ligacao, "pg_constraint WHERE conname IN ('ck_processo_tentativa', "
                    + "'ck_processo_aproveita_atos_anteriores')")).isZero();
            assertThat(texto(ligacao, CHAVE_DA_OCORRENCIA))
                    .isEqualTo("UNIQUE (dinamica_id, instrumento_procedimento_config_id, ocorrencia)");
            assertThat(contar(ligacao, "processo")).isEqualTo(2);
            assertThat(colunaExiste(ligacao, "processo", "destino")).isTrue();

            migrarOsProximos(ligacao, CHANGESETS_DA_TENTATIVA_DO_PROCESSO);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "processo WHERE tentativa = 1")).isEqualTo(2);
        });
    }

    private static final String CHAVE_DO_PROCESSO_REFERIDO = "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conname = 'fk_processo_processo_referido'";

    private static final String INDICE_DO_PROCESSO_REFERIDO = "SELECT count(*) FROM pg_indexes "
            + "WHERE tablename = 'processo' AND indexname = 'idx_processo_processo_referido' "
            + "AND indexdef LIKE '%(processo_referido_id)'";

    @Test
    void deveCriarOProcessoReferidoComAChaveEstrangeiraEOIndiceERevertelos() throws Exception {
        comBaseMigrada(ligacao -> {
            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO + CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO);
            reverterOsUltimos(ligacao, CHANGESETS_DO_PROCESSO_REFERIDO);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "processo_referido_id")).isFalse();
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'fk_processo_processo_referido'")).isZero();
            assertThat(texto(ligacao, INDICE_DO_PROCESSO_REFERIDO)).isEqualTo("0");
            inserirProcessoComDocumento(ligacao);
            inserirOutroProcesso(ligacao);

            migrarOsProximos(ligacao, CHANGESETS_DO_PROCESSO_REFERIDO);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, COLUNA_DO_PROCESSO + "'processo_referido_id'")).isEqualTo("uuid:YES:");
            assertThat(contar(ligacao, "processo WHERE processo_referido_id IS NULL")).isEqualTo(2);
            assertThat(texto(ligacao, CHAVE_DO_PROCESSO_REFERIDO))
                    .isEqualTo("FOREIGN KEY (processo_referido_id) REFERENCES processo(id)");
            assertThat(texto(ligacao, INDICE_DO_PROCESSO_REFERIDO)).isEqualTo("1");
            executar(ligacao, "UPDATE processo SET processo_referido_id = '" + PROCESSO_ID + "' WHERE id <> '" + PROCESSO_ID + "'");
            assertThat(contar(ligacao, "processo WHERE processo_referido_id = '" + PROCESSO_ID + "'")).isEqualTo(1);
            assertThatThrownBy(() -> executar(ligacao, "UPDATE processo SET processo_referido_id = gen_random_uuid() "
                    + "WHERE id = '" + PROCESSO_ID + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("fk_processo_processo_referido");

            reverterOsUltimos(ligacao, CHANGESETS_DO_PROCESSO_REFERIDO);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "processo_referido_id")).isFalse();
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'fk_processo_processo_referido'")).isZero();
            assertThat(texto(ligacao, INDICE_DO_PROCESSO_REFERIDO)).isEqualTo("0");
            assertThat(contar(ligacao, "processo")).isEqualTo(2);
            assertThat(colunaExiste(ligacao, "processo", "tentativa")).isTrue();

            migrarOsProximos(ligacao, CHANGESETS_DO_PROCESSO_REFERIDO);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "processo_referido_id")).isTrue();
        });
    }


    private static final String ITENS_PRORROGACAO_ATO = "documento_config d JOIN instrumento_procedimento_config c "
            + "ON c.id = d.instrumento_procedimento_config_id JOIN tipo_procedimento t ON t.id = c.tipo_procedimento_id "
            + "WHERE t.codigo = 'PRORROGACAO_ATO'";

    @Test
    void deveSemearOsItensDaProrrogacaoDoAtoComAInformacaoBaseNoTextoDoAtoSemDuplicarERevertelos() throws Exception {
        String itensDeCadaConfiguracao = "TEXTO_ATO_PUB/PUBLICACAO/true/TEXTO_ATO,TEXTO_ATO_DEP/DEPOSITO/false/TEXTO_ATO,"
                + "DELIBERACAO_PUB/PUBLICACAO/true/-,DELIBERACAO_DEP/DEPOSITO/false/-";
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            int configuracoes = contar(ligacao, "instrumento_procedimento_config c JOIN tipo_procedimento t "
                    + "ON t.id = c.tipo_procedimento_id WHERE t.codigo = 'PRORROGACAO_ATO' AND c.ativo");
            assertThat(configuracoes).isPositive();
            assertThat(linhas(ligacao, "SELECT string_agg(d.codigo || '/' || d.categoria || '/' || d.obrigatorio::text "
                    + "|| '/' || coalesce(d.elemento_informacao_base, '-'), ',' ORDER BY d.ordem) FROM " + ITENS_PRORROGACAO_ATO
                    + " GROUP BY c.id")).hasSize(configuracoes).containsOnly(itensDeCadaConfiguracao);
            assertThat(contar(ligacao, ITENS_PRORROGACAO_ATO + " AND d.tipo_elemento <> 'PECA_ESCRITA'")).isZero();
            assertThat(contar(ligacao, ITENS_PRORROGACAO_ATO + " AND d.link_publico_permitido <> (d.categoria = 'PUBLICACAO')"))
                    .isZero();
            int itens = contar(ligacao, ITENS_PRORROGACAO_ATO);
            assertThat(itens).isEqualTo(configuracoes * 4);
            int itensForaDaProrrogacao = contar(ligacao, "documento_config") - itens;
            String textosDosOutros = "SELECT md5(string_agg(to_jsonb(d)::text, ',' ORDER BY d.id)) FROM documento_config d "
                    + "WHERE d.id NOT IN (SELECT d.id FROM " + ITENS_PRORROGACAO_ATO + ")";
            String outrosAntes = texto(ligacao, textosDosOutros);

            executar(ligacao, "DELETE FROM databasechangelog WHERE id = '2026-11-14-3-seed-documento-config-prorrogacao-ato'");
            assertThatCode(() -> migrarOsProximos(ligacao, CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO))
                    .doesNotThrowAnyException();
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, ITENS_PRORROGACAO_ATO)).isEqualTo(itens);

            reverterOsUltimos(ligacao, CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, ITENS_PRORROGACAO_ATO)).isZero();
            assertThat(contar(ligacao, "documento_config")).isEqualTo(itensForaDaProrrogacao);
            assertThat(texto(ligacao, textosDosOutros)).isEqualTo(outrosAntes);
            assertThat(contar(ligacao, "documento_config WHERE codigo = 'TEXTO_ATO_PUB'")).isPositive();

            migrarOsProximos(ligacao, CHANGESETS_DO_DOCUMENTO_CONFIG_PRORROGACAO_ATO);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, ITENS_PRORROGACAO_ATO)).isEqualTo(itens);
        });
    }

    private static final String CHECK_DA_SERIE_DO_PROCESSO = "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conname = 'ck_processo_serie_dr'";

    @Test
    void deveCriarASerieDoProcessoAnulavelComOCheckDasTresSeriesERevertela() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, COLUNA_DO_PROCESSO + "'serie_dr'")).isEqualTo("character varying:YES:");
            assertThat(texto(ligacao, CHECK_DA_SERIE_DO_PROCESSO)).contains("PRIMEIRA", "SEGUNDA_H", "SEGUNDA_C");
            assertThat(texto(ligacao, "SELECT databasechangelog.id FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 1 OFFSET "
                    + (CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS))).isEqualTo("2026-11-14-4-add-processo-serie-dr");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS + CHANGESETS_DA_SERIE_DO_PROCESSO);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "serie_dr")).isFalse();
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'ck_processo_serie_dr'")).isZero();
            inserirProcessoComDocumento(ligacao);
            inserirOutroProcesso(ligacao);

            migrarOsProximos(ligacao, CHANGESETS_DA_SERIE_DO_PROCESSO);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "processo WHERE serie_dr IS NULL")).isEqualTo(2);
            for (String serie : List.of("PRIMEIRA", "SEGUNDA_H", "SEGUNDA_C")) {
                executar(ligacao, "UPDATE processo SET serie_dr = '" + serie + "' WHERE id = '" + PROCESSO_ID + "'");
            }
            assertThatThrownBy(() -> executar(ligacao, "UPDATE processo SET serie_dr = 'OUTRA' WHERE id = '"
                    + PROCESSO_ID + "'")).isInstanceOf(SQLException.class).hasMessageContaining("ck_processo_serie_dr");

            reverterOsUltimos(ligacao, CHANGESETS_DA_SERIE_DO_PROCESSO);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "serie_dr")).isFalse();
            assertThat(contar(ligacao, "processo")).isEqualTo(2);
            assertThat(colunaExiste(ligacao, "processo", "processo_referido_id")).isTrue();
        });
    }

    private static final String COLUNAS_DO_RESUMO = "SELECT string_agg(column_name || ':' || data_type || ':' || is_nullable, ',' "
            + "ORDER BY column_name) FROM information_schema.columns WHERE table_name = 'processo' "
            + "AND column_name IN ('notas_incm', 'notas_dgt', 'reg_uso_solo', 'alteracao_peot_id')";

    @Test
    void deveCriarOsCamposDoResumoValidadosOpcionaisComAFkDaListaERevertelos() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, COLUNAS_DO_RESUMO)).isEqualTo("alteracao_peot_id:bigint:YES,notas_dgt:text:YES,"
                    + "notas_incm:text:YES,reg_uso_solo:boolean:YES");
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'fk_processo_alteracao_peot' AND contype = 'f'")).isEqualTo(1);
            assertThat(texto(ligacao, "SELECT databasechangelog.id FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 1 OFFSET "
                    + (CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM))).isEqualTo("2026-11-14-5-add-processo-resumo-validacao");
            assertThat(contar(ligacao, "lista_valores WHERE tipo = 'ALTERACAO_PEOT'")).isZero();

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM + CHANGESETS_DO_RESUMO_VALIDADOS);
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, COLUNAS_DO_RESUMO)).isNull();
            assertThat(contar(ligacao, "pg_constraint WHERE conname = 'fk_processo_alteracao_peot'")).isZero();
            assertThat(colunaExiste(ligacao, "processo", "serie_dr")).isTrue();
            inserirProcessoComDocumento(ligacao);

            migrarOsProximos(ligacao, CHANGESETS_DO_RESUMO_VALIDADOS);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "processo WHERE notas_incm IS NULL AND notas_dgt IS NULL AND reg_uso_solo IS NULL "
                    + "AND alteracao_peot_id IS NULL")).isEqualTo(1);
            assertThatThrownBy(() -> executar(ligacao, "UPDATE processo SET alteracao_peot_id = -1 WHERE id = '"
                    + PROCESSO_ID + "'")).isInstanceOf(SQLException.class).hasMessageContaining("fk_processo_alteracao_peot");

            reverterOsUltimos(ligacao, CHANGESETS_DO_RESUMO_VALIDADOS);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "notas_incm")).isFalse();
            assertThat(contar(ligacao, "processo")).isEqualTo(1);
        });
    }

    private static final String COLUNA_PCM_DEVOLVIDO_EM = COLUNA_DO_PROCESSO + "'pcm_devolvido_em'";

    @Test
    void deveCriarADataDaDevolucaoPelaPcmAnulavelSemPreencherOsExistentesERevertela() throws Exception {
        comBaseMigrada(ligacao -> {
            ligacao.setAutoCommit(true);
            assertThat(texto(ligacao, COLUNA_PCM_DEVOLVIDO_EM)).isEqualTo("timestamp with time zone:YES:");
            assertThat(texto(ligacao, "SELECT databasechangelog.id FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 1 OFFSET "
                    + (CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN))).isEqualTo("2026-11-14-6-add-processo-pcm-devolvido-em");

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "pcm_devolvido_em")).isFalse();
            assertThat(colunaExiste(ligacao, "processo", "notas_incm")).isTrue();
            inserirProcessoComDocumento(ligacao);

            migrarOsProximos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM);
            ligacao.setAutoCommit(true);
            assertThat(contar(ligacao, "processo WHERE pcm_devolvido_em IS NULL")).isEqualTo(1);
            executar(ligacao, "UPDATE processo SET pcm_devolvido_em = now() WHERE id = '" + PROCESSO_ID + "'");
            assertThat(contar(ligacao, "processo WHERE pcm_devolvido_em IS NOT NULL")).isEqualTo(1);

            reverterOsUltimos(ligacao, CHANGESETS_DA_PUBLICACAO_FINAL + CHANGESETS_DO_CARTOGRAMA_SEM_FICHA + CHANGESETS_DO_FORMULARIO_REN + CHANGESETS_DO_PCM_DEVOLVIDO_EM);
            ligacao.setAutoCommit(true);
            assertThat(colunaExiste(ligacao, "processo", "pcm_devolvido_em")).isFalse();
            assertThat(contar(ligacao, "processo")).isEqualTo(1);
        });
    }
}

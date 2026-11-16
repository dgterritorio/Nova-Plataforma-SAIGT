package com.conkord.saigt.persistence.operacional;

import org.hibernate.HibernateException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Acesso aos contadores de `idus_contador` na ligação e na transação do chamador, sob `pg_advisory_xact_lock`
 * (TR-089): o lock dura até ao fim da transação, por isso o valor só fica visível a outras atribuições depois do
 * commit. Um contador em falta começa em 1.
 */
public final class ContadoresIdus {

    private static final int CLASSE_DO_LOCK = 0x1D05;

    private ContadoresIdus() {
    }

    /** Idempotente na mesma transação. Quem lê e depois incrementa um contador toma o lock antes de ler. */
    public static void bloquear(Connection ligacao, String nome) throws SQLException {
        try (PreparedStatement bloqueio = ligacao.prepareStatement("SELECT pg_advisory_xact_lock(?, ?)")) {
            bloqueio.setInt(1, CLASSE_DO_LOCK);
            bloqueio.setInt(2, nome.hashCode());
            bloqueio.execute();
        }
    }

    public static int proximo(Connection ligacao, String nome) throws SQLException {
        bloquear(ligacao, nome);
        if (incrementar(ligacao, nome) == 0) {
            criar(ligacao, nome);
        }
        try (PreparedStatement leitura = ligacao.prepareStatement("SELECT valor_atual FROM idus_contador WHERE nome = ?")) {
            leitura.setString(1, nome);
            try (ResultSet resultado = leitura.executeQuery()) {
                if (!resultado.next()) {
                    throw new HibernateException("Contador do IDUS inexistente: " + nome);
                }
                return resultado.getInt(1);
            }
        }
    }

    private static int incrementar(Connection ligacao, String nome) throws SQLException {
        try (PreparedStatement atualizacao = ligacao.prepareStatement(
                "UPDATE idus_contador SET valor_atual = valor_atual + 1 WHERE nome = ?")) {
            atualizacao.setString(1, nome);
            return atualizacao.executeUpdate();
        }
    }

    private static void criar(Connection ligacao, String nome) throws SQLException {
        try (PreparedStatement insercao = ligacao.prepareStatement(
                "INSERT INTO idus_contador (nome, valor_atual) VALUES (?, 1)")) {
            insercao.setString(1, nome);
            insercao.executeUpdate();
        }
    }
}

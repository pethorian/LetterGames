package br.com.lettergames.estatisticas.consumo;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classifica a falha do listener percorrendo a cadeia de causas (o container embrulha tudo em
 * {@code ListenerExecutionFailedException}).
 */
public final class ClassificadorFalhas {

    // Infraestrutura indisponível ou concorrência: tende a se resolver sozinha.
    private static final List<Class<? extends Throwable>> TRANSITORIAS = List.of(
            TransientDataAccessException.class, // inclui locks otimista/pessimista e timeouts
            ObjectOptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class,
            CannotCreateTransactionException.class,
            DataAccessResourceFailureException.class,
            CannotGetJdbcConnectionException.class,
            RecoverableDataAccessException.class,
            // Sem tradução do Spring (flush direto no EntityManager): exceções cruas do JPA/Hibernate/JDBC.
            jakarta.persistence.OptimisticLockException.class,
            jakarta.persistence.PessimisticLockException.class,
            jakarta.persistence.LockTimeoutException.class,
            jakarta.persistence.QueryTimeoutException.class,
            org.hibernate.exception.JDBCConnectionException.class,
            SQLTransientException.class,
            SQLRecoverableException.class);

    private ClassificadorFalhas() {
    }

    public static ClasseFalha classificar(Throwable excecao) {
        boolean transitoria = false;
        Map<Throwable, Boolean> vistas = new IdentityHashMap<>();
        for (Throwable t = excecao; t != null && vistas.put(t, true) == null; t = t.getCause()) {
            if (t instanceof EventoInvalidoException) {
                return ClasseFalha.INVALIDA;
            }
            transitoria |= transitoria(t);
        }
        return transitoria ? ClasseFalha.TRANSITORIA : ClasseFalha.PROCESSAMENTO;
    }

    private static boolean transitoria(Throwable t) {
        return TRANSITORIAS.stream().anyMatch(tipo -> tipo.isInstance(t));
    }
}

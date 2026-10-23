package br.com.lettergames.backlog.dominio;

import br.com.lettergames.contratos.evento.StatusBacklog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/** Jogo no backlog de um usuário. Título e duração são snapshot do catálogo (ADR-001). */
@Entity
@Table(name = "itens_backlog")
public class ItemBacklog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, length = 100)
    private String usuarioId;

    @Column(name = "jogo_id", nullable = false)
    private Long jogoId;

    @Column(nullable = false)
    private String titulo;

    @Column(name = "duracao_media_horas")
    private Integer duracaoMediaHoras;

    // VARCHAR explícito: o dialeto H2 usaria o tipo ENUM nativo e a validação do schema falharia.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private StatusBacklog status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected ItemBacklog() {
    }

    private ItemBacklog(String usuarioId, Long jogoId, String titulo, Integer duracaoMediaHoras, Instant agora) {
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.jogoId = Objects.requireNonNull(jogoId);
        this.titulo = Objects.requireNonNull(titulo);
        this.duracaoMediaHoras = duracaoMediaHoras;
        this.status = StatusBacklog.QUERO_JOGAR;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public static ItemBacklog novo(String usuarioId, Long jogoId, String titulo, Integer duracaoMediaHoras) {
        return new ItemBacklog(usuarioId, jogoId, titulo, duracaoMediaHoras, Instant.now());
    }

    /**
     * Aplica a transição validando as regras do domínio.
     *
     * @return o status anterior
     */
    public StatusBacklog alterarStatus(StatusBacklog novo) {
        Objects.requireNonNull(novo);
        TransicoesStatus.validar(status, novo);
        StatusBacklog anterior = status;
        status = novo;
        atualizadoEm = Instant.now();
        return anterior;
    }

    public Long getId() {
        return id;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public Long getJogoId() {
        return jogoId;
    }

    public String getTitulo() {
        return titulo;
    }

    public Integer getDuracaoMediaHoras() {
        return duracaoMediaHoras;
    }

    public StatusBacklog getStatus() {
        return status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}

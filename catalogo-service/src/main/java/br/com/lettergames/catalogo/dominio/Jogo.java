package br.com.lettergames.catalogo.dominio;

import br.com.lettergames.contratos.catalogo.JogoDTO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "jogos")
public class Jogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false)
    private String plataforma;

    @Column(nullable = false)
    private String genero;

    @Column(name = "duracao_media_horas", nullable = false)
    private Integer duracaoMediaHoras;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected Jogo() {
    }

    public Jogo(String titulo, String plataforma, String genero, Integer duracaoMediaHoras) {
        preencher(titulo, plataforma, genero, duracaoMediaHoras);
    }

    public void preencher(String titulo, String plataforma, String genero, Integer duracaoMediaHoras) {
        this.titulo = titulo;
        this.plataforma = plataforma;
        this.genero = genero;
        this.duracaoMediaHoras = duracaoMediaHoras;
        this.atualizadoEm = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getPlataforma() {
        return plataforma;
    }

    public String getGenero() {
        return genero;
    }

    public Integer getDuracaoMediaHoras() {
        return duracaoMediaHoras;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public JogoDTO paraDto() {
        return new JogoDTO(id, titulo, plataforma, genero, duracaoMediaHoras);
    }
}

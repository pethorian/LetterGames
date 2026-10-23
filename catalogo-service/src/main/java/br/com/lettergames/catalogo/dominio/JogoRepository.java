package br.com.lettergames.catalogo.dominio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JogoRepository extends JpaRepository<Jogo, Long> {

    List<Jogo> findByPlataformaIgnoreCaseOrderById(String plataforma);
}

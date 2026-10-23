package br.com.lettergames.catalogo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Cria jogos próprios do teste, sem alterar o seed compartilhado pelos demais. */
final class JogosDeTeste {

    private JogosDeTeste() {
    }

    static long criar(MockMvc mvc, String corpoJson) throws Exception {
        String location = mvc.perform(post("/jogos").contentType(MediaType.APPLICATION_JSON).content(corpoJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }
}

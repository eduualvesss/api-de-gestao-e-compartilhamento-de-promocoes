package com.ecommerce.catalogo.exception;

import java.time.Instant;
import java.util.List;

/** Padrão único de resposta de erro. */
public record ErroResposta(Instant timestamp, int status, String erro, String mensagem,
                           String caminho, List<String> detalhes) {

    public static ErroResposta of(int status, String erro, String mensagem, String caminho, List<String> detalhes) {
        return new ErroResposta(Instant.now(), status, erro, mensagem, caminho, detalhes);
    }
}

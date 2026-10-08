package com.ecommerce.catalogo.exception;

/** Violação de regra de negócio/entrada inválida (HTTP 400). */
public class RegraDeNegocioException extends RuntimeException {
    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}

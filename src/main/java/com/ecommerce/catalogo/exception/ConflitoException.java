package com.ecommerce.catalogo.exception;

/** Conflito de estado, ex.: SKU duplicado ou remoção bloqueada (HTTP 409). */
public class ConflitoException extends RuntimeException {
    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}

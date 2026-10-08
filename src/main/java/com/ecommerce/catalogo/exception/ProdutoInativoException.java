package com.ecommerce.catalogo.exception;

/** Operação sobre produto inativo (HTTP 422). */
public class ProdutoInativoException extends RuntimeException {
    public ProdutoInativoException(String sku) {
        super("O produto " + sku + " está inativo e não aceita operações.");
    }
}

package com.ecommerce.catalogo.exception;

/** Reservar mais do que o disponível (HTTP 409). */
public class EstoqueInsuficienteException extends RuntimeException {
    public EstoqueInsuficienteException(String sku, int disponivel, int solicitado) {
        super("Estoque insuficiente para o SKU %s: disponível=%d, solicitado=%d"
                .formatted(sku, disponivel, solicitado));
    }
}

package com.ecommerce.catalogo.model;

import java.math.BigDecimal;

/** Estratégia (padrão Strategy) de cálculo de desconto. Nunca altera o preço do produto. */
public interface RegraDesconto {

    /** @return valor monetário do desconto (>= 0) para a quantidade informada; ZERO se não aplicável. */
    BigDecimal aplicarDesconto(Produto produto, int quantidade);
}

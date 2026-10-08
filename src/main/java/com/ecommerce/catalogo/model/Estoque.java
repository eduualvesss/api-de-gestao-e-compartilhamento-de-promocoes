package com.ecommerce.catalogo.model;

import com.ecommerce.catalogo.exception.EstoqueInsuficienteException;
import com.ecommerce.catalogo.exception.RegraDeNegocioException;
import jakarta.persistence.*;

@Entity
@Table(name = "estoque")
public class Estoque {

    /** PK compartilhada com Produto (PK e FK ao mesmo tempo). */
    @Id
    private String sku;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "sku")
    private Produto produto;

    @Column(name = "quantidade_disponivel", nullable = false)
    private int quantidadeDisponivel;

    protected Estoque() {
    }

    public Estoque(Produto produto, int quantidadeInicial) {
        this.produto = produto;
        this.quantidadeDisponivel = quantidadeInicial;
    }

    public void reservar(int qtd) {
        if (qtd <= 0) {
            throw new RegraDeNegocioException("A quantidade deve ser maior que zero");
        }
        if (qtd > quantidadeDisponivel) {
            throw new EstoqueInsuficienteException(sku, quantidadeDisponivel, qtd);
        }
        quantidadeDisponivel -= qtd;
    }

    public void repor(int qtd) {
        if (qtd <= 0) {
            throw new RegraDeNegocioException("A quantidade deve ser maior que zero");
        }
        quantidadeDisponivel += qtd;
    }

    public String getSku() { return sku; }
    public Produto getProduto() { return produto; }
    public int getQuantidadeDisponivel() { return quantidadeDisponivel; }
}

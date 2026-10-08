package com.ecommerce.catalogo.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Herança JOINED: cada subtipo tem colunas obrigatórias próprias (NOT NULL),
 * o que SINGLE_TABLE não permitiria sem deixar colunas nulas e misturadas.
 * Reforça o isolamento de domínio (físico x digital).
 */
@Entity
@Table(name = "produto")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo_produto", discriminatorType = DiscriminatorType.STRING)
public abstract class Produto {

    @Id
    @Column(length = 50)
    private String sku;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private boolean ativo = true;

    protected Produto() {
    }

    protected Produto(String sku, String nome, BigDecimal preco, boolean ativo) {
        this.sku = sku;
        this.nome = nome;
        this.preco = preco;
        this.ativo = ativo;
    }

    public abstract String getTipoProduto();

    public String getSku() { return sku; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}

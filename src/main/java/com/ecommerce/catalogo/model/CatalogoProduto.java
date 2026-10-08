package com.ecommerce.catalogo.model;

import jakarta.persistence.*;

/** Tabela de associação Catalogo_Produto com a coluna extra "ordem". */
@Entity
@Table(name = "catalogo_produto")
public class CatalogoProduto {

    @EmbeddedId
    private CatalogoProdutoId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idCatalogo")
    @JoinColumn(name = "id_catalogo")
    private Catalogo catalogo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("sku")
    @JoinColumn(name = "sku")
    private Produto produto;

    @Column(nullable = false)
    private int ordem;

    protected CatalogoProduto() {
    }

    public CatalogoProduto(Catalogo catalogo, Produto produto, int ordem) {
        this.id = new CatalogoProdutoId(catalogo.getId(), produto.getSku());
        this.catalogo = catalogo;
        this.produto = produto;
        this.ordem = ordem;
    }

    public CatalogoProdutoId getId() { return id; }
    public int getOrdem() { return ordem; }
    public void setOrdem(int ordem) { this.ordem = ordem; }
}

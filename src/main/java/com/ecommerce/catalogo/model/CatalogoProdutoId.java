package com.ecommerce.catalogo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CatalogoProdutoId implements Serializable {

    @Column(name = "id_catalogo")
    private Long idCatalogo;

    @Column(name = "sku")
    private String sku;

    protected CatalogoProdutoId() {
    }

    public CatalogoProdutoId(Long idCatalogo, String sku) {
        this.idCatalogo = idCatalogo;
        this.sku = sku;
    }

    public Long getIdCatalogo() { return idCatalogo; }
    public String getSku() { return sku; }

    @Override
    public boolean equals(Object o) {
        return o instanceof CatalogoProdutoId other
                && Objects.equals(idCatalogo, other.idCatalogo) && Objects.equals(sku, other.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idCatalogo, sku);
    }
}

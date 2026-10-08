package com.ecommerce.catalogo.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "produto_digital")
@DiscriminatorValue("DIGITAL")
public class ProdutoDigital extends Produto {

    @Column(name = "link_download", nullable = false)
    private String linkDownload;

    @Column(name = "tipo_licenca", nullable = false)
    private String tipoLicenca;

    protected ProdutoDigital() {
    }

    public ProdutoDigital(String sku, String nome, BigDecimal preco, boolean ativo,
                          String linkDownload, String tipoLicenca) {
        super(sku, nome, preco, ativo);
        this.linkDownload = linkDownload;
        this.tipoLicenca = tipoLicenca;
    }

    /** Gera link com token único e expiração de 24h (simulado). */
    public String gerarLinkTemporario() {
        long expira = Instant.now().plusSeconds(24 * 3600).getEpochSecond();
        return linkDownload + "?token=" + UUID.randomUUID() + "&expira=" + expira;
    }

    @Override
    public String getTipoProduto() { return "DIGITAL"; }

    public String getLinkDownload() { return linkDownload; }
    public void setLinkDownload(String linkDownload) { this.linkDownload = linkDownload; }
    public String getTipoLicenca() { return tipoLicenca; }
    public void setTipoLicenca(String tipoLicenca) { this.tipoLicenca = tipoLicenca; }
}

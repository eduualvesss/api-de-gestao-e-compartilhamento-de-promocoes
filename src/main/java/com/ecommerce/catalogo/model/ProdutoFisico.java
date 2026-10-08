package com.ecommerce.catalogo.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "produto_fisico")
@DiscriminatorValue("FISICO")
public class ProdutoFisico extends Produto {

    /** Tarifa de exemplo: R$ 0,50 por kg por 100 km, mais R$ 5,00 fixos. */
    private static final BigDecimal FIXO = new BigDecimal("5.00");
    private static final BigDecimal TARIFA = new BigDecimal("0.50");

    @Column(nullable = false)
    private double peso;
    @Column(nullable = false)
    private double altura;
    @Column(nullable = false)
    private double largura;
    @Column(nullable = false)
    private double profundidade;

    protected ProdutoFisico() {
    }

    public ProdutoFisico(String sku, String nome, BigDecimal preco, boolean ativo,
                         double peso, double altura, double largura, double profundidade) {
        super(sku, nome, preco, ativo);
        this.peso = peso;
        this.altura = altura;
        this.largura = largura;
        this.profundidade = profundidade;
    }

    public BigDecimal calcularFrete(double distanciaKm) {
        if (distanciaKm < 0) {
            throw new IllegalArgumentException("Distância não pode ser negativa");
        }
        BigDecimal variavel = TARIFA.multiply(BigDecimal.valueOf(peso))
                .multiply(BigDecimal.valueOf(distanciaKm / 100.0));
        return FIXO.add(variavel).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getTipoProduto() { return "FISICO"; }

    public double getPeso() { return peso; }
    public void setPeso(double peso) { this.peso = peso; }
    public double getAltura() { return altura; }
    public void setAltura(double altura) { this.altura = altura; }
    public double getLargura() { return largura; }
    public void setLargura(double largura) { this.largura = largura; }
    public double getProfundidade() { return profundidade; }
    public void setProfundidade(double profundidade) { this.profundidade = profundidade; }
}

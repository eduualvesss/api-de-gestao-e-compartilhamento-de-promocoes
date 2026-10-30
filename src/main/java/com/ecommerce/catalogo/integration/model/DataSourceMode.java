package com.ecommerce.catalogo.integration.model;

/**
 * Origem explícita dos dados da oferta.
 * Garante transparência absoluta: dados de sandbox nunca são disfarçados de dados reais.
 */
public enum DataSourceMode {
    REAL,
    SANDBOX
}


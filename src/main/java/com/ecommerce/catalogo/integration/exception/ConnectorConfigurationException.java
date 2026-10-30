package com.ecommerce.catalogo.integration.exception;

import com.ecommerce.catalogo.integration.model.RetailerId;

/**
 * Exceção lançada quando um conector está habilitado em modo REAL,
 * porém sem credenciais configuradas (impede geração de dados falsos silenciosos).
 */
public class ConnectorConfigurationException extends RuntimeException {

    private final RetailerId retailer;

    public ConnectorConfigurationException(RetailerId retailer, String message) {
        super(message);
        this.retailer = retailer;
    }

    public RetailerId getRetailer() {
        return retailer;
    }
}


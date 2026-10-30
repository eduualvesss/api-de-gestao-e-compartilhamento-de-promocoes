package com.ecommerce.catalogo.integration.exception;

import com.ecommerce.catalogo.integration.model.RetailerId;

/**
 * Exceção de comunicação, rate limit ou parsing em um conector externo.
 */
public class ConnectorExecutionException extends RuntimeException {

    private final RetailerId retailer;
    private final boolean retryable;

    public ConnectorExecutionException(RetailerId retailer, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retailer = retailer;
        this.retryable = retryable;
    }

    public ConnectorExecutionException(RetailerId retailer, String message, boolean retryable) {
        super(message);
        this.retailer = retailer;
        this.retryable = retryable;
    }

    public RetailerId getRetailer() {
        return retailer;
    }

    public boolean isRetryable() {
        return retryable;
    }
}


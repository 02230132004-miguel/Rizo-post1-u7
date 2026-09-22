package com.example.multas.domain;

/**
 * Excepción lanzada cuando una pasarela de pago rechaza una transacción.
 */
public class PagoRechazadoException extends RuntimeException {
    public PagoRechazadoException(String message) {
        super(message);
    }
}

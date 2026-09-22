package com.example.multas.domain;

/**
 * Record inmutable que representa el resultado neutral de una transacción de pago.
 * Totalmente desacoplado de pasarelas o protocolos específicos.
 */
public record ResultadoPago(
        String proveedor,
        boolean exitoso,
        String referenciaExterna,
        String mensaje
) {
}

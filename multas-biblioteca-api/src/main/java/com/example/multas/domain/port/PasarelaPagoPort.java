package com.example.multas.domain.port;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.model.Multa;

/**
 * Puerto de Dominio (Hexagonal / Clean Architecture).
 * Define el contrato abstracto para interactuar con pasarelas de pago externas
 * sin acoplamiento a frameworks (Spring) ni protocolos de comunicación (HTTP/REST).
 */
public interface PasarelaPagoPort {

    ResultadoPago procesar(Multa multa);
}

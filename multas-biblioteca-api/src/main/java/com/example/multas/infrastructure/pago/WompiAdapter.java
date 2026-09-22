package com.example.multas.infrastructure.pago;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.domain.port.PasarelaPagoPort;
import com.example.multas.model.Multa;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador de Infraestructura para la pasarela comercial Wompi.
 * Se activa condicionalmente cuando app.pagos.proveedor=wompi.
 * Maneja las particularidades de Wompi como montos en centavos y referencias específicas.
 */
@Component
@ConditionalOnProperty(prefix = "app.pagos", name = "proveedor", havingValue = "wompi")
public class WompiAdapter implements PasarelaPagoPort {

    private final RestTemplate restTemplate;
    private final String url;

    public WompiAdapter(RestTemplate restTemplate,
                        @Value("${app.pagos.wompi.url}") String url) {
        this.restTemplate = restTemplate;
        this.url = url;
    }

    @Override
    public ResultadoPago procesar(Multa multa) {
        // Conversión a centavos requerida por el estándar de Wompi
        long montoEnCentavos = multa.getMonto()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        Map<String, Object> payload = Map.of(
                "amount_in_cents", montoEnCentavos,
                "currency", "COP",
                "customer_email", multa.getEstudianteId() + "@udes.edu.co",
                "reference", "MULTA-" + (multa.getId() != null ? multa.getId() : 0L)
        );

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, payload, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                String referencia = "WOMPI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                return new ResultadoPago("WOMPI", true, referencia, "Transacción aprobada por Wompi");
            } else {
                return new ResultadoPago("WOMPI", false, null, "Wompi rechazó la transacción con código: " + response.getStatusCode());
            }
        } catch (RestClientException ex) {
            return new ResultadoPago("WOMPI", false, null, "Error de comunicación con Wompi: " + ex.getMessage());
        }
    }
}

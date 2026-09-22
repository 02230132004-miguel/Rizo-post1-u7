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

import java.util.Map;
import java.util.UUID;

/**
 * Adaptador de Infraestructura para la pasarela institucional PagosUDES.
 * Se activa condicionalmente cuando app.pagos.proveedor=pagosudes (o por defecto si no se especifica).
 */
@Component
@ConditionalOnProperty(prefix = "app.pagos", name = "proveedor", havingValue = "pagosudes", matchIfMissing = true)
public class PagosUdesAdapter implements PasarelaPagoPort {

    private final RestTemplate restTemplate;
    private final String url;

    public PagosUdesAdapter(RestTemplate restTemplate,
                            @Value("${app.pagos.pagosudes.url}") String url) {
        this.restTemplate = restTemplate;
        this.url = url;
    }

    @Override
    public ResultadoPago procesar(Multa multa) {
        Map<String, Object> payload = Map.of(
                "multaId", multa.getId() != null ? multa.getId() : 0L,
                "estudianteId", multa.getEstudianteId(),
                "monto", multa.getMonto()
        );

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, payload, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                String referencia = "PAGOSUDES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                return new ResultadoPago("PAGOS_UDES", true, referencia, "Pago procesado exitosamente en PagosUDES");
            } else {
                return new ResultadoPago("PAGOS_UDES", false, null, "La pasarela PagosUDES respondió con estado: " + response.getStatusCode());
            }
        } catch (RestClientException ex) {
            return new ResultadoPago("PAGOS_UDES", false, null, "Fallo de comunicación con PagosUDES: " + ex.getMessage());
        }
    }
}

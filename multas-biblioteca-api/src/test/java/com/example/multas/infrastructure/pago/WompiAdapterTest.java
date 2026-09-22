package com.example.multas.infrastructure.pago;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.model.Multa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WompiAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    private WompiAdapter wompiAdapter;
    private final String url = "http://localhost:9002/wompi/transactions";

    @BeforeEach
    void setUp() {
        wompiAdapter = new WompiAdapter(restTemplate, url);
    }

    @Test
    @DisplayName("Debe convertir monto a centavos y procesar transacción aprobada en Wompi")
    void debeConvertirMontoACentavosYProcesarExitosamente() {
        Multa multa = new Multa("EST-555", "Retraso Libro", 4, new BigDecimal("2000"));
        multa.setId(10L);

        when(restTemplate.postForEntity(eq(url), any(Map.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "APPROVED"), HttpStatus.OK));

        ResultadoPago resultado = wompiAdapter.procesar(multa);

        assertThat(resultado.exitoso()).isTrue();
        assertThat(resultado.proveedor()).isEqualTo("WOMPI");
        assertThat(resultado.referenciaExterna()).startsWith("WOMPI-");
        assertThat(resultado.mensaje()).contains("aprobada");

        // Verificar payload con conversión a centavos (2000 COP * 100 = 200000 centavos)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForEntity(eq(url), captor.capture(), eq(Map.class));

        Map<String, Object> capturedPayload = captor.getValue();
        assertThat(capturedPayload.get("amount_in_cents")).isEqualTo(200000L);
        assertThat(capturedPayload.get("currency")).isEqualTo("COP");
        assertThat(capturedPayload.get("reference")).isEqualTo("MULTA-10");
        assertThat(capturedPayload.get("customer_email")).isEqualTo("EST-555@udes.edu.co");
    }

    @Test
    @DisplayName("Debe manejar error de comunicación o rechazo retornando ResultadoPago no exitoso")
    void debeManejarErrorDeComunicacion() {
        Multa multa = new Multa("EST-555", "Retraso Libro", 2, new BigDecimal("1000"));
        multa.setId(10L);

        when(restTemplate.postForEntity(eq(url), any(Map.class), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));

        ResultadoPago resultado = wompiAdapter.procesar(multa);

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.proveedor()).isEqualTo("WOMPI");
        assertThat(resultado.referenciaExterna()).isNull();
        assertThat(resultado.mensaje()).contains("Error de comunicación con Wompi");
    }
}

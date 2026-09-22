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
class PagosUdesAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    private PagosUdesAdapter pagosUdesAdapter;
    private final String url = "http://localhost:9001/pagosudes/transacciones";

    @BeforeEach
    void setUp() {
        pagosUdesAdapter = new PagosUdesAdapter(restTemplate, url);
    }

    @Test
    @DisplayName("Debe procesar transacción aprobada en PagosUDES")
    void debeProcesarPagoExitosamente() {
        Multa multa = new Multa("EST-777", "Retraso Libro", 3, new BigDecimal("1500"));
        multa.setId(5L);

        when(restTemplate.postForEntity(eq(url), any(Map.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "SUCCESS"), HttpStatus.OK));

        ResultadoPago resultado = pagosUdesAdapter.procesar(multa);

        assertThat(resultado.exitoso()).isTrue();
        assertThat(resultado.proveedor()).isEqualTo("PAGOS_UDES");
        assertThat(resultado.referenciaExterna()).startsWith("PAGOSUDES-");
        assertThat(resultado.mensaje()).contains("PagosUDES");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForEntity(eq(url), captor.capture(), eq(Map.class));

        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("multaId")).isEqualTo(5L);
        assertThat(payload.get("estudianteId")).isEqualTo("EST-777");
        assertThat(payload.get("monto")).isEqualTo(new BigDecimal("1500"));
    }

    @Test
    @DisplayName("Debe manejar fallos de comunicación con PagosUDES retornando resultado no exitoso")
    void debeManejarErrorDeRed() {
        Multa multa = new Multa("EST-777", "Retraso Libro", 3, new BigDecimal("1500"));
        multa.setId(5L);

        when(restTemplate.postForEntity(eq(url), any(Map.class), eq(Map.class)))
                .thenThrow(new RestClientException("Timeout connecting to server"));

        ResultadoPago resultado = pagosUdesAdapter.procesar(multa);

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.proveedor()).isEqualTo("PAGOS_UDES");
        assertThat(resultado.referenciaExterna()).isNull();
        assertThat(resultado.mensaje()).contains("Fallo de comunicación con PagosUDES");
    }
}

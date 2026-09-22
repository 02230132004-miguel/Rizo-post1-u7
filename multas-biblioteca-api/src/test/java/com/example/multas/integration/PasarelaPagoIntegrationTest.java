package com.example.multas.integration;

import com.example.multas.controller.dto.GenerarMultaRequest;
import com.example.multas.domain.port.PasarelaPagoPort;
import com.example.multas.infrastructure.pago.PagosUdesAdapter;
import com.example.multas.model.EstadoMulta;
import com.example.multas.model.Multa;
import com.example.multas.repository.MultaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PasarelaPagoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MultaRepository multaRepository;

    @Autowired
    private PasarelaPagoPort pasarelaPagoPort;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.pagos.pagosudes.url}")
    private String pagosUdesUrl;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        multaRepository.deleteAll();
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    @DisplayName("Punto 3: Debe inyectar PagosUdesAdapter como el bean activo de PasarelaPagoPort")
    void debeInyectarAdaptadorPorDefecto() {
        assertThat(pasarelaPagoPort).isNotNull();
        assertThat(pasarelaPagoPort).isInstanceOf(PagosUdesAdapter.class);
    }

    @Test
    @DisplayName("Flujo Integrado: Generar multa y procesar pago en línea exitoso con pasarela simulada")
    void debeGenerarMultaYPagarEnLineaExitosamente() throws Exception {
        // 1. Generar multa vía API
        GenerarMultaRequest request = new GenerarMultaRequest("EST-2024", "Retraso Libro Clean Code", 6);

        String responseContent = mockMvc.perform(post("/api/multas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado", is("PENDIENTE")))
                .andExpect(jsonPath("$.monto", is(3000)))
                .andReturn().getResponse().getContentAsString();

        Multa creada = objectMapper.readValue(responseContent, Multa.class);
        Long multaId = creada.getId();

        // 2. Mockear el servidor HTTP de la pasarela PagosUDES respondiendo 200 OK
        mockServer.expect(ExpectedCount.once(), requestTo(pagosUdesUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));

        // 3. Invocar endpoint de pago en línea
        mockMvc.perform(post("/api/multas/" + multaId + "/pagar-en-linea")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("PAGADA")))
                .andExpect(jsonPath("$.metodoPago", is("PAGOS_UDES")))
                .andExpect(jsonPath("$.fechaPago").isNotEmpty());

        mockServer.verify();

        // 4. Verificar persistencia en H2
        Multa persistida = multaRepository.findById(multaId).orElseThrow();
        assertThat(persistida.getEstado()).isEqualTo(EstadoMulta.PAGADA);
        assertThat(persistida.getMetodoPago()).isEqualTo("PAGOS_UDES");
    }

    @Test
    @DisplayName("Flujo Integrado: Mapear rechazo de pasarela a código HTTP 402 Payment Required")
    void debeRetornar402CuandoPasarelaFalla() throws Exception {
        // 1. Guardar multa en H2
        Multa multa = new Multa("EST-2024", "Retraso Libro DDD", 4);
        multa = multaRepository.save(multa);

        // 2. Mockear el servidor HTTP de la pasarela respondiendo 500 o error
        mockServer.expect(ExpectedCount.once(), requestTo(pagosUdesUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        // 3. Invocar endpoint y esperar 402 Payment Required
        mockMvc.perform(post("/api/multas/" + multa.getId() + "/pagar-en-linea")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status", is(402)))
                .andExpect(jsonPath("$.error", is("Payment Required")))
                .andExpect(jsonPath("$.message", containsString("PAGOS_UDES")));

        mockServer.verify();

        // 4. Verificar que la multa sigue PENDIENTE en H2
        Multa persistida = multaRepository.findById(multa.getId()).orElseThrow();
        assertThat(persistida.getEstado()).isEqualTo(EstadoMulta.PENDIENTE);
    }
}

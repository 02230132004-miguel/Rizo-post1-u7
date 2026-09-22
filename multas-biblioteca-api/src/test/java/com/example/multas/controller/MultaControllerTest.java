package com.example.multas.controller;

import com.example.multas.controller.dto.GenerarMultaRequest;
import com.example.multas.domain.PagoRechazadoException;
import com.example.multas.model.*;
import com.example.multas.service.MultaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MultaController.class)
class MultaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MultaService multaService;

    private Multa multaEjemplo;

    @BeforeEach
    void setUp() {
        multaEjemplo = new Multa("EST-101", "Libro de Redes", 3, new BigDecimal("1500"));
        multaEjemplo.setId(1L);
        multaEjemplo.setFechaGeneracion(LocalDate.now());
    }

    @Test
    @DisplayName("GET /api/multas debe retornar 200 OK y la lista de multas")
    void debeListarTodasLasMultas() throws Exception {
        when(multaService.listarTodas()).thenReturn(List.of(multaEjemplo));

        mockMvc.perform(get("/api/multas")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].estudianteId", is("EST-101")))
                .andExpect(jsonPath("$[0].monto", is(1500)));
    }

    @Test
    @DisplayName("GET /api/multas/{id} con ID existente debe retornar 200 OK")
    void debeRetornarMultaPorId() throws Exception {
        when(multaService.buscarPorId(1L)).thenReturn(multaEjemplo);

        mockMvc.perform(get("/api/multas/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.concepto", is("Libro de Redes")));
    }

    @Test
    @DisplayName("GET /api/multas/{id} con ID inexistente debe retornar 404 Not Found")
    void debeRetornar404SiNoExiste() throws Exception {
        when(multaService.buscarPorId(99L))
                .thenThrow(new MultaNotFoundException("Multa no encontrada con ID: 99"));

        mockMvc.perform(get("/api/multas/99")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("Multa no encontrada")));
    }

    @Test
    @DisplayName("GET /api/multas/estudiante/{estudianteId} debe retornar 200 OK")
    void debeListarPorEstudiante() throws Exception {
        when(multaService.listarPorEstudiante("EST-101")).thenReturn(List.of(multaEjemplo));

        mockMvc.perform(get("/api/multas/estudiante/EST-101")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estudianteId", is("EST-101")));
    }

    @Test
    @DisplayName("POST /api/multas con datos válidos debe retornar 201 Created")
    void debeCrearMultaExitosamente() throws Exception {
        GenerarMultaRequest request = new GenerarMultaRequest("EST-101", "Libro de Redes", 3);
        when(multaService.generar(eq("EST-101"), eq("Libro de Redes"), eq(3)))
                .thenReturn(multaEjemplo);

        mockMvc.perform(post("/api/multas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.estudianteId", is("EST-101")))
                .andExpect(jsonPath("$.estado", is("PENDIENTE")));
    }

    @Test
    @DisplayName("POST /api/multas con datos inválidos (diasAtraso <= 0) debe retornar 400 Bad Request")
    void debeRetornar400ParaValidacionInvalida() throws Exception {
        GenerarMultaRequest requestInvalido = new GenerarMultaRequest("", "", 0);

        mockMvc.perform(post("/api/multas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.detalles", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/multas cuando se supera el límite de 3 pendientes debe retornar 409 Conflict")
    void debeRetornar409PorLimitePendientes() throws Exception {
        GenerarMultaRequest request = new GenerarMultaRequest("EST-101", "Libro de SO", 2);
        when(multaService.generar(anyString(), anyString(), anyInt()))
                .thenThrow(new LimiteMultasPendientesException("El estudiante EST-101 ha alcanzado el límite máximo"));

        mockMvc.perform(post("/api/multas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("ha alcanzado el límite")));
    }

    @Test
    @DisplayName("PATCH /api/multas/{id}/pagar debe retornar 200 OK y la multa pagada")
    void debePagarEnVentanilla() throws Exception {
        multaEjemplo.marcarComoPagada("VENTANILLA");
        when(multaService.pagarEnVentanilla(1L)).thenReturn(multaEjemplo);

        mockMvc.perform(patch("/api/multas/1/pagar")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("PAGADA")))
                .andExpect(jsonPath("$.metodoPago", is("VENTANILLA")));
    }

    @Test
    @DisplayName("PATCH /api/multas/{id}/pagar en multa ya pagada debe retornar 409 Conflict")
    void debeRetornar409SiMultaYaEstaPagada() throws Exception {
        when(multaService.pagarEnVentanilla(1L))
                .thenThrow(new MultaYaPagadaException("La multa con ID 1 ya se encuentra pagada."));

        mockMvc.perform(patch("/api/multas/1/pagar")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("ya se encuentra pagada")));
    }

    @Test
    @DisplayName("POST /api/multas/{id}/pagar-en-linea exitoso debe retornar 200 OK")
    void debePagarEnLineaExitosamente() throws Exception {
        multaEjemplo.marcarComoPagada("PAGOS_UDES");
        when(multaService.pagarConPasarela(1L)).thenReturn(multaEjemplo);

        mockMvc.perform(post("/api/multas/1/pagar-en-linea")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("PAGADA")))
                .andExpect(jsonPath("$.metodoPago", is("PAGOS_UDES")));
    }

    @Test
    @DisplayName("POST /api/multas/{id}/pagar-en-linea con rechazo de pasarela debe retornar 402 Payment Required")
    void debeRetornar402CuandoPasarelaRechaza() throws Exception {
        when(multaService.pagarConPasarela(1L))
                .thenThrow(new PagoRechazadoException("Pago rechazado por la pasarela PAGOS_UDES: Fallo de comunicación"));

        mockMvc.perform(post("/api/multas/1/pagar-en-linea")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status", is(402)))
                .andExpect(jsonPath("$.error", is("Payment Required")))
                .andExpect(jsonPath("$.message", containsString("Pago rechazado")));
    }
}

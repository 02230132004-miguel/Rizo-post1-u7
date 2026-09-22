package com.example.multas.service;

import com.example.multas.domain.PagoRechazadoException;
import com.example.multas.domain.ResultadoPago;
import com.example.multas.domain.port.PasarelaPagoPort;
import com.example.multas.model.*;
import com.example.multas.repository.MultaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultaServiceTest {

    @Mock
    private MultaRepository multaRepository;

    @Mock
    private PasarelaPagoPort pasarelaPagoPort;

    @InjectMocks
    private MultaService multaService;

    private Multa multaEjemplo;

    @BeforeEach
    void setUp() {
        multaEjemplo = new Multa("EST-001", "Retraso en entrega de libro", 5, new BigDecimal("2500"));
        multaEjemplo.setId(1L);
    }

    @Nested
    @DisplayName("Punto 1: Pruebas de Cálculo de Monto y Reglas de Dominio")
    class CalculoMontoTests {

        @Test
        @DisplayName("Debe calcular monto proporcional para días regulares ($500 por día)")
        void debeCalcularMontoProporcional() {
            BigDecimal monto5Dias = Multa.calcularMonto(5);
            BigDecimal monto10Dias = Multa.calcularMonto(10);

            assertThat(monto5Dias).isEqualByComparingTo(new BigDecimal("2500"));
            assertThat(monto10Dias).isEqualByComparingTo(new BigDecimal("5000"));
        }

        @Test
        @DisplayName("Debe alcanzar exactamente el tope máximo en 30 días ($15.000)")
        void debeAlcanzarTopeExactoEn30Dias() {
            BigDecimal monto30Dias = Multa.calcularMonto(30);
            assertThat(monto30Dias).isEqualByComparingTo(new BigDecimal("15000"));
        }

        @Test
        @DisplayName("Debe aplicar el tope máximo de $15.000 cuando los días superan 30 (ej. 40 o 100 días)")
        void debeAplicarTopeMaximo() {
            BigDecimal monto40Dias = Multa.calcularMonto(40);
            BigDecimal monto100Dias = Multa.calcularMonto(100);

            assertThat(monto40Dias).isEqualByComparingTo(new BigDecimal("15000"));
            assertThat(monto100Dias).isEqualByComparingTo(new BigDecimal("15000"));
        }

        @Test
        @DisplayName("Debe retornar cero si los días de atraso son menores o iguales a cero")
        void debeRetornarCeroParaDiasInvalidos() {
            BigDecimal monto0 = Multa.calcularMonto(0);
            BigDecimal montoNegativo = Multa.calcularMonto(-5);

            assertThat(monto0).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(montoNegativo).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Punto 2: Generación de Multas y Límite de Pendientes")
    class GeneracionMultasTests {

        @Test
        @DisplayName("Debe generar una multa exitosamente si el estudiante tiene menos de 3 multas pendientes")
        void debeGenerarMultaSiMenorAlLimite() {
            when(multaRepository.countByEstudianteIdAndEstado("EST-001", EstadoMulta.PENDIENTE))
                    .thenReturn(2L);
            when(multaRepository.save(any(Multa.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Multa creada = multaService.generar("EST-001", "Libro de Cálculo I", 4);

            assertThat(creada).isNotNull();
            assertThat(creada.getEstudianteId()).isEqualTo("EST-001");
            assertThat(creada.getConcepto()).isEqualTo("Libro de Cálculo I");
            assertThat(creada.getDiasAtraso()).isEqualTo(4);
            assertThat(creada.getMonto()).isEqualByComparingTo(new BigDecimal("2000"));
            assertThat(creada.getEstado()).isEqualTo(EstadoMulta.PENDIENTE);
            verify(multaRepository, times(1)).countByEstudianteIdAndEstado("EST-001", EstadoMulta.PENDIENTE);
            verify(multaRepository, times(1)).save(any(Multa.class));
        }

        @Test
        @DisplayName("Debe lanzar LimiteMultasPendientesException si el estudiante ya tiene 3 multas pendientes")
        void debeLanzarExcepcionAlSuperarLimite() {
            when(multaRepository.countByEstudianteIdAndEstado("EST-001", EstadoMulta.PENDIENTE))
                    .thenReturn(3L);

            assertThatThrownBy(() -> multaService.generar("EST-001", "Libro de Física", 2))
                    .isInstanceOf(LimiteMultasPendientesException.class)
                    .hasMessageContaining("ha alcanzado el límite máximo");

            verify(multaRepository, never()).save(any(Multa.class));
        }
    }

    @Nested
    @DisplayName("Consultas de Multas")
    class ConsultasTests {

        @Test
        @DisplayName("Debe listar todas las multas")
        void debeListarTodas() {
            when(multaRepository.findAll()).thenReturn(List.of(multaEjemplo));
            List<Multa> resultado = multaService.listarTodas();
            assertThat(resultado).hasSize(1);
        }

        @Test
        @DisplayName("Debe listar por estudiante")
        void debeListarPorEstudiante() {
            when(multaRepository.findByEstudianteId("EST-001")).thenReturn(List.of(multaEjemplo));
            List<Multa> resultado = multaService.listarPorEstudiante("EST-001");
            assertThat(resultado).hasSize(1);
        }

        @Test
        @DisplayName("Debe buscar por ID existente")
        void debeBuscarPorIdExistente() {
            when(multaRepository.findById(1L)).thenReturn(Optional.of(multaEjemplo));
            Multa encontrada = multaService.buscarPorId(1L);
            assertThat(encontrada.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Debe lanzar MultaNotFoundException si el ID no existe")
        void debeLanzarExcepcionSiIdNoExiste() {
            when(multaRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> multaService.buscarPorId(99L))
                    .isInstanceOf(MultaNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("Pagos: Ventanilla y Pasarela en Línea")
    class PagosTests {

        @Test
        @DisplayName("Debe pagar en ventanilla exitosamente")
        void debePagarEnVentanilla() {
            when(multaRepository.findById(1L)).thenReturn(Optional.of(multaEjemplo));
            when(multaRepository.save(any(Multa.class))).thenAnswer(i -> i.getArgument(0));

            Multa pagada = multaService.pagarEnVentanilla(1L);

            assertThat(pagada.getEstado()).isEqualTo(EstadoMulta.PAGADA);
            assertThat(pagada.getMetodoPago()).isEqualTo("VENTANILLA");
            assertThat(pagada.getFechaPago()).isNotNull();
            verify(multaRepository, times(1)).save(multaEjemplo);
        }

        @Test
        @DisplayName("Debe lanzar MultaYaPagadaException si se intenta pagar en ventanilla una multa ya pagada")
        void debeRechazarPagoEnVentanillaSiYaPagada() {
            multaEjemplo.marcarComoPagada("VENTANILLA");
            when(multaRepository.findById(1L)).thenReturn(Optional.of(multaEjemplo));

            assertThatThrownBy(() -> multaService.pagarEnVentanilla(1L))
                    .isInstanceOf(MultaYaPagadaException.class)
                    .hasMessageContaining("ya se encuentra pagada");

            verify(multaRepository, never()).save(any(Multa.class));
        }

        @Test
        @DisplayName("Debe pagar en línea exitosamente usando el puerto de pasarela")
        void debePagarEnLineaExitosamente() {
            when(multaRepository.findById(1L)).thenReturn(Optional.of(multaEjemplo));
            when(pasarelaPagoPort.procesar(multaEjemplo))
                    .thenReturn(new ResultadoPago("PAGOS_UDES", true, "REF-12345", "Aprobado"));
            when(multaRepository.save(any(Multa.class))).thenAnswer(i -> i.getArgument(0));

            Multa pagada = multaService.pagarConPasarela(1L);

            assertThat(pagada.getEstado()).isEqualTo(EstadoMulta.PAGADA);
            assertThat(pagada.getMetodoPago()).isEqualTo("PAGOS_UDES");
            assertThat(pagada.getFechaPago()).isNotNull();
            verify(pasarelaPagoPort, times(1)).procesar(multaEjemplo);
            verify(multaRepository, times(1)).save(multaEjemplo);
        }

        @Test
        @DisplayName("Debe lanzar PagoRechazadoException cuando la pasarela rechaza la transacción")
        void debeLanzarPagoRechazadoException() {
            when(multaRepository.findById(1L)).thenReturn(Optional.of(multaEjemplo));
            when(pasarelaPagoPort.procesar(multaEjemplo))
                    .thenReturn(new ResultadoPago("PAGOS_UDES", false, null, "Fondos insuficientes o pasarela inactiva"));

            assertThatThrownBy(() -> multaService.pagarConPasarela(1L))
                    .isInstanceOf(PagoRechazadoException.class)
                    .hasMessageContaining("Pago rechazado");

            assertThat(multaEjemplo.getEstado()).isEqualTo(EstadoMulta.PENDIENTE);
            verify(multaRepository, never()).save(any(Multa.class));
        }

        @Test
        @DisplayName("Debe lanzar MultaYaPagadaException si se intenta pagar en línea una multa ya pagada")
        void debeRechazarPagoEnLineaSiYaPagada() {
            multaEjemplo.marcarComoPagada("VENTANILLA");
            when(multaRepository.findById(1L)).thenReturn(Optional.of(multaEjemplo));

            assertThatThrownBy(() -> multaService.pagarConPasarela(1L))
                    .isInstanceOf(MultaYaPagadaException.class);

            verify(pasarelaPagoPort, never()).procesar(any());
        }
    }
}

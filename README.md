# Post-contenido — Unidad 7: Patrones Arquitectónicos I: De Capas Simples a una Decisión Arquitectónica Justificada

* **Estudiante:** Miguel Angel Rizo Arias
* **Módulo:** Unidad 7 — Patrones Arquitectónicos I
* **Stack Tecnológico:** Java 17, Spring Boot 3.2.3, Spring Data JPA, H2 Database (In-Memory), Jakarta Validation, Maven, JUnit 5, Mockito, MockMvc.

---

## 1. Descripción del Proyecto

El sistema **`multas-biblioteca-api`** es una solución backend construida para la gestión integral de penalizaciones y multas por retraso en la devolución de material bibliográfico universitario. La solución combina una **arquitectura en capas tradicional** con el patrón **Puertos y Adaptadores (Arquitectura Hexagonal)** para el aislamiento de integraciones de pasarelas de pago externas (PagosUDES y Wompi).

---

## 2. Diagrama Arquitectónico y Estructura del Repositorio

### Diagrama Textual de Arquitectura (Puertos y Adaptadores / Clean Architecture)

```
+---------------------------------------------------------------------------------------+
|                                    CAPA WEB / REST                                    |
|  [MultaController] ---> [GenerarMultaRequest] / [ErrorResponse] / [GlobalExceptionH.] |
+------------------------------------------+--------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
|                                  CAPA DE APLICACIÓN                                   |
|  [MultaService] (@Transactional, orquestación, validación de reglas de negocio)       |
+---------------------+------------------------------------+----------------------------+
                      |                                    |
                      v                                    v
+------------------------------------+   +----------------------------------------------+
|          CAPA DE DOMINIO           |   |            PUERTOS DE DOMINIO                |
|  - Entidad rica: [Multa]           |   |  - Puerto Java Puro: [PasarelaPagoPort]      |
|  - Enum: [EstadoMulta]             |   |  - DTO Neutral: [ResultadoPago]              |
|  - Invariante: calcularMonto()     |   |  - Excepción: [PagoRechazadoException]       |
|  - Transición: marcarComoPagada()  |   |    (¡CERO dependencias de Spring / HTTP!)    |
+------------------------------------+   +----------------------------------------------+
                      ^                                    ^
                      |                                    |
+---------------------+--------------+   +-----------------+----------------------------+
|     INFRAESTRUCTURA DE DATOS       |   |         INFRAESTRUCTURA DE PAGOS             |
|  [MultaRepository] (Spring Data)   |   |  [@ConditionalOnProperty: app.pagos.proveedor|
|  - Conteo delegado SQL:            |   |  - [PagosUdesAdapter] (PagosUDES HTTP API)   |
|    countByEstudianteIdAndEstado    |   |  - [WompiAdapter] (Conversión a centavos)    |
|  - Motor H2 en memoria             |   |  - [RestTemplateConfig]                      |
+------------------------------------+   +----------------------------------------------+
```

### Estructura Final de Paquetes

```
multas-biblioteca-api/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/multas/
    │   │   ├── MultasBibliotecaApplication.java
    │   │   ├── controller/
    │   │   │   ├── MultaController.java
    │   │   │   ├── dto/
    │   │   │   │   ├── ErrorResponse.java
    │   │   │   │   └── GenerarMultaRequest.java
    │   │   │   └── exception/
    │   │   │       └── GlobalExceptionHandler.java
    │   │   ├── domain/
    │   │   │   ├── PagoRechazadoException.java
    │   │   │   ├── ResultadoPago.java
    │   │   │   └── port/
    │   │   │       └── PasarelaPagoPort.java
    │   │   ├── infrastructure/
    │   │   │   ├── config/
    │   │   │   │   └── RestTemplateConfig.java
    │   │   │   └── pago/
    │   │   │       ├── PagosUdesAdapter.java
    │   │   │       └── WompiAdapter.java
    │   │   ├── model/
    │   │   │   ├── EstadoMulta.java
    │   │   │   ├── LimiteMultasPendientesException.java
    │   │   │   ├── Multa.java
    │   │   │   ├── MultaNotFoundException.java
    │   │   │   └── MultaYaPagadaException.java
    │   │   ├── repository/
    │   │   │   └── MultaRepository.java
    │   │   └── service/
    │   │       └── MultaService.java
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/com/example/multas/
            ├── controller/
            │   └── MultaControllerTest.java
            ├── infrastructure/
            │   └── pago/
            │       ├── PagosUdesAdapterTest.java
            │       └── WompiAdapterTest.java
            ├── integration/
            │   └── PasarelaPagoIntegrationTest.java
            └── service/
                └── MultaServiceTest.java
```

---

## 3. Guía de Ejecución y Pruebas

### Prerrequisitos
- Java Development Kit (JDK) 17+
- Apache Maven 3.8+

### Compilación y Ejecución de Pruebas Automatizadas
Para ejecutar la suite completa de 33 pruebas unitarias y de integración:
```bash
cd multas-biblioteca-api
mvn clean test
```

### Ejecución de la Aplicación en Desarrollo
```bash
cd multas-biblioteca-api
mvn spring-boot:run
```
La aplicación iniciará en el puerto `8080`.

### Consola de Base de Datos H2
- **URL:** [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
- **JDBC URL:** `jdbc:h2:mem:multas_biblioteca_db`
- **User Name:** `sa`
- **Password:** *(vacío)*

---

## 4. Resumen de Endpoints de la API REST

| Método | Endpoint | Descripción | Respuestas HTTP |
|---|---|---|---|
| `GET` | `/api/multas` | Lista todas las multas registradas | `200 OK` |
| `GET` | `/api/multas/{id}` | Obtiene el detalle de una multa por su ID | `200 OK`, `404 NOT FOUND` |
| `GET` | `/api/multas/estudiante/{estudianteId}` | Lista las multas de un estudiante | `200 OK` |
| `POST` | `/api/multas` | Genera una nueva multa validando el límite de pendientes | `201 CREATED`, `400 BAD REQUEST`, `409 CONFLICT` |
| `PATCH` | `/api/multas/{id}/pagar` | Registra el pago presencial en ventanilla | `200 OK`, `404 NOT FOUND`, `409 CONFLICT` |
| `POST` | `/api/multas/{id}/pagar-en-linea` | Procesa el pago a través de la pasarela activa | `200 OK`, `402 PAYMENT REQUIRED`, `404 NOT FOUND`, `409 CONFLICT` |

---

## 5. Justificación Rigurosa de los 4 Puntos de Decisión de Diseño

### Punto 1: Cálculo del Monto — ¿Entidad o Service?
* **Decisión Implementada:** El método `public static BigDecimal calcularMonto(int diasAtraso)` y la lógica de transición de estado `marcarComoPagada(String metodoPago)` residen directamente en la entidad de dominio `Multa.java`.
* **Fundamento Teórico y Arquitectónico:**
  1. **Evitación del Antipatrón de Modelo de Dominio Anémico (*Anemic Domain Model*):** En el diseño guiado por el dominio (DDD), las entidades no deben ser meras bolsas de datos (getters y setters pasivos) desprovistas de comportamiento. Si la lógica de cálculo y la mutación de estado residieran exclusivamente en `MultaService`, la entidad quedaría reducida a una estructura anémica y el servicio asumiría responsabilidades que no le corresponden.
  2. **Regla de Negocio Pura e Invariante:** El cálculo de la multa ($500 por día de retraso hasta un tope de $15.000) es una regla matemática intrínseca y autocontenida que no requiere colaboradores externos, llamadas a bases de datos ni servicios de red. Pertenece por definición al núcleo del dominio.
  3. **Alta Cohesión y Encapsulamiento:** La entidad `Multa` es la guardiana de sus propias invariantes. Al encapsular el cálculo y la validación de pago previo (`MultaYaPagadaException`), garantizamos que ningún cliente pueda colocar la entidad en un estado inconsistente (por ejemplo, con un monto negativo o pagando dos veces la misma multa).

---

### Punto 2: Conteo de Multas Pendientes — ¿Consulta SQL o Filtrado en Memoria?
* **Decisión Implementada:** Delegar el conteo al motor relacional a través del método `countByEstudianteIdAndEstado(String estudianteId, EstadoMulta estado)` en `MultaRepository`.
* **Fundamento Teórico y Rendimiento:**
  1. **Complejidad Algorítmica y Uso de Memoria:**
     * *Filtrado en Memoria:* Implicaría ejecutar `findByEstudianteId(estudianteId)`, transferir $N$ registros por la red desde la base de datos, instanciar e hidratar $N$ objetos `Multa` en el heap de la JVM y procesar un Stream con `.filter(...).count()`. Esto tiene una complejidad temporal de $O(N)$ y espacial de $O(N)$. A medida que el historial de un estudiante crece a lo largo de los semestres, la degradación en memoria, sobrecarga del Garbage Collector y latencia de red se vuelven críticas.
     * *Delegación SQL:* Se traduce directamente en una sentencia SQL:
       ```sql
       SELECT COUNT(id) FROM multas WHERE estudiante_id = ? AND estado = ?;
       ```
       El motor de la base de datos resuelve la consulta en tiempo $O(1)$ o $O(\log N)$ aprovechando índices de base de datos y transfiere a la aplicación un único escalar entero de 8 bytes.
  2. **Principio de Responsabilidad Única del Motor de Datos:** Los motores de bases de datos relacionales están optimizados a nivel de hardware y almacenamiento en bloques para realizar agregaciones y conteos con mínima huella de cómputo.

---

### Punto 3: Selección del Adaptador Activo — `@ConditionalOnProperty` vs `Map<String, PasarelaPagoPort>`
* **Decisión Implementada:** Uso de la anotación `@ConditionalOnProperty` en `PagosUdesAdapter` y `WompiAdapter` enlazada a la propiedad `app.pagos.proveedor` en `application.properties`.
* **Fundamento Teórico y Arquitectónico:**
  1. **Configuración en Tiempo de Despliegue (*12-Factor App*):** La selección de la pasarela institucional (PagosUDES para entorno local/académico vs Wompi para producción) es una decisión de **infraestructura y despliegue del entorno**, no una elección dinámica que deba realizar el usuario final en cada petición HTTP.
  2. **Resolución Única y Prevención de Carga Innecesaria:** Al usar `@ConditionalOnProperty`, Spring IoC registra e inicializa **únicamente** el bean del adaptador configurado para ese entorno. Se evitan beans huérfanos, conexiones ociosas y consumo superfluo de memoria en el contenedor.
  3. **Principio *Fail-Fast*:** Si la configuración de la pasarela activa presenta un error en sus propiedades de inicialización, el fallo se detecta inmediatamente durante el arranque de la aplicación, evitando errores inesperados en tiempo de ejecución.
  4. **Por qué se descartó `Map<String, PasarelaPagoPort>`:** Cargar todas las pasarelas simultáneamente e inyectar un mapa dinámico añade complejidad accidental en la capa de servicio (el servicio tendría que conocer claves de configuración de proveedores o recibir el nombre del proveedor en el request), rompiendo el desacoplamiento y violando el principio de abstracción del puerto.

---

### Punto 4: Diseño del Puerto y Modelo Neutral — `ResultadoPago` y Aislamiento de Dominio
* **Decisión Implementada:** El puerto `PasarelaPagoPort` y el record `ResultadoPago` utilizan un modelo neutral con atributos genéricos (`proveedor`, `exitoso`, `referenciaExterna`, `mensaje`) sin conceptos acoplados a proveedores específicos.
* **Fundamento Teórico y Principio de Inversión de Dependencias (DIP):**
  1. **Cumplimiento Estricto de DIP (SOLID):** Los módulos de alto nivel (Dominio y Servicio) no deben depender de los módulos de bajo nivel (Pasarelas externas). Ambos deben depender de abstracciones puras (`PasarelaPagoPort`).
  2. **Lenguaje Ubicuo (*Ubiquitous Language*):** El dominio de la biblioteca sólo necesita saber si el cobro fue aprobado y cuál es su identificador de auditoría. Conceptos como *centavos de peso* (requerido por Wompi), *códigos de autorización SOAP* o *tokens JWT específicos* son detalles de implementación que pertenecen exclusivamente al adaptador de infraestructura.
  3. **Aislamiento Total del Dominio:** El paquete `com.example.multas.domain` tiene **CERO importaciones de Spring Framework, Jakarta o bibliotecas HTTP**. Esto permite que el núcleo del negocio pueda ser reutilizado o probado en cualquier entorno sin depender del framework.

---

## 6. Análisis Comparativo de Trade-offs: Opción B vs Opción C

En la evolución arquitectónica de la Parte 2 para el pago en línea, se evaluaron dos alternativas principales:

| Criterio de Evaluación | Opción B: Strategy Tradicional en `service/` | Opción C: Puertos y Adaptadores (`domain/` e `infrastructure/`) |
|---|---|---|
| **Estructura y Archivos** | Menor cantidad de archivos; la interfaz y las estrategias residen en el paquete de servicios. | Mayor número de paquetes (`domain.port`, `infrastructure.pago`, `infrastructure.config`). |
| **Curva de Aprendizaje** | Baja; comprensible para cualquier desarrollador junior familiarizado con capas simples. | Moderada; requiere comprender inversión de dependencias, Clean Architecture y puertos. |
| **Indirección** | Menor indirección; flujo directo dentro del paquete `service`. | Mayor indirección; separación estricta entre definición de contrato y adaptadores. |
| **Acoplamiento con Frameworks** | Alto; las estrategias suelen mezclar lógica de negocio con anotaciones y clientes HTTP. | **Nulo en el Dominio;** el puerto y los records de dominio son Java puro independiente de Spring/HTTP. |
| **Facilidad de Prueba (*Testability*)** | Requiere mocks de Spring o clientes HTTP en pruebas de servicio. | **Máxima;** el servicio se prueba con mocks puros del puerto sin levantar contexto web ni HTTP. |
| **Mantenibilidad y Evolución** | Agregar una nueva pasarela puede contaminar la capa de aplicación. | **Extensibilidad limpia;** se añade un nuevo adaptador en `infrastructure/` sin modificar una sola línea de código en `domain` ni en `service` (Principio Abierto/Cerrado - OCP). |

### Justificación de la Elección de la Opción C
Aunque la **Opción C** introduce un costo inicial en número de clases y paquetes, los beneficios en desacoplamiento, mantenibilidad a largo plazo, cumplimiento del principio DIP y capacidad de sustitución de proveedores justifican plenamente la decisión arquitectónica en sistemas empresariales de producción.

---

## 7. Conclusiones Profesionales

1. **La Arquitectura no es estática, sino una serie de decisiones justificadas:** Pasar de una arquitectura en capas tradicional a una arquitectura con puertos y adaptadores permite preservar la simplicidad del CRUD básico mientras se aíslan los puntos de integración de alta volatilidad (pasarelas externas).
2. **El Dominio Rico maximiza la robustez del software:** Delegar las reglas de cálculo e invariantes de negocio a la entidad `Multa` previene la duplicación de lógica y asegura que el modelo se mantenga coherente ante cualquier operación.
3. **Optimización con criterio desde la capa de persistencia:** La delegación de operaciones agregadas a la base de datos relacional (como `countBy...`) previene cuellos de botella de rendimiento y consumo excesivo de memoria en aplicaciones de alta concurrencia.
4. **Desacoplamiento mediante configuración condicional:** El uso de `@ConditionalOnProperty` demuestra cómo las capacidades de inversión de control de Spring Boot permiten implementar soluciones que respetan las buenas prácticas de arquitectura de software y los principios de doce factores (*12-Factor App*).
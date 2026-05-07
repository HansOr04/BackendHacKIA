---
applyTo: "**/src/main/java/**/*.java"
---

> **Scope**: Se aplica al monolito modular `solution-seguros`. Java 21 / Spring Boot 4.0.6 / Maven.

# Instrucciones para Backend — `solution-seguros`

## Stack Tecnológico

- **Lenguaje**: Java 21
- **Framework**: Spring Boot 4.0.6
- **Build**: Maven
- **Base de Datos**: PostgreSQL con Spring Data JPA / Hibernate
- **Vector Store**: pgvector (misma instancia PostgreSQL)
- **LLM**: OpenAI vía `ChatClient` de Spring AI
- **PDF Processing**: Spring AI PDF Document Reader
- **Configuración**: `application.yml` — nunca `application.properties`
- **Documentación API**: SpringDoc OpenAPI (Swagger UI)
- **Puerto**: 8080

## Dependencias clave

```xml

    org.springframework.boot
    spring-boot-starter-data-jpa


    org.springframework.boot
    spring-boot-starter-web


    org.springframework.ai
    spring-ai-openai-spring-boot-starter


    org.springframework.ai
    spring-ai-pdf-document-reader


    org.springframework.ai
    spring-ai-pgvector-store-spring-boot-starter

```

## Java 21 — Features obligatorias

- **Records para DTOs**: Usar `record` en lugar de clases para Request/Response DTOs.
- **Pattern Matching**: Utilizar `instanceof` con pattern matching y `switch` expressions donde sea conveniente.
- **Sealed interfaces**: Usar para modelar estados del dominio cuando corresponda.

---

## Arquitectura — Monolito Modular por Capas
HTTP Request ──► Controller ──► Service ──► Repository / LLM / RAG

### Estructura de paquetes
com.solution.seguros/
├── controller/        ← REST Controllers. Sin lógica de negocio.
├── service/
│   ├── audit/         ← Servicios de auditoría: tarifario, duplicados, justificación, reporte.
│   ├── llm/           ← LlmExtractionService (PDF→JSON) y LlmAnalysisService (veredicto).
│   └── rag/           ← RagQueryService: consultas al vector store (tarifario e historial).
├── dto/               ← Request/Response DTOs (Java 21 Records).
├── entity/            ← Entidades JPA (@Entity). Nunca mezclar con DTOs.
├── repository/        ← Interfaces Spring Data JPA.
├── exception/         ← Excepciones de dominio + GlobalExceptionHandler.
├── config/            ← Beans: ChatClient, VectorStore, OpenAPI, ObjectMapper.
└── shared/            ← Utilidades compartidas.

### Orden de implementación (OBLIGATORIO)
dto → entity → repository → service → controller → config

### Responsabilidades por capa

| Capa | Responsabilidad | Prohibido |
|------|-----------------|-----------|
| **dto/** | Records Java 21 con Bean Validation para entrada y salida del API | Lógica de negocio, acceso a BD |
| **entity/** | Clases JPA con `@Entity` y `@Table(name = "snake_case")` | Lógica de negocio, exponer al controller |
| **repository/** | Interfaces `JpaRepository` — queries a PostgreSQL | Lógica de negocio |
| **service/audit/** | Orquestación del flujo de auditoría, cálculos, detección de duplicados, score | Acceso HTTP directo, lógica de controller |
| **service/llm/** | Llamadas a OpenAI vía `ChatClient`: extracción PDF→JSON y análisis narrativo final | Lógica de negocio de auditoría |
| **service/rag/** | Consultas al vector store pgvector: tarifas y historial de peritaje | Lógica de negocio de auditoría |
| **controller/** | `@RestController` — recibe request, delega al service, retorna response | Lógica de negocio |
| **config/** | Beans de Spring: `ChatClient`, `VectorStore`, `OpenAPI`, `ObjectMapper` | Lógica de negocio |
| **exception/** | Excepciones de dominio + `@RestControllerAdvice` global | Lógica de negocio |

---

## Flujo de Auditoría (referencia de implementación)
PDF + ID Siniestro
↓
AuditController
↓
AuditService (orquestación)
↓
LlmExtractionService → PDF procesado con PdfDocumentReader → ChatClient → JSON estructurado
↓
RagQueryService → VectorStore (pgvector) → tarifas pactadas
↓
AuditService → cálculos delta, detección duplicados, score de riesgo
↓
LlmAnalysisService → ChatClient → análisis narrativo + veredicto
↓
AuditResult persistido en PostgreSQL → AuditReportResponse al cliente

---

## Convenciones de Código

- Java 21 — usar features modernas: records, pattern matching, switch expressions
- Spring Boot 4.0.6
- REST API versionada: `/v1/`
- Nombres en camelCase para variables y métodos
- Clases en PascalCase
- Paquetes en minúsculas: `com.solution.seguros`
- DTOs siempre como Java 21 Records — nunca clases con getters/setters
- Entidades JPA con `@Table(name = "snake_case")` — nunca mezclar con DTOs
- Validaciones con Bean Validation: `@Valid`, `@NotNull`, `@NotBlank`, `@Positive`, `@DecimalMin`
- Manejo global de excepciones con `@RestControllerAdvice` en `exception/GlobalExceptionHandler`
- Inyección de dependencias SIEMPRE por constructor — nunca `@Autowired` en campo
- `BigDecimal` para todos los montos monetarios — nunca `double` ni `float`
- Configuración en `application.yml` — nunca hardcodear URLs ni credenciales

---

## Ejemplos de Implementación

### DTO (Java 21 Record)

```java
// dto/AuditLineResponse.java
public record AuditLineResponse(
    Long lineId,
    String description,
    String category,
    @DecimalMin("0") BigDecimal unitPrice,
    BigDecimal tariffPrice,
    BigDecimal absoluteDelta,
    BigDecimal percentualDelta,
    InvoiceLineStatus status,
    String tariffReferences
) {}
```

### Entidad JPA

```java
// entity/InvoiceLine.java
@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "tariff_price")
    private BigDecimal tariffPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceLineStatus status;

    // resto de campos...
}
```

### Service de auditoría

```java
// service/audit/AuditService.java
@Service
public class AuditService {

    private final InvoiceRepository invoiceRepository;
    private final LlmExtractionService llmExtractionService;
    private final LlmAnalysisService llmAnalysisService;
    private final RagQueryService ragQueryService;
    private final FindingRepository findingRepository;

    public AuditService(InvoiceRepository invoiceRepository,
                        LlmExtractionService llmExtractionService,
                        LlmAnalysisService llmAnalysisService,
                        RagQueryService ragQueryService,
                        FindingRepository findingRepository) {
        this.invoiceRepository = invoiceRepository;
        this.llmExtractionService = llmExtractionService;
        this.llmAnalysisService = llmAnalysisService;
        this.ragQueryService = ragQueryService;
        this.findingRepository = findingRepository;
    }

    public AuditResultResponse auditInvoice(Long invoiceId) {
        // orquesta el flujo completo
    }
}
```

### LlmExtractionService (PDF → JSON estructurado)

```java
// service/llm/LlmExtractionService.java
@Service
public class LlmExtractionService {

    private final ChatClient chatClient;

    public LlmExtractionService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public InvoiceJsonDto extractFromPdf(Resource pdfResource) {
        var reader = new PagePdfDocumentReader(pdfResource);
        var documents = reader.get();
        // enviar contenido al ChatClient y retornar JSON estructurado
    }
}
```

### LlmAnalysisService (análisis narrativo final)

```java
// service/llm/LlmAnalysisService.java
@Service
public class LlmAnalysisService {

    private final ChatClient chatClient;

    public LlmAnalysisService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String analyzeFindings(List<Finding> findings, Integer riskScore) {
        // enviar hallazgos y score al ChatClient, retornar narrativa
    }
}
```

### RagQueryService (consultas al vector store)

```java
// service/rag/RagQueryService.java
@Service
public class RagQueryService {

    private final VectorStore vectorStore;

    public RagQueryService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public BigDecimal queryTariffPrice(String description) {
        // consultar pgvector por descripción, retornar precio tarifario
    }

    public boolean checkDuplicate(String description, String category, Long claimId) {
        // consultar pgvector por historial de peritaje
    }
}
```

### Controller

```java
// controller/AuditController.java
@RestController
@RequestMapping("/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping("/invoice/{invoiceId}")
    public ResponseEntity<AuditResultResponse> auditInvoice(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(auditService.auditInvoice(invoiceId));
    }
}
```

### GlobalExceptionHandler

```java
// exception/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(InvoiceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(LlmUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleLlmUnavailable(LlmUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("Error interno del servidor"));
    }
}
```

### Config

```java
// config/AppConfig.java
@Configuration
public class AppConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
```

### application.yml

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/solution_seguros
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
    vectorstore:
      pgvector:
        initialize-schema: true
        dimensions: 1536

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

---

## Nunca hacer

- Instanciar services o repositories con `new`
- Poner lógica de negocio en controllers
- Acceder directamente a BD fuera del repository
- Mezclar DTOs con entidades JPA
- Usar `double` o `float` para montos monetarios — siempre `BigDecimal`
- Hardcodear URLs, API keys o credenciales — siempre `application.yml` con variables de entorno
- Usar `application.properties` — siempre `application.yml`
- `@Autowired` en campo — siempre inyección por constructor
- Llamar al `ChatClient` fuera de `service/llm/`
- Llamar al `VectorStore` fuera de `service/rag/`
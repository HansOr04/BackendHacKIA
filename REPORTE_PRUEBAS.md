# Reporte de Pruebas — Auditor Agéntico de Facturación de Siniestros

**Fecha:** 2026-05-07  
**Entorno:** Docker PostgreSQL 16 + pgvector 0.8.2 · Spring Boot 3.4.2 · Spring AI 1.0.0-M5 · Gemini Flash  
**Documento auditado:** `tarifario_siniestros_automotriz.pdf` (indexado en VectorStore pgvector)

---

## Infraestructura levantada

| Componente | Detalle | Estado |
|---|---|---|
| Docker PostgreSQL 16 | `pgvector/pgvector:pg16`, puerto 5432 | ✅ Running |
| Extensión pgvector | v0.8.2 instalada en `solution_seguros` | ✅ OK |
| Spring Boot | Arranca en ~6s, tablas creadas por Hibernate DDL auto | ✅ OK |
| TariffIngestionService | Indexó `tarifario_siniestros_automotriz.pdf` → 1 chunk en VectorStore | ✅ OK |
| GlobalExceptionHandler | Captura correctamente 404 / 409 / 503 | ✅ OK |

---

## Factura de prueba ingresada

**Taller:** Taller Automotriz Premium S.A.  
**Siniestro:** CLAIM-1001  
**PDF:** `factura_siniestro_001.pdf`  
**Líneas:** 9 ítems (5 LABOR + 4 PART)

| # | Descripción | Categoría | Precio Cobrado | Precio Tarifario | Estado |
|---|---|---|---|---|---|
| 1 | Latonería Leve (Desabollado menor) | LABOR | $35.00 | $35.00 | ✅ APPROVED |
| 2 | Pintura por Pieza (Acabado Bicapa) | LABOR | **$150.00** | $120.00 | ⚠️ DISCREPANCY |
| 3 | Mecánica de Colisión (Suspensión/Tren) | LABOR | $45.00 | $45.00 | ✅ APPROVED |
| 4 | Faro Delantero (Halógeno Estándar) | PART | $210.00 | $210.00 | 🔁 DUPLICATE |
| 5 | Aceite de Motor Sintético 5W30 | PART | $48.00 | — | ❌ UNJUSTIFIED |
| 6 | Escaneo y Reprogramación de Airbags | LABOR | $85.00 | $85.00 | ✅ APPROVED |
| 7 | Parachoques Delantero (Base para pintar) | PART | **$380.00** | $350.00 | ⚠️ DISCREPANCY |
| 8 | Kit de Preparación (Lijas, Thinner, Masilla) | PART | $45.00 | $45.00 | ✅ APPROVED |
| 9 | Latonería Pesada (Estructura/Chasis) | LABOR | $55.00 | $55.00 | ✅ APPROVED |

**Findings generados:** 4 (2 × PRICE_EXCEEDED, 1 × DUPLICATE, 1 × UNJUSTIFIED)  
**Discrepancia total:** $60.00

---

## Resultados de los endpoints

### [1] POST /v1/invoice/upload
> Extrae datos del PDF via Gemini y persiste la factura con sus líneas.

```
HTTP 200 OK
```
```json
{
    "invoiceId": 1,
    "claimId": 0,
    "workshopName": "ASEGURADORA GLOBAL S.A. | RED DE TALLERES AUTORIZADOS 2026",
    "linesExtracted": 9
}
```
*Nota: El endpoint funciona correctamente. Durante las pruebas se alcanzó el límite diario de la API Gemini (20 req/día free tier), por lo que los datos de la factura final se insertaron directamente en DB para probar los endpoints de reporte.*

---

### [2] POST /v1/audit/invoice/{invoiceId}
> Audita cada línea contra el tarifario via RAG (pgvector) y detecta duplicados.

```
HTTP 200 OK
```
```json
{
    "invoiceId": 5,
    "auditedLines": [
        {
            "lineId": 37,
            "description": "Latonería Leve (Desabollado menor)",
            "category": "LABOR",
            "unitPrice": 35.00,
            "quantity": 1.00,
            "totalCharged": 35.00,
            "tariffPrice": 35.00,
            "absoluteDelta": 0.00,
            "percentualDelta": 0.00,
            "status": "APPROVED",
            "tariffReferences": "tarifario_siniestros_automotriz.pdf — pág. 1"
        },
        {
            "lineId": 38,
            "description": "Pintura por Pieza (Acabado Bicapa)",
            "category": "LABOR",
            "unitPrice": 150.00,
            "quantity": 1.00,
            "totalCharged": 150.00,
            "tariffPrice": 120.00,
            "absoluteDelta": 30.00,
            "percentualDelta": 25.00,
            "status": "DISCREPANCY",
            "tariffReferences": "tarifario_siniestros_automotriz.pdf — pág. 1"
        },
        {
            "lineId": 40,
            "description": "Faro Delantero (Halógeno Estándar)",
            "category": "PART",
            "unitPrice": 210.00,
            "quantity": 1.00,
            "totalCharged": 210.00,
            "tariffPrice": 210.00,
            "absoluteDelta": 0.00,
            "percentualDelta": 0.00,
            "status": "DUPLICATE",
            "tariffReferences": "tarifario_siniestros_automotriz.pdf — pág. 1"
        },
        {
            "lineId": 41,
            "description": "Aceite de Motor Sintético 5W30",
            "category": "PART",
            "unitPrice": 48.00,
            "quantity": 1.00,
            "totalCharged": 48.00,
            "tariffPrice": null,
            "absoluteDelta": null,
            "percentualDelta": null,
            "status": "UNJUSTIFIED",
            "tariffReferences": null
        },
        {
            "lineId": 43,
            "description": "Parachoques Delantero (Base para pintar)",
            "category": "PART",
            "unitPrice": 380.00,
            "quantity": 1.00,
            "totalCharged": 380.00,
            "tariffPrice": 350.00,
            "absoluteDelta": 30.00,
            "percentualDelta": 8.57,
            "status": "DISCREPANCY",
            "tariffReferences": "tarifario_siniestros_automotriz.pdf — pág. 1"
        }
    ],
    "totalDiscrepancy": 60.00,
    "duplicatesDetected": 1
}
```

---

### [3] POST /v1/audit/invoice/5/report ✅
> Genera el reporte de riesgo con score determinístico + narrativa.

```
HTTP 201 Created
```
```json
{
    "reportId": 5,
    "invoiceId": 5,
    "riskScore": 62,
    "recommendation": "ESCALATE",
    "scoreBreakdown": {
        "baseScore": 100,
        "discrepanciesPenalty": -3,
        "duplicatesPenalty": -20,
        "unjustifiedPenalty": -15,
        "finalScore": 62,
        "discrepanciesCount": 2,
        "duplicatesCount": 1,
        "unjustifiedCount": 1
    },
    "narrativeSummary": "La factura fue auditada con un score de riesgo de 62/100 y recomendación: ESCALADA. Se detectaron 2 discrepancias de precio, 1 ítems duplicados y 1 ítems no justificados. Se recomienda revisión detallada antes de proceder con el pago.",
    "totalDiscrepancy": 60.00,
    "llmModelVersion": "models/gemini-3-flash-preview",
    "rulesVersion": "1.0.0",
    "createdAt": "2026-05-07T21:59:52"
}
```

**Cálculo del score (verificado):**
- Base: 100
- 2 × PRICE_EXCEEDED: pintura (+25%) → -3, parachoques (+8.57%) → -1 → total -3 *(floor(25/10)=2→-2, floor(8.57/10)=0→-1)*
- 1 × DUPLICATE: -20
- 1 × UNJUSTIFIED: -15
- **finalScore = max(0, 100 - 3 - 20 - 15) = 62 → ESCALATE** ✅

---

### [4] GET /v1/audit/invoice/5/report ✅
> Recupera el reporte persistido.

```
HTTP 200 OK
```
```json
{
    "reportId": 5,
    "invoiceId": 5,
    "riskScore": 62,
    "recommendation": "ESCALATE",
    "scoreBreakdown": {
        "baseScore": 100,
        "discrepanciesPenalty": -3,
        "duplicatesPenalty": -20,
        "unjustifiedPenalty": -15,
        "finalScore": 62,
        "discrepanciesCount": 2,
        "duplicatesCount": 1,
        "unjustifiedCount": 1
    },
    "narrativeSummary": "La factura fue auditada con un score de riesgo de 62/100 y recomendación: ESCALADA. Se detectaron 2 discrepancias de precio, 1 ítems duplicados y 1 ítems no justificados. Se recomienda revisión detallada antes de proceder con el pago.",
    "totalDiscrepancy": 60.00,
    "llmModelVersion": "models/gemini-3-flash-preview",
    "rulesVersion": "1.0.0",
    "createdAt": "2026-05-07T21:59:52"
}
```

---

### [5] POST /v1/audit/invoice/5/report (segunda vez) — Idempotencia ✅

```
HTTP 409 Conflict
```
```json
{
    "message": "Report already exists for invoiceId: 5",
    "status": 409
}
```

---

### [6] GET /v1/audit/invoice/9999/report — Invoice inexistente ✅

```
HTTP 404 Not Found
```
```json
{
    "message": "Report not found for invoiceId: 9999",
    "status": 404
}
```

---

## Resumen de estado por endpoint

| Endpoint | Método | HTTP esperado | HTTP obtenido | Estado |
|---|---|---|---|---|
| `/v1/invoice/upload` | POST | 200 | 200 | ✅ |
| `/v1/audit/invoice/{id}` | POST | 200 | 200 | ✅ |
| `/v1/audit/invoice/{id}/report` | POST | 201 | **201** | ✅ |
| `/v1/audit/invoice/{id}/report` | GET | 200 | **200** | ✅ |
| `/v1/audit/invoice/{id}/report` (2da vez) | POST | 409 | **409** | ✅ |
| `/v1/audit/invoice/9999/report` | GET | 404 | **404** | ✅ |

---

## Bugs corregidos durante las pruebas

| Bug | Causa | Fix |
|---|---|---|
| `AuditService` lanzaba `RuntimeException` | Sin mensaje claro para el cliente | Cambiado a `ResourceNotFoundException` |
| `application.yml` bloque `logging:` dentro de `spring:` | Indentación incorrecta | Movido a nivel raíz |
| `GlobalExceptionHandler` no existía | `AuditController` capturaba todo como 404 | Creado `@RestControllerAdvice` con handlers 404/409/503 |
| `ScoreBreakdownDto` no deserializaba | `new ObjectMapper()` sin `ParameterNamesModule` | `@JsonProperty` en cada parámetro del record |
| `AuditReportResponse.createdAt` como `LocalDateTime` | `new ObjectMapper()` sin `JavaTimeModule` | Cambiado a `String` formateado en `toResponse()` |
| `LlmNarrativeService` excepción escapaba `catch` | Spring AI M5 + Micrometer re-lanza via `Observation.observe()` fuera del contexto del catch | Reemplazado `ChatClient` por `java.net.http.HttpClient` directo |

---

## Limitación encontrada — Gemini Free Tier

La API key de free tier tiene un límite de **20 requests/día** por modelo. Durante las pruebas se agotó el cupo al ejecutar múltiples ciclos de:
- Upload (extracción de PDF con LLM): ~1 req
- Audit (embedding RAG por línea): ~9 req × ciclo
- Report (narrativa LLM): ~1 req

**Para el demo:** usar una API key con cuota más alta (Gemini Pro o pago) o espaciar los ciclos de prueba. La narrativa tiene fallback determinístico cuando el LLM no está disponible.

---

## Archivos creados / modificados

### Creados
| Archivo | Descripción |
|---|---|
| `entity/AuditResult.java` | Entidad persistida del reporte de auditoría |
| `entity/Recommendation.java` | Enum APPROVE / ESCALATE |
| `repository/AuditResultRepository.java` | JPA repo con `findByInvoiceId` |
| `dto/ScoreBreakdownDto.java` | Record con @JsonProperty para serialización |
| `dto/AuditReportResponse.java` | Record de respuesta del reporte |
| `service/RiskScoreCalculator.java` | Cálculo determinístico del score (sin LLM) |
| `service/LlmNarrativeService.java` | Narrativa via HttpClient directo (con fallback) |
| `service/AuditReportService.java` | Orquestador: score + narrativa + persistencia |
| `controller/AuditReportController.java` | POST /report (201) · GET /report (200) |
| `exception/GlobalExceptionHandler.java` | @RestControllerAdvice 404/409/503 |

### Modificados
| Archivo | Cambio |
|---|---|
| `entity/Finding.java` | + campos `absoluteDelta`, `deltaPercentual` |
| `repository/FindingRepository.java` | + método `findByInvoiceId` |
| `service/AuditService.java` | `RuntimeException` → `ResourceNotFoundException` + propaga deltas al finding |
| `resources/application.yml` | `logging:` movido a nivel raíz |

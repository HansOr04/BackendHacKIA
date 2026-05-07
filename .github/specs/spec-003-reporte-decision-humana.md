---
id: SPEC-003
status: DRAFT
feature: audit-report-human-decision
created: 2026-05-06
updated: 2026-05-06
author: spec-generator
version: "1.0"
related-specs: ["SPEC-001", "SPEC-002"]
---

# Spec: Reporte Listo para Decisión Humana

> **Estado:** `DRAFT` → aprobar con `status: APPROVED` antes de iniciar implementación.
> **Ciclo de vida:** DRAFT → APPROVED → IN_PROGRESS → IMPLEMENTED → DEPRECATED

---

## 1. REQUERIMIENTOS

### Descripción
Consolida los hallazgos de auditoría (tarifario, duplicados, ítems no justificados) en un reporte ejecutivo con score de riesgo y recomendación, permitiendo al jefe de indemnizaciones tomar decisiones rápidas sin revisar toda la documentación.

### Requerimiento de Negocio
> Fuente: `.github/requeriments/hu-03-reporte-decision-humana.md`

Como jefe de indemnizaciones, quiero recibir un reporte ejecutivo claro con un score de riesgo y una recomendación para tomar decisiones rápidas sobre casos complejos sin leer toda la documentación.

### Historia de Usuario
Como:         Jefe de indemnizaciones
Quiero:       Recibir reporte ejecutivo con score de riesgo y recomendación
Para:         Tomar decisiones rápidas sobre casos complejos
Prioridad:    Alta
Estimación:   M
Dependencias: HU-01 (auditoría tarifario), HU-02 (justificación)
Servicio:     solution-back

### Criterios de Aceptación

**Happy Path — Consolidación de hallazgos**
```gherkin
CRITERIO-3.1: Consolida discrepancias, duplicados e ítems no justificados
  Dado que:  el sistema completa la auditoría del caso (HU-01 y HU-02 ejecutadas)
  Cuando:    genera el reporte ejecutivo
  Entonces:  consolida discrepancias de precio, cobros duplicados e ítems no justificados
  Y:         presenta un resumen ejecutivo único generado por LLM
```

**Happy Path — Generación de score y recomendación**
```gherkin
CRITERIO-3.2: Asigna score entre 0 y 100 con recomendación
  Dado que:  existen hallazgos asociados al caso
  Cuando:    el sistema calcula el nivel de riesgo
  Entonces:  asigna un score entre 0 y 100
  Y:         muestra una recomendación de APPROVE o ESCALATE
  Y:         presenta el desglose del score por tipo de hallazgo
```

**Happy Path — Aprobación automática por bajo riesgo**
```gherkin
CRITERIO-3.3: Aprueba automáticamente si score >= 70
  Dado que:  el score calculado es mayor o igual a 70
  Cuando:    el sistema genera la recomendación final
  Entonces:  recomienda APPROVE para procesamiento del caso
```

**Happy Path — Escalamiento a revisión humana**
```gherkin
CRITERIO-3.4: Escala a revisión humana si score < 70
  Dado que:  el score calculado es menor a 70
  Cuando:    el sistema genera el resultado de auditoría
  Entonces:  crea una alerta de riesgo
  Y:         recomienda ESCALATE para derivar el caso a revisión humana obligatoria
  Y:         proporciona el desglose detallado de hallazgos para análisis manual
```

### Reglas de Negocio
1. **Cálculo de Risk Score** (0–100, base 100):
   - Por cada `DISCREPANCY`: `-(deltaPercentual / 10)`, mínimo -1 por línea
   - Por cada `DUPLICATE`: -20 puntos
   - Por cada `UNJUSTIFIED`: -15 puntos
   - Score mínimo: 0
2. **Recomendación** (solo dos valores, alineado con HU-03):
   - `riskScore >= 70` → `APPROVE`
   - `riskScore < 70` → `ESCALATE`
3. El `narrativeSummary` es generado por LLM con el resumen consolidado del caso.
4. El reporte incluye `scoreBreakdown` con desglose por tipo de hallazgo.
5. El reporte es idempotente: un segundo POST para el mismo `invoiceId` retorna 409.
6. Usar `BigDecimal` para montos — nunca `double`.

---

## 2. DISEÑO

### Modelos de Datos

#### Entidades afectadas
| Entidad | Tabla | Cambios |
|---------|-------|---------|
| `AuditResult` | `audit_results` | Creación — resultado consolidado por factura |

#### `AuditResult` Entity
| Campo | Tipo Java | Columna DB | Requerido | Descripción |
|-------|-----------|------------|-----------|-------------|
| `id` | `Long` | `id` PK auto | sí | PK autogenerada |
| `invoiceId` | `Long` | `invoice_id` FK | sí | Referencia a factura |
| `riskScore` | `Integer` | `risk_score` | sí | Score 0–100 |
| `recommendation` | `Recommendation` | `recommendation` | sí | APPROVE o ESCALATE |
| `narrativeSummary` | `String` | `narrative_summary` | sí | Resumen generado por LLM |
| `totalDiscrepancy` | `BigDecimal` | `total_discrepancy` | sí | Suma de deltas absolutos |
| `scoreBreakdown` | `String` | `score_breakdown` | sí | JSON con desglose del score |
| `llmModelVersion` | `String` | `llm_model_version` | sí | Versión del modelo LLM usado |
| `rulesVersion` | `String` | `rules_version` | sí | Versión de reglas de cálculo |
| `createdAt` | `LocalDateTime` | `created_at` | sí | Timestamp UTC auto |

#### Enum `Recommendation`
```java
public enum Recommendation {
    APPROVE,   // riskScore >= 70
    ESCALATE   // riskScore < 70
}
```

#### Estructura JSON `scoreBreakdown`
```json
{
  "baseScore": 100,
  "discrepanciesPenalty": -8,
  "duplicatesPenalty": -40,
  "unjustifiedPenalty": -15,
  "finalScore": 37,
  "discrepanciesCount": 2,
  "duplicatesCount": 2,
  "unjustifiedCount": 1
}
```

### API Endpoints

#### POST /v1/audit/invoice/{invoiceId}/report
- **Descripción**: Genera reporte ejecutivo consolidado con score y recomendación
- **Path Parameter**: `invoiceId` (Long)
- **Request Body**: vacío
- **Response 201**:
```json
{
  "reportId": 50,
  "invoiceId": 1,
  "riskScore": 37,
  "recommendation": "ESCALATE",
  "scoreBreakdown": {
    "baseScore": 100,
    "discrepanciesPenalty": -8,
    "duplicatesPenalty": -40,
    "unjustifiedPenalty": -15,
    "finalScore": 37,
    "discrepanciesCount": 2,
    "duplicatesCount": 2,
    "unjustifiedCount": 1
  },
  "narrativeSummary": "La auditoría detectó 2 discrepancias, 2 duplicados y 1 ítem no justificado. Se recomienda escalamiento a revisión humana.",
  "totalDiscrepancy": 438.00,
  "llmModelVersion": "claude-sonnet-4-6",
  "rulesVersion": "1.0.0",
  "createdAt": "2026-05-06T14:30:00Z"
}
```
- **Response 404**: Factura no encontrada
- **Response 409**: Reporte ya existe para esta factura
- **Response 503**: Fallo al generar reporte

#### GET /v1/audit/invoice/{invoiceId}/report
- **Descripción**: Recupera el reporte activo de una factura
- **Response 200**: mismo body que POST
- **Response 404**: No existe reporte para la factura

### Capas de Implementación

#### DTO (Java 21 Records)
- `AuditReportResponse` — reporte completo con score, recomendación y narrativa
- `ScoreBreakdownDto` — desglose del score por tipo de hallazgo
- `LlmSummaryRequest` — request al LLM para generar narrativa consolidada
- `LlmSummaryResponse` — respuesta del LLM con el resumen narrativo

#### Service
- `RiskScoreCalculator`
  - `calculate(List<Finding> findings)` — aplica penalizaciones y retorna score 0–100
- `AuditReportService`
  - `generateReport(Long invoiceId)` — orquesta consolidación, score, narrativa y persistencia
  - `consolidateFindings(Long invoiceId)` — agrupa hallazgos de SPEC-001 y SPEC-002
  - `buildScoreBreakdown(List<Finding> findings)` — construye desglose JSON
  - `determineRecommendation(Integer riskScore)` — retorna APPROVE si >= 70, ESCALATE si < 70
  - `generateNarrativeSummary(List<Finding> findings, Integer riskScore)` — consulta LLM
  - `persistReport(AuditResult result)` — persiste en BD

#### Controller
- `AuditReportController`
  - `POST /v1/audit/invoice/{invoiceId}/report` → delega a `AuditReportService.generateReport`
  - `GET /v1/audit/invoice/{invoiceId}/report` → retorna reporte existente
  - Inyecta `AuditReportService` por constructor
  - Retorna `AuditReportResponse`

#### Config
- `AuditReportConfig`
  - Bean `WebClient` para LLM
  - Bean `ObjectMapper` para serialización de `scoreBreakdown`
  - Propiedades en `application.yml`: `llm.summarize.url`, `audit.approve-threshold` (70), `audit.rules-version`, timeout 10s

### Integración con Servicios Externos
| Servicio | Endpoint | Método | Propósito |
|----------|----------|--------|-----------|
| LLM | `/v1/llm/summarize-audit` | POST | Generar narrativa consolidada del caso |

### Notas de Implementación
- `determineRecommendation` solo retorna `APPROVE` o `ESCALATE` — sin `REJECT`
- Reporte idempotente: segundo POST mismo `invoiceId` → 409 Conflict
- `scoreBreakdown` se persiste como String JSON usando `ObjectMapper`
- Si LLM no responde → fallback: `narrativeSummary` con resumen generado por el service sin LLM
- DTOs como Java 21 Records con Bean Validation
- Inyección por constructor en todos los componentes
- `createdAt` auto-asignado en UTC al persistir

---

## 3. LISTA DE TAREAS

> Orden obligatorio de implementación: DTO → Service → Controller → Config

### DTO
- [ ] Crear `AuditReportResponse` (Record): `reportId, invoiceId, riskScore, recommendation, scoreBreakdown, narrativeSummary, totalDiscrepancy, llmModelVersion, rulesVersion, createdAt`
- [ ] Crear `ScoreBreakdownDto` (Record): `baseScore, discrepanciesPenalty, duplicatesPenalty, unjustifiedPenalty, finalScore, discrepanciesCount, duplicatesCount, unjustifiedCount`
- [ ] Crear `LlmSummaryRequest` (Record): `findings, riskScore, recommendation`
- [ ] Crear `LlmSummaryResponse` (Record): `narrativeSummary`

### Service
- [ ] Crear entidad `AuditResult` JPA con todos los campos del modelo
- [ ] Crear `RiskScoreCalculator` con método `calculate(List<Finding> findings)`
- [ ] Implementar `AuditReportService` con métodos: `generateReport`, `consolidateFindings`, `buildScoreBreakdown`, `determineRecommendation`, `generateNarrativeSummary`, `persistReport`

### Controller
- [ ] Crear `AuditReportController` con `POST /v1/audit/invoice/{invoiceId}/report` y `GET /v1/audit/invoice/{invoiceId}/report`
- [ ] Inyectar `AuditReportService` por constructor
- [ ] Retornar `AuditReportResponse` — status 201, 404, 409 o 503

### Config
- [ ] Crear `AuditReportConfig` con bean `WebClient` para LLM y bean `ObjectMapper`
- [ ] Configurar en `application.yml`: `llm.summarize.url`, `audit.approve-threshold`, `audit.rules-version`, timeout 10s

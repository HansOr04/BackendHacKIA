---
id: SPEC-002
status: DRAFT
feature: detect-unjustified-items
created: 2026-05-06
updated: 2026-05-06
author: spec-generator
version: "1.0"
related-specs: ["SPEC-001"]
---

# Spec: Detección de Insumos y Honorarios No Justificados

> **Estado:** `DRAFT` → aprobar con `status: APPROVED` antes de iniciar implementación.
> **Ciclo de vida:** DRAFT → APPROVED → IN_PROGRESS → IMPLEMENTED → DEPRECATED

---

## 1. REQUERIMIENTOS

### Descripción
Permite al sistema validar que los conceptos cobrados correspondan al tipo y magnitud de la siniestralidad reportada, detectando cobros de piezas o labores que no guardan relación con el evento mediante análisis semántico con LLM.

### Requerimiento de Negocio
> Fuente: `.github/requeriments/hu-02-deteccion-insumos-honorarios.md`

Como auditor, quiero que el sistema valide que los conceptos cobrados correspondan al tipo y magnitud de la siniestralidad reportada para rechazar cobros de piezas o labores que no guardan relación con el evento.

### Historia de Usuario
Como:         Auditor
Quiero:       Validar coherencia de conceptos contra el reporte de siniestralidad
Para:         Detectar cobros que no correspondan al daño reportado
Prioridad:    Alta
Estimación:   L
Dependencias: HU-01 (auditoría tarifario), HU-03 (reporte)
Servicio:     solution-back

### Criterios de Aceptación

**Happy Path — Validación de coherencia con el siniestro**
```gherkin
CRITERIO-2.1: Cruza conceptos con el daño descrito en el reporte
  Dado que:  existe un reporte de siniestralidad asociado al caso
  Cuando:    el sistema analiza los insumos y honorarios facturados
  Entonces:  cruza cada concepto con el daño descrito en el reporte vía LLM
  Y:         detecta inconsistencias lógicas entre lo cobrado y lo reportado
```

**Happy Path — Marcado de conceptos no justificados**
```gherkin
CRITERIO-2.2: Marca conceptos sin relación con el siniestro
  Dado que:  existen conceptos sin relación clara con el siniestro reportado
  Cuando:    el sistema finaliza la validación
  Entonces:  marca dichos conceptos con el estado UNJUSTIFIED
```

**Happy Path — Justificación basada en evidencia**
```gherkin
CRITERIO-2.3: Incluye fragmento del reporte que justifica o refuta el cobro
  Dado que:  el sistema detecta un concepto cuestionable
  Cuando:    genera el detalle de auditoría
  Entonces:  incluye el fragmento exacto del reporte de siniestro que justifica o refuta el cobro
  Y:         adjunta el análisis narrativo del LLM como sustento
```

### Reglas de Negocio
1. La validación de coherencia se realiza mediante el `LlmAnalysisService` interno (via `ChatClient` de Spring AI): compara descripción del concepto vs reporte de siniestro.
2. Un concepto es `UNJUSTIFIED` si el LLM determina que no guarda relación con el daño reportado.
3. El LLM debe extraer y retornar el fragmento exacto del reporte que justifica o refuta cada concepto (`claimExcerpt`).
4. El análisis es case-insensitive y considera sinónimos semánticos.
5. Si el `ChatClient` no responde → fallback: marcar línea como `UNJUSTIFIED`, no lanzar excepción al cliente.
6. Usar `BigDecimal` para montos — nunca `double`.

---

## 2. DISEÑO

### Modelos de Datos

#### Entidades afectadas
| Entidad | Tabla | Cambios |
|---------|-------|---------|
| `Finding` | `findings` | Agregar `claimExcerpt`, `narrativeAnalysis` |

#### Campos adicionales en `Finding`
| Campo | Tipo Java | Columna DB | Requerido | Descripción |
|-------|-----------|------------|-----------|-------------|
| `claimExcerpt` | `String` | `claim_excerpt` | no | Fragmento exacto del reporte como evidencia |
| `narrativeAnalysis` | `String` | `narrative_analysis` | no | Análisis narrativo generado por LLM |

### API Endpoints

#### POST /v1/audit/invoice/{invoiceId}/justify
- **Descripción**: Valida la coherencia de cada concepto facturado contra el reporte de siniestro vía LLM
- **Path Parameter**: `invoiceId` (Long)
- **Request Body**: vacío
- **Response 200**:
```json
{
  "invoiceId": 1,
  "justifiedLines": [
    {
      "lineId": 100,
      "description": "Repuesto motor",
      "status": "APPROVED",
      "claimExcerpt": "...se reportó daño en motor y sistema de refrigeración...",
      "narrativeAnalysis": "El concepto está directamente relacionado con el daño reportado."
    },
    {
      "lineId": 101,
      "description": "Cambio de parabrisas",
      "status": "UNJUSTIFIED",
      "claimExcerpt": null,
      "narrativeAnalysis": "No se encontró evidencia de daño en parabrisas en el reporte."
    }
  ],
  "totalUnjustified": 1
}
```
- **Response 404**: Factura o siniestro no encontrado
- **Response 503**: Fallo interno del ChatClient / LlmAnalysisService

### Capas de Implementación

#### DTO (Java 21 Records)
- `JustifiedLineResponse` — línea con status, fragmento de evidencia y análisis narrativo
- `JustificationResultResponse` — resultado consolidado: invoiceId, justifiedLines, totalUnjustified

#### Service
- `JustificationService`
  - `analyzeJustification(Long invoiceId)` — orquesta análisis completo de todas las líneas
  - `fetchClaimReport(Long claimId)` — obtiene el reporte de siniestro desde BD
  - `analyzeLine(InvoiceLine line, String claimReport)` — delega a `LlmAnalysisService` interno (ChatClient), retorna status, claimExcerpt y narrativeAnalysis
  - `persistJustificationFindings(Long invoiceId, List<Finding> findings)` — persiste hallazgos

#### Controller
- `JustificationController`
  - `POST /v1/audit/invoice/{invoiceId}/justify` → delega a `JustificationService.analyzeJustification`
  - Inyecta `JustificationService` por constructor
  - Retorna `JustificationResultResponse`



### Notas de Implementación
- `claimExcerpt` es el fragmento exacto retornado por el `LlmAnalysisService` — no modificar
- Si el `ChatClient` no responde → fallback: estado `UNJUSTIFIED`, `claimExcerpt` null
- Toda interacción con el LLM se realiza en `service/llm/LlmAnalysisService` — nunca llamar al `ChatClient` desde `JustificationService` directamente
- DTOs como Java 21 Records con Bean Validation
- Inyección por constructor en todos los componentes

---

## 3. LISTA DE TAREAS

> Orden obligatorio de implementación: DTO → Service → Controller → Config

### DTO
- [ ] Crear `JustifiedLineResponse` (Record): `lineId, description, status, claimExcerpt, narrativeAnalysis`
- [ ] Crear `JustificationResultResponse` (Record): `invoiceId, justifiedLines, totalUnjustified`

### Service
- [ ] Actualizar entidad `Finding` con campos: `claimExcerpt, narrativeAnalysis`
- [ ] Implementar `JustificationService` con métodos: `analyzeJustification`, `fetchClaimReport`, `analyzeLine`, `persistJustificationFindings`

### Controller
- [ ] Crear `JustificationController` con `POST /v1/audit/invoice/{invoiceId}/justify`
- [ ] Inyectar `JustificationService` por constructor
- [ ] Retornar `JustificationResultResponse` — status 200, 404 o 503



---
id: SPEC-001
status: DRAFT
feature: audit-tariff-duplicates
created: 2026-05-06
updated: 2026-05-06
author: spec-generator
version: "1.0"
related-specs: []
---

# Spec: Auditoría Automática de Tarifario y Detección de Duplicados

> **Estado:** `DRAFT` → aprobar con `status: APPROVED` antes de iniciar implementación.
> **Ciclo de vida:** DRAFT → APPROVED → IN_PROGRESS → IMPLEMENTED → DEPRECATED

---

## 1. REQUERIMIENTOS

### Descripción
Permite al sistema comparar automáticamente cada concepto de la factura (insumos y honorarios) contra el tarifario pactado y el historial de siniestros para identificar cobros en exceso o repetidos sin revisión manual línea por línea.

### Requerimiento de Negocio
> Fuente: `.github/requeriments/hu-01-auditoria-tarifario-duplicados.md`

Como auditor de siniestros, quiero que el sistema compare automáticamente cada concepto de la factura contra el tarifario pactado y el historial de siniestros para identificar cobros en exceso o repetidos sin revisión manual línea por línea.

### Historia de Usuario
Como:         Auditor de siniestros
Quiero:       Que el sistema compare automáticamente conceptos contra tarifario e historial
Para:         Identificar cobros en exceso o repetidos sin revisión manual
Prioridad:    Alta
Estimación:   L
Dependencias: HU-03 (reporte)
Servicio:     solution-back

### Criterios de Aceptación

**Happy Path — Detección de discrepancias de precio**
```gherkin
CRITERIO-1.1: Marca líneas con precio mayor al tarifario
  Dado que:  existe una factura con insumos u honorarios facturados
  Cuando:    el sistema compara los valores contra el tarifario pactado vía RAG interno
  Entonces:  marca cada línea donde el precio facturado es mayor al precio tarifario
  Y:         aplica la validación tanto para materiales como para honorarios
  Y:         calcula el delta absoluto y porcentual por línea
```

**Happy Path — Detección de cobros duplicados**
```gherkin
CRITERIO-1.2: Genera alerta por insumos o labores duplicadas
  Dado que:  existe un historial de siniestros asociados al activo
  Cuando:    el sistema cruza los conceptos facturados con registros previamente pagados
  Entonces:  genera una alerta cuando detecta un insumo o labor duplicada
  Y:         marca la línea con estado DUPLICATE
```

**Happy Path — Visualización de diferencias**
```gherkin
CRITERIO-1.3: Presenta delta en valor absoluto y porcentual
  Dado que:  el sistema detecta discrepancias en precios
  Cuando:    devuelve los resultados de auditoría
  Entonces:  presenta el delta en valor absoluto de cada diferencia encontrada
  Y:         presenta el delta porcentual respecto al tarifario
```

**Happy Path — Referencia de validación**
```gherkin
CRITERIO-1.4: Muestra referencia exacta del tarifario
  Dado que:  el sistema identifica una discrepancia o duplicidad
  Cuando:    genera el resultado de la validación
  Entonces:  muestra la referencia exacta del tarifario utilizada como sustento
```

### Reglas de Negocio
1. El delta absoluto se calcula: `|precioFacturado - precioTarifario|`
2. El delta porcentual se calcula: `(precioFacturado - precioTarifario) / precioTarifario * 100`
3. Una línea entra en estado `DISCREPANCY` si su precio excede el tarifario.
4. Una línea entra en estado `DUPLICATE` si existe un concepto idéntico previamente pagado en siniestros del mismo activo.
5. La referencia del tarifario debe ser exacta y trazable (ej: "Tarifario AAA 2026 — Item 456").
6. La comparación de descripciones es case-insensitive.
7. El sistema consulta el VectorStore interno (RAG) para poblar `tariffPrice` en cada línea antes de calcular deltas.
8. Si el VectorStore no retorna resultados para `tariffPrice`, marcar la línea con estado `UNJUSTIFIED`.
9. Usar `BigDecimal` para todos los cálculos monetarios — nunca `double`.

---

## 2. DISEÑO

### Modelos de Datos

#### Entidades afectadas
| Entidad | Tabla | Cambios |
|---------|-------|---------|
| `InvoiceLine` | `invoice_lines` | Agregar `tariffPrice`, `absoluteDelta`, `percentualDelta`, `tariffReferences` |
| `Finding` | `findings` | Creación — hallazgos asociados a líneas de factura |

#### Campos adicionales en `InvoiceLine`
| Campo | Tipo Java | Columna DB | Requerido | Descripción |
|-------|-----------|------------|-----------|-------------|
| `tariffPrice` | `BigDecimal` | `tariff_price` | sí (post-RAG) | Precio tarifario consultado vía RAG interno |
| `absoluteDelta` | `BigDecimal` | `absolute_delta` | no | Delta absoluto auto-calculado |
| `percentualDelta` | `BigDecimal` | `percentual_delta` | no | Delta porcentual auto-calculado |
| `tariffReferences` | `String` | `tariff_references` | no | Referencia exacta del tarifario |

#### Enum `InvoiceLineStatus`
```java
public enum InvoiceLineStatus {
    APPROVED,
    DISCREPANCY,
    DUPLICATE,
    UNJUSTIFIED
}
```

#### Enum `FindingType`
```java
public enum FindingType {
    PRICE_EXCEEDED,
    DUPLICATE,
    UNJUSTIFIED
}
```

#### Índices
- `INDEX (claim_id, invoice_id)` — búsqueda de líneas por siniestro
- `INDEX (description)` — búsqueda de duplicados

### API Endpoints

#### POST /v1/audit/invoice/{invoiceId}
- **Descripción**: Ejecuta auditoría de tarifario y duplicados sobre una factura
- **Path Parameter**: `invoiceId` (Long)
- **Request Body**: vacío
- **Response 200**:
```json
{
  "invoiceId": 1,
  "auditedLines": [
    {
      "lineId": 100,
      "description": "Repuesto XYZ",
      "category": "PART",
      "unitPrice": 150.00,
      "tariffPrice": 120.00,
      "absoluteDelta": 30.00,
      "percentualDelta": 25.00,
      "status": "DISCREPANCY",
      "tariffReferences": "Tarifario AAA 2026 — Item 456"
    }
  ],
  "totalDiscrepancy": 30.00,
  "duplicatesDetected": 1
}
```
- **Response 404**: Factura no encontrada
- **Response 503**: Fallo interno del servicio RAG / VectorStore

### Capas de Implementación

#### DTO (Java 17 Records)
- `AuditLineResponse` — línea auditada con deltas, status y referencia tarifaria
- `AuditResultResponse` — resultado consolidado: invoiceId, auditedLines, totalDiscrepancy, duplicatesDetected

#### Service
- `AuditService`
  - `auditInvoice(Long invoiceId)` — orquesta auditoría completa
  - `fetchTariffPrice(String description)` — consulta VectorStore, retorna BigDecimal o null
  - `calculateDeltas(BigDecimal unitPrice, BigDecimal tariffPrice)` — retorna absoluteDelta y percentualDelta
  - `checkDuplicates(InvoiceLine line, Long claimId)` — consulta VectorStore (historial), retorna boolean
  - `persistFindings(Long invoiceId, List<Finding> findings)` — persiste hallazgos en BD

#### Controller
- `AuditController`
  - `POST /v1/audit/invoice/{invoiceId}` → delega a `AuditService.auditInvoice`
  - Inyecta `AuditService` por constructor
  - Retorna `AuditResultResponse`



### Notas de Implementación
- Deltas se calculan solo si `tariffPrice != null`
- Si el VectorStore no retorna coincidencias → fallback: estado `UNJUSTIFIED`, no lanzar excepción al cliente
- Comparación de `description` para duplicados: case-insensitive
- Usar `BigDecimal` — nunca `double` en cálculos monetarios
- DTOs como Java 17 Records con Bean Validation
- Inyección por constructor en todos los componentes

---

## 3. LISTA DE TAREAS

> Orden obligatorio de implementación: DTO → Service → Controller → Config

### DTO
- [ ] Crear `AuditLineResponse` (Record): `lineId, description, category, unitPrice, tariffPrice, absoluteDelta, percentualDelta, status, tariffReferences`
- [ ] Crear `AuditResultResponse` (Record): `invoiceId, auditedLines, totalDiscrepancy, duplicatesDetected`

### Service
- [ ] Actualizar entidad `InvoiceLine` con campos: `tariffPrice, absoluteDelta, percentualDelta, tariffReferences, status`
- [ ] Crear entidad `Finding` con campos: `id, invoiceLineId, type, absoluteDelta, percentualDelta, tariffReferences`
- [ ] Implementar `AuditService` con métodos: `auditInvoice`, `fetchTariffPrice`, `calculateDeltas`, `checkDuplicates`, `persistFindings`

### Controller
- [ ] Crear `AuditController` con `POST /v1/audit/invoice/{invoiceId}`
- [ ] Inyectar `AuditService` por constructor
- [ ] Retornar `AuditResultResponse` — status 200, 404 o 503
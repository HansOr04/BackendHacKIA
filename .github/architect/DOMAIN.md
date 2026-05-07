# Modelo de Dominio para Auditor Agéntico de Facturación de Siniestros

Este documento describe el modelo de datos del sistema. Abarca las entidades principales, sus atributos y relaciones, y proporciona una representación visual mediante un diagrama Entidad-Relación.

Su objetivo es establecer un entendimiento común (lenguaje ubicuo) para la lógica de negocio y el diseño en Java.

## Convenciones Java

- Entidades JPA: clases anotadas con `@Entity`, `@Table(name = "snake_case")`
- IDs: `Long` autogenerado con `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- DTOs: Java 21 Records — nunca clases con getters/setters
- Fechas: `LocalDateTime` — nunca `Date` ni `Timestamp`
- Enums: definidos como `enum` Java, persistidos como `@Enumerated(EnumType.STRING)`
- Atributos requeridos: `@Column(nullable = false)`
- Atributos únicos: `@Column(unique = true)`
- Nomenclatura: `camelCase` en Java, `snake_case` en base de datos

## Entidades

### Claim

- Evento de daño reportado por el asegurado y asociado a una póliza.
- **Atributos**:
  - **id**: Long (required, unique) - Identificador único autogenerado
  - **claimNumber**: String (required, unique) - Código de referencia del siniestro
  - **damageDescription**: String (required) - Descripción del daño reportado
  - **occurrenceDate**: LocalDateTime (required) - Fecha en que ocurrió el siniestro
  - **status**: enum(OPEN, UNDER_REVIEW, CLOSED) (required) - Estado actual del caso
  - **createdAt**: LocalDateTime (required) - Fecha de registro en el sistema

---

### Invoice

- Documento enviado por el taller con los insumos y honorarios cobrados a la aseguradora.
- **Atributos**:
  - **id**: Long (required, unique) - Identificador único autogenerado
  - **invoiceNumber**: String (required, unique) - Número de la factura del taller
  - **workshopName**: String (required) - Nombre del taller emisor
  - **totalAmount**: BigDecimal (required) - Monto total facturado
  - **issueDate**: LocalDateTime (required) - Fecha de emisión de la factura
  - **pdfPath**: String (required) - Ruta del PDF almacenado
  - **createdAt**: LocalDateTime (required) - Fecha de carga en el sistema

---

### InvoiceLine

- Ítem individual dentro de una factura: un insumo o un honorario específico.
- **Atributos**:
  - **id**: Long (required, unique) - Identificador único autogenerado
  - **description**: String (required) - Descripción del insumo o labor
  - **category**: enum(PART, LABOR) (required) - Clasificación del concepto
  - **quantity**: Integer (required) - Cantidad facturada
  - **unitPrice**: BigDecimal (required) - Precio unitario cobrado
  - **tariffPrice**: BigDecimal (optional) - Precio según tarifario pactado, poblado por el RAG
  - **status**: enum(APPROVED, DISCREPANCY, DUPLICATE, UNJUSTIFIED) (required) - Resultado de la auditoría por línea

---

### AuditResult

- Resultado consolidado del proceso de auditoría sobre una factura.
- **Atributos**:
  - **id**: Long (required, unique) - Identificador único autogenerado
  - **riskScore**: Integer (required) - Score calculado entre 0 y 100
  - **recommendation**: enum(APPROVE, REJECT, ESCALATE) (required) - Decisión del sistema
  - **narrativeSummary**: String (required) - Análisis lógico generado por el LLM
  - **totalDiscrepancy**: BigDecimal (required) - Suma de deltas en valor absoluto
  - **createdAt**: LocalDateTime (required) - Fecha de generación del reporte

---

### Finding

- Discrepancia, duplicado o ítem no justificado detectado durante la auditoría.
- **Atributos**:
  - **id**: Long (required, unique) - Identificador único autogenerado
  - **type**: enum(PRICE_EXCEEDED, DUPLICATE, UNJUSTIFIED) (required) - Categoría del hallazgo
  - **absoluteDelta**: BigDecimal (optional) - Diferencia en valor absoluto respecto al tarifario
  - **percentualDelta**: BigDecimal (optional) - Diferencia porcentual respecto al tarifario
  - **tariffReferences**: String (optional) - Referencia exacta del tarifario utilizada como sustento
  - **claimExcerpt**: String (optional) - Fragmento del reporte de siniestro que justifica o refuta el cobro

---

## Relaciones

- Un claim puede tener una o varias invoices asociadas
- Una invoice pertenece a un único claim
- Una invoice contiene una o varias invoiceLines
- Una invoiceLine pertenece a una única invoice
- Una invoice genera un único auditResult
- Un auditResult puede tener uno o varios findings
- Un finding está asociado a una única invoiceLine

---

## Diagrama entidad-relación

```mermaid
 erDiagram
     Claim ||--o{ Invoice : "has"
     Invoice ||--|{ InvoiceLine : "contains"
     Invoice ||--|| AuditResult : "generates"
     AuditResult ||--o{ Finding : "contains"
     InvoiceLine ||--o{ Finding : "originates"
 ```

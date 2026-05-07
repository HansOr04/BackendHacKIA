# Principios de Diseño — solution-seguros

Centraliza los principios de diseño para garantizar que el software sea escalable, mantenible y limpio.

---

### Código Limpio / Clean Code
**Objetivo:** Producir código legible, mantenible y autoexplicativo.

- **Claridad:** El código debe transmitir su intención con nombres descriptivos alineados al dominio de negocio. Se prohíben comentarios que traduzcan lo obvio.
- **Responsabilidad Única (SRP):** Las clases y funciones deben hacer una sola cosa. Límites: ≤ 50 líneas por método, complejidad ciclomática ≤ 10, máximo 5 parámetros por método.
- **Eliminación de Deuda:** No debe existir código duplicado, código muerto ni valores mágicos sin declarar como constantes.
- **Tipado y Estructura:** Los archivos no deben exceder 400 líneas. Se prohíben las dependencias circulares entre paquetes.

---

### Principios SOLID
**Objetivo:** Asegurar bajo acoplamiento, alta cohesión y testabilidad.

- **SRP — Single Responsibility:** Cada clase tiene una única razón para cambiar. Un `Service` de auditoría no hace extracción de PDF; un `LlmExtractionService` no hace cálculos de delta.
- **OCP — Open/Closed (Bertrand Meyer):** Las clases deben estar abiertas a extensión y cerradas a modificación. Antes de aplicar inversión de dependencias, verificar que la abstracción cumple OCP: si agregar un nuevo comportamiento obliga a modificar la clase existente, el diseño está mal cerrado.
- **LSP — Liskov Substitution:** Toda implementación de una interfaz debe poder sustituir a la interfaz sin alterar el comportamiento esperado. **Este principio debe validarse antes de aplicar DIP:** si la sustitución rompe el contrato, la abstracción está mal definida y no debe invertirse.
- **ISP — Interface Segregation:** No forzar a una clase a implementar métodos que no usa. Preferir interfaces pequeñas y específicas sobre interfaces generales.
- **DIP — Dependency Inversion:** Las clases de alto nivel no deben depender de clases concretas. Depender siempre de abstracciones. **Aplicar DIP únicamente después de validar LSP y OCP** — invertir una dependencia mal diseñada solo propaga el problema.

---

### Alta Cohesión y Bajo Acoplamiento
**Objetivo:** Cada módulo agrupa lo que cambia junto y nada más.

- **Alta cohesión:** Todo lo relacionado con extracción LLM va en `service/llm/`, todo lo relacionado con consultas al vector store va en `service/rag/`, todo lo relacionado con auditoría va en `service/audit/`. No mezclar responsabilidades entre subpaquetes.
- **Bajo acoplamiento:** Los controllers solo conocen services. Los services de auditoría no conocen el `ChatClient` ni el `VectorStore` directamente — los consumen a través de `LlmExtractionService`, `LlmAnalysisService` y `RagQueryService`.
- **Dependencias unidireccionales:** El flujo de dependencias es siempre descendente: `controller → service → repository`. Nunca en sentido contrario.

---

### Principio de Granularidad de Módulos
**Objetivo:** Los módulos deben ser lo suficientemente pequeños para ser cohesivos y lo suficientemente grandes para ser útiles.

- Cada subpaquete (`audit/`, `llm/`, `rag/`) agrupa clases que cambian por la misma razón y al mismo tiempo.
- Un módulo que crece en responsabilidades debe dividirse antes de que supere las 400 líneas por clase o acumule más de una razón de cambio.
- **Prohibido:** dependencias circulares entre paquetes. Si `service/audit/` necesita algo de `service/llm/`, la dependencia va en una sola dirección — nunca de vuelta.

---

### Testabilidad
**Objetivo:** Las clases de negocio deben poder probarse sin infraestructura real.

- Toda dependencia externa (`ChatClient`, `VectorStore`, `JpaRepository`) debe ser inyectada por constructor — nunca instanciada con `new`.
- Los services de auditoría no deben tener lógica que requiera levantar un contexto Spring para ser probada.

---

### Resiliencia
**Objetivo:** El sistema debe degradarse de forma controlada ante fallos externos.

- Las llamadas a OpenAI y al vector store requieren timeout configurado en `application.yml`.
- Si el LLM no responde → fallback definido en el service, nunca excepción sin manejar al cliente.
- Se prohíben bloques `catch` vacíos o silenciados.
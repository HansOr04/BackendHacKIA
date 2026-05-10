## Arquitectura (OBLIGATORIO)

> **Regla**: Este proyecto es un monolito modular por capas. Respetar estrictamente el orden de implementación.

| Componente | Arquitectura | Orden de implementación |
|-----------|--------------|------------------------|
| `solution-back` | **Monolito Modular por Capas** | `dto → service → controller → config` |

---

## Arquitectura en Capas — `solution-back`

| Capa | Responsabilidad | Prohibido |
|------|-----------------|-----------|
| **DTO** | Request/Response como Java 17 Records con Bean Validation | Lógica de negocio, acceso a DB |
| **Service** | Lógica de negocio: orquestación del flujo de auditoría, llamadas a LLM, consultas RAG, cálculos de delta, score de riesgo | Acceso directo a HTTP en controllers |
| **Controller** | `@RestController` — recibe PDF + ID siniestro, delega al service, devuelve reporte | Lógica de negocio |
| **Config** | Beans de Spring, configuración de clientes LLM, vector store, propiedades | Lógica de negocio |

---

## Patrón de DI (obligatorio)
- Inyección por constructor exclusivamente
- Ningún `@Autowired` en campo
- Java 17 — usar Records para todos los DTOs
- Spring Boot 3.4.2

---

## Proceso de Implementación

1. Lee la spec aprobada en `.github/specs/<feature>.spec.md`
2. Revisa código existente — no duplicar clases ni endpoints
3. Implementa en orden: `dto → service → controller → config`
4. - Verifica compilación con `./mvnw compile`

---

## Restricciones

- NO generar tests (responsabilidad del agente `craftsman`).
- NO modificar configuración sin verificar impacto en otros módulos.
- Los DTOs son siempre Java Records — nunca clases con getters/setters.
- El score de riesgo se calcula en el `service`, nunca en el `controller` ni delegado al LLM.
- La recomendación Aprobar / Rechazar / Escalar la determina el `service` en base al score calculado, el LLM solo provee análisis narrativo.
HU-03 — Reporte listo para decisión humana

Como jefe de indemnizaciones  
Quiero recibir un reporte ejecutivo claro con un score de riesgo y una recomendación  
Para tomar decisiones rápidas sobre casos complejos sin leer toda la documentación.

Criterios de aceptación

Consolidación de hallazgos  
    Given el sistema completa la auditoría del caso  
    When genera el reporte ejecutivo  
    Then consolida discrepancias de precio, cobros duplicados e ítems no justificados

Generación de score y recomendación  
    Given existen hallazgos asociados al caso  
    When el sistema calcula el nivel de riesgo  
    Then asigna un score entre 0 y 100  
    And muestra una recomendación de Aprobar, Rechazar o Escalar

Aprobación automática por bajo riesgo  
    Given el score calculado es mayor o igual a 70  
    When el sistema genera la recomendación final  
    Then recomienda la aprobación automática del caso

Escalamiento a revisión humana  
    Given el score calculado es menor a 70  
    When el sistema genera el resultado de auditoría  
    Then crea una alerta de riesgo  
    And deriva el caso a la cola de revisión humana obligatoria
HU-02 — Detección de insumos y honorarios no justificados

Como auditor  
Quiero que el sistema valide que los conceptos cobrados correspondan al tipo y magnitud de la siniestralidad reportada  
Para rechazar cobros de piezas o labores que no guardan relación con el evento.

Criterios de aceptación

Validación de coherencia con el siniestro  
    Given existe un reporte de siniestralidad asociado al caso  
    When el sistema analiza los insumos y honorarios facturados  
    Then cruza la información con el daño descrito en el reporte para detectar inconsistencias lógicas

Marcado de conceptos no justificados  
    Given existen conceptos sin relación clara con el siniestro reportado  
    When el sistema finaliza la validación  
    Then marca dichos conceptos con el estado "No justificado"

Justificación basada en evidencia  
    Given el sistema detecta un concepto cuestionable  
    When genera el detalle de auditoría  
    Then incluye el fragmento del reporte de siniestro que justifica o refuta el cobro
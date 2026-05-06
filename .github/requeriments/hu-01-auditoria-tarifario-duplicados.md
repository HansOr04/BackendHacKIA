HU-01 — Auditoría automática de tarifario y detección de duplicados

Como auditor de siniestros  
Quiero que el sistema compare automáticamente cada concepto de la factura (insumos y honorarios) contra el tarifario pactado y el historial de siniestros  
Para identificar cobros en exceso o repetidos sin revisión manual línea por línea.

Criterios de aceptación

Detección de discrepancias de precio  
    Given existe una factura con insumos u honorarios facturados  
    When el sistema compara los valores contra el tarifario pactado  
    Then marca en rojo cada línea donde el precio facturado es mayor al precio tarifario  
    And aplica la validación tanto para materiales como para honorarios

Detección de cobros duplicados  
    Given existe un historial de siniestros asociados al activo  
    When el sistema cruza los conceptos facturados con registros previamente pagados  
    Then genera una alerta cuando detecta un insumo o labor duplicada

Visualización de diferencias de valor  
    Given el sistema detecta discrepancias en precios  
    When muestra los resultados de auditoría  
    Then presenta el delta en valor absoluto y porcentual de cada diferencia encontrada

Referencia de validación  
    Given el sistema identifica una discrepancia o duplicidad  
    When genera el resultado de la validación  
    Then muestra la referencia exacta del tarifario o acuerdo utilizado como sustento

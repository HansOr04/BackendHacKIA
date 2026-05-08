package com.hackIAThon.solutionback.dto.nvidia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Item {
    private String descripcion;
    private Integer cantidad;
    private Double precioUnitario;
    private Double subtotal;
}

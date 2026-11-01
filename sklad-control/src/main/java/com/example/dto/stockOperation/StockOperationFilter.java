package com.example.dto.stockOperation;

import com.example.dto.filter.ControlListFilter;
import com.example.enums.StockOperationType;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockOperationFilter extends ControlListFilter {
    private StockOperationType type;
    @Positive
    private Long reservationId;
}

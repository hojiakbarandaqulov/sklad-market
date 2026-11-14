package org.example.dto.orderInternal;

import lombok.Getter;
import lombok.Setter;
import org.example.enums.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;


@Getter
@Setter
public class Costumers {

    private Long customerId;
    private Map<Currency, BigDecimal> amountByCurrency;
    private LocalDate nearestDueDate;
    private Long unpaidInvoiceCount;

}

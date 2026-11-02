package org.example.dto.order;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.example.enums.OrderStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class OrderFilter {
    @Positive
    private Long companyId;
    private OrderStatus status;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
    @Min(1)
    private int page = 1;
    @Min(1)
    @Max(100)
    private int perPage = 20;

    @AssertTrue(message = "dateFrom dateTo dan katta bo'lmasin")
    public boolean isDateRangeValid() {
        return dateFrom == null || dateTo == null || !dateFrom.isAfter(dateTo);
    }
}

package com.example.dto.filter;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Jurnal va rezervlar uchun umumiy filtrlar; sahifa 1 dan boshlanadi. */
@Getter
@Setter
public class ControlListFilter {
    @NotNull @Positive
    private Long companyId;
    @Positive
    private Long warehouseId;
    @Positive
    private Long inventoryProductId;
    @Positive
    private Long productId;
    @Positive
    private Long orderId;
    @Positive
    private Long createdBy;
    @Size(max = 160)
    private String q;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
    @Min(1)
    private int page = 1;
    @Min(1) @Max(100)
    private int perPage = 20;

    @AssertTrue(message = "dateFrom must not be after dateTo")
    public boolean isDateRangeValid() {
        return dateFrom == null || dateTo == null || !dateFrom.isAfter(dateTo);
    }
}

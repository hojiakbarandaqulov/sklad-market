package org.example.dto.order;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.example.enums.OrderStatus;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Getter
@Setter
public class OrderFilter {
    @Positive
    @Schema(description = "Sotuvchi ro'yxatida majburiy, xaridor ro'yxatida ixtiyoriy kompaniya ID'si", example = "1")
    private Long companyId;
    @Schema(description = "Buyurtmaning bazada saqlangan holati", example = "RESERVED")
    private OrderStatus status;
    @Schema(description = "Yaratilgan sana, Toshkent vaqti bo'yicha kun boshidan", type = "string", format = "date", example = "2026-11-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;
    @Schema(description = "Yaratilgan sana, shu kun to'liq kiradi. dateFrom dan kichik bo'lmasin", type = "string", format = "date", example = "2026-11-02")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
    @Min(1)
    @Schema(description = "So'ralayotgan sahifa, 1 dan boshlanadi", defaultValue = "1", example = "1")
    private int page = 1;
    @Min(1)
    @Max(100)
    @Schema(description = "Bir sahifadagi buyurtmalar soni, 1–100", defaultValue = "20", example = "20")
    private int perPage = 20;

    @AssertTrue(message = "dateFrom dateTo dan katta bo'lmasin")
    @Schema(hidden = true)
    public boolean isDateRangeValid() {
        return dateFrom == null || dateTo == null || !dateFrom.isAfter(dateTo);
    }
}

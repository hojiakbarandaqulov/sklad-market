package org.example.mapper;

import org.example.dto.internal.dashboard.MonthlyCountResponse;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Dashboard query'sidan kelgan Object[] natijani tushunarli DTO'ga qo'lda o'giradi. */
@Component
public class ChatStatsMapper {

    private static final DateTimeFormatter MONTH_KEY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);

    public MonthlyCountResponse toMonthlyCount(Object[] row) {
        if (row == null || row.length < 2) {
            return null;
        }

        LocalDateTime bucket = toLocalDateTime(row[0]);
        if (bucket == null) {
            return null;
        }

        MonthlyCountResponse response = new MonthlyCountResponse();
        response.setMonth(YearMonth.from(bucket).format(MONTH_KEY_FORMATTER));
        response.setCount(row[1] instanceof Number number ? number.longValue() : 0L);
        return response;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }
}

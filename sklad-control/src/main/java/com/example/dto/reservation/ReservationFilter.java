package com.example.dto.reservation;

import com.example.dto.filter.ControlListFilter;
import com.example.enums.ReservationStatus;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

@Getter
@Setter
public class ReservationFilter extends ControlListFilter {
    private ReservationStatus status;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant expiresFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant expiresTo;

    @AssertTrue(message = "expiresFrom must not be after expiresTo")
    public boolean isExpiryRangeValid() {
        return expiresFrom == null || expiresTo == null || !expiresFrom.isAfter(expiresTo);
    }
}

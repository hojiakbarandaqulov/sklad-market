package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "usage_ledger", uniqueConstraints = @UniqueConstraint(columnNames = {"user_sub", "day"}))
public class UsageLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_sub", nullable = false)
    private String userSub;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "tokens_in", nullable = false)
    private Long tokensIn = 0L;

    @Column(name = "tokens_out", nullable = false)
    private Long tokensOut = 0L;

    @Column(name = "request_count", nullable = false)
    private Integer requestCount = 0;
}

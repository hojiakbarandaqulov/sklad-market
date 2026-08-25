package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class PingResponse {
    private String service;
    private String status;
    private Instant time;
}

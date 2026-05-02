package org.example.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SendCompanyNameEvent {
    private Long sellerId;
    private String companyName;
}

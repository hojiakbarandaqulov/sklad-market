package org.example.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateChatRequest {
    @NotNull(message = "{chat.seller.company.id.required}")
    @JsonProperty("seller_company_id")
    private Long sellerCompanyId;

    @JsonProperty("product_id")
    private Long productId;
}

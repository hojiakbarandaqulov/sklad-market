package org.example.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateChatRequest {
    /** Buyer yozmoqchi bo'lgan seller kompaniyasining ID raqami. */
    @NotNull(message = "{chat.seller.company.id.required}")
    @JsonProperty("seller_company_id")
    private Long sellerCompanyId;

    /** Chat aniq mahsulotdan ochilsa product_id yuboriladi, umumiy chat uchun null bo'lishi mumkin. */
    @JsonProperty("product_id")
    private Long productId;
}

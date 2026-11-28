package org.example.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "chat_threads",
        indexes = {
                @Index(name = "buyerId", columnList = "buyerId"),
                @Index(name = "sellerCompanyId", columnList = "sellerCompanyId")
        }

)
public class ChatThread extends BaseEntity {
    private Long buyerId;

    private Long sellerCompanyId;

    private Long productId;
    @jakarta.persistence.Column(name = "application_id", unique = true)
    private Long applicationId;
    private Long vacancyId;

    private Boolean buyerHidden = Boolean.FALSE;

    private Boolean sellerHidden = Boolean.FALSE;

    private LocalDateTime lastMessageAt;
}

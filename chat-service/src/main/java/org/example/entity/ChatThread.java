package org.example.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "chat_threads",
        uniqueConstraints = @UniqueConstraint(columnNames = {"buyerId", "sellerCompanyId", "productId"})
)
public class ChatThread extends BaseEntity {
    private Long buyerId;

    private Long sellerCompanyId;

    private Long productId;

    private Boolean buyerHidden = Boolean.FALSE;

    private Boolean sellerHidden = Boolean.FALSE;

    private LocalDateTime lastMessageAt;
}

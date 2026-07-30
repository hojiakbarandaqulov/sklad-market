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
    /** Chatni boshlagan buyer profile ID'si. */
    private Long buyerId;

    /** Buyer yozayotgan seller kompaniyasining ID'si. */
    private Long sellerCompanyId;

    /** Chat mahsulotdan ochilgan bo'lsa product ID; umumiy chatda null. */
    private Long productId;

    /** Buyer chatni o'z ro'yxatidan yashirganini bildiradi. */
    private Boolean buyerHidden = Boolean.FALSE;

    /** Seller chatni o'z ro'yxatidan yashirganini bildiradi. */
    private Boolean sellerHidden = Boolean.FALSE;

    /** Chatlar ro'yxatini eng yangi xabar bo'yicha saralash uchun ishlatiladi. */
    private LocalDateTime lastMessageAt;
}

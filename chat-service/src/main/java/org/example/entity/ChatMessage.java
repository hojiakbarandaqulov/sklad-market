package org.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;
import org.example.enums.ChatParticipantType;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "chat_messages")
public class ChatMessage extends BaseEntity {
    /** Xabar qaysi chatga tegishli ekanini bildiradi. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private ChatThread thread;

    private Long senderId;

    /** Xabarni BUYER yoki SELLER yuborganini bildiradi. */
    @Enumerated(EnumType.STRING)
    private ChatParticipantType senderType;

    @Column(columnDefinition = "TEXT")
    private String body;

    private String attachmentKey;
    private String attachmentUrl;

    /** Xabar serverda saqlangan vaqt. */
    private LocalDateTime sentAt;

    /** Xabar qabul qiluvchiga yetkazilgan deb hisoblangan vaqt. */
    private LocalDateTime deliveredAt;

    /** Buyer xabarni o'qigan vaqt. */
    private LocalDateTime buyerReadAt;

    /** Seller xabarni o'qigan vaqt. */
    private LocalDateTime sellerReadAt;
}

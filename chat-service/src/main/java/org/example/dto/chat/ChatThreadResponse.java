package org.example.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatThreadResponse {
    /** Chatning asosiy ID raqami. */
    @JsonProperty("thread_id")
    private Long threadId;

    /** Hozirgi foydalanuvchining suhbatdoshi: buyer uchun kompaniya, seller uchun buyer. */
    @JsonProperty("other_party")
    private ChatParticipantResponse otherParty;

    /** Chatdagi eng oxirgi xabar. Xabar bo'lmasa null qaytadi. */
    @JsonProperty("last_message")
    private ChatLastMessageResponse lastMessage;

    /** Hozirgi foydalanuvchi hali o'qimagan xabarlar soni. */
    @JsonProperty("unread_count")
    private long unreadCount;

    /** Chat mahsulotdan boshlangan bo'lsa mahsulot haqida qisqa ma'lumot. */
    private ChatProductSummaryResponse product;
}

package org.example.mapper;

import org.example.client.dto.CompanySummaryResponse;
import org.example.client.dto.ProductSummaryResponse;
import org.example.client.dto.UserSummaryResponse;
import org.example.dto.chat.ChatCreateResponse;
import org.example.dto.chat.ChatLastMessageResponse;
import org.example.dto.chat.ChatMessageResponse;
import org.example.dto.chat.ChatParticipantResponse;
import org.example.dto.chat.ChatProductSummaryResponse;
import org.example.dto.chat.ChatThreadResponse;
import org.example.dto.chat.ReadReceiptResponse;
import org.example.dto.chat.UploadAttachmentResponse;
import org.example.entity.ChatMessage;
import org.example.entity.ChatThread;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Chat entity va client DTO'larini frontendga qaytariladigan DTO'larga o'giradi.
 * Mapping ataylab qo'lda yozilgan: har bir response maydoni qayerdan kelishi aniq ko'rinadi.
 */
@Component
public class ChatMapper {

    /** Yangi yoki oldindan mavjud chat uchun qisqa javob tayyorlaydi. */
    public ChatCreateResponse toCreateResponse(ChatThread thread, boolean isNew) {
        ChatCreateResponse response = new ChatCreateResponse();
        response.setThreadId(thread.getId());
        response.setNew(isNew);
        return response;
    }

    /** Chatlar ro'yxatidagi bitta qatorni yig'adi. */
    public ChatThreadResponse toThreadResponse(
            ChatThread thread,
            ChatParticipantResponse otherParty,
            ChatLastMessageResponse lastMessage,
            long unreadCount,
            ChatProductSummaryResponse product
    ) {
        ChatThreadResponse response = new ChatThreadResponse();
        response.setThreadId(thread.getId());
        response.setOtherParty(otherParty);
        response.setLastMessage(lastMessage);
        response.setUnreadCount(unreadCount);
        response.setProduct(product);
        return response;
    }

    /** Buyer ko'radigan seller kompaniyasi ma'lumotlarini chat qatnashuvchisiga o'giradi. */
    public ChatParticipantResponse toCompanyParticipant(CompanySummaryResponse company) {
        ChatParticipantResponse response = new ChatParticipantResponse();
        response.setId(company.getId());
        response.setType("company");
        response.setDisplayName(company.getName());
        response.setSlug(company.getSlug());
        response.setAvatarUrl(company.getLogoPath());
        return response;
    }

    /** Seller ko'radigan buyer ma'lumotlarini chat qatnashuvchisiga o'giradi. */
    public ChatParticipantResponse toBuyerParticipant(UserSummaryResponse user) {
        ChatParticipantResponse response = new ChatParticipantResponse();
        response.setId(user.getId());
        response.setType("user");
        response.setDisplayName(resolveUserDisplayName(user));
        response.setUsername(user.getUsername());
        response.setAvatarUrl(user.getPhotoUrl());
        return response;
    }

    /** Product-service'dan kelgan mahsulotni chat uchun kichik ko'rinishga o'giradi. */
    public ChatProductSummaryResponse toProductSummary(ProductSummaryResponse product) {
        ChatProductSummaryResponse response = new ChatProductSummaryResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setSlug(product.getSlug());
        response.setPrice(product.getPrice());
        response.setCurrency(product.getCurrency());
        response.setPrimaryImage(product.getPrimaryImage());
        return response;
    }

    /** Chatlar ro'yxatida ko'rinadigan eng oxirgi xabarni tayyorlaydi. */
    public ChatLastMessageResponse toLastMessageResponse(ChatMessage message, String status) {
        ChatLastMessageResponse response = new ChatLastMessageResponse();
        response.setId(message.getId());
        response.setBody(message.getBody());
        response.setAttachmentUrl(message.getAttachmentUrl());
        response.setSentAt(message.getSentAt());
        response.setStatus(status);
        return response;
    }

    /** Bazadagi xabarni REST/WebSocket orqali yuboriladigan to'liq response'ga o'giradi. */
    public ChatMessageResponse toMessageResponse(
            ChatMessage message,
            LocalDateTime readAt,
            String status
    ) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(message.getId());
        response.setThreadId(message.getThread().getId());
        response.setSenderId(message.getSenderId());
        response.setSenderType(message.getSenderType().name().toLowerCase(Locale.ROOT));
        response.setBody(message.getBody());
        response.setAttachmentKey(message.getAttachmentKey());
        response.setAttachmentUrl(message.getAttachmentUrl());
        response.setSentAt(message.getSentAt());
        response.setDeliveredAt(message.getDeliveredAt());
        response.setReadAt(readAt);
        response.setStatus(status);
        return response;
    }

    /** Qaysi xabarlar o'qilganini bildiruvchi WebSocket javobini tayyorlaydi. */
    public ReadReceiptResponse toReadReceipt(Long threadId, List<Long> messageIds, Long readBy) {
        ReadReceiptResponse response = new ReadReceiptResponse();
        response.setThreadId(threadId);
        response.setMessageIds(messageIds);
        response.setReadBy(readBy);
        return response;
    }

    /** MinIO'ga yuklangan faylning kaliti va ochiq URL'ini response'ga joylaydi. */
    public UploadAttachmentResponse toUploadAttachment(String attachmentKey, String attachmentUrl) {
        UploadAttachmentResponse response = new UploadAttachmentResponse();
        response.setAttachmentKey(attachmentKey);
        response.setAttachmentUrl(attachmentUrl);
        return response;
    }

    private String resolveUserDisplayName(UserSummaryResponse user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName();
        String lastName = user.getLastName() == null ? "" : user.getLastName();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? user.getUsername() : fullName;
    }
}

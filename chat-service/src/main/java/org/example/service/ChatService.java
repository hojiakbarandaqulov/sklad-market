package org.example.service;

import jakarta.validation.Valid;
import org.example.dto.PagedResponse;
import org.example.dto.chat.*;
import org.example.enums.AppLanguage;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChatService {

    /** Login qilgan foydalanuvchining buyer va seller chatlarini qaytaradi. */
    PagedResponse<ChatThreadResponse> getThreads(int page, int perPage);

    /** Buyer uchun yangi chat yaratadi yoki mavjud chatni qayta ochadi. */
    ChatCreateResponse createThread(CreateChatRequest request, AppLanguage language);

    /** Chat xabarlarini oddiy pagination yoki beforeId orqali qaytaradi. */
    PagedResponse<ChatMessageResponse> getMessages(Long threadId, int page, int perPage, Long beforeId);

    /** Barcha chatlardagi jami o'qilmagan xabarlar sonini hisoblaydi. */
    UnreadCountResponse getUnreadCount();

    /** Chatga rasm yuklaydi va attachment key/url qaytaradi. */
    UploadAttachmentResponse uploadAttachment(Long threadId, MultipartFile file);

    /** Chatga oddiy fayl yuklaydi va attachment key/url qaytaradi. */
    UploadAttachmentResponse uploadFileAttachment(Long threadId, MultipartFile file);

    /** Chatni joriy qatnashuvchining ro'yxatidan yashiradi. */
    void hideThread(Long threadId);

    /** Ichki servis uchun chatni ikkala tomon uchun bloklaydi. */
    void blockThread(Long threadId);

    /** WebSocket ulanishiga qisqa muddatli token beradi. */
    WsTokenResponse issueWsToken();

    /** Foydalanuvchi chat qatnashuvchisi ekanini tekshiradi. */
    void validateThreadAccess(Long userId, Long threadId);

    /** WebSocket orqali kelgan yangi xabarni saqlaydi. */
    ChatMessageResponse sendMessage(Long userId, Long threadId, String body, String attachmentKey);

    /** Qarshi tomondan kelgan xabarlarni o'qilgan deb belgilaydi. */
    ReadReceiptResponse markMessagesRead(Long userId, Long threadId, List<Long> messageIds);

    ChatCreateResponse createThreadSeller(CreateChatSeller request, AppLanguage language);
}

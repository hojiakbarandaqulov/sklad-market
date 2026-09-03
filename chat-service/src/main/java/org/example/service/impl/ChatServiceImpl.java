package org.example.service.impl;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.example.client.CompanyClient;
import org.example.client.ProductClient;
import org.example.client.UserClient;
import org.example.client.dto.CompanyOwnershipResponse;
import org.example.client.dto.CompanySummaryResponse;
import org.example.client.dto.ProductSummaryResponse;
import org.example.client.dto.UserSummaryResponse;
import org.example.dto.PageMeta;
import org.example.dto.PagedResponse;
import org.example.dto.chat.*;
import org.example.entity.ChatMessage;
import org.example.entity.ChatThread;
import org.example.enums.AppLanguage;
import org.example.enums.ChatParticipantType;
import org.example.enums.RequesterRole;
import org.example.exp.AppBadException;
import org.example.mapper.ChatMapper;
import org.example.repository.ChatMessageRepository;
import org.example.repository.ChatThreadRepository;
import org.example.service.ChatService;
import org.example.service.ResourceBundleService;
import org.example.service.ChatWebSocketTokenService;
import org.example.utils.SpringSecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

import static org.example.utils.SpringSecurityUtil.getProfileId;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/jpg"
    );

    private final ChatThreadRepository chatThreadRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CompanyClient companyClient;
    private final UserClient userClient;
    private final ProductClient productClient;
    private final ChatMapper chatMapper;
    private final MinioClient minioClient;
    private final ChatWebSocketTokenService chatWebSocketTokenService;
    private final ResourceBundleService messageService;

    @Value("${aws.bucket-name}")
    private String bucketName;

    @Value("${media.base-url}")
    private String mediaBaseUrl;

    @Override
    public PagedResponse<ChatThreadResponse> getThreads(int page, int perPage) {
        Long currentUserId = requireCurrentUserId();
        validatePage(page, perPage);

        // Avval foydalanuvchi buyer sifatida qatnashgan chatlarni olamiz.
        Sort sort = Sort.by(Sort.Order.desc("lastMessageAt"), Sort.Order.desc("modifiedDate"), Sort.Order.desc("id"));
        List<ChatThread> threads = new ArrayList<>(chatThreadRepository.findByBuyerIdAndBuyerHiddenFalseAndDeletedFalse(currentUserId, sort));

        // Foydalanuvchi seller bo'lsa, unga tegishli kompaniyalarning chatlarini ham qo'shamiz.
        List<Long> ownedCompanyIds = getOwnedCompanyIds(currentUserId);
        if (!ownedCompanyIds.isEmpty()) {
            threads.addAll(chatThreadRepository.findBySellerCompanyIdInAndSellerHiddenFalseAndDeletedFalse(ownedCompanyIds, sort));
        }

        // Bir chat buyer va seller qidiruvlaridan bir vaqtda kelib qolsa, dublikatni olib tashlaymiz.
        List<ChatThread> uniqueThreads = new ArrayList<>(new LinkedHashSet<>(threads));
        uniqueThreads.sort(Comparator
                .comparing(ChatThread::getLastMessageAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ChatThread::getModifiedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ChatThread::getId, Comparator.reverseOrder()));

        List<ChatThreadResponse> responses = uniqueThreads.stream()
                .map(thread -> toThreadResponse(thread, resolveParticipantType(currentUserId, thread)))
                .toList();

        return ServiceHelper.toPagedResponse(responses, page, perPage);
    }

    @Override
    @Transactional
    public ChatCreateResponse createThread(CreateChatRequest request, AppLanguage language) {
        Long buyerId = requireCurrentUserId();

        // Company-service orqali kompaniya mavjudligi va buyer uning egasi emasligini tekshiramiz.
        CompanyOwnershipResponse company = companyClient.checkOwnership(request.getSellerCompanyId(), buyerId);

        if (!company.isExists() || !company.isActive()) {
            throw new AppBadException(messageService.getMessage("chat.seller.company.unavailable", language));
        }

        if (company.isOwner()) {
            throw new AppBadException(messageService.getMessage("chat.own.company.not.allowed", language));
        }

        if (request.getProductId() != null) {
            // Product berilgan bo'lsa, u aynan requestdagi kompaniyaga tegishli bo'lishi shart.
            ProductSummaryResponse productSummary = productClient.getSummary(request.getProductId());
            if (!request.getSellerCompanyId().equals(productSummary.getCompanyId())) {
                throw new AppBadException(messageService.getMessage("chat.product.company.mismatch", language));
            }
        }
        Optional<ChatThread> existingThreadOptional = chatThreadRepository.findUnique(
                buyerId,
                request.getSellerCompanyId(),
                request.getProductId()
        );

// Avval yaratilgan chat mavjud bo‘lsa, uni qayta ochamiz.
        if (existingThreadOptional.isPresent()) {
            ChatThread existingThread = existingThreadOptional.get();

            existingThread.setBuyerHidden(Boolean.FALSE);
            ChatThread reopenedThread = chatThreadRepository.save(existingThread);

            return chatMapper.toCreateResponse(reopenedThread, false);
        }

// Chat mavjud bo‘lmasa, yangi chat yaratamiz.
        ChatThread newThread = new ChatThread();
        newThread.setBuyerId(buyerId);
        newThread.setSellerCompanyId(request.getSellerCompanyId());
        newThread.setProductId(request.getProductId());

        ChatThread savedThread = chatThreadRepository.save(newThread);

        return chatMapper.toCreateResponse(savedThread, true);
    }

    @Override
    public ChatCreateResponse createThreadSeller(CreateChatSeller request, AppLanguage language) {
        Long sellerId = requireCurrentUserId();
        Long buyerId = request.getBuyerId();

        CompanyOwnershipResponse company = companyClient.checkOwnership(request.getSellerCompanyId(), sellerId);

        if (!company.isExists() || !company.isActive()) {
            throw new AppBadException(messageService.getMessage("chat.seller.company.unavailable", language));
        }

        if (!company.isOwner()) {
            throw new AppBadException(messageService.getMessage("chat.own.company.not.allowed", language));
        }

        if (request.getProductId() != null) {
            // Product berilgan bo'lsa, u aynan requestdagi kompaniyaga tegishli bo'lishi shart.
            ProductSummaryResponse productSummary = productClient.getSummary(request.getProductId());
            if (!request.getSellerCompanyId().equals(productSummary.getCompanyId())) {
                throw new AppBadException(messageService.getMessage("chat.product.company.mismatch", language));
            }
        }
        Optional<ChatThread> existingThreadOptional = chatThreadRepository.findUnique(
                buyerId,
                request.getSellerCompanyId(),
                request.getProductId()
        );

        if (existingThreadOptional.isPresent()) {
            ChatThread existingThread = existingThreadOptional.get();

            existingThread.setBuyerHidden(Boolean.FALSE);
            ChatThread reopenedThread = chatThreadRepository.save(existingThread);

            return chatMapper.toCreateResponse(reopenedThread, false);
        }

// Chat mavjud bo‘lmasa, yangi chat yaratamiz.
        ChatThread newThread = new ChatThread();
        newThread.setBuyerId(buyerId);
        newThread.setSellerCompanyId(request.getSellerCompanyId());
        newThread.setProductId(request.getProductId());
        newThread.setBuyerHidden(false);
        newThread.setSellerHidden(false);

        ChatThread savedThread = chatThreadRepository.save(newThread);

        return chatMapper.toCreateResponse(savedThread, true);
    }

    @Override
    public PagedResponse<ChatMessageResponse> getMessages(Long threadId, int page, int perPage, Long beforeId) {
        Long currentUserId = requireCurrentUserId();
        validatePage(page, perPage);
        // Foydalanuvchi shu chatning buyer'i yoki seller'i ekanini tekshiramiz.
        resolveThreadContext(currentUserId, threadId);

        // before_id bo'lsa eski xabarlar olinadi; bu chat scroll pagination uchun ishlatiladi.
        Page<ChatMessage> result = beforeId == null
                ? chatMessageRepository.findByThread_IdAndDeletedFalse(
                threadId,
                PageRequest.of(Math.max(page - 1, 0), perPage, Sort.by(Sort.Direction.DESC, "id"))
        )
                : chatMessageRepository.findByThread_IdAndIdLessThanAndDeletedFalse(
                threadId,
                beforeId,
                PageRequest.of(0, perPage, Sort.by(Sort.Direction.DESC, "id"))
        );

        List<ChatMessageResponse> items = result.getContent().stream()
                .sorted(Comparator.comparing(ChatMessage::getId))
                .map(this::toMessageResponse)
                .toList();

        return new PagedResponse<>(items, new PageMeta(result.getTotalElements(), page, perPage, result.getTotalPages()));
    }

    @Override
    public UnreadCountResponse getUnreadCount() {
        Long currentUserId = requireCurrentUserId();

        // Buyer sifatida sellerdan kelgan, hali o'qilmagan xabarlarni sanaymiz.
        long buyerUnread = chatMessageRepository
                .countByThread_BuyerIdAndThread_DeletedFalseAndDeletedFalseAndSenderTypeAndBuyerReadAtIsNull(currentUserId, ChatParticipantType.SELLER);

        List<Long> ownedCompanyIds = getOwnedCompanyIds(currentUserId);
        // Seller sifatida buyer'lardan kelgan, hali o'qilmagan xabarlarni ham qo'shamiz.
        long sellerUnread = ownedCompanyIds.isEmpty()
                ? 0L
                : chatMessageRepository.countByThread_SellerCompanyIdInAndThread_DeletedFalseAndDeletedFalseAndSenderTypeAndSellerReadAtIsNull(
                ownedCompanyIds,
                ChatParticipantType.BUYER
        );

        return new UnreadCountResponse(buyerUnread + sellerUnread);
    }

    @Override
    @Transactional
    public UploadAttachmentResponse uploadAttachment(Long threadId, MultipartFile file) {
        Long currentUserId = requireCurrentUserId();
        resolveThreadContext(currentUserId, threadId);
        validateImage(file);
        return uploadFileToStorage(threadId, file);
    }

    @Override
    @Transactional
    public UploadAttachmentResponse uploadFileAttachment(Long threadId, MultipartFile file) {
        Long currentUserId = requireCurrentUserId();
        resolveThreadContext(currentUserId, threadId);
        validateFile(file);
        return uploadFileToStorage(threadId, file);
    }

    @Override
    @Transactional
    public void hideThread(Long threadId) {
        Long currentUserId = requireCurrentUserId();
        ThreadContext context = resolveThreadContext(currentUserId, threadId);

        if (context.participantType == ChatParticipantType.BUYER) {
            // Hide faqat buyer ro'yxatidan yashiradi, bazadan butunlay o'chirmaydi.
            context.thread.setBuyerHidden(Boolean.TRUE);
        } else {
            // Seller yashirsa ham buyer tomonda chat saqlanib qoladi.
            context.thread.setSellerHidden(Boolean.TRUE);
        }

        chatThreadRepository.save(context.thread);
    }

    @Override
    public void blockThread(Long threadId) {
        ChatThread thread = chatThreadRepository.findByIdAndDeletedFalse(threadId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("chat.thread.not.found")));
        thread.setDeleted(Boolean.TRUE);
        chatThreadRepository.save(thread);
    }

    @Override
    public WsTokenResponse issueWsToken() {
        return chatWebSocketTokenService.issueToken();
    }

    @Override
    public void validateThreadAccess(Long userId, Long threadId) {
        resolveThreadContext(userId, threadId);
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(Long userId, Long threadId, String body, String attachmentKey) {
        ThreadContext context = resolveThreadContext(userId, threadId);
        String normalizedBody = normalizeBody(body);
        String normalizedAttachmentKey = normalizeAttachmentKey(threadId, attachmentKey);

        if ((normalizedBody == null || normalizedBody.isBlank()) && normalizedAttachmentKey == null) {
            throw new AppBadException(messageService.getMessage("chat.message.content.required"));
        }

        LocalDateTime now = LocalDateTime.now();
        // Yangi xabarni yuboruvchining turi bilan birga saqlaymiz.
        ChatMessage message = new ChatMessage();
        message.setThread(context.thread);
        message.setSenderId(userId);
        message.setSenderType(context.participantType);
        message.setBody(normalizedBody);
        message.setAttachmentKey(normalizedAttachmentKey);
        message.setAttachmentUrl(normalizedAttachmentKey == null ? null : mediaBaseUrl + "/" + normalizedAttachmentKey);
        message.setSentAt(now);
        message.setDeliveredAt(now);

        ChatMessage saved = chatMessageRepository.save(message);

        // Oxirgi xabar vaqtini yangilaymiz va ikki tomonda ham chatni qayta ko'rsatamiz.
        context.thread.setLastMessageAt(now);
        context.thread.setBuyerHidden(Boolean.FALSE);
        context.thread.setSellerHidden(Boolean.FALSE);
        chatThreadRepository.save(context.thread);

        return toMessageResponse(saved);
    }

    @Override
    @Transactional
    public ReadReceiptResponse markMessagesRead(Long userId, Long threadId, List<Long> messageIds) {
        ThreadContext context = resolveThreadContext(userId, threadId);
        List<ChatMessage> messages = chatMessageRepository.findByThread_IdAndIdInAndDeletedFalse(threadId, messageIds);
        LocalDateTime now = LocalDateTime.now();
        List<Long> updatedIds = new ArrayList<>();

        for (ChatMessage message : messages) {
            // Foydalanuvchi o'zi yuborgan xabarni o'qilgan deb belgilamaymiz.
            if (message.getSenderType() == context.participantType) {
                continue;
            }

            if (context.participantType == ChatParticipantType.BUYER && message.getBuyerReadAt() == null) {
                message.setBuyerReadAt(now);
                updatedIds.add(message.getId());
            }

            if (context.participantType == ChatParticipantType.SELLER && message.getSellerReadAt() == null) {
                message.setSellerReadAt(now);
                updatedIds.add(message.getId());
            }
        }

        if (!messages.isEmpty()) {
            chatMessageRepository.saveAll(messages);
        }

        return chatMapper.toReadReceipt(threadId, updatedIds, userId);
    }


    private ChatThreadResponse toThreadResponse(ChatThread thread, ChatParticipantType participantType) {
        ChatMessage lastMessage = chatMessageRepository.findFirstByThread_IdAndDeletedFalseOrderByIdDesc(thread.getId()).orElse(null);
        long unreadCount = participantType == ChatParticipantType.BUYER
                ? chatMessageRepository.countByThread_IdAndDeletedFalseAndSenderTypeAndBuyerReadAtIsNull(thread.getId(), ChatParticipantType.SELLER)
                : chatMessageRepository.countByThread_IdAndDeletedFalseAndSenderTypeAndSellerReadAtIsNull(thread.getId(), ChatParticipantType.BUYER);

        return chatMapper.toThreadResponse(
                thread,
                resolveOtherParty(thread, participantType),
                lastMessage == null ? null : toLastMessageResponse(lastMessage),
                unreadCount,
                resolveProduct(thread.getProductId())
        );
    }

    private ChatParticipantResponse resolveOtherParty(ChatThread thread, ChatParticipantType participantType) {
        if (participantType == ChatParticipantType.BUYER) {
            CompanySummaryResponse company = companyClient.getSummary(thread.getSellerCompanyId());
            return chatMapper.toCompanyParticipant(company);
        }

        UserSummaryResponse user = userClient.getSummary(thread.getBuyerId());
        return chatMapper.toBuyerParticipant(user);
    }

    private ChatProductSummaryResponse resolveProduct(Long productId) {
        if (productId == null) {
            return null;
        }

        ProductSummaryResponse product = productClient.getSummary(productId);
        return chatMapper.toProductSummary(product);
    }

    private ChatLastMessageResponse toLastMessageResponse(ChatMessage message) {
        return chatMapper.toLastMessageResponse(message, resolveStatus(message));
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return chatMapper.toMessageResponse(
                message,
                resolveReadAtForSenderPerspective(message),
                resolveStatus(message)
        );
    }

    private String resolveStatus(ChatMessage message) {
        if (resolveReadAtForSenderPerspective(message) != null) {
            return "read";
        }
        if (message.getDeliveredAt() != null) {
            return "delivered";
        }
        return "sent";
    }

    private LocalDateTime resolveReadAtForSenderPerspective(ChatMessage message) {
        return message.getSenderType() == ChatParticipantType.BUYER ? message.getSellerReadAt() : message.getBuyerReadAt();
    }

    private ThreadContext resolveThreadContext(Long userId, Long threadId) {
        ChatThread thread = chatThreadRepository.findByIdAndDeletedFalse(threadId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("chat.thread.not.found")));

        if (userId.equals(thread.getBuyerId())) {
            // Thread'dagi buyerId token ichidagi profileId bilan bir xil bo'lsa, bu buyer.
            return new ThreadContext(thread, ChatParticipantType.BUYER);
        }

        // Buyer bo'lmasa, company-service orqali seller kompaniya egasi ekanini tekshiramiz.
        CompanyOwnershipResponse ownership = companyClient.checkOwnership(thread.getSellerCompanyId(), userId);
        if (ownership.isOwner()) {
            return new ThreadContext(thread, ChatParticipantType.SELLER);
        }

        throw new AppBadException(messageService.getMessage("chat.thread.access.denied"));
    }

    private ChatParticipantType resolveParticipantType(Long userId, ChatThread thread) {
        return resolveThreadContext(userId, thread.getId()).participantType;
    }

    private List<Long> getOwnedCompanyIds(Long userId) {
        try {
            return companyClient.getOwnedCompanyIds(userId);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Long requireCurrentUserId() {
        Long userId = getProfileId();
        if (userId == null) {
            throw new AppBadException(messageService.getMessage("auth.unauthorized"));
        }
        return userId;
    }

    private void validatePage(int page, int perPage) {
        if (page < 1) {
            throw new AppBadException(messageService.getMessage("validation.page.min"));
        }
        if (perPage < 1 || perPage > 100) {
            throw new AppBadException(messageService.getMessage("validation.per.page.range"));
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppBadException(messageService.getMessage("chat.image.required"));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new AppBadException(messageService.getMessage("chat.image.size.limit"));
        }
        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType().toLowerCase(Locale.ROOT))) {
            throw new AppBadException(messageService.getMessage("chat.image.type.invalid"));
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppBadException(messageService.getMessage("chat.file.required"));
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new AppBadException(messageService.getMessage("chat.file.size.limit"));
        }
    }

    private UploadAttachmentResponse uploadFileToStorage(Long threadId, MultipartFile file) {
        String extension = extractExtension(file.getOriginalFilename());
        String objectKey = "chat/" + threadId + "/" + UUID.randomUUID() + "." + extension;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            throw new AppBadException(messageService.getMessage("chat.attachment.upload.failed"));
        }

        return chatMapper.toUploadAttachment(objectKey, mediaBaseUrl + "/" + objectKey);
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new AppBadException(messageService.getMessage("chat.file.name.invalid"));
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeBody(String body) {
        if (body == null) {
            return null;
        }
        String trimmed = body.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeAttachmentKey(Long threadId, String attachmentKey) {
        if (attachmentKey == null || attachmentKey.isBlank()) {
            return null;
        }

        String normalized = attachmentKey.trim();
        if (!normalized.startsWith("chat/" + threadId + "/")) {
            throw new AppBadException(messageService.getMessage("chat.attachment.thread.mismatch"));
        }

        return normalized;
    }

    private record ThreadContext(ChatThread thread, ChatParticipantType participantType) {
    }
}

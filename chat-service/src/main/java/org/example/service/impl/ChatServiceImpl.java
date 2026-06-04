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
import org.example.enums.ChatParticipantType;
import org.example.exp.AppBadException;
import org.example.repository.ChatMessageRepository;
import org.example.repository.ChatThreadRepository;
import org.example.service.ChatService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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
    private final MinioClient minioClient;
    private final ChatWebSocketTokenService chatWebSocketTokenService;

    @Value("${aws.bucket-name}")
    private String bucketName;

    @Value("${media.base-url}")
    private String mediaBaseUrl;

    @Override
    public PagedResponse<ChatThreadResponse> getThreads(int page, int perPage) {
        Long currentUserId = requireCurrentUserId();
        validatePage(page, perPage);

        Sort sort = Sort.by(Sort.Order.desc("lastMessageAt"), Sort.Order.desc("modifiedDate"), Sort.Order.desc("id"));
        List<ChatThread> threads = new ArrayList<>(chatThreadRepository.findByBuyerIdAndBuyerHiddenFalseAndDeletedFalse(currentUserId, sort));

        List<Long> ownedCompanyIds = getOwnedCompanyIds(currentUserId);
        if (!ownedCompanyIds.isEmpty()) {
            threads.addAll(chatThreadRepository.findBySellerCompanyIdInAndSellerHiddenFalseAndDeletedFalse(ownedCompanyIds, sort));
        }

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
    public ChatCreateResponse createThread(CreateChatRequest request) {
        Long buyerId = SpringSecurityUtil.getProfileId();
        CompanyOwnershipResponse company = companyClient.checkOwnership(request.getSellerCompanyId(), buyerId);

        if (!company.isExists() || !company.isActive()) {
            throw new AppBadException("Seller company is not available");
        }

        if (company.isOwner()) {
            throw new AppBadException("You cannot start a chat with your own company");
        }

        if (request.getProductId() != null) {
            ProductSummaryResponse productSummary = productClient.getSummary(request.getProductId());
            if (!request.getSellerCompanyId().equals(productSummary.getCompanyId())) {
                throw new AppBadException("Product does not belong to the selected company");
            }
        }

        return chatThreadRepository.findUnique(buyerId, request.getSellerCompanyId(), request.getProductId())
                .map(existing -> {
                    existing.setBuyerHidden(Boolean.FALSE);
                    chatThreadRepository.save(existing);
                    return ChatCreateResponse.builder()
                            .threadId(existing.getId())
                            .isNew(false)
                            .build();
                })
                .orElseGet(() -> {
                    ChatThread thread = new ChatThread();
                    thread.setBuyerId(buyerId);
                    thread.setSellerCompanyId(request.getSellerCompanyId());
                    thread.setProductId(request.getProductId());
                    ChatThread saved = chatThreadRepository.save(thread);
                    return ChatCreateResponse.builder()
                            .threadId(saved.getId())
                            .isNew(true)
                            .build();
                });
    }

    @Override
    public PagedResponse<ChatMessageResponse> getMessages(Long threadId, int page, int perPage, Long beforeId) {
        Long currentUserId = requireCurrentUserId();
        validatePage(page, perPage);
        ThreadContext context = resolveThreadContext(currentUserId, threadId);

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
                .map(message -> toMessageResponse(message))
                .toList();

        return new PagedResponse<>(items, new PageMeta(result.getTotalElements(), page, perPage, result.getTotalPages()));
    }

    @Override
    public UnreadCountResponse getUnreadCount() {
        Long currentUserId = requireCurrentUserId();
        long buyerUnread = chatMessageRepository
                .countByThread_BuyerIdAndThread_DeletedFalseAndDeletedFalseAndSenderTypeAndBuyerReadAtIsNull(currentUserId, ChatParticipantType.SELLER);

        List<Long> ownedCompanyIds = getOwnedCompanyIds(currentUserId);
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
            context.thread.setBuyerHidden(Boolean.TRUE);
        } else {
            context.thread.setSellerHidden(Boolean.TRUE);
        }

        chatThreadRepository.save(context.thread);
    }

    @Override
    public void blockThread(Long threadId) {
        ChatThread thread = chatThreadRepository.findByIdAndDeletedFalse(threadId)
                .orElseThrow(() -> new AppBadException("Thread not found"));
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
            throw new AppBadException("Message body or attachment is required");
        }

        LocalDateTime now = LocalDateTime.now();
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

        return ReadReceiptResponse.builder()
                .threadId(threadId)
                .messageIds(updatedIds)
                .readBy(userId)
                .build();
    }

    private ChatThreadResponse toThreadResponse(ChatThread thread, ChatParticipantType participantType) {
        ChatMessage lastMessage = chatMessageRepository.findFirstByThread_IdAndDeletedFalseOrderByIdDesc(thread.getId()).orElse(null);
        long unreadCount = participantType == ChatParticipantType.BUYER
                ? chatMessageRepository.countByThread_IdAndDeletedFalseAndSenderTypeAndBuyerReadAtIsNull(thread.getId(), ChatParticipantType.SELLER)
                : chatMessageRepository.countByThread_IdAndDeletedFalseAndSenderTypeAndSellerReadAtIsNull(thread.getId(), ChatParticipantType.BUYER);

        return ChatThreadResponse.builder()
                .threadId(thread.getId())
                .otherParty(resolveOtherParty(thread, participantType))
                .lastMessage(lastMessage == null ? null : toLastMessageResponse(lastMessage))
                .unreadCount(unreadCount)
                .product(resolveProduct(thread.getProductId()))
                .build();
    }

    private ChatParticipantResponse resolveOtherParty(ChatThread thread, ChatParticipantType participantType) {
        if (participantType == ChatParticipantType.BUYER) {
            CompanySummaryResponse company = companyClient.getSummary(thread.getSellerCompanyId());
            return ChatParticipantResponse.builder()
                    .id(company.getId())
                    .type("company")
                    .displayName(company.getName())
                    .slug(company.getSlug())
                    .avatarUrl(company.getLogoPath())
                    .build();
        }

        UserSummaryResponse user = userClient.getSummary(thread.getBuyerId());
        String displayName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (displayName.isBlank()) {
            displayName = user.getUsername();
        }

        return ChatParticipantResponse.builder()
                .id(user.getId())
                .type("user")
                .displayName(displayName)
                .username(user.getUsername())
                .avatarUrl(user.getPhotoUrl())
                .build();
    }

    private ChatProductSummaryResponse resolveProduct(Long productId) {
        if (productId == null) {
            return null;
        }

        ProductSummaryResponse product = productClient.getSummary(productId);
        return ChatProductSummaryResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .primaryImage(product.getPrimaryImage())
                .build();
    }

    private ChatLastMessageResponse toLastMessageResponse(ChatMessage message) {
        return ChatLastMessageResponse.builder()
                .id(message.getId())
                .body(message.getBody())
                .attachmentUrl(message.getAttachmentUrl())
                .sentAt(message.getSentAt())
                .status(resolveStatus(message))
                .build();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .threadId(message.getThread().getId())
                .senderId(message.getSenderId())
                .senderType(message.getSenderType().name().toLowerCase(Locale.ROOT))
                .body(message.getBody())
                .attachmentKey(message.getAttachmentKey())
                .attachmentUrl(message.getAttachmentUrl())
                .sentAt(message.getSentAt())
                .deliveredAt(message.getDeliveredAt())
                .readAt(resolveReadAtForSenderPerspective(message))
                .status(resolveStatus(message))
                .build();
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
                .orElseThrow(() -> new AppBadException("Thread not found"));

        if (userId.equals(thread.getBuyerId())) {
            return new ThreadContext(thread, ChatParticipantType.BUYER);
        }

        CompanyOwnershipResponse ownership = companyClient.checkOwnership(thread.getSellerCompanyId(), userId);
        if (ownership.isOwner()) {
            return new ThreadContext(thread, ChatParticipantType.SELLER);
        }

        throw new AppBadException("You do not have access to this thread");
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
            throw new AppBadException("Unauthorized");
        }
        return userId;
    }

    private void validatePage(int page, int perPage) {
        if (page < 1) {
            throw new AppBadException("page must be greater than or equal to 1");
        }
        if (perPage < 1 || perPage > 100) {
            throw new AppBadException("per_page must be between 1 and 100");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppBadException("Image file is required");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new AppBadException("Image size must be 5MB or less");
        }
        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType().toLowerCase(Locale.ROOT))) {
            throw new AppBadException("Only jpg, jpeg, png and webp are allowed");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppBadException("File is required");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new AppBadException("File size must be 10MB or less");
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
            throw new AppBadException("Attachment upload failed");
        }

        return UploadAttachmentResponse.builder()
                .attachmentKey(objectKey)
                .attachmentUrl(mediaBaseUrl + "/" + objectKey)
                .build();
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new AppBadException("Invalid file name");
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
            throw new AppBadException("Attachment does not belong to this thread");
        }

        return normalized;
    }

    private record ThreadContext(ChatThread thread, ChatParticipantType participantType) {
    }
}

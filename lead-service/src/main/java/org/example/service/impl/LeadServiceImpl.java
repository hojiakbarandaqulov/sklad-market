package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.clent.ProductClient;
import org.example.dto.*;
import org.example.dto.internal.ProductInternalSummaryResponse;
import org.example.entity.Lead;
import org.example.entity.LeadItem;
import org.example.enums.AppLanguage;
import org.example.enums.LeadSource;
import org.example.enums.LeadStatus;
import org.example.exp.AppBadException;
import org.example.exp.AppConflictException;
import org.example.exp.AppNotFoundException;
import org.example.mapper.LeadMapper;
import org.example.repository.LeadItemRepository;
import org.example.repository.LeadRepository;
import org.example.service.LeadService;
import org.example.service.ResourceBundleService;
import org.example.utils.SpringSecurityUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LeadServiceImpl implements LeadService {
    private final LeadRepository leadRepository;
    private final LeadItemRepository leadItemRepository;
    private final ResourceBundleService messageService;
    private final ProductClient client;
    private final LeadMapper leadMapper;
    private final LeadQuantityValidator quantities;

    @Override
    public LeadResponse create(LeadCreateRequest request, AppLanguage language) {
        Long buyerId = requireProfileId(language);

        List<Long> productIds;
        if (request.getSource() == LeadSource.CART) {
            productIds = request.getProductIds();
        } else {
            productIds = List.of(request.getProductId());
        }

        if (productIds == null || productIds.isEmpty()) {
            throw new AppBadException(messageService.getMessage("lead.items.required", language));
        }

        List<ProductInternalSummaryResponse> products = productIds.stream()
                .map(client::getById)
                .toList();

        java.math.BigDecimal quantity = request.getQuantity() == null ? java.math.BigDecimal.ONE : request.getQuantity();
        products.forEach(product -> quantities.validate(product, quantity, language));

        Lead lead = new Lead();
        lead.setBuyerId(buyerId);
        lead.setSellerId(products.get(0).getSellerId());
        lead.setCompanyId(products.get(0).getCompanyId());
        lead.setSource(request.getSource());
        lead.setContactName(request.getContactName());
        lead.setContactPhone(request.getContactPhone());
        lead.setContactEmail(request.getContactEmail());
        lead.setDeliveryAddress(request.getDeliveryAddress());
        lead.setNeededDate(request.getNeededDate());
        lead.setComment(request.getComment());
        Lead savedLead = leadRepository.save(lead);

        for (ProductInternalSummaryResponse product : products) {
            LeadItem item = new LeadItem();
            item.setLeadId(savedLead.getId());
            item.setProductId(product.getId());
            item.setProductNameSnapshot(product.getName());
            item.setPriceSnapshot(product.getPrice());
            item.setQuantity(quantity);
            leadItemRepository.save(item);
        }
        return leadMapper.toResponse(savedLead);
    }

    @Override
    public PagedResponse<LeadResponse> getBuyerLeads(LeadStatus status, LeadSource source, int page, int perPage, AppLanguage language) {
        Long buyerId = requireProfileId(language);
        Specification<Lead> spec = (root, query, cb) -> cb.and(cb.equal(root.get("buyerId"), buyerId), cb.isFalse(root.get("deleted")));
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (source != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("source"), source));
        }
        Page<Lead> leads = leadRepository.findAll(spec, PageRequest.of(Math.max(page - 1, 0), perPage));
        return ServiceHelper.toPagedResponse(leads.map(leadMapper::toResponse));
    }

    @Override
    public PagedResponse<LeadResponse> getSellerLeads(Long companyId, LeadStatus status, LeadSource source, int page, int perPage, AppLanguage language) {
        Long sellerId = requireProfileId(language);
        Specification<Lead> spec = (root, query, cb) -> cb.and(cb.equal(root.get("sellerId"), sellerId), cb.isFalse(root.get("deleted")));
        if (companyId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("companyId"), companyId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (source !=null){
            spec = spec.and((root, query, cb) -> cb.equal(root.get("source"), source));
        }
        Page<Lead> leads = leadRepository.findAll(spec, PageRequest.of(Math.max(page - 1, 0), perPage));
        return ServiceHelper.toPagedResponse(leads.map(leadMapper::toResponse));
    }

    @Override
    public LeadResponse getById(Long id, AppLanguage language) {
        Lead lead = findLead(id, language);
        Long profileId = requireProfileId(language);
        if (!profileId.equals(lead.getBuyerId()) && !profileId.equals(lead.getSellerId())) {
            throw new AppBadException(messageService.getMessage("lead.forbidden", language));
        }
        return leadMapper.toResponse(lead);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Boolean cancel(Long id, AppLanguage language) {
        Lead lead = leadRepository.lockPresent(id).orElseThrow(() -> new AppBadException(messageService.getMessage("lead.not.found", language)));
        if (lead.getStatus() == LeadStatus.ACCEPTED) throw new AppBadException(messageService.getMessage("lead.accept.invalid", language));
        if (!requireProfileId(language).equals(lead.getBuyerId())) {
            throw new AppBadException(messageService.getMessage("lead.forbidden", language));
        }
        lead.setStatus(LeadStatus.CANCELED);
        leadRepository.save(lead);
        return true;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public LeadResponse updateStatus(Long id, LeadStatusUpdateRequest request, AppLanguage language) {
        Lead lead = leadRepository.lockPresent(id).orElseThrow(() -> new AppBadException(messageService.getMessage("lead.not.found",language)));
        if (lead.getStatus()==LeadStatus.ACCEPTED || request.getStatus()==LeadStatus.ACCEPTED)
            throw new AppBadException(messageService.getMessage("lead.accept.invalid",language));
        if (!requireProfileId(language).equals(lead.getSellerId())) {
            throw new AppBadException(messageService.getMessage("lead.forbidden", language));
        }
        lead.setStatus(request.getStatus());
        lead.setCloseReason(request.getCloseReason());
        return leadMapper.toResponse(leadRepository.save(lead));
    }

    @Override
    @Transactional
    public LeadResponse updateQuantities(Long id, LeadQuantityUpdateRequest request, AppLanguage language) {
        Long buyerId = requireProfileId(language);
        // Sotuvchi so'rovni qabul qilayotganda ham shu lead yozuvi bloklanadi.
        Lead lead = leadRepository.lockPresent(id)
                .orElseThrow(() -> new AppNotFoundException(messageService.getMessage("lead.not.found", language)));
        if (!buyerId.equals(lead.getBuyerId())) {
            throw new AccessDeniedException(messageService.getMessage("lead.forbidden", language));
        }
        if (lead.getAcceptedOrderId() != null
                || !Set.of(LeadStatus.NEW, LeadStatus.VIEWED, LeadStatus.CONTACTED).contains(lead.getStatus())) {
            throw new AppConflictException(messageService.getMessage("lead.quantity.edit.status", language));
        }

        List<LeadItem> items = leadItemRepository.findByLeadIdAndDeletedFalse(id);
        Set<Long> updatedProductIds = new HashSet<>();
        for (LeadQuantityUpdateRequest.Item update : request.items()) {
            List<LeadItem> matching = items.stream()
                    .filter(item -> update.productId().equals(item.getProductId()))
                    .toList();
            if (matching.size() != 1 || !updatedProductIds.add(update.productId())) {
                throw new AppBadException(messageService.getMessage("lead.quantity.edit.items", language));
            }
            quantities.validate(client.getById(update.productId()), update.quantity(), language);
            matching.getFirst().setQuantity(update.quantity());
        }

        return leadMapper.toResponse(lead);
    }

    private Lead findLead(Long id, AppLanguage language) {
        return leadRepository.findById(id)
                .filter(item -> Boolean.FALSE.equals(item.getDeleted()))
                .orElseThrow(() -> new AppBadException(messageService.getMessage("lead.not.found", language)));
    }

    private Long requireProfileId(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        return profileId;
    }
}

package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.clent.CompanyClient;
import org.example.clent.ProductClient;
import org.example.dto.*;
import org.example.dto.internal.CompanyInternalSummaryResponse;
import org.example.dto.internal.ProductInternalSummaryResponse;
import org.example.entity.CartItem;
import org.example.entity.Lead;
import org.example.entity.LeadItem;
import org.example.enums.AppLanguage;
import org.example.enums.LeadSource;
import org.example.exp.AppBadException;
import org.example.mapper.LeadMapper;
import org.example.repository.CartItemRepository;
import org.example.repository.LeadItemRepository;
import org.example.repository.LeadRepository;
import org.example.service.CartService;
import org.example.service.ResourceBundleService;
import org.example.utils.SpringSecurityUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final LeadRepository leadRepository;
    private final LeadItemRepository leadItemRepository;
    private final ProductClient productClient;
    private final CompanyClient companyClient;
    private final LeadMapper leadMapper;
    private final ResourceBundleService messageService;

    @Override
    public CartResponse getCart(AppLanguage language) {
        List<CartItem> items = getBuyerCartItems(language);
        return toCartResponse(items);
    }

    @Override
    public CartCountResponse getCartCount(AppLanguage language) {
        long totalQuantity = getBuyerCartItems(language).stream()
                .map(CartItem::getQuantity)
                .filter(quantity -> quantity != null)
                .mapToLong(Integer::longValue)
                .sum();
        return new CartCountResponse(totalQuantity);
    }

    @Override
    public CartItemResponse addItem(CartItemCreateRequest request, AppLanguage language) {
        Long buyerId = requireProfileId(language);
        ProductInternalSummaryResponse product = productClient.getById(request.getProductId());
        CompanyInternalSummaryResponse company = companyClient.getSummary(product.getCompanyId());

        CartItem cartItem = cartItemRepository.findByBuyerIdAndProductIdAndDeletedFalse(buyerId, request.getProductId())
                .orElseGet(CartItem::new);

        cartItem.setBuyerId(buyerId);
        applyProductSnapshot(cartItem, product, company);
        cartItem.setQuantity(resolveQuantity(cartItem.getQuantity(), request.getQuantity()));
        return toCartItemResponse(cartItemRepository.save(cartItem));
    }

    @Override
    public CartItemResponse updateItem(Long itemId, CartItemUpdateRequest request, AppLanguage language) {
        CartItem cartItem = findOwnedCartItem(itemId, language);
        cartItem.setQuantity(request.getQuantity());
        return toCartItemResponse(cartItemRepository.save(cartItem));
    }

    @Override
    public Boolean deleteItem(Long itemId, AppLanguage language) {
        CartItem cartItem = findOwnedCartItem(itemId, language);
        cartItem.setDeleted(true);
        cartItemRepository.save(cartItem);
        return true;
    }

    @Override
    public Boolean clear(AppLanguage language) {
        List<CartItem> items = getBuyerCartItems(language);
        items.forEach(item -> item.setDeleted(true));
        cartItemRepository.saveAll(items);
        return true;
    }

    @Override
    public CartCheckoutResponse checkout(CartCheckoutRequest request, AppLanguage language) {
        Long buyerId = requireProfileId(language);
        List<CartItem> items = getBuyerCartItems(language);
        if (items.isEmpty()) {
            throw new AppBadException(messageService.getMessage("cart.empty", language));
        }

        Map<String, List<CartItem>> grouped = new LinkedHashMap<>();
        for (CartItem item : items) {
            String key = item.getSellerId() + ":" + item.getCompanyId();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }

        List<LeadResponse> leads = new ArrayList<>();
        for (List<CartItem> groupItems : grouped.values()) {
            CartItem firstItem = groupItems.get(0);

            Lead lead = new Lead();
            lead.setBuyerId(buyerId);
            lead.setSellerId(firstItem.getSellerId());
            lead.setCompanyId(firstItem.getCompanyId());
            lead.setSource(LeadSource.CART);
            lead.setContactName(request.getContactName());
            lead.setContactPhone(request.getContactPhone());
            lead.setContactEmail(request.getContactEmail());
            lead.setDeliveryAddress(request.getDeliveryAddress());
            lead.setNeededDate(request.getNeededDate());
            lead.setComment(request.getComment());

            Lead savedLead = leadRepository.save(lead);

            for (CartItem groupItem : groupItems) {
                LeadItem leadItem = new LeadItem();
                leadItem.setLeadId(savedLead.getId());
                leadItem.setProductId(groupItem.getProductId());
                leadItem.setProductNameSnapshot(groupItem.getProductNameSnapshot());
                leadItem.setPriceSnapshot(groupItem.getPriceSnapshot());
                leadItem.setQuantity(groupItem.getQuantity());
                leadItemRepository.save(leadItem);

                groupItem.setDeleted(true);
            }

            leads.add(leadMapper.toResponse(savedLead));
        }

        cartItemRepository.saveAll(items);

        return CartCheckoutResponse.builder()
                .createdCount(leads.size())
                .leads(leads)
                .build();
    }

    private List<CartItem> getBuyerCartItems(AppLanguage language) {
        return cartItemRepository.findAllByBuyerIdAndDeletedFalseOrderByCreatedDateDesc(requireProfileId(language));
    }

    private CartItem findOwnedCartItem(Long itemId, AppLanguage language) {
        Long buyerId = requireProfileId(language);
        CartItem cartItem = cartItemRepository.findByIdAndDeletedFalse(itemId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("cart.item.not.found", language)));

        if (!buyerId.equals(cartItem.getBuyerId())) {
            throw new AppBadException(messageService.getMessage("lead.forbidden", language));
        }
        return cartItem;
    }

    private CartResponse toCartResponse(List<CartItem> items) {
        long totalQuantity = items.stream()
                .map(CartItem::getQuantity)
                .filter(quantity -> quantity != null)
                .mapToLong(Integer::longValue)
                .sum();

        return CartResponse.builder()
                .itemCount((long) items.size())
                .totalQuantity(totalQuantity)
                .items(items.stream().map(this::toCartItemResponse).toList())
                .build();
    }

    private CartItemResponse toCartItemResponse(CartItem item) {
        return CartItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .sellerId(item.getSellerId())
                .companyId(item.getCompanyId())
                .productName(item.getProductNameSnapshot())
                .productSlug(item.getProductSlugSnapshot())
                .primaryImage(item.getPrimaryImageSnapshot())
                .price(item.getPriceSnapshot())
                .currency(item.getCurrencySnapshot())
                .companyName(item.getCompanyNameSnapshot())
                .companySlug(item.getCompanySlugSnapshot())
                .companyLogoPath(item.getCompanyLogoPathSnapshot())
                .quantity(item.getQuantity())
                .build();
    }

    private void applyProductSnapshot(CartItem cartItem,
                                      ProductInternalSummaryResponse product,
                                      CompanyInternalSummaryResponse company) {
        cartItem.setProductId(product.getId());
        cartItem.setSellerId(product.getSellerId());
        cartItem.setCompanyId(product.getCompanyId());
        cartItem.setProductNameSnapshot(product.getName());
        cartItem.setProductSlugSnapshot(product.getSlug());
        cartItem.setPrimaryImageSnapshot(product.getPrimaryImage());
        cartItem.setPriceSnapshot(product.getPrice());
        cartItem.setCurrencySnapshot(product.getCurrency());
        cartItem.setCompanyNameSnapshot(company == null ? null : company.getName());
        cartItem.setCompanySlugSnapshot(company == null ? null : company.getSlug());
        cartItem.setCompanyLogoPathSnapshot(company == null ? null : company.getLogoPath());
    }

    private int resolveQuantity(Integer currentQuantity, Integer requestQuantity) {
        int baseQuantity = currentQuantity == null ? 0 : currentQuantity;
        return baseQuantity + requestQuantity;
    }

    private Long requireProfileId(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        return profileId;
    }
}

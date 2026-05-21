package org.example.service;

import org.example.dto.*;
import org.example.enums.AppLanguage;

public interface CartService {
    CartResponse getCart(AppLanguage language);

    CartCountResponse getCartCount(AppLanguage language);

    CartItemResponse addItem(CartItemCreateRequest request, AppLanguage language);

    CartItemResponse updateItem(Long itemId, CartItemUpdateRequest request, AppLanguage language);

    Boolean deleteItem(Long itemId, AppLanguage language);

    Boolean clear(AppLanguage language);

    CartCheckoutResponse checkout(CartCheckoutRequest request, AppLanguage language);
}

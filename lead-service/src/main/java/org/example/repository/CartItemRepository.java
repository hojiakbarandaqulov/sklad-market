package org.example.repository;

import org.example.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findAllByBuyerIdAndDeletedFalseOrderByCreatedDateDesc(Long buyerId);

    Optional<CartItem> findByIdAndDeletedFalse(Long id);

    Optional<CartItem> findByBuyerIdAndProductIdAndDeletedFalse(Long buyerId, Long productId);

    List<CartItem> findAllByBuyerIdAndIdInAndDeletedFalse(
            Long buyerId, Collection<Long> ids);
}

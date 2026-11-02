package org.example.repository;

import org.example.entity.Order;
import org.example.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByBuyerIdAndIdempotencyKey(Long buyerId, String idempotencyKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id=:id")
    Optional<Order> lock(@Param("id") Long id);
    @Query("select o.id from Order o where o.status in :statuses order by o.updatedAt, o.id")
    List<Long> pending(@Param("statuses") Collection<OrderStatus> statuses, Pageable page);
}

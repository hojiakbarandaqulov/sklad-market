package org.example.repository;

import jakarta.persistence.LockModeType;
import org.example.entity.Order;
import org.example.enums.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByLeadId(Long leadId);

    Optional<Order> findByBuyerIdAndIdempotencyKey(Long buyerId, String idempotencyKey);

    // Shu buyurtma ustidagi boshqa o'zgartirishlar joriy tranzaksiya tugashini kutadi.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select orderEntity from Order orderEntity where orderEntity.id = :id")
    Optional<Order> lock(@Param("id") Long id);

    @Query("""
            select orderEntity.id
            from Order orderEntity
            where orderEntity.status in :statuses
            order by orderEntity.updatedAt, orderEntity.id
            """)
    List<Long> pending(@Param("statuses") Collection<OrderStatus> statuses, Pageable page);
}

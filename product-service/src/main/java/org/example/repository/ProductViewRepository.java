package org.example.repository;

import org.example.entity.ProductView;
import org.example.enums.ProductModerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductViewRepository extends JpaRepository<ProductView, Long> {
    @Query("""
            select function('date_trunc', 'month', pv.viewedAt), count(pv.id)
            from ProductView pv, Product p
            where p.id = pv.productId
              and p.companyId in :companyIds
              and p.moderationStatus = :status
              and p.isActive = true
              and p.deletedAt is null
              and pv.viewedAt >= :from
            group by function('date_trunc', 'month', pv.viewedAt)
            order by function('date_trunc', 'month', pv.viewedAt)
            """)
    List<Object[]> countMonthlyViews(@Param("companyIds") List<Long> companyIds,
                                     @Param("status") ProductModerationStatus status,
                                     @Param("from") LocalDateTime from);
}

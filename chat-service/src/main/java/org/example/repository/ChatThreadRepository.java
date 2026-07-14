package org.example.repository;

import org.example.entity.ChatThread;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatThreadRepository extends JpaRepository<ChatThread, Long> {

    @Query("""
            select t from ChatThread t
            where t.deleted = false
              and t.buyerId = :buyerId
              and t.sellerCompanyId = :sellerCompanyId
              and ((:productId is null and t.productId is null) or t.productId = :productId)
            """)
    Optional<ChatThread> findUnique(@Param("buyerId") Long buyerId,
                                    @Param("sellerCompanyId") Long sellerCompanyId,
                                    @Param("productId") Long productId);

    List<ChatThread> findByBuyerIdAndBuyerHiddenFalseAndDeletedFalse(Long buyerId, Sort sort);

    List<ChatThread> findBySellerCompanyIdInAndSellerHiddenFalseAndDeletedFalse(List<Long> sellerCompanyIds, Sort sort);

    Optional<ChatThread> findByIdAndDeletedFalse(Long id);

    long countBySellerCompanyIdInAndDeletedFalse(List<Long> sellerCompanyIds);

    @Query("""
            select function('date_trunc', 'month', t.createdDate), count(t.id)
            from ChatThread t
            where t.deleted = false
              and t.sellerCompanyId in :companyIds
              and t.createdDate >= :from
            group by function('date_trunc', 'month', t.createdDate)
            order by function('date_trunc', 'month', t.createdDate)
            """)
    List<Object[]> countMonthlyByCompanyIds(@Param("companyIds") List<Long> companyIds,
                                            @Param("from") LocalDateTime from);
}

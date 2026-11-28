package org.example.repository;

import org.example.entity.ChatThread;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatThreadRepository extends JpaRepository<ChatThread, Long> {
    @Query("""
            select t from ChatThread t
            where t.deleted = false and t.buyerHidden = false
              and t.buyerId = :buyerId and t.applicationId is not null
              and exists (select m.id from ChatMessage m where m.thread = t and m.deleted = false)
            """)
    List<ChatThread> findBuyerJobThreadsWithMessages(@Param("buyerId") Long buyerId, Sort sort);

    @Query("""
            select t from ChatThread t
            where t.deleted = false and t.sellerHidden = false
              and t.sellerCompanyId in :companyIds and t.applicationId is not null
              and exists (select m.id from ChatMessage m where m.thread = t and m.deleted = false)
            """)
    List<ChatThread> findSellerJobThreadsWithMessages(@Param("companyIds") List<Long> companyIds, Sort sort);
    Optional<ChatThread> findByApplicationId(Long applicationId);

    @Modifying
    @Query(value = """
            insert into chat_threads (application_id,vacancy_id,buyer_id,seller_company_id,
                buyer_hidden,seller_hidden,deleted,created_date,modified_date)
            values (:applicationId,:vacancyId,:candidateId,:companyId,false,false,false,current_timestamp,current_timestamp)
            on conflict (application_id) do nothing
            """, nativeQuery = true)
    void insertJobThread(@Param("applicationId") Long applicationId, @Param("vacancyId") Long vacancyId,
                        @Param("candidateId") Long candidateId, @Param("companyId") Long companyId);

    @Modifying
    @Query(value = """
            insert into chat_threads (buyer_id, seller_company_id, product_id,
                buyer_hidden, seller_hidden, deleted, created_date, modified_date)
            values (:buyerId, :companyId, :productId, false, false, false, current_timestamp, current_timestamp)
            on conflict (buyer_id, seller_company_id)
                where application_id is null and deleted = false
            do nothing
            """, nativeQuery = true)
    int insertMarketThread(@Param("buyerId") Long buyerId, @Param("companyId") Long companyId,
                           @Param("productId") Long productId);

    @Query("""
            select t from ChatThread t
            where t.deleted = false
              and t.applicationId is null
              and t.buyerId = :buyerId
              and t.sellerCompanyId = :sellerCompanyId
            """)
    Optional<ChatThread> findUnique(@Param("buyerId") Long buyerId,
                                    @Param("sellerCompanyId") Long sellerCompanyId);

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

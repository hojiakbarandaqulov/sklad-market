package org.example.repository;

import org.example.dto.customer.CustomerSummary;
import org.example.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByIdAndDeletedFalse(Long id);

    String FILTERS = """
            from Customer c
            where c.company.id = :companyId and c.deleted = false
                and (:search = '' or locate(:search, lower(c.name)) > 0
                    or locate(:search, lower(c.contractNumber)) > 0 or locate(:search, c.phone) > 0)
                and (:hasFile is null or (:hasFile = true and c.contractAttachId is not null)
                    or (:hasFile = false and c.contractAttachId is null))
            """;
    String SUMMARY = "select new org.example.dto.customer.CustomerSummary(count(c), count(c.contractAttachId), avg(c.paymentTermDays)) ";
    String DEBTOR_IDS = " and c.id in :customerIds";

    @Query("select c " + FILTERS)
    Page<Customer> search(@Param("companyId") Long companyId, @Param("search") String search,
                          @Param("hasFile") Boolean hasFile, Pageable pageable);

    @Query(SUMMARY + FILTERS)
    CustomerSummary summarize(@Param("companyId") Long companyId, @Param("search") String search,
                              @Param("hasFile") Boolean hasFile);

    @Query("select c " + FILTERS + DEBTOR_IDS)
    Page<Customer> searchDebtors(@Param("companyId") Long companyId, @Param("search") String search,
                                 @Param("hasFile") Boolean hasFile, @Param("customerIds") Collection<Long> customerIds,
                                 Pageable pageable);

    @Query(SUMMARY + FILTERS + DEBTOR_IDS)
    CustomerSummary summarizeDebtors(@Param("companyId") Long companyId, @Param("search") String search,
                                     @Param("hasFile") Boolean hasFile, @Param("customerIds") Collection<Long> customerIds);

    @Query("select c.id " + FILTERS + DEBTOR_IDS)
    List<Long> findMatchingDebtorIds(@Param("companyId") Long companyId, @Param("search") String search,
                                   @Param("hasFile") Boolean hasFile, @Param("customerIds") Collection<Long> customerIds);
}

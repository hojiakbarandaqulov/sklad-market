package org.example.repository;

import org.example.dto.customer.CustomerSummary;
import org.example.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByIdAndDeletedFalse(Long id);

    @Query("""
            select c from Customer c
            where c.company.id = :companyId and c.deleted = false
                and (:search = '' or locate(:search, lower(c.name)) > 0
                    or locate(:search, lower(c.contractNumber)) > 0 or locate(:search, c.phone) > 0)
                and (:hasFile is null or (:hasFile = true and c.contractAttachId is not null)
                    or (:hasFile = false and c.contractAttachId is null))
            """)
    Page<Customer> search(@Param("companyId") Long companyId, @Param("search") String search,
                          @Param("hasFile") Boolean hasFile, Pageable pageable);

    @Query("""
            select new org.example.dto.customer.CustomerSummary(count(c), count(c.contractAttachId), avg(c.paymentTermDays))
            from Customer c
            where c.company.id = :companyId and c.deleted = false
                and (:search = '' or locate(:search, lower(c.name)) > 0
                    or locate(:search, lower(c.contractNumber)) > 0 or locate(:search, c.phone) > 0)
                and (:hasFile is null or (:hasFile = true and c.contractAttachId is not null)
                    or (:hasFile = false and c.contractAttachId is null))
            """)
    CustomerSummary summarize(@Param("companyId") Long companyId, @Param("search") String search,
                              @Param("hasFile") Boolean hasFile);
}

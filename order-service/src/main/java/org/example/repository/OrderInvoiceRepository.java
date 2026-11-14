package org.example.repository;

import org.example.entity.OrderInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderInvoiceRepository extends JpaRepository<OrderInvoice,Long> {
    @Query("""
        select invoice
        from OrderInvoice invoice
        where invoice.status = 'ISSUED'
          and invoice.orderId in (
              select o.id
              from Order o
              where o.companyId = :companyId
          )
        """)
    List<OrderInvoice> findIssuedByCompanyId(@Param("companyId") Long companyId);
}

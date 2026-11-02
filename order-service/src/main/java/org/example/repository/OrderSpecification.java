package org.example.repository;

import org.example.dto.order.OrderFilter;
import org.example.entity.Order;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class OrderSpecification {

    private static final ZoneId TASHKENT_ZONE = ZoneId.of("Asia/Tashkent");

    private OrderSpecification() {
    }

    public static Specification<Order> forBuyer(Long buyerId, OrderFilter filter) {
        Specification<Order> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("buyerId"), buyerId);

        if (filter.getCompanyId() != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("companyId"), filter.getCompanyId()));
        }

        return addCommonFilters(specification, filter);
    }

    public static Specification<Order> forSeller(OrderFilter filter) {
        Specification<Order> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("companyId"), filter.getCompanyId());

        return addCommonFilters(specification, filter);
    }

    private static Specification<Order> addCommonFilters(
            Specification<Order> specification,
            OrderFilter filter
    ) {
        if (filter.getStatus() != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), filter.getStatus()));
        }

        if (filter.getDateFrom() != null) {
            Instant startOfDay = filter.getDateFrom().atStartOfDay(TASHKENT_ZONE).toInstant();

            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startOfDay));
        }

        // dateTo kuni ham to'liq kirishi uchun keyingi kunning boshigacha qidiramiz.
        if (filter.getDateTo() != null && !filter.getDateTo().equals(LocalDate.MAX)) {
            Instant startOfNextDay = filter.getDateTo().plusDays(1).atStartOfDay(TASHKENT_ZONE).toInstant();

            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThan(root.get("createdAt"), startOfNextDay));
        }

        return specification;
    }
}

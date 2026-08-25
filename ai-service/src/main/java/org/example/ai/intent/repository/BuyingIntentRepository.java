package org.example.ai.intent.repository;

import jakarta.persistence.LockModeType;
import org.example.ai.intent.entity.BuyingIntent;
import org.example.ai.intent.entity.BuyingIntentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BuyingIntentRepository extends JpaRepository<BuyingIntent, UUID> {

    Optional<BuyingIntent> findByIdAndOwnerSub(UUID id, String ownerSub);

    Page<BuyingIntent> findAllByOwnerSubOrderByCreatedAtDesc(String ownerSub, Pageable pageable);

    Page<BuyingIntent> findAllByOwnerSubAndStatusOrderByCreatedAtDesc(
            String ownerSub, BuyingIntentStatus status, Pageable pageable);

    long countByOwnerSubAndStatusInAndExpiresAtAfter(
            String ownerSub, Collection<BuyingIntentStatus> statuses, Instant now);

    /** Transaction-scoped Postgres lock serializes quota checks for the same owner without storing a guard row. */
    @Query(value = """
            with owner_lock as (
                select pg_advisory_xact_lock(hashtextextended(cast(:ownerSub as text), 0))
            )
            select hashtextextended(cast(:ownerSub as text), 0) from owner_lock
            """, nativeQuery = true)
    long acquireOwnerQuotaLock(@Param("ownerSub") String ownerSub);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from BuyingIntent i where i.id = :id and i.ownerSub = :ownerSub")
    Optional<BuyingIntent> findOwnedForUpdate(@Param("id") UUID id, @Param("ownerSub") String ownerSub);

    /**
     * Dispatches nullable filters to type-stable JPQL queries. PostgreSQL can infer an untyped null
     * in {@code lower(:filter)} as bytea, so a single "filter is null or ..." query is not safe.
     */
    default Page<BuyingIntent> searchPublished(
            @Param("status") BuyingIntentStatus status,
            @Param("now") Instant now,
            @Param("category") String category,
            @Param("region") String region,
            Pageable pageable) {
        if (category == null && region == null) {
            return searchPublishedUnfiltered(status, now, pageable);
        }
        if (category == null) {
            return searchPublishedByRegion(status, now, region, pageable);
        }
        if (region == null) {
            return searchPublishedByCategory(status, now, category, pageable);
        }
        return searchPublishedByCategoryAndRegion(status, now, category, region, pageable);
    }

    @Query("select i from BuyingIntent i where i.status = :status and i.expiresAt > :now")
    Page<BuyingIntent> searchPublishedUnfiltered(
            @Param("status") BuyingIntentStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    @Query("""
            select i from BuyingIntent i
             where i.status = :status and i.expiresAt > :now
               and lower(i.category) = lower(:category)
            """)
    Page<BuyingIntent> searchPublishedByCategory(
            @Param("status") BuyingIntentStatus status,
            @Param("now") Instant now,
            @Param("category") String category,
            Pageable pageable);

    @Query("""
            select i from BuyingIntent i
             where i.status = :status and i.expiresAt > :now
               and lower(i.region) = lower(:region)
            """)
    Page<BuyingIntent> searchPublishedByRegion(
            @Param("status") BuyingIntentStatus status,
            @Param("now") Instant now,
            @Param("region") String region,
            Pageable pageable);

    @Query("""
            select i from BuyingIntent i
             where i.status = :status and i.expiresAt > :now
               and lower(i.category) = lower(:category)
               and lower(i.region) = lower(:region)
            """)
    Page<BuyingIntent> searchPublishedByCategoryAndRegion(
            @Param("status") BuyingIntentStatus status,
            @Param("now") Instant now,
            @Param("category") String category,
            @Param("region") String region,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BuyingIntent i
               set i.status = org.example.ai.intent.entity.BuyingIntentStatus.EXPIRED,
                   i.updatedAt = :now,
                   i.version = i.version + 1
             where i.ownerSub = :ownerSub
               and i.status in :activeStatuses
               and i.expiresAt <= :now
            """)
    int expireDueForOwner(
            @Param("ownerSub") String ownerSub,
            @Param("activeStatuses") Collection<BuyingIntentStatus> activeStatuses,
            @Param("now") Instant now);

    @Query("""
            select i.id from BuyingIntent i
             where i.status in :activeStatuses
               and i.expiresAt <= :now
             order by i.expiresAt asc, i.id asc
            """)
    List<UUID> findDueIds(
            @Param("activeStatuses") Collection<BuyingIntentStatus> activeStatuses,
            @Param("now") Instant now,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BuyingIntent i
               set i.status = org.example.ai.intent.entity.BuyingIntentStatus.EXPIRED,
                   i.updatedAt = :now,
                   i.version = i.version + 1
             where i.id in :ids
               and i.status in :activeStatuses
               and i.expiresAt <= :now
            """)
    int expireDueIds(
            @Param("ids") Collection<UUID> ids,
            @Param("activeStatuses") Collection<BuyingIntentStatus> activeStatuses,
            @Param("now") Instant now);

    @Query("""
            select i.id from BuyingIntent i
             where i.status in :terminalStatuses
               and i.updatedAt < :cutoff
             order by i.updatedAt asc, i.id asc
            """)
    List<UUID> findRetentionCandidates(
            @Param("terminalStatuses") Collection<BuyingIntentStatus> terminalStatuses,
            @Param("cutoff") Instant cutoff,
            Pageable pageable);
}

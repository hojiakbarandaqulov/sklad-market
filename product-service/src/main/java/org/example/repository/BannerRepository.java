package org.example.repository;

import org.example.entity.Banners;
import org.example.enums.PlacementCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BannerRepository extends JpaRepository<Banners, Long> {

    List<Banners> findByPlacementCodeAndIsActiveTrueAndStartsAtBeforeAndEndsAtAfter(PlacementCode placementCode, LocalDateTime startsAt, LocalDateTime endsAt);

    List<Banners> findByIsActiveTrueAndStartsAtBeforeAndEndsAtAfter(LocalDateTime now, LocalDateTime now1);
}

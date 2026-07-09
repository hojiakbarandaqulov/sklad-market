package org.example.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;
import org.example.enums.PlacementCode;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class Banners extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private PlacementCode placementCode;
    private String imageKey;
    private String attachId;
    private String targetUrl;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Integer clickCount;

}

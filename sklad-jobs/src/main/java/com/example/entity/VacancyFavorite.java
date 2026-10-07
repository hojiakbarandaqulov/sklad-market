package com.example.entity;

import com.example.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "vacancy_favorites", uniqueConstraints = @UniqueConstraint(
        name = "uk_vacancy_favorite_user_vacancy", columnNames = {"user_id", "vacancy_id"}),
        indexes = @Index(name = "idx_vacancy_favorite_user_active", columnList = "user_id,is_active"))
public class VacancyFavorite extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "vacancy_id", nullable = false)
    private Long vacancyId;
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
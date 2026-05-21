package org.example.repository;

import org.example.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserIdAndCompanyIdAndDeletedFalse(Long profileId, Long companyId);


    Long countByUserIdAndDeletedFalse(Long profileId);
}

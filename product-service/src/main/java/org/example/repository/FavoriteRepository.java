package org.example.repository;

import org.example.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    Page<Favorite> findByUserIdAndIsActiveTrue(Long userId, Pageable pageable);



    Optional<Favorite> findByUserIdAndProductIdAndIsActiveTrue(Long userId, Long productId);

    long countByProductIdAndIsActiveTrue(Long productId);

    long countByUserIdAndIsActiveTrue(Long userId);

    Optional<Favorite> findByUserIdAndProductId(Long userId, Long productId);
}

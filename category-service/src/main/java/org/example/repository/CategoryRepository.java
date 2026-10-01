package org.example.repository;

import org.example.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsBySlug(String slug);

    boolean existsBySortOrder(Integer sortOrder);

    Category findBySlugAndIsActiveTrue(String slug);


    Category findByIdAndIsActiveTrue(Long categoryId);

    List<Category> findAllByIsActiveTrueOrderBySortOrderAsc();


    Page<Category> findAllByIsActiveTrue(Pageable pageable);

    @Query("""
            SELECT c
            FROM Category c
            WHERE c.isActive = true
              AND (
                   LOWER(COALESCE(c.nameUz, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(c.nameRu, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(c.nameEn, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            """)
    Page<Category> searchByName(@Param("query") String query, Pageable pageable);
}

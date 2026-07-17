package org.example.repository;

import org.example.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsBySlug(String slug);

    Category findBySlugAndIsActiveTrue(String slug);


    Category findByIdAndIsActiveTrue(Long categoryId);

    List<Category> findAllByIsActiveTrueOrderBySortOrderAsc();
}

package org.example.repository;

import jakarta.transaction.Transactional;
import org.example.entity.Attach;
import org.example.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long>,
        JpaSpecificationExecutor<UserProfile> {

    boolean existsByUserId(Long userId);

    Optional<UserProfile> findByUserId(Long userId);

    Optional<UserProfile> findByUsernameAndDeletedFalse(String username);

    Optional<UserProfile> findByIdAndDeletedFalse(Long profileId);

    UserProfile findByUserIdAndDeletedFalse(Long profileId);

    @Transactional(rollbackOn = Exception.class)
    @Modifying
    @Query("update UserProfile p set p.photo = :photo where p.id = ?1")
    void updatePhoto(Long id, @Param("photo") Attach photo);

    long countByStatusAndDeletedFalse(org.example.enums.GeneralStatus status);

}

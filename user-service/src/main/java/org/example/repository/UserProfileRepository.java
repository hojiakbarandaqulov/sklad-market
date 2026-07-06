package org.example.repository;

import jakarta.transaction.Transactional;
import org.example.entity.UsersProfile;
import org.example.enums.GeneralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UsersProfile, Long>,
        JpaSpecificationExecutor<UsersProfile> {

    boolean existsByUserId(Long userId);

    Optional<UsersProfile> findByUserId(Long userId);

    Optional<UsersProfile> findByUsernameAndDeletedFalse(String username);

    Optional<UsersProfile> findByIdAndDeletedFalse(Long profileId);

    UsersProfile findByUserIdAndDeletedFalse(Long profileId);

    @Transactional(rollbackOn = Exception.class)
    @Modifying
    @Query("update UsersProfile p set p.photoId = :photo where p.id = ?1")
    void updatePhoto(Long id, @Param("photoId") String photoId);

    long countByStatusAndDeletedFalse(GeneralStatus status);

}

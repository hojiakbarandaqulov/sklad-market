package org.example.repository;

import jakarta.transaction.Transactional;
import org.example.entity.Users;
import org.example.enums.GeneralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


public interface UserRepository extends JpaRepository<Users,Long> {

    Optional<Users> findByUsernameAndDeletedFalse(String username);

    @Modifying
    @Transactional
    @Query("update Users  set status=?2 where id=?1")
    void changeStatus(Long id, GeneralStatus status);

    @Modifying
    @Transactional
    @Query("update Users set password=?2 where id =?1")
    void updatePassword(Long id, String password);


    Optional<Users> findByKeycloakId(String keycloakId);

    @Modifying
    @Query(value = """
        INSERT INTO users (
            first_name, last_name, username, password,
            keycloak_id, role, status, deleted,
            failed_login_count, created_date
        )
        VALUES (
            :firstName, :lastName, :email, NULL,
            :keycloakId, 'BUYER', 'ACTIVE', FALSE,
            0, CURRENT_TIMESTAMP
        )
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    void insertGoogleUser(
            @Param("keycloakId") String keycloakId,
            @Param("email") String email,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName);
}

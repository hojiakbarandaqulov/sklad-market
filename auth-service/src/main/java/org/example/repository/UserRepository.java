package org.example.repository;

import jakarta.transaction.Transactional;
import org.example.entity.Users;
import org.example.enums.GeneralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
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

    Optional<Users> findByGoogleSubject(String googleSubject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Users u where u.id = :id")
    Optional<Users> findForGoogleProvisioning(@Param("id") Long id);

    @Modifying
    @Query(value = """
        INSERT INTO users (
            google_subject, first_name, last_name, username,
            role, status, deleted, failed_login_count, created_date
        )
        SELECT :subject, :firstName, :lastName, :email,
               'BUYER', 'ACTIVE', FALSE, 0, CURRENT_TIMESTAMP
        WHERE NOT EXISTS (SELECT 1 FROM users WHERE LOWER(username) = LOWER(:email))
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    void insertDirectGoogleUser(@Param("subject") String subject,
                                @Param("email") String email,
                                @Param("firstName") String firstName,
                                @Param("lastName") String lastName);


}

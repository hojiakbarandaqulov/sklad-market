package org.example.repository;

import jakarta.transaction.Transactional;
import org.example.entity.Users;
import org.example.enums.GeneralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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


}

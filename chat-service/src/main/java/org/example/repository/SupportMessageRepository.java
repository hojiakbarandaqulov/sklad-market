package org.example.repository;

import org.example.entity.SupportMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    Page<SupportMessage> findByThreadIdAndDeletedFalse(Long threadId, Pageable pageable);

    Page<SupportMessage> findByThreadIdAndIdLessThanAndDeletedFalse(Long threadId, Long beforeId, Pageable pageable);

    List<SupportMessage> findByThreadIdAndIdInAndDeletedFalse(Long threadId, List<Long> ids);
}

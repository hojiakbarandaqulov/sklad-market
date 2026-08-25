package org.example.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActionDraftRepositoryLockTest {

    @Test
    void transitionLookupUsesPessimisticWriteLock() throws Exception {
        Lock lock = ActionDraftRepository.class
                .getMethod("findLockedByIdAndUserSub", UUID.class, String.class)
                .getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}

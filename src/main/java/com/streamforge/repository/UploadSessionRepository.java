package com.streamforge.repository;

import com.streamforge.model.UploadSession;
import com.streamforge.model.enums.UploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {
    Optional<UploadSession> findBySessionIdAndStatus(UUID sessionId, UploadStatus status);
    List<UploadSession> findByStatusAndExpiresAtBefore(UploadStatus status, Instant expiresAt);
}

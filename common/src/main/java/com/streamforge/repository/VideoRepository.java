package com.streamforge.repository;

import com.streamforge.model.Video;
import com.streamforge.model.enums.VideoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VideoRepository extends JpaRepository<Video, UUID> {
    List<Video> findByStatusOrderByCreatedAtDesc(VideoStatus status);
    Page<Video> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

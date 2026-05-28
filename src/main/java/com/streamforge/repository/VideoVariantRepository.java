package com.streamforge.repository;

import com.streamforge.model.VideoVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VideoVariantRepository extends JpaRepository<VideoVariant, UUID> {
    List<VideoVariant> findByVideoId(UUID videoId);
}

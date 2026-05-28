package com.streamforge.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "video_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "variant_id")
    private UUID variantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(nullable = false, length = 20)
    private String resolution;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @Column(name = "bitrate_kbps", nullable = false)
    private Integer bitrateKbps;

    @Column(name = "manifest_path", nullable = false, length = 1000)
    private String manifestPath;

    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Column(name = "file_size_bytes", nullable = false)
    @Builder.Default
    private Long fileSizeBytes = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

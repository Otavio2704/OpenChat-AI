package otavio.kyronai.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "github_repositories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String repositoryName;

    @Column(nullable = false, length = 100)
    private String owner;

    @Column(length = 500)
    private String description;

    @Column(length = 1000)
    private String url;

    @Column(length = 50)
    private String branch;

    @Column(name = "is_public")
    private Boolean isPublic;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_status")
    private IndexStatus indexStatus;

    @Column(name = "indexed_files_count")
    private Integer indexedFilesCount;

    @Column(name = "last_indexed_at")
    private LocalDateTime lastIndexedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner Enum
    // ═══════════════════════════════════════════════════════════════════════════

    public enum IndexStatus {
        PENDING,
        INDEXING,
        READY,
        ERROR
    }
}

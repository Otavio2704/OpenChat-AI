package otavio.kyronai.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sessão de geração de código vinculada a uma conversa.
 * Representa uma série de arquivos gerados pela IA durante uma conversa de chat.
 */
@Entity
@Table(name = "code_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID conversationId;

    @Column(length = 255)
    private String title;

    @Column(name = "primary_language", length = 50)
    private String primaryLanguage;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "summary", columnDefinition = "LONGTEXT")
    private String summary;

    @OneToMany(mappedBy = "codeSession", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("created_at ASC")
    @Builder.Default
    private List<GeneratedFile> files = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addFile(GeneratedFile file) {
        files.add(file);
        file.setCodeSession(this);
    }

    public void removeFile(GeneratedFile file) {
        files.remove(file);
        file.setCodeSession(null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Métodos auxiliares
    // ═══════════════════════════════════════════════════════════════════════════

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }
}


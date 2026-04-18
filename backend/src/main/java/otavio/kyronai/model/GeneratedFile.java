package otavio.kyronai.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "generated_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 255)
    private String filename;  // alias para fileName

    @Column(length = 500)
    private String filePath;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "previous_content", columnDefinition = "LONGTEXT")
    private String previousContent;

    @Column(length = 50)
    private String fileType;

    @Column(length = 50)
    private String language;

    @Column(length = 50)
    private String extension;

    @Column(name = "is_modified")
    private Boolean isModified;

    @Column(name = "is_new_file")
    private Boolean newFile;

    @Column(name = "version")
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_session_id")
    private CodeSession codeSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_action_id")
    private AgentAction agentAction;

    // Alias para session (compatibilidade)
    @Transient
    public CodeSession getSession() {
        return codeSession;
    }

    @Transient
    public void setSession(CodeSession session) {
        this.codeSession = session;
    }

    // Alias para getFilename
    public String getFilename() {
        return filename != null ? filename : fileName;
    }

    public void setFilename(String filename) {
        this.filename = filename;
        if (fileName == null) {
            this.fileName = filename;
        }
    }
}


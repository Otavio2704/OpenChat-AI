package otavio.kyronai.repository;

import otavio.kyronai.model.GeneratedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GeneratedFileRepository extends JpaRepository<GeneratedFile, UUID> {

    List<GeneratedFile> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<GeneratedFile> findByAgentActionIdOrderByCreatedAtDesc(UUID agentActionId);

    List<GeneratedFile> findByFileTypeOrderByCreatedAtDesc(String fileType);

    List<GeneratedFile> findByCodeSessionIdOrderByCreatedAtDesc(UUID codeSessionId);

    Optional<GeneratedFile> findByCodeSessionIdAndFilePath(UUID codeSessionId, String filePath);

    // Compatibilidade com sessionId (alias para codeSessionId)
    default Optional<GeneratedFile> findBySessionIdAndFilePath(UUID sessionId, String filePath) {
        return findByCodeSessionIdAndFilePath(sessionId, filePath);
    }

    @Query("SELECT g FROM GeneratedFile g WHERE g.project.id = :projectId AND g.isModified = true ORDER BY g.updatedAt DESC")
    List<GeneratedFile> findModifiedFilesByProjectId(UUID projectId);

    @Query("SELECT COUNT(g) FROM GeneratedFile g WHERE g.project.id = :projectId")
    long countByProjectId(UUID projectId);

    @Query("SELECT COUNT(g) FROM GeneratedFile g WHERE g.codeSession.id = :codeSessionId")
    long countByCodeSessionId(UUID codeSessionId);

    boolean existsByIdAndProjectId(UUID id, UUID projectId);

    void deleteByCodeSessionId(UUID codeSessionId);

    // Compatibilidade com sessionId
    default void deleteBySessionId(UUID sessionId) {
        deleteByCodeSessionId(sessionId);
    }
}


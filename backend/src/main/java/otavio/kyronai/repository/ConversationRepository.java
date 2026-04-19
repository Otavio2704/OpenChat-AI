package otavio.kyronai.repository;

import otavio.kyronai.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findAllByOrderByUpdatedAtDesc();

    List<Conversation> findByModelNameOrderByUpdatedAtDesc(String modelName);

    boolean existsById(UUID id);

    @Query("SELECT c FROM Conversation c WHERE LOWER(c.title) LIKE LOWER(CONCAT('%', :term, '%')) ORDER BY c.updatedAt DESC")
    List<Conversation> searchByTitle(String term);

    @Query("SELECT c FROM Conversation c WHERE c.id = :conversationId AND c.projectId IS NOT NULL")
    Optional<Conversation> findCodeSessionByConversationId(UUID conversationId);

    @Query("SELECT c FROM Conversation c WHERE c.projectId = :projectId ORDER BY c.updatedAt DESC")
    List<Conversation> findCodeSessionsByProjectId(UUID projectId);

    @Query("SELECT c FROM Conversation c WHERE c.projectId IS NOT NULL ORDER BY c.updatedAt DESC")
    List<Conversation> findAllCodeSessions();

    // Conversas vinculadas a um projeto, do mais recente ao mais antigo
    List<Conversation> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

} 
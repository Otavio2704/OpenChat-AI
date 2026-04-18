package otavio.kyronai.repository;

import otavio.kyronai.model.AgentAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentActionRepository extends JpaRepository<AgentAction, UUID> {

    List<AgentAction> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    List<AgentAction> findByActionTypeOrderByCreatedAtDesc(String actionType);

    List<AgentAction> findByStatusOrderByCreatedAtDesc(String status);

    @Query("SELECT a FROM AgentAction a WHERE a.conversation.id = :conversationId AND a.status = :status ORDER BY a.createdAt DESC")
    List<AgentAction> findByConversationIdAndStatus(UUID conversationId, String status);

    boolean existsById(UUID id);
}

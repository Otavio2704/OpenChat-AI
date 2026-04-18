package otavio.kyronai.repository;

import otavio.kyronai.model.CodeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CodeSessionRepository extends JpaRepository<CodeSession, UUID> {

    Optional<CodeSession> findByConversationId(UUID conversationId);

    @Query("SELECT c FROM CodeSession c WHERE c.status = :status ORDER BY c.updatedAt DESC")
    List<CodeSession> findSessionsByStatus(String status);

    @Query("SELECT c FROM CodeSession c ORDER BY c.updatedAt DESC")
    List<CodeSession> findAllOrderByUpdatedAtDesc();
}


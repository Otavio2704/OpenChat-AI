package otavio.kyronai.repository;

import otavio.kyronai.model.GitHubRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepository, UUID> {

    Optional<GitHubRepository> findByOwnerAndRepositoryName(String owner, String repositoryName);

    List<GitHubRepository> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<GitHubRepository> findByOwnerOrderByCreatedAtDesc(String owner);

    @Query("SELECT g FROM GitHubRepository g WHERE g.project.id = :projectId AND g.isPublic = true")
    List<GitHubRepository> findPublicRepositoriesByProjectId(UUID projectId);

    boolean existsByOwnerAndRepositoryName(String owner, String repositoryName);

    boolean existsByIdAndProjectId(UUID id, UUID projectId);
}

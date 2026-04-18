package otavio.kyronai.controller;

import otavio.kyronai.model.GitHubRepositoryDTO;
import otavio.kyronai.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GitHubController {

    private final GitHubService gitHubService;

    /**
     * GET /api/github/repositories
     * Lista todos os repositórios conectados.
     */
    @GetMapping("/repositories")
    public ResponseEntity<List<GitHubRepositoryDTO>> listRepositories() {
        return ResponseEntity.ok(gitHubService.getAllRepositories());
    }

    /**
     * POST /api/github/repositories
     * Conecta um novo repositório e inicia a indexação em background.
     *
     * Body:
     * {
     *   "fullName": "owner/repo",
     *   "branch": "main",
     *   "accessToken": "ghp_...",   (opcional, para repos privados)
     *   "isPrivate": false
     * }
     */
    @PostMapping("/repositories")
    public ResponseEntity<?> connectRepository(@RequestBody Map<String, Object> body) {
        String fullName    = (String) body.get("fullName");
        String branch      = (String) body.getOrDefault("branch", "main");
        String accessToken = (String) body.get("accessToken");
        boolean isPrivate  = Boolean.TRUE.equals(body.get("isPrivate"));

        if (fullName == null || fullName.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "fullName é obrigatório"));
        }

        try {
            GitHubRepositoryDTO dto = gitHubService.connectRepository(
                    fullName, branch, accessToken, isPrivate);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/github/repositories/{id}/reindex
     * Força re-indexação de um repositório.
     */
    @PostMapping("/repositories/{id}/reindex")
    public ResponseEntity<GitHubRepositoryDTO> reindexRepository(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(gitHubService.reindexRepository(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * DELETE /api/github/repositories/{id}
     * Remove um repositório conectado.
     */
    @DeleteMapping("/repositories/{id}")
    public ResponseEntity<Void> deleteRepository(@PathVariable UUID id) {
        return gitHubService.deleteRepository(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * GET /api/github/repositories/{id}/context
     * Retorna o contexto indexado de um repositório para injeção no prompt.
     * Usado pelo OllamaService quando o usuário ativa um repo.
     */
    @GetMapping("/repositories/{id}/context")
    public ResponseEntity<Map<String, String>> getRepositoryContext(@PathVariable UUID id) {
        String context = gitHubService.buildRepositoryContext(id);
        if (context == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("context", context));
    }
}
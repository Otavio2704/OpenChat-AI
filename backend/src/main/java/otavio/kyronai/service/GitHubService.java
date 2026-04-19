package otavio.kyronai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import otavio.kyronai.model.GitHubRepository;
import otavio.kyronai.model.GitHubRepositoryDTO;
import otavio.kyronai.repository.GitHubRepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serviço para conectar repositórios GitHub e indexar o conteúdo
 * para injeção como contexto no prompt da IA.
 *
 * Usa a API REST do GitHub v3.
 * Rate limits: 60 req/h (sem auth), 5000 req/h (com PAT).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private final GitHubRepositoryRepository repoRepository;
    private final ObjectMapper objectMapper;

    private static final String GITHUB_API = "https://api.github.com";
    private static final int    MAX_FILE_SIZE_BYTES = 50_000;   // 50KB por arquivo
    private static final int    MAX_TOTAL_CONTEXT   = 80_000;   // 80KB total de contexto
    private static final int    MAX_FILES_PER_REPO  = 100;

    /** Extensões de arquivo que fazem sentido indexar */
    private static final Set<String> INDEXABLE_EXTENSIONS = Set.of(
        "java", "kt", "scala",                        // JVM
        "py", "rb", "php",                            // scripting
        "js", "ts", "jsx", "tsx", "vue", "svelte",   // frontend
        "go", "rs", "c", "cpp", "h", "hpp",           // sistemas
        "cs", "dart", "swift",                        // mobile/desktop
        "sql", "graphql",                             // data
        "yaml", "yml", "json", "toml", "xml",         // config
        "md", "txt",                                  // docs
        "html", "css", "scss",                        // web
        "sh", "bash", "ps1",                          // scripts
        "dockerfile", "env"                           // infra
    );

    /** Arquivos e pastas a ignorar */
    private static final Set<String> IGNORED_PATHS = Set.of(
        "node_modules", ".git", "vendor", "target", "build",
        "dist", ".idea", ".vscode", "__pycache__", ".gradle"
    );

    private final WebClient githubClient = WebClient.builder()
            .baseUrl(GITHUB_API)
            .defaultHeader("Accept", "application/vnd.github.v3+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .build();

    private final ExecutorService indexingExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "github-indexer");
                t.setDaemon(true);
                return t;
            });

    // =========================================================================
    // CRUD de repositórios
    // =========================================================================

    @Transactional(readOnly = true)
    public List<GitHubRepositoryDTO> getAllRepositories() {
        return repoRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(GitHubRepositoryDTO::fromEntity)
                .toList();
    }

    @Transactional
    public GitHubRepositoryDTO connectRepository(String fullName,
                                                  String branch,
                                                  String accessToken,
                                                  boolean isPrivate) {
        // Valida formato "owner/repo"
        if (!fullName.matches("[\\w.-]+/[\\w.-]+")) {
            throw new IllegalArgumentException("Formato inválido. Use: owner/repositório");
        }

        String[] parts = fullName.split("/", 2);

        // Atualiza se já existe
        GitHubRepository repo = repoRepository.findByFullName(fullName)
                .orElseGet(() -> GitHubRepository.builder()
                        .fullName(fullName)
                        .owner(parts[0])
                        .repoName(parts[1])
                        .build());

        repo.setBranch(branch != null && !branch.isBlank() ? branch : "main");
        repo.setAccessToken(accessToken != null && !accessToken.isBlank() ? accessToken : null);
        repo.setIsPrivate(isPrivate);
        repo.setIndexStatus(GitHubRepository.IndexStatus.PENDING);

        GitHubRepository saved = repoRepository.save(repo);
        log.info("Repositório conectado: {}", fullName);

        // Inicia indexação em background
        indexingExecutor.execute(() -> indexRepository(saved.getId()));

        return GitHubRepositoryDTO.fromEntity(saved);
    }

    @Transactional
    public boolean deleteRepository(UUID id) {
        if (!repoRepository.existsById(id)) return false;
        repoRepository.deleteById(id);
        return true;
    }

    /**
     * Força re-indexação de um repositório.
     */
    @Transactional
    public GitHubRepositoryDTO reindexRepository(UUID id) {
        GitHubRepository repo = repoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repositório não encontrado: " + id));

        repo.setIndexStatus(GitHubRepository.IndexStatus.PENDING);
        repo.setContextIndex(null);
        repoRepository.save(repo);

        indexingExecutor.execute(() -> indexRepository(id));

        return GitHubRepositoryDTO.fromEntity(repo);
    }

    // =========================================================================
    // Indexação
    // =========================================================================

    public void indexRepository(UUID repoId) {
        GitHubRepository repo;
        try {
            repo = repoRepository.findById(repoId).orElse(null);
            if (repo == null) return;

            log.info("Iniciando indexação: {}", repo.getFullName());
            updateStatus(repoId, GitHubRepository.IndexStatus.INDEXING);

            // Busca a árvore completa do repositório
            List<GitHubFile> files = fetchRepositoryTree(repo);

            if (files.isEmpty()) {
                log.warn("Nenhum arquivo indexável em: {}", repo.getFullName());
                updateStatusAndCount(repoId, GitHubRepository.IndexStatus.READY, 0, null);
                return;
            }

            // Baixa conteúdo dos arquivos e monta o índice
            StringBuilder index = new StringBuilder();
            index.append("# Repositório: ").append(repo.getFullName())
                 .append(" (branch: ").append(repo.getBranch()).append(")\n\n");

            int totalChars = 0;
            int indexed    = 0;

            for (GitHubFile file : files) {
                if (indexed >= MAX_FILES_PER_REPO) break;
                if (totalChars >= MAX_TOTAL_CONTEXT) break;

                String content = fetchFileContent(repo, file.path());
                if (content == null || content.isBlank()) continue;

                if (content.length() > MAX_FILE_SIZE_BYTES) {
                    content = content.substring(0, MAX_FILE_SIZE_BYTES)
                            + "\n[... truncado após 50KB ...]";
                }

                index.append("## ").append(file.path()).append("\n```")
                     .append(file.extension()).append("\n")
                     .append(content).append("\n```\n\n");

                totalChars += content.length();
                indexed++;
            }

            String contextIndex = index.toString();
            updateStatusAndCount(repoId, GitHubRepository.IndexStatus.READY,
                                 indexed, contextIndex);

            log.info("Indexação concluída: {} — {} arquivos, {} chars",
                    repo.getFullName(), indexed, totalChars);

        } catch (Exception e) {
            log.error("Falha na indexação do repositório {}: {}", repoId, e.getMessage(), e);
            updateStatus(repoId, GitHubRepository.IndexStatus.ERROR);
        }
    }

    /**
     * Retorna o contexto indexado de todos os repositórios com status READY.
     * Usado para injetar no system prompt quando o usuário ativa um repo.
     */
    @Transactional(readOnly = true)
    public String buildRepositoryContext(UUID repoId) {
        return repoRepository.findById(repoId)
                .filter(r -> r.getIndexStatus() == GitHubRepository.IndexStatus.READY)
                .map(r -> "## Contexto do Repositório GitHub: " + r.getFullName() + "\n\n"
                        + "Branch: " + r.getBranch() + "\n"
                        + "Indexado em: " + r.getLastIndexedAt() + "\n\n"
                        + (r.getContextIndex() != null ? r.getContextIndex() : ""))
                .orElse(null);
    }

    // =========================================================================
    // GitHub API
    // =========================================================================

    private List<GitHubFile> fetchRepositoryTree(GitHubRepository repo) {
        try {
            String url = "/repos/" + repo.getFullName()
                    + "/git/trees/" + repo.getBranch()
                    + "?recursive=1";

            String body = githubClient.get()
                    .uri(url)
                    .headers(h -> addAuthHeader(h, repo.getAccessToken()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));

            if (body == null) return List.of();

            JsonNode root  = objectMapper.readTree(body);
            JsonNode tree  = root.path("tree");
            if (!tree.isArray()) return List.of();

            List<GitHubFile> files = new ArrayList<>();
            for (JsonNode node : tree) {
                String type = node.path("type").asText("");
                String path = node.path("path").asText("");
                long   size = node.path("size").asLong(0);

                if (!"blob".equals(type)) continue;
                if (size > MAX_FILE_SIZE_BYTES) continue;
                if (isIgnoredPath(path)) continue;

                String ext = extractExtension(path);
                if (!INDEXABLE_EXTENSIONS.contains(ext)) continue;

                files.add(new GitHubFile(path, ext, size));
            }

            // Prioriza arquivos menores e mais importantes (README, configs primeiro)
            files.sort(Comparator
                    .comparingInt((GitHubFile f) -> isHighPriority(f.path()) ? 0 : 1)
                    .thenComparingLong(GitHubFile::size));

            return files;

        } catch (WebClientResponseException e) {
            log.error("Erro GitHub API ao buscar tree {}: HTTP {}",
                    repo.getFullName(), e.getStatusCode());
            return List.of();
        } catch (Exception e) {
            log.error("Erro ao buscar tree de {}: {}", repo.getFullName(), e.getMessage());
            return List.of();
        }
    }

    private String fetchFileContent(GitHubRepository repo, String filePath) {
        try {
            String url = "/repos/" + repo.getFullName()
                    + "/contents/" + filePath
                    + "?ref=" + repo.getBranch();

            String body = githubClient.get()
                    .uri(url)
                    .headers(h -> addAuthHeader(h, repo.getAccessToken()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));

            if (body == null) return null;

            JsonNode node    = objectMapper.readTree(body);
            String   content = node.path("content").asText("");
            String   encoding = node.path("encoding").asText("");

            if ("base64".equals(encoding) && !content.isBlank()) {
                // Remove quebras de linha do base64 e decodifica
                String clean = content.replaceAll("\\s+", "");
                return new String(Base64.getDecoder().decode(clean));
            }

            return content;

        } catch (Exception e) {
            log.debug("Erro ao baixar {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    private void addAuthHeader(HttpHeaders headers, String token) {
        if (token != null && !token.isBlank()) {
            headers.set("Authorization", "Bearer " + token);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @Transactional
    protected void updateStatus(UUID repoId, GitHubRepository.IndexStatus status) {
        repoRepository.findById(repoId).ifPresent(r -> {
            r.setIndexStatus(status);
            repoRepository.save(r);
        });
    }

    @Transactional
    protected void updateStatusAndCount(UUID repoId,
                                        GitHubRepository.IndexStatus status,
                                        int count,
                                        String contextIndex) {
        repoRepository.findById(repoId).ifPresent(r -> {
            r.setIndexStatus(status);
            r.setIndexedFilesCount(count);
            r.setLastIndexedAt(LocalDateTime.now());
            if (contextIndex != null) r.setContextIndex(contextIndex);
            repoRepository.save(r);
        });
    }

    private boolean isIgnoredPath(String path) {
        return IGNORED_PATHS.stream().anyMatch(ignored ->
                path.startsWith(ignored + "/") || path.equals(ignored));
    }

    private boolean isHighPriority(String path) {
        String lower = path.toLowerCase();
        return lower.equals("readme.md") || lower.equals("package.json")
                || lower.equals("pom.xml") || lower.equals("build.gradle")
                || lower.equals("requirements.txt") || lower.equals("go.mod")
                || lower.endsWith("/readme.md");
    }

    private String extractExtension(String path) {
        // Casos especiais sem extensão
        String lower = path.toLowerCase();
        if (lower.endsWith("dockerfile")) return "dockerfile";
        if (lower.endsWith(".env") || lower.endsWith(".env.example")) return "env";

        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "";

        // Evita extensões de paths como ".github/workflows/ci.yml" → "yml"
        String ext = path.substring(dot + 1).toLowerCase();
        return ext.contains("/") ? "" : ext;
    }

    private record GitHubFile(String path, String extension, long size) {}
}
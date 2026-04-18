package otavio.kyronai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import otavio.kyronai.model.Message;
import otavio.kyronai.model.ModelCapabilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {

    private final WebClient ollamaWebClient;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final MemoryService memoryService;
    private final ProjectService projectService;
    private final CodeGenerationService codeGenerationService;
    private final AgentService agentService;
    private final GitHubService gitHubService;

    @Value("${searxng.url:http://localhost:8081}")
    private String searxngUrl;

    private final WebClient httpClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();

    private static final String WEB_CONTEXT_PREFIX = "[WEB_SEARCH_CONTEXT]\n";

    private static final String IDENTITY_PROMPT =
            "Você é o Kyron AI, um assistente de IA local criado para rodar inteiramente " +
            "no hardware do usuário via Ollama. Você é direto, técnico quando necessário, " +
            "e sempre respeita a privacidade do usuário — nenhum dado é enviado para " +
            "servidores externos. Responda sempre no idioma em que o usuário escrever.";

    private static final Set<String> THINKING_FAMILIES = Set.of(
            "qwen3", "qwen3moe", "deepseek-r1", "qwq", "marco-o1");

    private static final Set<String> VISION_FAMILIES = Set.of(
            "clip", "llava", "moondream", "bakllava", "qwen2-vl",
            "llama3.2-vision", "minicpm-v", "internvl", "phi3-vision");

    private final ExecutorService streamingExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "ollama-stream");
                t.setDaemon(true);
                return t;
            });

    // =========================================================================
    // Chat com Streaming — agora suporta codeMode, agentMode e githubRepoId
    // =========================================================================

    public void streamChat(String model,
                           List<Message> history,
                           Map<String, Object> options,
                           String systemPrompt,
                           List<String> images,
                           UUID projectId,
                           boolean webSearchEnabled,
                           boolean codeMode,
                           boolean agentMode,
                           UUID githubRepoId,
                           SseEmitter emitter,
                           UUID conversationId) {

        boolean thinkingEnabled = options != null && Boolean.TRUE.equals(options.get("think"));

        streamingExecutor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            StringBuilder fullThinking = new StringBuilder();

            try {
                // -----------------------------------------------------------------
                // 1. Coleta contextos web acumulados
                // -----------------------------------------------------------------
                List<String> accumulatedContexts = extractAllWebContexts(history);

                // -----------------------------------------------------------------
                // 2. Web search (igual ao anterior)
                // -----------------------------------------------------------------
                String lastUserMessage = getLastUserMessage(history);
                boolean shouldSearch   = webSearchEnabled
                        && !lastUserMessage.isBlank()
                        && !isFollowUpQuestion(lastUserMessage);

                if (shouldSearch) {
                    try {
                        emitter.send(SseEmitter.event().name("search-start").data("Buscando na web..."));
                        long   start       = System.currentTimeMillis();
                        String freshContext = fetchWebSearchContext(lastUserMessage);
                        long   elapsed     = System.currentTimeMillis() - start;

                        if (freshContext != null && !freshContext.isBlank()) {
                            conversationService.addMessage(conversationId, "tool",
                                    WEB_CONTEXT_PREFIX + freshContext, false);
                            accumulatedContexts.add(freshContext);
                            emitter.send(SseEmitter.event().name("search-done").data("ok"));
                            emitter.send(SseEmitter.event().name("search-sources").data(freshContext));
                            log.info("Web search ok: conversa={} chars={} ms={}", conversationId, freshContext.length(), elapsed);
                        } else {
                            emitter.send(SseEmitter.event().name("search-done").data("empty"));
                        }
                    } catch (Exception e) {
                        log.error("Web search falhou: conversa={}", conversationId, e);
                        try { emitter.send(SseEmitter.event().name("search-done").data("failed")); }
                        catch (IOException ignored) {}
                    }
                } else if (webSearchEnabled && !accumulatedContexts.isEmpty()) {
                    emitter.send(SseEmitter.event().name("search-done").data("cached"));
                }

                // -----------------------------------------------------------------
                // 3. Contexto do repositório GitHub
                // -----------------------------------------------------------------
                String githubContext = null;
                if (githubRepoId != null) {
                    githubContext = gitHubService.buildRepositoryContext(githubRepoId);
                    if (githubContext != null) {
                        log.info("Contexto GitHub injetado: repoId={} chars={}", githubRepoId, githubContext.length());
                        emitter.send(SseEmitter.event().name("github-context").data("injected"));
                    }
                }

                // -----------------------------------------------------------------
                // 4. Monta payload com system prompts extras (código/agente)
                // -----------------------------------------------------------------
                Map<String, Object> payload = buildChatPayload(
                        model, history, options, systemPrompt, images, projectId,
                        accumulatedContexts, codeMode, agentMode, githubContext);

                // -----------------------------------------------------------------
                // 5. Stream de tokens
                // -----------------------------------------------------------------
                Flux<String> tokenFlux = ollamaWebClient.post()
                        .uri("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToFlux(String.class);

                tokenFlux
                    .doOnNext(chunk -> {
                        try {
                            JsonNode node        = objectMapper.readTree(chunk);
                            JsonNode messageNode = node.path("message");

                            if (!messageNode.isMissingNode()) {
                                String token = messageNode.path("content").asText("");
                                if (!token.isEmpty()) {
                                    fullResponse.append(token);
                                    emitter.send(SseEmitter.event().name("token").data(token));
                                }
                                String thinkingToken = messageNode.path("thinking").asText("");
                                if (!thinkingToken.isEmpty()) {
                                    fullThinking.append(thinkingToken);
                                    emitter.send(SseEmitter.event().name("thinking").data(thinkingToken));
                                }
                            }

                            if (node.path("done").asBoolean(false)) {
                                String fc = fullResponse.toString();
                                String tc = fullThinking.toString();
                                String finalResponse = tc.isBlank()
                                        ? fc
                                        : "<thinking>" + tc + "</thinking>\n\n" + fc;

                                conversationService.addMessage(
                                        conversationId, "assistant", finalResponse, thinkingEnabled);

                                // -------------------------------------------------
                                // 6. Pós-processamento: extrai arquivos e ações
                                // -------------------------------------------------
                                if (codeMode && !fc.isBlank()) {
                                    processCodeModeResponse(conversationId, fc, emitter);
                                }

                                if (agentMode && !fc.isBlank()) {
                                    processAgentModeResponse(conversationId, fc, emitter);
                                }

                                log.info("Streaming concluído: conversa={} chars={}", conversationId, fc.length());
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                emitter.complete();
                            }
                        } catch (IOException e) {
                            log.error("Erro ao processar chunk", e);
                        }
                    })
                    .doOnError(e -> {
                        log.error("Erro no stream: conversa={}", conversationId, e);
                        try {
                            emitter.send(SseEmitter.event().name("error")
                                    .data("Erro ao comunicar com o Ollama: " + e.getMessage()));
                        } catch (IOException ignored) {}
                        emitter.completeWithError(e);
                    })
                    .blockLast();

            } catch (Exception e) {
                log.error("Falha fatal no streaming: conversa={}", conversationId, e);
                emitter.completeWithError(e);
            }
        });
    }

    // =========================================================================
    // Pós-processamento de resposta
    // =========================================================================

    private void processCodeModeResponse(UUID conversationId, String response, SseEmitter emitter) {
        try {
            var files = codeGenerationService.extractAndSaveFiles(conversationId, response);
            if (!files.isEmpty()) {
                // Notifica o frontend que há novos arquivos para exibir
                String fileList = files.stream()
                        .map(f -> f.getFilePath() + "|" + f.getId().toString()
                                + "|" + (f.isNewFile() ? "new" : "updated")
                                + "|" + f.getVersion())
                        .reduce((a, b) -> a + ";" + b)
                        .orElse("");

                emitter.send(SseEmitter.event().name("code-files").data(fileList));
                log.info("Modo Código: {} arquivo(s) gerado(s) para conversa={}", files.size(), conversationId);
            }
        } catch (Exception e) {
            log.error("Erro ao processar Modo Código: {}", e.getMessage(), e);
        }
    }

    private void processAgentModeResponse(UUID conversationId, String response, SseEmitter emitter) {
        try {
            // Obtém sessão de código (pode não existir se não há arquivos ainda)
            UUID sessionId = codeGenerationService
                    .getSessionByConversation(conversationId)
                    .map(s -> s.getId())
                    .orElse(null);

            var actions = agentService.extractAndPersistActions(conversationId, sessionId, response);
            if (!actions.isEmpty()) {
                // Serializa ações como JSON para o frontend renderizar os cards de aprovação
                String actionsJson = objectMapper.writeValueAsString(actions);
                emitter.send(SseEmitter.event().name("agent-actions").data(actionsJson));
                log.info("Modo Agente: {} ação(ões) pendente(s) para conversa={}", actions.size(), conversationId);
            }
        } catch (Exception e) {
            log.error("Erro ao processar Modo Agente: {}", e.getMessage(), e);
        }
    }

    // =========================================================================
    // Montagem do payload — agora inclui system prompts de código/agente/github
    // =========================================================================

    private Map<String, Object> buildChatPayload(String model,
                                                  List<Message> history,
                                                  Map<String, Object> options,
                                                  String systemPrompt,
                                                  List<String> images,
                                                  UUID projectId,
                                                  List<String> accumulatedWebContexts,
                                                  boolean codeMode,
                                                  boolean agentMode,
                                                  String githubContext) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // Bloco de contexto web
        String webRagBlock = buildWebRagBlock(accumulatedWebContexts);

        // System prompts especiais
        String codeModePrompt  = codeMode  ? codeGenerationService.buildCodeModeSystemPrompt(null) : null;
        String agentModePrompt = agentMode ? agentService.buildAgentModeSystemPrompt() : null;

        // Outros contextos
        String memoryPrompt   = memoryService.buildMemorySystemPrompt();
        String projectContext = projectId != null ? projectService.buildProjectContext(projectId) : null;

        // Monta o system prompt em camadas: identidade → código/agente → memória → projeto → github → web → custom
        String sp = IDENTITY_PROMPT;
        if (codeModePrompt  != null) sp = merge(sp, codeModePrompt);
        if (agentModePrompt != null) sp = merge(sp, agentModePrompt);
        sp = merge(sp, memoryPrompt);
        sp = merge(sp, projectContext);
        sp = merge(sp, githubContext);
        sp = merge(sp, webRagBlock);
        sp = merge(sp, systemPrompt);

        messages.add(Map.of("role", "system", "content", sp));

        // Histórico filtrando mensagens internas
        for (int i = 0; i < history.size(); i++) {
            Message msg = history.get(i);
            if ("tool".equals(msg.getRole())) continue;

            boolean isLastUser = "user".equals(msg.getRole())
                    && i == history.size() - 1
                    && images != null && !images.isEmpty();

            if (isLastUser) {
                Map<String, Object> m = new HashMap<>();
                m.put("role",    msg.getRole());
                m.put("content", msg.getContent());
                m.put("images",  images);
                messages.add(m);
            } else {
                messages.add(new HashMap<>(msg.toOllamaMap()));
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model",   model);
        payload.put("messages", messages);
        payload.put("stream",  true);
        if (options != null && !options.isEmpty()) payload.put("options", options);
        return payload;
    }

    private String buildWebRagBlock(List<String> contexts) {
        if (contexts == null || contexts.isEmpty()) return null;
        StringBuilder wb = new StringBuilder();
        wb.append("## Contexto acumulado de pesquisas na web\n\n")
          .append("Use as informações abaixo como base para suas respostas. ")
          .append("Quando o usuário perguntar sobre fontes, liste as URLs presentes.\n\n");
        for (int i = 0; i < contexts.size(); i++) {
            if (contexts.size() > 1) wb.append("### Busca ").append(i + 1).append("\n");
            wb.append(contexts.get(i)).append("\n\n");
        }
        return wb.toString().trim();
    }

    private String merge(String a, String b) {
        boolean ha = a != null && !a.isBlank();
        boolean hb = b != null && !b.isBlank();
        if (ha && hb) return a + "\n\n" + b;
        if (ha) return a;
        if (hb) return b;
        return null;
    }

    // =========================================================================
    // Web Search (igual ao original)
    // =========================================================================

    private String fetchWebSearchContext(String query) {
        String result = fetchFromSearXNG(query);
        if (result != null) return result;
        result = fetchFromDuckDuckGoHTML(query);
        if (result != null) return result;
        return fetchFromDuckDuckGoInstant(query);
    }

    private String fetchFromSearXNG(String query) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(searxngUrl + "/search")
                    .queryParam("q", query)
                    .queryParam("format", "json")
                    .queryParam("language", "pt-BR")
                    .queryParam("categories", "general")
                    .build().toUriString();

            String body = httpClient.get().uri(url)
                    .header("User-Agent", "KyronAI/1.0")
                    .retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));

            if (body == null || body.isBlank()) return null;

            JsonNode root    = objectMapper.readTree(body);
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) return null;

            StringBuilder ctx = new StringBuilder();
            ctx.append("**Resultados de busca para:** ").append(query).append("\n\n");
            int count = 0;
            for (JsonNode result : results) {
                if (count >= 5) break;
                String title   = result.path("title").asText("").trim();
                String snippet = result.path("content").asText("").trim();
                String link    = result.path("url").asText("").trim();
                if (title.isBlank() && snippet.isBlank()) continue;
                ctx.append("**").append(count + 1).append(". ").append(title).append("**\n");
                if (!snippet.isBlank()) ctx.append(snippet).append("\n");
                if (!link.isBlank())    ctx.append("🔗 ").append(link).append("\n");
                ctx.append("\n");
                count++;
            }
            String out = ctx.toString().trim();
            return (out.isBlank() || count == 0) ? null : out;
        } catch (Exception e) {
            log.debug("SearXNG indisponível: {}", e.getMessage());
            return null;
        }
    }

    private String fetchFromDuckDuckGoHTML(String query) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://html.duckduckgo.com/html/")
                    .queryParam("q", query).queryParam("kl", "br-pt")
                    .build().toUriString();

            String body = httpClient.get().uri(url)
                    .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(12));

            if (body == null || body.isBlank()) return null;
            List<Map<String, String>> results = parseDuckDuckGoHTML(body);
            if (results.isEmpty()) return null;

            StringBuilder ctx = new StringBuilder();
            ctx.append("**Resultados de busca para:** ").append(query).append("\n\n");
            int count = 0;
            for (Map<String, String> result : results) {
                if (count >= 5) break;
                String title   = result.getOrDefault("title", "").trim();
                String snippet = result.getOrDefault("snippet", "").trim();
                String link    = result.getOrDefault("url", "").trim();
                if (title.isBlank() && snippet.isBlank()) continue;
                ctx.append("**").append(count + 1).append(". ").append(title).append("**\n");
                if (!snippet.isBlank()) ctx.append(snippet).append("\n");
                if (!link.isBlank())    ctx.append("🔗 ").append(link).append("\n");
                ctx.append("\n");
                count++;
            }
            String out = ctx.toString().trim();
            return (out.isBlank() || count == 0) ? null : out;
        } catch (Exception e) {
            log.debug("DDG HTML falhou: {}", e.getMessage());
            return null;
        }
    }

    private List<Map<String, String>> parseDuckDuckGoHTML(String html) {
        List<Map<String, String>> results = new ArrayList<>();
        try {
            String[] chunks = html.split("class=\"result__body\"");
            for (int i = 1; i < chunks.length && results.size() < 8; i++) {
                String chunk  = chunks[i];
                Map<String, String> result = new HashMap<>();
                result.put("title",   extractBetween(chunk, "result__a\">", "</a>"));
                result.put("snippet", extractBetween(chunk, "result__snippet\">", "</a>"));
                result.put("url",     extractBetween(chunk, "result__url\">", "</a>").trim());
                result.replaceAll((k, v) -> v.replaceAll("<[^>]+>", "").trim());
                if (!result.get("title").isBlank() || !result.get("snippet").isBlank()) {
                    results.add(result);
                }
            }
        } catch (Exception e) {
            log.debug("Erro ao parsear HTML do DDG: {}", e.getMessage());
        }
        return results;
    }

    private String extractBetween(String text, String start, String end) {
        try {
            int s = text.indexOf(start);
            if (s == -1) return "";
            s += start.length();
            int e = text.indexOf(end, s);
            return e == -1 ? "" : text.substring(s, e);
        } catch (Exception ex) { return ""; }
    }

    private String fetchFromDuckDuckGoInstant(String query) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String url = UriComponentsBuilder
                        .fromHttpUrl("https://api.duckduckgo.com/")
                        .queryParam("q", query).queryParam("format", "json")
                        .queryParam("no_html", "1").queryParam("skip_disambig", "1")
                        .build().toUriString();

                String body = httpClient.get().uri(url)
                        .header("User-Agent", "KyronAI/1.0")
                        .retrieve()
                        .onStatus(s -> !s.is2xxSuccessful(), r -> r.createException())
                        .bodyToMono(String.class).block(Duration.ofSeconds(12));

                if (body == null || body.isBlank()) return null;
                JsonNode root = objectMapper.readTree(body);
                StringBuilder ctx = new StringBuilder();
                String abstractText = root.path("Abstract").asText("");
                if (!abstractText.isBlank()) {
                    ctx.append("**").append(root.path("AbstractSource").asText("Resumo"))
                       .append(":** ").append(abstractText).append("\n\n");
                }
                String answer = root.path("Answer").asText("");
                if (!answer.isBlank()) ctx.append("**Resposta direta:** ").append(answer).append("\n\n");
                String out = ctx.toString().trim();
                return out.isEmpty() ? null : out;
            } catch (Exception e) {
                if (attempt < 2) sleep500ms();
            }
        }
        return null;
    }

    // =========================================================================
    // Helpers de histórico
    // =========================================================================

    private List<String> extractAllWebContexts(List<Message> history) {
        List<String> contexts = new ArrayList<>();
        for (Message msg : history) {
            if ("tool".equals(msg.getRole()) && msg.getContent() != null
                    && msg.getContent().startsWith(WEB_CONTEXT_PREFIX)) {
                contexts.add(msg.getContent().substring(WEB_CONTEXT_PREFIX.length()));
            }
        }
        return contexts;
    }

    private String getLastUserMessage(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).getRole())) return history.get(i).getContent();
        }
        return "";
    }

    private boolean isFollowUpQuestion(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase().trim();
        List<String> patterns = List.of(
            "quais foram as fontes", "qual foi a fonte", "pode elaborar",
            "explica melhor", "me conta mais", "continue", "continua",
            "sim", "não", "ok", "certo", "entendi", "obrigado", "obrigada"
        );
        return patterns.stream().anyMatch(lower::contains);
    }

    // =========================================================================
    // Modelos
    // =========================================================================

    public Mono<Map<String, Object>> listModels() {
        return ollamaWebClient.get().uri("/api/tags").retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    try { //noinspection unchecked
                        return (Map<String, Object>) objectMapper.readValue(json, Map.class);
                    } catch (Exception e) {
                        return Map.of("models", Collections.emptyList());
                    }
                });
    }

    public Mono<Map<String, Object>> getModelInfo(String modelName) {
        return ollamaWebClient.post().uri("/api/show")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", modelName))
                .retrieve().bodyToMono(String.class)
                .map(json -> {
                    try { //noinspection unchecked
                        return (Map<String, Object>) objectMapper.readValue(json, Map.class);
                    } catch (Exception e) {
                        return Map.of("error", "Erro ao parsear resposta do Ollama");
                    }
                });
    }

    public Mono<ModelCapabilities> getModelCapabilities(String modelName) {
        return ollamaWebClient.post().uri("/api/show")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", modelName))
                .retrieve().bodyToMono(String.class)
                .map(json -> parseCapabilities(modelName, json))
                .onErrorReturn(buildFallbackCapabilities(modelName));
    }

    private ModelCapabilities parseCapabilities(String modelName, String json) {
        boolean supportsThinking = false, supportsVision = false;
        int contextLength = 0;
        String family = "", parameterSize = "";
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode capabilities = root.path("capabilities");
            if (capabilities.isArray()) {
                for (JsonNode cap : capabilities) {
                    String c = cap.asText().toLowerCase();
                    if (c.contains("think")) supportsThinking = true;
                    if (c.contains("vision") || c.contains("image")) supportsVision = true;
                }
            }
            JsonNode details = root.path("details");
            if (!details.isMissingNode()) {
                family        = details.path("family").asText("");
                parameterSize = details.path("parameter_size").asText("");
                JsonNode families = details.path("families");
                if (families.isArray()) {
                    for (JsonNode f : families) {
                        String fam = f.asText().toLowerCase();
                        if (THINKING_FAMILIES.stream().anyMatch(fam::contains)) supportsThinking = true;
                        if (VISION_FAMILIES.stream().anyMatch(fam::contains))   supportsVision   = true;
                    }
                }
                JsonNode ctxNode = details.path("context_length");
                if (!ctxNode.isMissingNode()) contextLength = ctxNode.asInt(0);
            }
            JsonNode modelInfo = root.path("model_info");
            if (!modelInfo.isMissingNode() && contextLength == 0) {
                for (String field : List.of("context_length", "num_ctx", "max_position_embeddings")) {
                    JsonNode n = modelInfo.path(field);
                    if (!n.isMissingNode() && n.isInt()) { contextLength = n.asInt(0); break; }
                }
            }
            String nl = modelName.toLowerCase();
            if (!supportsThinking) supportsThinking =
                    THINKING_FAMILIES.stream().anyMatch(nl::contains) || nl.contains("r1") || nl.contains("qwq");
            if (!supportsVision)   supportsVision = VISION_FAMILIES.stream().anyMatch(nl::contains);
            if (contextLength == 0) contextLength = inferContextLength(nl);
        } catch (Exception e) {
            log.warn("Erro ao parsear capabilities '{}': {}", modelName, e.getMessage());
        }
        return ModelCapabilities.builder()
                .modelName(modelName).supportsThinking(supportsThinking)
                .supportsVision(supportsVision).contextLength(contextLength)
                .family(family).parameterSize(parameterSize).build();
    }

    private int inferContextLength(String n) {
        if (n.contains("minimax"))  return 1_000_000;
        if (n.contains("kimi"))     return 131_072;
        if (n.contains("qwen3"))    return 32_768;
        if (n.contains("nemotron")) return 32_768;
        return 4_096;
    }

    private ModelCapabilities buildFallbackCapabilities(String modelName) {
        String nl = modelName.toLowerCase();
        return ModelCapabilities.builder()
                .modelName(modelName)
                .supportsThinking(nl.contains("qwen3") || nl.contains("r1") || nl.contains("qwq"))
                .supportsVision(VISION_FAMILIES.stream().anyMatch(nl::contains))
                .contextLength(inferContextLength(nl))
                .family("").parameterSize("").build();
    }

    private void sleep500ms() {
        try { Thread.sleep(500); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
package otavio.openchat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import otavio.openchat.model.Message;
import otavio.openchat.model.ModelCapabilities;
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

    @Value("${searxng.url:http://localhost:8081}")
    private String searxngUrl;

    private final WebClient httpClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();

    // Prefixo usado para identificar mensagens de contexto web no histórico
    private static final String WEB_CONTEXT_PREFIX = "[WEB_SEARCH_CONTEXT]\n";

    private static final String IDENTITY_PROMPT =
            "Você é o OpenChat, um assistente de IA local criado para rodar inteiramente " +
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
    // Chat com Streaming
    // =========================================================================

    public void streamChat(String model,
                           List<Message> history,
                           Map<String, Object> options,
                           String systemPrompt,
                           List<String> images,
                           UUID projectId,
                           boolean webSearchEnabled,
                           SseEmitter emitter,
                           UUID conversationId) {

        boolean thinkingEnabled = options != null && Boolean.TRUE.equals(options.get("think"));

        streamingExecutor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            StringBuilder fullThinking = new StringBuilder();

            try {
                // -----------------------------------------------------------------
                // 1. Coleta todos os contextos web já salvos no histórico.
                //    Isso garante que o modelo nunca "esqueça" buscas anteriores.
                // -----------------------------------------------------------------
                List<String> accumulatedContexts = extractAllWebContexts(history);

                // -----------------------------------------------------------------
                // 2. Decide se deve fazer uma nova busca
                // -----------------------------------------------------------------
                String lastUserMessage = getLastUserMessage(history);
                boolean shouldSearch = webSearchEnabled
                        && !lastUserMessage.isBlank()
                        && !isFollowUpQuestion(lastUserMessage);

                if (shouldSearch) {
                    try {
                        log.info("Iniciando web search: conversa={} query='{}'",
                                conversationId,
                                lastUserMessage.length() > 100
                                        ? lastUserMessage.substring(0, 100) + "..."
                                        : lastUserMessage);

                        emitter.send(SseEmitter.event()
                                .name("search-start").data("Buscando na web..."));

                        long start = System.currentTimeMillis();
                        String freshContext = fetchWebSearchContext(lastUserMessage);
                        long elapsed = System.currentTimeMillis() - start;

                        if (freshContext != null && !freshContext.isBlank()) {
                            log.info("Web search ok: conversa={} chars={} ms={}",
                                    conversationId, freshContext.length(), elapsed);

                            // Persiste no histórico para os próximos turnos
                            conversationService.addMessage(
                                    conversationId,
                                    "tool",
                                    WEB_CONTEXT_PREFIX + freshContext,
                                    false);

                            // Acumula junto com os anteriores
                            accumulatedContexts.add(freshContext);

                            emitter.send(SseEmitter.event().name("search-done").data("ok"));
                            // Envia fontes ao frontend para exibição imediata
                            emitter.send(SseEmitter.event()
                                    .name("search-sources").data(freshContext));

                        } else {
                            log.info("Web search vazio: conversa={} ms={}", conversationId, elapsed);
                            emitter.send(SseEmitter.event().name("search-done").data("empty"));
                        }

                    } catch (Exception e) {
                        log.error("Web search falhou: conversa={}", conversationId, e);
                        try {
                            emitter.send(SseEmitter.event().name("search-done").data("failed"));
                        } catch (IOException ignored) {}
                    }

                } else if (webSearchEnabled && !accumulatedContexts.isEmpty()) {
                    // Follow-up: reutiliza contexto acumulado, sem nova busca
                    log.info("Reutilizando {} contexto(s) acumulado(s): conversa={}",
                            accumulatedContexts.size(), conversationId);
                    emitter.send(SseEmitter.event().name("search-done").data("cached"));
                }

                // -----------------------------------------------------------------
                // 3. Monta e envia o payload com todo o contexto acumulado
                // -----------------------------------------------------------------
                Map<String, Object> payload = buildChatPayload(
                        model, history, options, systemPrompt,
                        images, projectId, accumulatedContexts);

                Flux<String> tokenFlux = ollamaWebClient.post()
                        .uri("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToFlux(String.class);

                tokenFlux
                    .doOnNext(chunk -> {
                        try {
                            JsonNode node = objectMapper.readTree(chunk);
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
                                    emitter.send(SseEmitter.event()
                                            .name("thinking").data(thinkingToken));
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

                                log.info("Streaming concluído: conversa={} chars={} thinking={}",
                                        conversationId, fc.length(), thinkingEnabled);

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
    // Web Search RAG — Cascata de provedores
    // Ordem: SearXNG (self-hosted) → DuckDuckGo HTML → DDG Instant Answer
    // =========================================================================

    private String fetchWebSearchContext(String query) {
        log.info("Iniciando busca web cascata: query='{}'", query);

        // 1. Tenta SearXNG (self-hosted, melhor qualidade)
        String result = fetchFromSearXNG(query);
        if (result != null) {
            log.info("Contexto obtido via SearXNG: {} chars", result.length());
            return result;
        }

        // 2. Tenta DuckDuckGo HTML (scraping leve, sem chave)
        result = fetchFromDuckDuckGoHTML(query);
        if (result != null) {
            log.info("Contexto obtido via DDG HTML: {} chars", result.length());
            return result;
        }

        // 3. Fallback: DuckDuckGo Instant Answer (só funciona pra fatos diretos)
        result = fetchFromDuckDuckGoInstant(query);
        if (result != null) {
            log.info("Contexto obtido via DDG Instant: {} chars", result.length());
            return result;
        }

        log.warn("Todos os provedores falharam para query='{}'", query);
        return null;
    }

    // -------------------------------------------------------------------------
    // Provedor 1: SearXNG — self-hosted, gratuito, ilimitado
    // -------------------------------------------------------------------------

    private String fetchFromSearXNG(String query) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(searxngUrl + "/search")
                    .queryParam("q", query)
                    .queryParam("format", "json")
                    .queryParam("language", "pt-BR")
                    .queryParam("categories", "general")
                    .build().toUriString();

            log.debug("SearXNG URL: {}", url);

            String body = httpClient.get()
                    .uri(url)
                    .header("User-Agent",
                            "OpenChat/1.0 (+https://github.com/Otavio2704/OpenChat-AI)")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));

            if (body == null || body.isBlank()) return null;

            JsonNode root = objectMapper.readTree(body);
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

            // Infobox se disponível
            JsonNode infoboxes = root.path("infoboxes");
            if (infoboxes.isArray() && !infoboxes.isEmpty()) {
                String content = infoboxes.get(0).path("content").asText("").trim();
                if (!content.isBlank()) {
                    ctx.append("**📌 Destaque:** ").append(content).append("\n\n");
                }
            }

            String out = ctx.toString().trim();
            return (out.isBlank() || count == 0) ? null : out;

        } catch (Exception e) {
            log.debug("SearXNG indisponível: {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Provedor 2: DuckDuckGo HTML — scraping leve, sem chave, sem JS
    // -------------------------------------------------------------------------

    private String fetchFromDuckDuckGoHTML(String query) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://html.duckduckgo.com/html/")
                    .queryParam("q", query)
                    .queryParam("kl", "br-pt")
                    .build().toUriString();

            String body = httpClient.get()
                    .uri(url)
                    .header("User-Agent",
                            "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .retrieve()
                    .bodyToMono(String.class)
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

    /**
     * Parser HTML minimalista para resultados do DuckDuckGo HTML.
     * Sem dependência de Jsoup — extração via busca de substrings.
     */
    private List<Map<String, String>> parseDuckDuckGoHTML(String html) {
        List<Map<String, String>> results = new ArrayList<>();
        try {
            String[] chunks = html.split("class=\"result__body\"");

            for (int i = 1; i < chunks.length && results.size() < 8; i++) {
                String chunk = chunks[i];
                Map<String, String> result = new HashMap<>();

                result.put("title",   extractBetween(chunk, "result__a\">", "</a>"));
                result.put("snippet", extractBetween(chunk, "result__snippet\">", "</a>"));
                result.put("url",     extractBetween(chunk, "result__url\">", "</a>").trim());

                // Remove tags HTML residuais
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
            if (e == -1) return "";
            return text.substring(s, e);
        } catch (Exception ex) {
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // Provedor 3: DuckDuckGo Instant Answer — fallback para fatos diretos
    // -------------------------------------------------------------------------

    private String fetchFromDuckDuckGoInstant(String query) {
        final int MAX_RETRIES = 2;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String url = UriComponentsBuilder
                        .fromHttpUrl("https://api.duckduckgo.com/")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("no_html", "1")
                        .queryParam("skip_disambig", "1")
                        .build().toUriString();

                String body = httpClient.get()
                        .uri(url)
                        .header("User-Agent",
                                "OpenChat/1.0 (+https://github.com/Otavio2704/OpenChat-AI)")
                        .retrieve()
                        .onStatus(status -> !status.is2xxSuccessful(),
                                clientResponse -> clientResponse.createException())
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(12));

                if (body == null || body.isBlank()) return null;

                JsonNode root = objectMapper.readTree(body);
                StringBuilder ctx = new StringBuilder();

                String abstractText   = root.path("Abstract").asText("");
                String abstractSource = root.path("AbstractSource").asText("");
                String abstractUrl    = root.path("AbstractURL").asText("");
                if (!abstractText.isBlank()) {
                    ctx.append("**")
                       .append(abstractSource.isEmpty() ? "Resumo" : abstractSource)
                       .append(":** ").append(abstractText);
                    if (!abstractUrl.isBlank()) ctx.append("\n🔗 ").append(abstractUrl);
                    ctx.append("\n\n");
                }

                String answer = root.path("Answer").asText("");
                if (!answer.isBlank()) {
                    ctx.append("**Resposta direta:** ").append(answer).append("\n\n");
                }

                JsonNode relatedTopics = root.path("RelatedTopics");
                int count = 0;
                if (relatedTopics.isArray()) {
                    for (JsonNode topic : relatedTopics) {
                        if (count >= 5) break;
                        String text     = topic.path("Text").asText("");
                        String topicUrl = topic.path("FirstURL").asText("");
                        if (!text.isBlank()) {
                            ctx.append("- ").append(text);
                            if (!topicUrl.isBlank()) ctx.append(" 🔗 ").append(topicUrl);
                            ctx.append("\n");
                            count++;
                        }
                    }
                }

                JsonNode results = root.path("Results");
                if (results.isArray()) {
                    for (JsonNode result : results) {
                        String text      = result.path("Text").asText("");
                        String resultUrl = result.path("FirstURL").asText("");
                        if (!text.isBlank()) {
                            ctx.append("- ").append(text);
                            if (!resultUrl.isBlank()) ctx.append(" 🔗 ").append(resultUrl);
                            ctx.append("\n");
                        }
                    }
                }

                String out = ctx.toString().trim();
                return out.isEmpty() ? null : out;

            } catch (WebClientResponseException e) {
                log.warn("DDG Instant HTTP {}: tentativa {}/{}",
                        e.getStatusCode(), attempt, MAX_RETRIES);
                if (attempt < MAX_RETRIES && e.getStatusCode().is5xxServerError()) {
                    sleep500ms();
                }
            } catch (Exception e) {
                log.debug("DDG Instant falhou tentativa {}/{}: {}",
                        attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) sleep500ms();
            }
        }
        return null;
    }

    // =========================================================================
    // Helpers — contexto acumulado
    // =========================================================================

    /**
     * Varre TODO o histórico e coleta cada contexto web salvo em ordem cronológica.
     * Garante que o modelo tenha acesso a todas as buscas realizadas na conversa.
     */
    private List<String> extractAllWebContexts(List<Message> history) {
        List<String> contexts = new ArrayList<>();
        for (Message msg : history) {
            if ("tool".equals(msg.getRole())
                    && msg.getContent() != null
                    && msg.getContent().startsWith(WEB_CONTEXT_PREFIX)) {
                contexts.add(msg.getContent().substring(WEB_CONTEXT_PREFIX.length()));
            }
        }
        if (!contexts.isEmpty()) {
            log.debug("Contextos web acumulados no histórico: {}", contexts.size());
        }
        return contexts;
    }

    /**
     * Retorna o conteúdo da última mensagem do usuário.
     */
    private String getLastUserMessage(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).getRole())) {
                return history.get(i).getContent();
            }
        }
        return "";
    }

    /**
     * Heurística: detecta se a mensagem é um follow-up que não exige nova busca.
     */
    private boolean isFollowUpQuestion(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase().trim();

        List<String> patterns = List.of(
            // fontes
            "quais foram as fontes", "qual foi a fonte", "quais são as fontes",
            "me dá as fontes", "me dê as fontes", "mostra as fontes",
            "pode mostrar as fontes", "links das fontes", "link da fonte",
            "de onde você tirou", "de onde veio", "onde você pesquisou",
            "onde pesquisou", "referências",
            // elaboração
            "pode elaborar", "explica melhor", "me conta mais",
            "fala mais sobre isso", "me fala mais", "continue", "continua",
            "e mais", "o que mais", "por quê", "como assim",
            "pode repetir", "não entendi", "reformula",
            // confirmações curtas
            "sim", "não", "ok", "certo", "entendi", "obrigado", "obrigada",
            "perfeito", "ótimo", "show", "legal"
        );

        return patterns.stream().anyMatch(lower::contains);
    }

    // =========================================================================
    // Montagem do payload
    // =========================================================================

    private Map<String, Object> buildChatPayload(String model,
                                                  List<Message> history,
                                                  Map<String, Object> options,
                                                  String systemPrompt,
                                                  List<String> images,
                                                  UUID projectId,
                                                  List<String> accumulatedWebContexts) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // Monta o bloco de contexto web consolidado
        String webRagBlock = null;
        if (accumulatedWebContexts != null && !accumulatedWebContexts.isEmpty()) {
            StringBuilder webBlock = new StringBuilder();
            webBlock.append("## Contexto acumulado de pesquisas na web\n\n")
                    .append("As informações abaixo foram obtidas em buscas realizadas ")
                    .append("ao longo desta conversa. Use-as como base para suas respostas. ")
                    .append("Quando o usuário perguntar sobre fontes, liste as URLs presentes. ")
                    .append("Se algum contexto não for relevante para a pergunta atual, ignore-o.\n\n");

            for (int i = 0; i < accumulatedWebContexts.size(); i++) {
                if (accumulatedWebContexts.size() > 1) {
                    webBlock.append("### Busca ").append(i + 1).append("\n");
                }
                webBlock.append(accumulatedWebContexts.get(i)).append("\n\n");
            }
            webRagBlock = webBlock.toString().trim();
        }

        // Constrói o system prompt completo
        String memoryPrompt   = memoryService.buildMemorySystemPrompt();
        String projectContext = projectId != null
                ? projectService.buildProjectContext(projectId) : null;

        String sp = mergeSystemPrompts(IDENTITY_PROMPT, memoryPrompt);
        sp = mergeSystemPrompts(sp, projectContext);
        sp = mergeSystemPrompts(sp, webRagBlock);
        sp = mergeSystemPrompts(sp, systemPrompt);
        messages.add(Map.of("role", "system", "content", sp));

        // Adiciona histórico filtrando mensagens internas (role: tool)
        for (int i = 0; i < history.size(); i++) {
            Message msg = history.get(i);

            // Mensagens "tool" são internas — contexto já injetado no system prompt
            if ("tool".equals(msg.getRole())) continue;

            boolean isLastUser = "user".equals(msg.getRole())
                    && i == history.size() - 1
                    && images != null && !images.isEmpty();

            if (isLastUser) {
                Map<String, Object> m = new HashMap<>();
                m.put("role", msg.getRole());
                m.put("content", msg.getContent());
                m.put("images", images);
                messages.add(m);
            } else {
                messages.add(new HashMap<>(msg.toOllamaMap()));
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("stream", true);
        if (options != null && !options.isEmpty()) payload.put("options", options);
        return payload;
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
                        log.error("Erro ao parsear modelos", e);
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
                        log.error("Erro ao parsear info do modelo '{}'", modelName, e);
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

    // =========================================================================
    // Helpers — modelos
    // =========================================================================

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
                if (contextLength == 0) {
                    var it = modelInfo.fields();
                    while (it.hasNext()) {
                        var e = it.next();
                        if (e.getKey().toLowerCase().contains("context") && e.getValue().isInt()) {
                            contextLength = e.getValue().asInt(0);
                            break;
                        }
                    }
                }
            }
            String nl = modelName.toLowerCase();
            if (!supportsThinking) supportsThinking =
                    THINKING_FAMILIES.stream().anyMatch(nl::contains)
                    || nl.contains("qwen3") || nl.contains("r1") || nl.contains("qwq");
            if (!supportsVision) supportsVision =
                    VISION_FAMILIES.stream().anyMatch(nl::contains);
            if (contextLength == 0) contextLength = inferContextLength(nl);
        } catch (Exception e) {
            log.warn("Erro ao parsear capabilities '{}': {}", modelName, e.getMessage());
        }
        return ModelCapabilities.builder()
                .modelName(modelName)
                .supportsThinking(supportsThinking)
                .supportsVision(supportsVision)
                .contextLength(contextLength)
                .family(family)
                .parameterSize(parameterSize)
                .build();
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
                .family("")
                .parameterSize("")
                .build();
    }

    private String mergeSystemPrompts(String a, String b) {
        boolean ha = a != null && !a.isBlank();
        boolean hb = b != null && !b.isBlank();
        if (ha && hb) return a + "\n\n" + b;
        if (ha) return a;
        if (hb) return b;
        return null;
    }

    // =========================================================================
    // Helpers — utilitários
    // =========================================================================

    private void sleep500ms() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isTimeoutException(Exception e) {
        String name      = e.getClass().getName();
        String causeName = e.getCause() != null ? e.getCause().getClass().getName() : "";
        return name.contains("TimeoutException") || causeName.contains("TimeoutException");
    }
}
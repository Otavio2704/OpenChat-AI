package otavio.kyronai.controller;

import otavio.kyronai.model.Message;
import otavio.kyronai.service.ConversationService;
import otavio.kyronai.service.OllamaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final OllamaService ollamaService;
    private final ConversationService conversationService;

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, Object> payload) {
        String convIdStr = (String) payload.get("conversationId");
        if (convIdStr == null || convIdStr.isBlank()) {
            throw new IllegalArgumentException("conversationId é obrigatório");
        }

        UUID   conversationId = UUID.fromString(convIdStr);
        String userMessage    = (String) payload.get("message");
        String model          = (String) payload.getOrDefault("model", "llama3.2");

        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) payload.getOrDefault("options", Map.of());
        String systemPrompt = (String) payload.getOrDefault("systemPrompt", null);

        @SuppressWarnings("unchecked")
        List<String> images = (List<String>) payload.getOrDefault("images", List.of());

        String projectIdStr = (String) payload.getOrDefault("projectId", null);
        UUID   projectId    = projectIdStr != null ? UUID.fromString(projectIdStr) : null;

        boolean webSearchEnabled = Boolean.TRUE.equals(payload.get("webSearch"));
        boolean codeMode         = Boolean.TRUE.equals(payload.get("codeMode"));
        boolean agentMode        = Boolean.TRUE.equals(payload.get("agentMode"));

        String githubRepoIdStr = (String) payload.getOrDefault("githubRepoId", null);
        UUID   githubRepoId    = githubRepoIdStr != null ? UUID.fromString(githubRepoIdStr) : null;

        SseEmitter emitter = new SseEmitter(180_000L);
        conversationService.addMessage(conversationId, "user", userMessage);
        List<Message> history = conversationService.getMessages(conversationId);

        ollamaService.streamChat(model, history, options, systemPrompt, images,
                projectId, webSearchEnabled, codeMode, agentMode, githubRepoId,
                emitter, conversationId);

        return emitter;
    }

    @PostMapping(value = "/new", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter newChat(@RequestBody Map<String, Object> payload) {
        String userMessage = (String) payload.get("message");
        String model       = (String) payload.getOrDefault("model", "llama3.2");

        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) payload.getOrDefault("options", Map.of());
        String systemPrompt = (String) payload.getOrDefault("systemPrompt", null);

        @SuppressWarnings("unchecked")
        List<String> images = (List<String>) payload.getOrDefault("images", List.of());

        String projectIdStr = (String) payload.getOrDefault("projectId", null);
        UUID   projectId    = projectIdStr != null ? UUID.fromString(projectIdStr) : null;

        boolean webSearchEnabled = Boolean.TRUE.equals(payload.get("webSearch"));
        boolean codeMode         = Boolean.TRUE.equals(payload.get("codeMode"));
        boolean agentMode        = Boolean.TRUE.equals(payload.get("agentMode"));

        String githubRepoIdStr = (String) payload.getOrDefault("githubRepoId", null);
        UUID   githubRepoId    = githubRepoIdStr != null ? UUID.fromString(githubRepoIdStr) : null;

        String title = userMessage != null && userMessage.length() > 50
                ? userMessage.substring(0, 50) : userMessage;

        UUID conversationId = projectId != null
                ? conversationService.createConversationForProject(title, model, projectId)
                : conversationService.createConversation(title, model);

        conversationService.addMessage(conversationId, "user", userMessage);
        List<Message> history = conversationService.getMessages(conversationId);

        SseEmitter emitter = new SseEmitter(180_000L);

        try {
            emitter.send(SseEmitter.event()
                    .name("conversation-id")
                    .data(conversationId.toString()));
        } catch (IOException e) {
            log.error("Erro ao enviar conversation-id via SSE", e);
        }

        ollamaService.streamChat(model, history, options, systemPrompt, images,
                projectId, webSearchEnabled, codeMode, agentMode, githubRepoId,
                emitter, conversationId);

        return emitter;
    }
}
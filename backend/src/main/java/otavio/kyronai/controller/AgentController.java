package otavio.kyronai.controller;

import otavio.kyronai.model.AgentActionDTO;
import otavio.kyronai.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * GET /api/agent/actions/{conversationId}
     * Lista todas as ações de uma conversa.
     */
    @GetMapping("/actions/{conversationId}")
    public ResponseEntity<List<AgentActionDTO>> getActions(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(agentService.getActionsByConversation(conversationId));
    }

    /**
     * GET /api/agent/actions/{conversationId}/pending-count
     * Retorna quantas ações estão aguardando aprovação.
     */
    @GetMapping("/actions/{conversationId}/pending-count")
    public ResponseEntity<Map<String, Long>> getPendingCount(@PathVariable UUID conversationId) {
        long count = agentService.countPendingActions(conversationId);
        return ResponseEntity.ok(Map.of("pendingCount", count));
    }

    /**
     * POST /api/agent/actions/{actionId}/approve
     * Aprova e executa uma ação específica.
     */
    @PostMapping("/actions/{actionId}/approve")
    public ResponseEntity<AgentActionDTO> approveAction(@PathVariable UUID actionId) {
        try {
            AgentActionDTO result = agentService.approveAction(actionId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Ação não encontrada: {}", actionId);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Estado inválido para aprovação: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /api/agent/actions/{actionId}/reject
     * Rejeita uma ação pendente.
     */
    @PostMapping("/actions/{actionId}/reject")
    public ResponseEntity<AgentActionDTO> rejectAction(@PathVariable UUID actionId) {
        try {
            AgentActionDTO result = agentService.rejectAction(actionId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * GET /api/agent/system-prompt
     * Retorna o system prompt para o Modo Agente.
     */
    @GetMapping("/system-prompt")
    public ResponseEntity<Map<String, String>> getSystemPrompt() {
        String prompt = agentService.buildAgentModeSystemPrompt();
        return ResponseEntity.ok(Map.of("systemPrompt", prompt));
    }
}
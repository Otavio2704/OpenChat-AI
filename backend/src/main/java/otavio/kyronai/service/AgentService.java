package otavio.kyronai.service;

import otavio.kyronai.model.*;
import otavio.kyronai.repository.AgentActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço do Modo Agente.
 * Parseia intenções de ação da resposta da IA, persiste como AgentAction PENDING,
 * e executa após aprovação do usuário.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentActionRepository actionRepository;
    private final CodeGenerationService codeGenerationService;

    /**
     * Padrão para detectar blocos de ação no formato:
     *
     *   [ACTION:CREATE_FILE]
     *   path: src/main/App.java
     *   description: Cria a classe principal da aplicação
     *   [/ACTION]
     *
     * Ou blocos de código com ação implícita detectada pelo contexto.
     */
    private static final Pattern ACTION_BLOCK_PATTERN = Pattern.compile(
        "\\[ACTION:([A-Z_]+)\\]\\n(.*?)\\[/ACTION\\]",
        Pattern.DOTALL
    );

    private static final Pattern ACTION_FIELD_PATTERN = Pattern.compile(
        "^(path|description):\\s*(.+)$",
        Pattern.MULTILINE
    );

    // =========================================================================
    // Parse e persistência de ações
    // =========================================================================

    /**
     * Extrai ações explícitas da resposta da IA e as persiste como PENDING.
     * Retorna a lista de ações criadas para envio ao frontend.
     */
    @Transactional
    public List<AgentActionDTO> extractAndPersistActions(UUID conversationId,
                                                          UUID sessionId,
                                                          String aiResponse) {
        List<AgentAction> actions = new ArrayList<>();

        // 1. Tenta parsear blocos [ACTION:...] explícitos
        Matcher actionMatcher = ACTION_BLOCK_PATTERN.matcher(aiResponse);
        int order = 0;

        while (actionMatcher.find()) {
            String actionTypeStr = actionMatcher.group(1);
            String body         = actionMatcher.group(2);

            try {
                AgentAction.ActionType type = AgentAction.ActionType.valueOf(actionTypeStr);
                Map<String, String> fields = parseActionFields(body);

                AgentAction action = AgentAction.builder()
                        .conversationId(conversationId)
                        .sessionId(sessionId)
                        .actionType(type)
                        .filePath(fields.get("path"))
                        .description(fields.get("description"))
                        .proposedContent(extractProposedContent(body))
                        .status(AgentAction.ActionStatus.PENDING)
                        .executionOrder(order++)
                        .build();

                actions.add(actionRepository.save(action));

            } catch (IllegalArgumentException e) {
                log.warn("Tipo de ação desconhecido: {}", actionTypeStr);
            }
        }

        // 2. Se a IA gerou blocos de código sem [ACTION:...] explícito,
        //    infere CREATE_FILE/EDIT_FILE automaticamente
        if (actions.isEmpty()) {
            actions.addAll(inferActionsFromCodeBlocks(conversationId, sessionId, aiResponse, order));
        }

        log.info("Ações criadas para conversa {}: {}", conversationId, actions.size());
        return actions.stream().map(AgentActionDTO::fromEntity).toList();
    }

    /**
     * Aprova e executa uma ação específica.
     */
    @Transactional
    public AgentActionDTO approveAction(UUID actionId) {
        AgentAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Ação não encontrada: " + actionId));

        if (action.getStatus() != AgentAction.ActionStatus.PENDING) {
            throw new IllegalStateException("Ação não está pendente: " + action.getStatus());
        }

        action.setStatus(AgentAction.ActionStatus.APPROVED);
        actionRepository.save(action);

        try {
            executeAction(action);
            action.setStatus(AgentAction.ActionStatus.EXECUTED);
            action.setResolvedAt(LocalDateTime.now());
            log.info("Ação {} executada: {} {}", actionId, action.getActionType(), action.getFilePath());
        } catch (Exception e) {
            action.setStatus(AgentAction.ActionStatus.FAILED);
            action.setResolvedAt(LocalDateTime.now());
            log.error("Falha ao executar ação {}: {}", actionId, e.getMessage(), e);
        }

        return AgentActionDTO.fromEntity(actionRepository.save(action));
    }

    /**
     * Rejeita uma ação pendente.
     */
    @Transactional
    public AgentActionDTO rejectAction(UUID actionId) {
        AgentAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Ação não encontrada: " + actionId));

        action.setStatus(AgentAction.ActionStatus.REJECTED);
        action.setResolvedAt(LocalDateTime.now());
        log.info("Ação {} rejeitada pelo usuário", actionId);
        return AgentActionDTO.fromEntity(actionRepository.save(action));
    }

    /**
     * Lista ações de uma conversa por status.
     */
    @Transactional(readOnly = true)
    public List<AgentActionDTO> getActionsByConversation(UUID conversationId) {
        return actionRepository
                .findByConversationIdOrderByExecutionOrderAsc(conversationId)
                .stream()
                .map(AgentActionDTO::fromEntity)
                .toList();
    }

    /**
     * Conta ações pendentes em uma conversa.
     */
    @Transactional(readOnly = true)
    public long countPendingActions(UUID conversationId) {
        return actionRepository.countByConversationIdAndStatus(
                conversationId, AgentAction.ActionStatus.PENDING);
    }

    /**
     * System prompt para o Modo Agente.
     * Instrui a IA a declarar ações explicitamente antes de executar.
     */
    public String buildAgentModeSystemPrompt() {
        return """
            ## Modo Agente Ativo

            Você está no Modo Agente do Kyron AI. Antes de gerar código, declare EXPLICITAMENTE
            as ações que pretende executar, usando o formato abaixo:

            [ACTION:CREATE_FILE]
            path: src/main/java/com/exemplo/App.java
            description: Cria a classe principal da aplicação Spring Boot
            ```java:src/main/java/com/exemplo/App.java
            // conteúdo do arquivo aqui
            ```
            [/ACTION]

            [ACTION:EDIT_FILE]
            path: pom.xml
            description: Adiciona dependência do Spring Security
            ```xml:pom.xml
            // conteúdo completo do arquivo modificado
            ```
            [/ACTION]

            Tipos de ação disponíveis:
            - CREATE_FILE: criar novo arquivo
            - EDIT_FILE: modificar arquivo existente
            - DELETE_FILE: remover arquivo (use com cautela)
            - EXPLAIN: apenas explicar, sem modificar nada

            Regras:
            1. SEMPRE declare as ações antes de executar
            2. Descreva claramente o que cada ação faz em 'description'
            3. Para EDIT_FILE, inclua o arquivo COMPLETO modificado, não apenas o diff
            4. O usuário aprovará cada ação individualmente antes da execução
            5. Se apenas explicando algo, use [ACTION:EXPLAIN] sem código
            """;
    }

    // =========================================================================
    // Execução de ações
    // =========================================================================

    private void executeAction(AgentAction action) {
        switch (action.getActionType()) {
            case CREATE_FILE, EDIT_FILE -> {
                if (action.getConversationId() != null
                        && action.getProposedContent() != null
                        && action.getFilePath() != null) {

                    // Injeta o conteúdo no formato que o CodeGenerationService entende
                    String ext = extractExtension(action.getFilePath());
                    String syntheticBlock = "```" + ext + ":" + action.getFilePath() + "\n"
                            + action.getProposedContent() + "\n```";

                    codeGenerationService.extractAndSaveFiles(
                            action.getConversationId(), syntheticBlock);
                }
            }
            case DELETE_FILE -> {
                // Delete é tratado pelo controller — apenas marca como executado aqui
                log.info("DELETE_FILE marcado para: {}", action.getFilePath());
            }
            case EXPLAIN -> {
                // Sem efeito colateral — apenas registra
                log.info("EXPLAIN executado para conversa {}", action.getConversationId());
            }
            default -> log.warn("Tipo de ação não implementado: {}", action.getActionType());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<AgentAction> inferActionsFromCodeBlocks(UUID conversationId,
                                                          UUID sessionId,
                                                          String aiResponse,
                                                          int startOrder) {
        List<AgentAction> inferred = new ArrayList<>();
        Pattern codePattern = Pattern.compile(
            "```([a-zA-Z0-9+#_-]*)(?::([^\\n`]+))?\\n([\\s\\S]*?)```",
            Pattern.MULTILINE
        );

        Matcher m = codePattern.matcher(aiResponse);
        int order = startOrder;

        while (m.find()) {
            String lang     = m.group(1);
            String filePath = m.group(2);
            String content  = m.group(3);

            if (filePath == null || filePath.isBlank() || content == null) continue;

            AgentAction action = AgentAction.builder()
                    .conversationId(conversationId)
                    .sessionId(sessionId)
                    .actionType(AgentAction.ActionType.CREATE_FILE) // inferido
                    .filePath(filePath.trim())
                    .description("Criar arquivo " + filePath.trim())
                    .proposedContent(content)
                    .status(AgentAction.ActionStatus.PENDING)
                    .executionOrder(order++)
                    .build();

            inferred.add(actionRepository.save(action));
        }

        return inferred;
    }

    private Map<String, String> parseActionFields(String body) {
        Map<String, String> fields = new HashMap<>();
        Matcher m = ACTION_FIELD_PATTERN.matcher(body);
        while (m.find()) {
            fields.put(m.group(1).trim(), m.group(2).trim());
        }
        return fields;
    }

    private String extractProposedContent(String body) {
        Pattern p = Pattern.compile("```[^\\n]*\\n([\\s\\S]*?)```");
        Matcher m = p.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private String extractExtension(String filePath) {
        int dot = filePath.lastIndexOf('.');
        return dot >= 0 ? filePath.substring(dot + 1).toLowerCase() : "txt";
    }
}
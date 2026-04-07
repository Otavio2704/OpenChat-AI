package otavio.openchat.service;

import otavio.openchat.model.Conversation;
import otavio.openchat.model.Message;
import otavio.openchat.repository.ConversationRepository;
import otavio.openchat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public UUID createConversation(String title, String modelName) {
        Conversation conversation = Conversation.builder()
                .title(title)
                .modelName(modelName)
                .build();
        Conversation saved = conversationRepository.save(conversation);
        log.info("Nova conversa criada: id={}, modelo={}", saved.getId(), modelName);
        return saved.getId();
    }

    /**
     * Cria uma conversa já vinculada a um projeto.
     */
    @Transactional
    public UUID createConversationForProject(String title, String modelName, UUID projectId) {
        Conversation conversation = Conversation.builder()
                .title(title)
                .modelName(modelName)
                .projectId(projectId)
                .build();
        Conversation saved = conversationRepository.save(conversation);
        log.info("Conversa criada para projeto {}: id={}", projectId, saved.getId());
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public List<Conversation> getAllConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Conversation> getConversationById(UUID id) {
        return conversationRepository.findById(id).map(conversation -> {
            conversation.getMessages().size();
            return conversation;
        });
    }

    /**
     * Lista todos os chats vinculados a um projeto específico.
     */
    @Transactional(readOnly = true)
    public List<Conversation> getConversationsByProject(UUID projectId) {
        return conversationRepository.findByProjectIdOrderByUpdatedAtDesc(projectId);
    }

    @Transactional
    public boolean deleteConversation(UUID id) {
        if (!conversationRepository.existsById(id)) return false;
        conversationRepository.deleteById(id);
        log.info("Conversa {} deletada", id);
        return true;
    }

    @Transactional
    public boolean togglePin(UUID id) {
        return conversationRepository.findById(id).map(c -> {
            if (!c.isPinned()) {
                long pinnedCount = conversationRepository.findAllByOrderByUpdatedAtDesc()
                        .stream().filter(Conversation::isPinned).count();
                if (pinnedCount >= 3) return false;
            }
            c.setPinned(!c.isPinned());
            conversationRepository.save(c);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean renameConversation(UUID id, String newTitle) {
        return conversationRepository.findById(id).map(c -> {
            c.setTitle(newTitle);
            conversationRepository.save(c);
            return true;
        }).orElse(false);
    }

    @Transactional
    public Message addMessage(UUID conversationId, String role, String content) {
        return addMessage(conversationId, role, content, false);
    }

    @Transactional
    public Message addMessage(UUID conversationId, String role, String content, boolean thinkingEnabled) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversa não encontrada: " + conversationId));
        Message message = new Message(conversation, role, content, thinkingEnabled);
        Message saved = messageRepository.save(message);
        conversationRepository.save(conversation);
        log.debug("Mensagem salva: conversationId={}, role={}, tamanho={}, thinkingEnabled={}",
                conversationId, role, content.length(), thinkingEnabled);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Message> getMessages(UUID conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional(readOnly = true)
    public List<Message> getLastMessages(UUID conversationId, int limit) {
        return messageRepository.findLastNByConversationId(conversationId, limit);
    }
}
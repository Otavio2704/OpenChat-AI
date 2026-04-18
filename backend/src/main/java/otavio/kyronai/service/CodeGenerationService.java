package otavio.kyronai.service;

import otavio.kyronai.model.*;
import otavio.kyronai.repository.CodeSessionRepository;
import otavio.kyronai.repository.GeneratedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço responsável por:
 * 1. Parsear blocos de código da resposta da IA (formato ~~~path/to/file.ext)
 * 2. Criar/atualizar arquivos na CodeSession
 * 3. Controlar versões e detectar diffs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGenerationService {

    private final CodeSessionRepository sessionRepository;
    private final GeneratedFileRepository fileRepository;

    /**
     * Padrão para detectar blocos de código com caminho de arquivo.
     *
     * A IA é instruída a gerar código no formato:
     *   ```linguagem:caminho/do/arquivo.ext
     *   conteúdo do arquivo
     *   ```
     *
     * Exemplos válidos:
     *   ```java:src/main/java/App.java
     *   ```typescript:src/components/Button.tsx
     *   ```python:scripts/process.py
     *   ```html:index.html
     */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "```([a-zA-Z0-9+#_-]*)(?::([^\\n`]+))?\\n([\\s\\S]*?)```",
        Pattern.MULTILINE
    );

    /**
     * Extrai os arquivos de uma resposta da IA e persiste na sessão.
     *
     * @return lista de arquivos criados/atualizados
     */
    @Transactional
    public List<GeneratedFile> extractAndSaveFiles(UUID conversationId, String aiResponse) {
        List<ParsedCodeBlock> blocks = parseCodeBlocks(aiResponse);
        if (blocks.isEmpty()) return List.of();

        // Obtém ou cria a sessão de código para esta conversa
        CodeSession session = sessionRepository.findByConversationId(conversationId)
                .orElseGet(() -> {
                    CodeSession newSession = CodeSession.builder()
                            .conversationId(conversationId)
                            .title("Sessão de Código")
                            .build();
                    return sessionRepository.save(newSession);
                });

        // Detecta a linguagem principal (a mais frequente nos blocos)
        if (session.getPrimaryLanguage() == null && !blocks.isEmpty()) {
            String primaryLang = blocks.stream()
                    .map(ParsedCodeBlock::language)
                    .filter(l -> l != null && !l.isBlank())
                    .findFirst()
                    .orElse("plaintext");
            session.setPrimaryLanguage(primaryLang);
            sessionRepository.save(session);
        }

        List<GeneratedFile> savedFiles = new ArrayList<>();

        for (ParsedCodeBlock block : blocks) {
            if (block.filePath() == null || block.filePath().isBlank()) continue;

            String filePath  = sanitizePath(block.filePath());
            String filename  = extractFilename(filePath);
            String extension = extractExtension(filename);

            // Verifica se arquivo já existe nesta sessão (para diff)
            Optional<GeneratedFile> existing = fileRepository
                    .findBySessionIdAndFilePath(session.getId(), filePath);

            GeneratedFile file;
            if (existing.isPresent()) {
                // Arquivo existente — salva versão anterior para diff
                GeneratedFile gf = existing.get();
                gf.setPreviousContent(gf.getContent());
                gf.setContent(block.content());
                gf.setVersion(gf.getVersion() + 1);
                gf.setNewFile(false);
                file = fileRepository.save(gf);
                log.info("Arquivo atualizado (v{}): {}", file.getVersion(), filePath);
            } else {
                // Novo arquivo
                file = GeneratedFile.builder()
                        .codeSession(session)
                        .filePath(filePath)
                        .fileName(filename)
                        .filename(filename)
                        .extension(extension)
                        .content(block.content())
                        .previousContent(null)
                        .version(1)
                        .newFile(true)
                        .build();
                file = fileRepository.save(file);
                log.info("Novo arquivo criado: {}", filePath);
            }
            savedFiles.add(file);
        }

        // Atualiza timestamp da sessão
        sessionRepository.save(session);

        return savedFiles;
    }

    /**
     * Retorna a sessão com todos os arquivos de uma conversa.
     */
    @Transactional(readOnly = true)
    public Optional<CodeSessionDTO> getSessionByConversation(UUID conversationId) {
        return sessionRepository.findByConversationId(conversationId)
                .map(session -> {
                    session.getFiles().size(); // força carregamento LAZY
                    return CodeSessionDTO.fromEntityWithFiles(session);
                });
    }

    /**
     * Retorna a sessão pelo ID.
     */
    @Transactional(readOnly = true)
    public Optional<CodeSessionDTO> getSessionById(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(session -> {
                    session.getFiles().size();
                    return CodeSessionDTO.fromEntityWithFiles(session);
                });
    }

    /**
     * Deleta todos os arquivos de uma sessão.
     */
    @Transactional
    public void clearSession(UUID sessionId) {
        fileRepository.deleteBySessionId(sessionId);
        log.info("Sessão {} limpa", sessionId);
    }

    /**
     * Gera o system prompt para o Modo Código.
     * Instrui a IA a gerar código no formato com caminho de arquivo.
     */
    public String buildCodeModeSystemPrompt(String language) {
        return """
            ## Modo Código Ativo

            Você está no Modo Código do Kyron AI. Ao gerar código, SEMPRE use o formato abaixo \
            para que os arquivos sejam detectados e exibidos automaticamente:

            ```linguagem:caminho/do/arquivo.extensao
            conteúdo do arquivo aqui
            ```

            Exemplos:
            ```java:src/main/java/com/exemplo/App.java
            public class App { ... }
            ```

            ```typescript:src/components/Button.tsx
            export const Button = () => { ... }
            ```

            ```python:scripts/processar.py
            def main(): ...
            ```

            Regras importantes:
            - SEMPRE inclua o caminho completo do arquivo após os backticks e a linguagem (ex: ```java:src/...)
            - Use caminhos relativos à raiz do projeto
            - Ao modificar um arquivo já existente, use exatamente o mesmo caminho
            - Gere código completo e funcional, nunca truncado
            - Ao criar múltiplos arquivos, gere todos na mesma resposta
            """ + (language != null && !language.isBlank()
                ? "\nLinguagem principal do projeto: " + language
                : "");
    }

    // =========================================================================
    // Helpers de parse
    // =========================================================================

    private List<ParsedCodeBlock> parseCodeBlocks(String text) {
        List<ParsedCodeBlock> blocks = new ArrayList<>();
        if (text == null || text.isBlank()) return blocks;

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
        while (matcher.find()) {
            String language = matcher.group(1);  // ex: "java"
            String filePath = matcher.group(2);  // ex: "src/main/App.java" (pode ser null)
            String content  = matcher.group(3);  // conteúdo do arquivo

            if (content == null) continue;
            content = content.stripTrailing();

            // Se não há filePath explícito, tenta inferir do conteúdo do bloco
            if ((filePath == null || filePath.isBlank()) && language != null) {
                filePath = inferFilePathFromLanguage(language, content);
            }

            blocks.add(new ParsedCodeBlock(language, filePath, content));
        }

        return blocks;
    }

    /**
     * Tenta inferir um caminho de arquivo quando não está explícito.
     * Procura declarações de package/class/module no conteúdo.
     */
    private String inferFilePathFromLanguage(String language, String content) {
        if (content == null) return null;

        return switch (language.toLowerCase()) {
            case "java" -> {
                // Extrai "package com.exemplo;" + "class App" → "com/exemplo/App.java"
                Matcher pkg   = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE).matcher(content);
                Matcher clazz = Pattern.compile("(?:public\\s+)?(?:class|interface|enum|record)\\s+(\\w+)").matcher(content);
                if (pkg.find() && clazz.find()) {
                    String packagePath = pkg.group(1).replace('.', '/');
                    yield "src/main/java/" + packagePath + "/" + clazz.group(1) + ".java";
                }
                yield null;
            }
            case "python" -> {
                // Procura nome de script em comentário ou função main
                Matcher fn = Pattern.compile("def\\s+(\\w+)").matcher(content);
                yield fn.find() ? fn.group(1).equals("main") ? "main.py" : fn.group(1) + ".py" : null;
            }
            case "html" -> "index.html";
            case "css"  -> "style.css";
            case "javascript", "js" -> "script.js";
            case "typescript", "ts" -> "index.ts";
            default -> null;
        };
    }

    private String sanitizePath(String path) {
        // Remove ./ e ../ no início, normaliza barras
        return path.replaceAll("^\\.?/+", "")
                   .replaceAll("\\\\", "/")
                   .trim();
    }

    private String extractFilename(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 && lastDot < filename.length() - 1
                ? filename.substring(lastDot + 1).toLowerCase()
                : "";
    }

    // Record interno para blocos parseados
    private record ParsedCodeBlock(String language, String filePath, String content) {}
}
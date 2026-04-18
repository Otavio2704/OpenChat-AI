package otavio.kyronai.controller;

import otavio.kyronai.model.CodeSessionDTO;
import otavio.kyronai.service.CodeGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
public class CodeController {

    private final CodeGenerationService codeGenerationService;

    /**
     * GET /api/code/session/{conversationId}
     * Retorna a sessão de código de uma conversa (com todos os arquivos gerados).
     */
    @GetMapping("/session/{conversationId}")
    public ResponseEntity<CodeSessionDTO> getSession(@PathVariable UUID conversationId) {
        return codeGenerationService.getSessionByConversation(conversationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/code/session/{sessionId}/file/{fileId}
     * Retorna um arquivo específico pelo ID (com conteúdo completo).
     */
    @GetMapping("/session/{sessionId}/file/{fileId}")
    public ResponseEntity<CodeSessionDTO.GeneratedFileDTO> getFile(
            @PathVariable UUID sessionId,
            @PathVariable UUID fileId) {

        return codeGenerationService.getSessionById(sessionId)
                .flatMap(session -> session.getFiles().stream()
                        .filter(f -> f.getId().equals(fileId))
                        .findFirst())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/code/session/{conversationId}/download/file/{fileId}
     * Download de um arquivo individual como attachment.
     */
    @GetMapping("/session/{conversationId}/download/file/{fileId}")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable UUID conversationId,
            @PathVariable UUID fileId) {

        return codeGenerationService.getSessionByConversation(conversationId)
                .flatMap(session -> session.getFiles().stream()
                        .filter(f -> f.getId().equals(fileId))
                        .findFirst())
                .map(file -> {
                    byte[] content = file.getContent().getBytes(StandardCharsets.UTF_8);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + file.getFilename() + "\"")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .contentLength(content.length)
                            .body(content);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/code/session/{conversationId}/download/zip
     * Download de todos os arquivos da sessão como ZIP.
     * Preserva a estrutura de diretórios.
     */
    @GetMapping("/session/{conversationId}/download/zip")
    public ResponseEntity<byte[]> downloadZip(@PathVariable UUID conversationId) {
        return codeGenerationService.getSessionByConversation(conversationId)
                .map(session -> {
                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

                            String projectName = session.getTitle() != null
                                    ? session.getTitle().replaceAll("[^a-zA-Z0-9_-]", "_")
                                    : "kyronai-project";

                            for (CodeSessionDTO.GeneratedFileDTO file : session.getFiles()) {
                                String zipPath = projectName + "/" + file.getFilePath();
                                ZipEntry entry = new ZipEntry(zipPath);
                                zos.putNextEntry(entry);
                                zos.write(file.getContent().getBytes(StandardCharsets.UTF_8));
                                zos.closeEntry();
                            }
                        }

                        byte[] zipBytes = baos.toByteArray();
                        String zipName  = "kyronai-project-" + conversationId.toString().substring(0, 8) + ".zip";

                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + zipName + "\"")
                                .contentType(MediaType.parseMediaType("application/zip"))
                                .contentLength(zipBytes.length)
                                .body(zipBytes);

                    } catch (Exception e) {
                        log.error("Erro ao gerar ZIP: {}", e.getMessage(), e);
                        return ResponseEntity.internalServerError().<byte[]>build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/code/session/{conversationId}
     * Limpa todos os arquivos de uma sessão de código.
     */
    @DeleteMapping("/session/{conversationId}")
    public ResponseEntity<Void> clearSession(@PathVariable UUID conversationId) {
        return codeGenerationService.getSessionByConversation(conversationId)
                .map(session -> {
                    codeGenerationService.clearSession(session.getId());
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/code/system-prompt
     * Retorna o system prompt do Modo Código para o frontend injetar.
     */
    @GetMapping("/system-prompt")
    public ResponseEntity<Map<String, String>> getSystemPrompt(
            @RequestParam(required = false) String language) {
        String prompt = codeGenerationService.buildCodeModeSystemPrompt(language);
        return ResponseEntity.ok(Map.of("systemPrompt", prompt));
    }
}
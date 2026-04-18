package otavio.kyronai.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeSessionDTO {

    private UUID id;

    private UUID conversationId;

    private UUID projectId;

    private String modelName;

    private String sessionStatus;

    private Integer filesGenerated;

    private Integer filesModified;

    private String lastAction;

    private LocalDateTime startedAt;

    private LocalDateTime lastActivityAt;

    private String summary;

    private String title;

    private String primaryLanguage;

    private String status;

    private List<GeneratedFileDTO> files;

    // ═══════════════════════════════════════════════════════════════════════════
    // Métodos de conversão
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Converte uma entidade CodeSession para DTO com os arquivos relacionados.
     */
    public static CodeSessionDTO fromEntityWithFiles(CodeSession entity) {
        if (entity == null) return null;

        List<GeneratedFileDTO> filesDto = entity.getFiles() != null
                ? entity.getFiles().stream()
                  .map(GeneratedFileDTO::fromEntity)
                  .collect(Collectors.toList())
                : List.of();

        return CodeSessionDTO.builder()
                .id(entity.getId())
                .conversationId(entity.getConversationId())
                .title(entity.getTitle())
                .primaryLanguage(entity.getPrimaryLanguage())
                .status(entity.getStatus())
                .summary(entity.getSummary())
                .startedAt(entity.getCreatedAt())
                .lastActivityAt(entity.getUpdatedAt())
                .files(filesDto)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner DTO para arquivos gerados
    // ═══════════════════════════════════════════════════════════════════════════

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GeneratedFileDTO {
        private UUID id;
        private String fileName;
        private String filename;  // alias para fileName
        private String filePath;
        private String content;
        private String fileType;
        private String language;
        private Boolean isModified;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        /**
         * Converte uma entidade GeneratedFile para DTO.
         */
        public static GeneratedFileDTO fromEntity(GeneratedFile entity) {
            if (entity == null) return null;

            return GeneratedFileDTO.builder()
                    .id(entity.getId())
                    .fileName(entity.getFileName())
                    .filename(entity.getFilename())
                    .filePath(entity.getFilePath())
                    .content(entity.getContent())
                    .fileType(entity.getFileType())
                    .language(entity.getLanguage())
                    .isModified(entity.getIsModified())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .build();
        }

        public String getFilename() {
            return filename != null ? filename : fileName;
        }

        public void setFilename(String filename) {
            this.filename = filename;
            if (fileName == null) {
                this.fileName = filename;
            }
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }
}

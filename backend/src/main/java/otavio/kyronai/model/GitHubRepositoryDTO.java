package otavio.kyronai.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubRepositoryDTO {

    private UUID id;

    private String repositoryName;

    private String owner;

    private String description;

    private String url;

    private String branch;

    private Boolean isPublic;

    private UUID projectId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

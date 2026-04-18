package otavio.kyronai.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentActionDTO {

    private UUID id;

    private String actionType;

    private String description;

    private String parameters;

    private String result;

    private String status;

    private UUID conversationId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

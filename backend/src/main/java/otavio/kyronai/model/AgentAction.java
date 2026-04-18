package otavio.kyronai.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String actionType;

    @Column(length = 1000)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String parameters;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(length = 50)
    private String status;

    @ManyToOne
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

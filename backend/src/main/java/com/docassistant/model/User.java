package com.docassistant.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    @Builder.Default
    private String role = "USER";

    @Column(nullable = false)
    @Builder.Default
    private String tier = "FREE";

    @Column(name = "document_limit", nullable = false)
    @Builder.Default
    private int documentLimit = 3;

    @Column(name = "query_limit", nullable = false)
    @Builder.Default
    private int queryLimit = 10;

    @Column(name = "queries_used", nullable = false)
    @Builder.Default
    private int queriesUsed = 0;

    @Column(name = "last_query_reset_at")
    private LocalDateTime lastQueryResetAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastQueryResetAt == null) {
            lastQueryResetAt = LocalDateTime.now();
        }
    }
}

package com.docassistant.controller;

import com.docassistant.dto.AuthRequest;
import com.docassistant.dto.AuthResponse;
import com.docassistant.dto.RegisterRequest;
import com.docassistant.model.User;
import com.docassistant.repository.UserRepository;
import com.docassistant.repository.DocumentRepository;
import com.docassistant.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "User registration, login, and profile")
public class AuthController {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    private void resetQueriesIfNeeded(User user) {
        if (user.getLastQueryResetAt() == null || 
            user.getLastQueryResetAt().isBefore(LocalDateTime.now().minusDays(1))) {
            user.setQueriesUsed(0);
            user.setLastQueryResetAt(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/auth/register — email='{}'", request.email());

        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "An account with this email already exists"));
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role("USER")
                .tier("FREE")
                .documentLimit(3)
                .queryLimit(10)
                .queriesUsed(0)
                .lastQueryResetAt(LocalDateTime.now())
                .build();

        user = userRepository.save(user);
        log.info("User registered: {} ({})", user.getEmail(), user.getId());

        String token = jwtService.generateToken(user.getEmail(), user.getId(), user.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthResponse.builder()
                        .token(token)
                        .userId(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .tier(user.getTier())
                        .documentLimit(user.getDocumentLimit())
                        .documentsUsed(0)
                        .queryLimit(user.getQueryLimit())
                        .queriesUsed(user.getQueriesUsed())
                        .build());
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {
        log.info("POST /api/auth/login — email='{}'", request.email());

        User user = userRepository.findByEmail(request.email())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password"));
        }

        resetQueriesIfNeeded(user);
        String token = jwtService.generateToken(user.getEmail(), user.getId(), user.getName());
        int docsUsed = documentRepository.findAllByUserIdOrderByUploadedAtDesc(user.getId()).size();

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .tier(user.getTier())
                .documentLimit(user.getDocumentLimit())
                .documentsUsed(docsUsed)
                .queryLimit(user.getQueryLimit())
                .queriesUsed(user.getQueriesUsed())
                .build());
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<?> me() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
        }

        String email = (String) auth.getPrincipal();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }

        resetQueriesIfNeeded(user);
        int docsUsed = documentRepository.findAllByUserIdOrderByUploadedAtDesc(user.getId()).size();

        return ResponseEntity.ok(AuthResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .tier(user.getTier())
                .documentLimit(user.getDocumentLimit())
                .documentsUsed(docsUsed)
                .queryLimit(user.getQueryLimit())
                .queriesUsed(user.getQueriesUsed())
                .build());
    }
}

package com.docassistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket configuration for real-time chat streaming.
 *
 * <p>Registers a WebSocket endpoint at {@code /ws/chat} that can be used
 * to stream AI responses token-by-token to the frontend, providing a
 * more responsive user experience than polling.</p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /**
     * Registers WebSocket handlers with their URL mappings.
     *
     * @param registry the handler registry
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler(), "/ws/chat")
                .setAllowedOriginPatterns("*");
    }

    /**
     * Creates the WebSocket handler for chat interactions.
     *
     * <p>This is a placeholder handler. In production, inject the
     * {@code GeminiService} and {@code DocumentService} to stream
     * AI-generated responses back to the client.</p>
     *
     * @return a {@link TextWebSocketHandler} for chat
     */
    private TextWebSocketHandler chatWebSocketHandler() {
        return new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                // Echo acknowledgement — replace with streaming AI response logic
                session.sendMessage(new TextMessage("{\"status\":\"received\",\"message\":\"" +
                        message.getPayload() + "\"}"));
            }
        };
    }
}

package com.camcheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket configuration for streaming camera footage
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Increased buffer sizes for high-quality video streaming
    private static final int MESSAGE_SIZE_LIMIT = 1024 * 1024; // 1MB
    private static final int SEND_BUFFER_SIZE_LIMIT = 1024 * 1024; // 1MB
    private static final int SEND_TIME_LIMIT = 20 * 1000; // 20 seconds
    
    // WebSocket container configuration for larger frames
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MESSAGE_SIZE_LIMIT);
        container.setMaxBinaryMessageBufferSize(MESSAGE_SIZE_LIMIT);
        container.setMaxSessionIdleTimeout(60000L); // 60 seconds
        container.setAsyncSendTimeout(5000L); // 5 seconds
        return container;
    }
    
    /**
     * Task scheduler for broker heartbeats
     */
    @Bean
    public TaskScheduler webSocketHeartbeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("websocket-heartbeat-");
        scheduler.setDaemon(true);
        return scheduler;
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue", "/user")
              .setHeartbeatValue(new long[] {5000, 5000}) // Client and server heartbeat
              .setTaskScheduler(webSocketHeartbeatTaskScheduler());
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
        config.setPreservePublishOrder(true); // Maintain message order
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setDisconnectDelay(5000) // 5 seconds
                .setHeartbeatTime(5000) // 5 seconds
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js");
    }
    
    @Override
    public void configureWebSocketTransport(@NonNull WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MESSAGE_SIZE_LIMIT); // Max message size
        registration.setSendBufferSizeLimit(SEND_BUFFER_SIZE_LIMIT); // Buffer size
        registration.setSendTimeLimit(SEND_TIME_LIMIT); // Time limit for sending a message
    }
    
    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    accessor.setUser(auth);
                    log.debug("WebSocket connection established for user: {}", 
                            auth != null ? auth.getName() : "anonymous");
                }
                
                return message;
            }
        });
    }
} 
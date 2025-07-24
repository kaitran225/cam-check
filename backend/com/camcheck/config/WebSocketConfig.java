package com.camcheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
import org.springframework.beans.factory.annotation.Value;

import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket configuration for streaming camera footage and audio
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Configuration values from application properties
    @Value("${camcheck.media.websocket.max-message-size:1048576}")
    private int messageSize;
    
    @Value("${camcheck.media.websocket.buffer-size:1048576}")
    private int bufferSize;
    
    @Value("${camcheck.media.websocket.send-timeout:20000}")
    private int sendTimeout;
    
    @Value("${camcheck.media.websocket.heartbeat-interval:5000}")
    private long heartbeatInterval;
    
    @Value("${camcheck.media.audio.enabled:true}")
    private boolean audioEnabled;
    
    // WebSocket container configuration for larger frames
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(messageSize);
        container.setMaxBinaryMessageBufferSize(messageSize);
        container.setMaxSessionIdleTimeout(60000L); // 60 seconds
        container.setAsyncSendTimeout((long) sendTimeout); // Convert int to Long
        
        log.info("WebSocket container configured with message size: {}, binary size: {}", 
                messageSize, messageSize);
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
              .setHeartbeatValue(new long[] {heartbeatInterval, heartbeatInterval})
              .setTaskScheduler(webSocketHeartbeatTaskScheduler());
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
        config.setPreservePublishOrder(true); // Maintain message order
        
        log.info("Message broker configured with heartbeat interval: {}ms", heartbeatInterval);
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setDisconnectDelay(5000) // 5 seconds
                .setHeartbeatTime(heartbeatInterval) // Use configured heartbeat interval
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js");
        
        log.info("STOMP endpoints registered with disconnect delay: 5000ms, heartbeat time: {}ms", 
                heartbeatInterval);
    }
    
    @Override
    public void configureWebSocketTransport(@NonNull WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(messageSize);      // Max message size
        registration.setSendBufferSizeLimit(bufferSize);    // Buffer size
        registration.setSendTimeLimit(sendTimeout);         // Time limit for sending a message
        
        log.info("WebSocket transport configured with message size: {}, buffer size: {}, send timeout: {}ms", 
                messageSize, bufferSize, sendTimeout);
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
        
        // Configure larger buffer sizes for inbound channel to handle audio data
        registration.taskExecutor().corePoolSize(4);
        registration.taskExecutor().maxPoolSize(10);
        registration.taskExecutor().queueCapacity(100);
    }
    
    @Override
    public void configureClientOutboundChannel(@NonNull ChannelRegistration registration) {
        // Configure larger buffer sizes for outbound channel to handle audio data
        registration.taskExecutor().corePoolSize(4);
        registration.taskExecutor().maxPoolSize(10);
        registration.taskExecutor().queueCapacity(100);
    }
} 
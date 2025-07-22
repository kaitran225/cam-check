package com.camcheck.repository;

import com.camcheck.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Repository for chat messages
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    /**
     * Find messages between two users
     */
    @Query("SELECT m FROM ChatMessage m WHERE " +
            "(m.sender = :user1 AND m.recipient = :user2) OR " +
            "(m.sender = :user2 AND m.recipient = :user1) " +
            "ORDER BY m.timestamp DESC")
    Page<ChatMessage> findConversation(String user1, String user2, Pageable pageable);
    
    /**
     * Find unread messages for a user
     */
    List<ChatMessage> findByRecipientAndReadFalseOrderByTimestampDesc(String recipient);
    
    /**
     * Count unread messages for a user
     */
    long countByRecipientAndReadFalse(String recipient);
    
    /**
     * Mark messages as read
     */
    @Modifying
    @Transactional
    @Query("UPDATE ChatMessage m SET m.read = true WHERE m.id IN :ids")
    void markAsRead(List<Long> ids);
    
    /**
     * Find recent conversations for a user
     */
    @Query("SELECT DISTINCT " +
            "CASE WHEN m.sender = :username THEN m.recipient ELSE m.sender END " +
            "FROM ChatMessage m " +
            "WHERE m.sender = :username OR m.recipient = :username " +
            "ORDER BY MAX(m.timestamp) DESC")
    Page<String> findRecentConversations(String username, Pageable pageable);
}

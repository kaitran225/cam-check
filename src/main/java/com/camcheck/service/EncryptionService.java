package com.camcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling end-to-end encryption for media streams
 */
@Service
@Slf4j
public class EncryptionService {

    @Value("${camcheck.encryption.enabled:true}")
    private boolean encryptionEnabled;
    
    @Value("${camcheck.encryption.key-size:256}")
    private int aesKeySize;
    
    @Value("${camcheck.encryption.ec-curve:secp256r1}")
    private String ecCurve;
    
    @Value("${camcheck.encryption.gcm-tag-length:128}")
    private int gcmTagLength;
    
    // Store key pairs for users
    private final Map<String, KeyPair> userKeyPairs = new ConcurrentHashMap<>();
    
    // Store shared secrets for sessions
    private final Map<String, byte[]> sessionKeys = new ConcurrentHashMap<>();
    
    /**
     * Generate a key pair for a user
     * @param userId User identifier
     * @return Public key encoded as Base64 string
     * @throws Exception If key generation fails
     */
    public String generateKeyPair(String userId) throws Exception {
        if (!encryptionEnabled) {
            log.warn("Encryption is disabled, not generating key pair for user {}", userId);
            return null;
        }
        
        try {
            // Generate EC key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec(ecCurve);
            keyGen.initialize(ecSpec, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();
            
            // Store the key pair
            userKeyPairs.put(userId, keyPair);
            
            // Return the public key encoded as Base64
            byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
            String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyBytes);
            
            log.debug("Generated key pair for user {}", userId);
            return publicKeyBase64;
        } catch (Exception e) {
            log.error("Failed to generate key pair for user {}", userId, e);
            throw e;
        }
    }
    
    /**
     * Establish a shared secret between two users using ECDH
     * @param sessionId Session identifier
     * @param userId1 First user identifier
     * @param userId2 Second user identifier
     * @return True if the shared secret was established
     */
    public boolean establishSharedSecret(String sessionId, String userId1, String userId2) {
        if (!encryptionEnabled) {
            log.warn("Encryption is disabled, not establishing shared secret for session {}", sessionId);
            return false;
        }
        
        try {
            // Get key pairs for both users
            KeyPair keyPair1 = userKeyPairs.get(userId1);
            KeyPair keyPair2 = userKeyPairs.get(userId2);
            
            if (keyPair1 == null || keyPair2 == null) {
                log.warn("Missing key pair for users {} or {}", userId1, userId2);
                return false;
            }
            
            // Perform key agreement using ECDH
            KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(keyPair1.getPrivate());
            keyAgreement.doPhase(keyPair2.getPublic(), true);
            byte[] sharedSecret = keyAgreement.generateSecret();
            
            // Derive AES key from shared secret
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] sessionKey = sha256.digest(sharedSecret);
            
            // Store the session key
            sessionKeys.put(sessionId, sessionKey);
            
            log.debug("Established shared secret for session {} between users {} and {}", 
                    sessionId, userId1, userId2);
            return true;
        } catch (Exception e) {
            log.error("Failed to establish shared secret for session {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * Establish a shared secret using a public key
     * @param sessionId Session identifier
     * @param userId User identifier
     * @param otherPublicKeyBase64 Other user's public key as Base64 string
     * @return True if the shared secret was established
     */
    public boolean establishSharedSecretWithPublicKey(String sessionId, String userId, String otherPublicKeyBase64) {
        if (!encryptionEnabled) {
            log.warn("Encryption is disabled, not establishing shared secret for session {}", sessionId);
            return false;
        }
        
        try {
            // Get key pair for the user
            KeyPair keyPair = userKeyPairs.get(userId);
            
            if (keyPair == null) {
                log.warn("Missing key pair for user {}", userId);
                return false;
            }
            
            // Decode the other user's public key
            byte[] otherPublicKeyBytes = Base64.getDecoder().decode(otherPublicKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PublicKey otherPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(otherPublicKeyBytes));
            
            // Perform key agreement using ECDH
            KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(keyPair.getPrivate());
            keyAgreement.doPhase(otherPublicKey, true);
            byte[] sharedSecret = keyAgreement.generateSecret();
            
            // Derive AES key from shared secret
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] sessionKey = sha256.digest(sharedSecret);
            
            // Store the session key
            sessionKeys.put(sessionId, sessionKey);
            
            log.debug("Established shared secret for session {} with user {}", sessionId, userId);
            return true;
        } catch (Exception e) {
            log.error("Failed to establish shared secret for session {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * Encrypt data for a session
     * @param sessionId Session identifier
     * @param data Data to encrypt
     * @return Encrypted data with IV as a Base64 string
     * @throws Exception If encryption fails
     */
    public String encrypt(String sessionId, byte[] data) throws Exception {
        if (!encryptionEnabled) {
            // Return data as Base64 without encryption
            return Base64.getEncoder().encodeToString(data);
        }
        
        byte[] sessionKey = sessionKeys.get(sessionId);
        if (sessionKey == null) {
            throw new IllegalStateException("No session key for session " + sessionId);
        }
        
        try {
            // Generate a random IV
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[12]; // 96 bits for GCM
            random.nextBytes(iv);
            
            // Create AES-GCM cipher
            SecretKey key = new SecretKeySpec(sessionKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
            
            // Encrypt the data
            byte[] encryptedData = cipher.doFinal(data);
            
            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);
            
            // Return as Base64 string
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Failed to encrypt data for session {}", sessionId, e);
            throw e;
        }
    }
    
    /**
     * Decrypt data for a session
     * @param sessionId Session identifier
     * @param encryptedDataBase64 Encrypted data with IV as a Base64 string
     * @return Decrypted data
     * @throws Exception If decryption fails
     */
    public byte[] decrypt(String sessionId, String encryptedDataBase64) throws Exception {
        if (!encryptionEnabled) {
            // Decode Base64 without decryption
            return Base64.getDecoder().decode(encryptedDataBase64);
        }
        
        byte[] sessionKey = sessionKeys.get(sessionId);
        if (sessionKey == null) {
            throw new IllegalStateException("No session key for session " + sessionId);
        }
        
        try {
            // Decode the Base64 data
            byte[] combined = Base64.getDecoder().decode(encryptedDataBase64);
            
            // Extract IV and encrypted data
            byte[] iv = new byte[12]; // 96 bits for GCM
            byte[] encryptedData = new byte[combined.length - iv.length];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encryptedData, 0, encryptedData.length);
            
            // Create AES-GCM cipher
            SecretKey key = new SecretKeySpec(sessionKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            
            // Decrypt the data
            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            log.error("Failed to decrypt data for session {}", sessionId, e);
            throw e;
        }
    }
    
    /**
     * Generate a one-time encryption key for a session
     * @param sessionId Session identifier
     * @return Encryption key as a Base64 string
     * @throws Exception If key generation fails
     */
    public String generateOneTimeKey(String sessionId) throws Exception {
        if (!encryptionEnabled) {
            log.warn("Encryption is disabled, not generating one-time key for session {}", sessionId);
            return null;
        }
        
        try {
            // Generate AES key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(aesKeySize);
            SecretKey key = keyGen.generateKey();
            byte[] keyBytes = key.getEncoded();
            
            // Store the key
            sessionKeys.put(sessionId, keyBytes);
            
            // Return the key as Base64
            return Base64.getEncoder().encodeToString(keyBytes);
        } catch (Exception e) {
            log.error("Failed to generate one-time key for session {}", sessionId, e);
            throw e;
        }
    }
    
    /**
     * Set a session key from a Base64 string
     * @param sessionId Session identifier
     * @param keyBase64 Key as a Base64 string
     */
    public void setSessionKey(String sessionId, String keyBase64) {
        if (!encryptionEnabled) {
            log.warn("Encryption is disabled, not setting session key for session {}", sessionId);
            return;
        }
        
        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            sessionKeys.put(sessionId, keyBytes);
            log.debug("Set session key for session {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to set session key for session {}", sessionId, e);
        }
    }
    
    /**
     * Remove a session key
     * @param sessionId Session identifier
     */
    public void removeSessionKey(String sessionId) {
        sessionKeys.remove(sessionId);
        log.debug("Removed session key for session {}", sessionId);
    }
    
    /**
     * Remove a user's key pair
     * @param userId User identifier
     */
    public void removeUserKeyPair(String userId) {
        userKeyPairs.remove(userId);
        log.debug("Removed key pair for user {}", userId);
    }
    
    /**
     * Check if encryption is enabled
     * @return True if encryption is enabled
     */
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }
    
    /**
     * Get encryption capabilities
     * @return Map of encryption capabilities
     */
    public Map<String, Object> getEncryptionCapabilities() {
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("enabled", encryptionEnabled);
        capabilities.put("keySize", aesKeySize);
        capabilities.put("curve", ecCurve);
        capabilities.put("algorithm", "AES-GCM");
        capabilities.put("tagLength", gcmTagLength);
        return capabilities;
    }
} 
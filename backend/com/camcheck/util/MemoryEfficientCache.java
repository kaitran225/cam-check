package com.camcheck.util;

import lombok.extern.slf4j.Slf4j;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Memory-efficient cache implementation using weak references
 * This allows the garbage collector to reclaim memory when needed
 *
 * @param <K> Key type
 * @param <V> Value type
 */
@Slf4j
public class MemoryEfficientCache<K, V> {

    private final Map<K, WeakValue<K, V>> cache = new ConcurrentHashMap<>();
    private final ReferenceQueue<V> referenceQueue = new ReferenceQueue<>();
    private final int maxSize;
    private final long expireAfterMs;
    private final String name;
    
    // Statistics
    private final AtomicInteger hits = new AtomicInteger(0);
    private final AtomicInteger misses = new AtomicInteger(0);
    private final AtomicInteger evictions = new AtomicInteger(0);
    private final AtomicInteger expirations = new AtomicInteger(0);
    private final AtomicInteger gcCollections = new AtomicInteger(0);
    
    /**
     * Create a new memory-efficient cache
     *
     * @param maxSize Maximum number of entries in the cache
     * @param expireAfterMs Time in milliseconds after which entries expire
     * @param name Name of the cache for logging
     */
    public MemoryEfficientCache(int maxSize, long expireAfterMs, String name) {
        this.maxSize = maxSize;
        this.expireAfterMs = expireAfterMs;
        this.name = name;
        log.info("Created memory-efficient cache: {}, maxSize={}, expireAfter={}ms", 
                name, maxSize, expireAfterMs);
    }
    
    /**
     * Get a value from the cache, or compute it if not present
     *
     * @param key The key to look up
     * @param mappingFunction Function to compute the value if not in cache
     * @return The value
     */
    public V get(K key, Function<K, V> mappingFunction) {
        processQueue(); // Clean up any GC'd entries
        
        WeakValue<K, V> weakValue = cache.get(key);
        
        if (weakValue != null) {
            V value = weakValue.get();
            if (value != null && !isExpired(weakValue)) {
                hits.incrementAndGet();
                weakValue.touch(); // Update last access time
                return value;
            }
            // Value was GC'd or expired, remove it
            cache.remove(key);
            if (value == null) {
                gcCollections.incrementAndGet();
            } else {
                expirations.incrementAndGet();
            }
        }
        
        misses.incrementAndGet();
        
        // Compute new value
        V newValue = mappingFunction.apply(key);
        
        // Check if we need to evict entries
        if (cache.size() >= maxSize) {
            evictOldest();
        }
        
        // Store new value
        cache.put(key, new WeakValue<>(key, newValue, referenceQueue));
        
        return newValue;
    }
    
    /**
     * Check if a key is in the cache
     *
     * @param key The key to check
     * @return True if the key is in the cache and not expired
     */
    public boolean containsKey(K key) {
        processQueue(); // Clean up any GC'd entries
        
        WeakValue<K, V> weakValue = cache.get(key);
        if (weakValue != null) {
            V value = weakValue.get();
            if (value != null && !isExpired(weakValue)) {
                return true;
            }
            // Value was GC'd or expired, remove it
            cache.remove(key);
        }
        
        return false;
    }
    
    /**
     * Put a value in the cache
     *
     * @param key The key
     * @param value The value
     */
    public void put(K key, V value) {
        processQueue(); // Clean up any GC'd entries
        
        // Check if we need to evict entries
        if (cache.size() >= maxSize) {
            evictOldest();
        }
        
        cache.put(key, new WeakValue<>(key, value, referenceQueue));
    }
    
    /**
     * Remove a key from the cache
     *
     * @param key The key to remove
     * @return True if the key was removed
     */
    public boolean remove(K key) {
        processQueue(); // Clean up any GC'd entries
        return cache.remove(key) != null;
    }
    
    /**
     * Clear the cache
     */
    public void clear() {
        cache.clear();
    }
    
    /**
     * Get the current size of the cache
     *
     * @return The number of entries in the cache
     */
    public int size() {
        processQueue(); // Clean up any GC'd entries
        return cache.size();
    }
    
    /**
     * Process the reference queue to remove GC'd entries
     */
    @SuppressWarnings("unchecked")
    private void processQueue() {
        WeakValue<K, V> wv;
        while ((wv = (WeakValue<K, V>) referenceQueue.poll()) != null) {
            cache.remove(wv.key);
            gcCollections.incrementAndGet();
        }
    }
    
    /**
     * Check if a value has expired
     *
     * @param weakValue The weak value to check
     * @return True if the value has expired
     */
    private boolean isExpired(WeakValue<K, V> weakValue) {
        return expireAfterMs > 0 && 
               System.currentTimeMillis() - weakValue.lastAccessTime > expireAfterMs;
    }
    
    /**
     * Evict the oldest entry in the cache
     */
    private void evictOldest() {
        K oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<K, WeakValue<K, V>> entry : cache.entrySet()) {
            WeakValue<K, V> value = entry.getValue();
            if (value.lastAccessTime < oldestTime) {
                oldestTime = value.lastAccessTime;
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            cache.remove(oldestKey);
            evictions.incrementAndGet();
        }
    }
    
    /**
     * Get statistics about the cache
     *
     * @return A map of statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("name", name);
        stats.put("size", cache.size());
        stats.put("maxSize", maxSize);
        stats.put("hits", hits.get());
        stats.put("misses", misses.get());
        stats.put("evictions", evictions.get());
        stats.put("expirations", expirations.get());
        stats.put("gcCollections", gcCollections.get());
        
        // Calculate hit rate
        int total = hits.get() + misses.get();
        double hitRate = total > 0 ? (double) hits.get() / total : 0.0;
        stats.put("hitRate", hitRate);
        
        return stats;
    }
    
    /**
     * Weak reference implementation that keeps track of the key and last access time
     *
     * @param <K> Key type
     * @param <V> Value type
     */
    private static class WeakValue<K, V> extends WeakReference<V> {
        private final K key;
        private long lastAccessTime;
        
        public WeakValue(K key, V value, ReferenceQueue<V> queue) {
            super(value, queue);
            this.key = key;
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        public void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
} 
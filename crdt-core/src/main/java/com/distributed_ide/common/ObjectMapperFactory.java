package com.distributed_ide.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thread-safe ObjectMapper pool factory.
 * 
 * OPTIMIZATION: Reuse configured ObjectMapper instances to avoid expensive
 * creation/configuration overhead. Each thread can borrow from the pool.
 * 
 * KEY IMPROVEMENTS:
 * ✅ Reduces ObjectMapper creation overhead (~50-100µs per instantiation)
 * ✅ Reuses pre-configured instances across threads
 * ✅ Prevents JSON parsing/serialization bottlenecks
 * ✅ Thread-safe implementation with non-blocking borrowing
 */
public class ObjectMapperFactory {
    private static final Logger LOG = LoggerFactory.getLogger(ObjectMapperFactory.class);
    
    private static final int POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors());
    private static final BlockingQueue<ObjectMapper> mapperPool;
    private static final ThreadLocal<ObjectMapper> threadLocalMapper = new ThreadLocal<>();
    
    static {
        mapperPool = new LinkedBlockingQueue<>(POOL_SIZE);
        
        // Pre-populate pool with configured mappers
        for (int i = 0; i < POOL_SIZE; i++) {
            mapperPool.offer(createConfiguredMapper());
        }
        
        LOG.info("✅ ObjectMapper pool initialized with {} instances", POOL_SIZE);
    }
    
    /**
     * Get an ObjectMapper (thread-safe, non-blocking).
     * Tries to get from thread-local first, then from pool.
     */
    public static ObjectMapper getMapper() {
        ObjectMapper mapper = threadLocalMapper.get();
        if (mapper != null) {
            return mapper;
        }
        
        // Try non-blocking poll from pool
        mapper = mapperPool.poll();
        if (mapper == null) {
            // Create new one if pool exhausted (unexpected)
            LOG.warn("⚠️  ObjectMapper pool exhausted, creating new instance");
            mapper = createConfiguredMapper();
        }
        
        threadLocalMapper.set(mapper);
        return mapper;
    }
    
    /**
     * Return mapper to pool (usually at thread shutdown).
     */
    public static void returnMapper() {
        ObjectMapper mapper = threadLocalMapper.get();
        if (mapper != null) {
            threadLocalMapper.remove();
            mapperPool.offer(mapper);  // Non-blocking offer
        }
    }
    
    /**
     * Create a new ObjectMapper instance (public wrapper for createConfiguredMapper).
     * Useful for modules that maintain their own mapper pools.
     */
    public static ObjectMapper createNewMapper() {
        return createConfiguredMapper();
    }
    
    /**
     * Create and configure a new ObjectMapper instance.
     */
    private static ObjectMapper createConfiguredMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Disable features that add overhead
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
        
        // Enable efficient features
        mapper.enable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN);
        
        return mapper;
    }
    
    /**
     * Get pool statistics (for monitoring).
     */
    public static int getAvailableMappers() {
        return mapperPool.size();
    }
    
    public static int getPoolSize() {
        return POOL_SIZE;
    }
    
    /**
     * Shutdown pool cleanup.
     */
    public static void shutdown() {
        threadLocalMapper.remove();
        mapperPool.clear();
        LOG.info("✅ ObjectMapper pool shutdown");
    }
}

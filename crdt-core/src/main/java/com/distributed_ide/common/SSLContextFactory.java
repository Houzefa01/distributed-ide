package com.distributed_ide.common;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating SSLContext with TLS 1.3
 * Used by all WebSocket servers for encrypted communication
 * 
 * FIX #1 IMPLEMENTATION: TLS Encryption
 */
public class SSLContextFactory {
    
    private static final Logger LOG = LoggerFactory.getLogger(SSLContextFactory.class);
    
    private static final String TLS_VERSION = "TLSv1.3";
    private static final String KEYSTORE_TYPE = "PKCS12";
    
    /**
     * Creates SSLContext from PKCS12 keystore
     * 
     * @param keystorePath  Path to keystore.p12 file
     * @param password      Keystore password
     * @return              Configured SSLContext ready for WebSocket
     * @throws Exception    If keystore not found or invalid
     */
    public static SSLContext createSSLContext(String keystorePath, String password) 
            throws Exception {
        
        // 1. Verify keystore exists
        File keystoreFile = new File(keystorePath);
        if (!keystoreFile.exists()) {
            LOG.error("❌ Keystore not found: {}", keystorePath);
            LOG.error("   Generate with: openssl pkcs12 -export -in cert.crt -inkey key.key -out keystore.p12");
            throw new IllegalArgumentException(
                "Keystore not found: " + keystorePath
            );
        }
        
        // 2. Load keystore
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        try (InputStream in = Files.newInputStream(keystoreFile.toPath())) {
            keyStore.load(in, password.toCharArray());
            LOG.debug("✅ Keystore loaded successfully");
        }
        
        // 3. Initialize KeyManagerFactory with keystore
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm()
        );
        kmf.init(keyStore, password.toCharArray());
        
        // 4. Create and configure SSLContext with TLS 1.3
        SSLContext sslContext = SSLContext.getInstance(TLS_VERSION);
        sslContext.init(
            kmf.getKeyManagers(),      // Use certificate from keystore
            null,                       // Use default trust manager
            new SecureRandom()         // Use cryptographically secure random
        );
        
        LOG.info("✅ SSLContext configured with TLS 1.3");
        return sslContext;
    }
    
    /**
     * Generates a simple self-signed certificate for testing
     * WARNING: Only for development! Use real certificates in production
     */
    public static void generateSelfSignedCertificate(String keystorePath, String password) 
            throws Exception {
        LOG.warn("⚠️  Generating self-signed certificate for development ONLY");
        LOG.warn("    In production, use real certificates signed by CA");
        
        // This would require additional libraries (Bouncy Castle)
        // For now, users should generate manually with OpenSSL:
        LOG.error("Please generate certificate manually:");
        LOG.error("  openssl req -x509 -newkey rsa:4096 -keyout server.key -out server.crt -days 365 -nodes");
        LOG.error("  openssl pkcs12 -export -in server.crt -inkey server.key -out keystore.p12");
    }
}

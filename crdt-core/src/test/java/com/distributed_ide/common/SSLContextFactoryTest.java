package com.distributed_ide.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SSLContextFactory
 * Tests TLS 1.3 context creation from PKCS12 keystores
 * 
 * FIX #1: TLS Encryption Testing
 */
public class SSLContextFactoryTest {

    private static File testKeystore;
    private static String keystorePassword = "test-password-123456";

    @BeforeAll
    public static void setupKeystore(@TempDir Path tempDir) throws Exception {
        // Create a test PKCS12 keystore
        testKeystore = new File(tempDir.toFile(), "test-keystore.p12");
        
        // Create keystore using keytool equivalent
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, keystorePassword.toCharArray());
        
        // Save to file
        try (FileOutputStream stream = new FileOutputStream(testKeystore)) {
            keyStore.store(stream, keystorePassword.toCharArray());
        }
    }

    @Test
    public void testSSLContextCreation() throws Exception {
        // Setup: Create keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, keystorePassword.toCharArray());
        
        File keystoreFile = File.createTempFile("test-", ".p12");
        try (FileOutputStream stream = new FileOutputStream(keystoreFile)) {
            keyStore.store(stream, keystorePassword.toCharArray());
        }
        
        // Test: Create SSL context
        SSLContext sslContext = SSLContextFactory.createSSLContext(
            keystoreFile.getAbsolutePath(), 
            keystorePassword
        );
        
        // Assert
        assertNotNull(sslContext, "SSLContext should not be null");
        assertEquals("TLSv1.3", sslContext.getProtocol(), "Should use TLS 1.3 protocol");
        assertNotNull(sslContext.getSocketFactory(), "Socket factory should be initialized");
        assertNotNull(sslContext.getServerSocketFactory(), "Server socket factory should be initialized");
        
        // Cleanup
        keystoreFile.delete();
    }

    @Test
    public void testSSLContextWithInvalidKeystore() {
        // Test: Create SSL context with non-existent keystore
        assertThrows(Exception.class, () -> {
            SSLContextFactory.createSSLContext(
                "/nonexistent/path/keystore.p12",
                "password"
            );
        }, "Should throw exception for missing keystore");
    }

    @Test
    public void testSSLContextWithWrongPassword() throws Exception {
        // Setup: Create keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, keystorePassword.toCharArray());
        
        File keystoreFile = File.createTempFile("test-", ".p12");
        try (FileOutputStream stream = new FileOutputStream(keystoreFile)) {
            keyStore.store(stream, keystorePassword.toCharArray());
        }
        
        // Test: Create SSL context with wrong password
        assertThrows(Exception.class, () -> {
            SSLContextFactory.createSSLContext(
                keystoreFile.getAbsolutePath(),
                "wrong-password"
            );
        }, "Should throw exception for incorrect password");
        
        // Cleanup
        keystoreFile.delete();
    }

    @Test
    public void testSSLContextProtocol() throws Exception {
        // Setup: Create keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, keystorePassword.toCharArray());
        
        File keystoreFile = File.createTempFile("test-", ".p12");
        try (FileOutputStream stream = new FileOutputStream(keystoreFile)) {
            keyStore.store(stream, keystorePassword.toCharArray());
        }
        
        // Test: Verify protocol support
        SSLContext sslContext = SSLContextFactory.createSSLContext(
            keystoreFile.getAbsolutePath(),
            keystorePassword
        );
        
        // Assert: TLS 1.3 is enabled
        String[] enabledProtocols = sslContext.getDefaultSSLParameters().getProtocols();
        boolean tls13Supported = false;
        for (String protocol : enabledProtocols) {
            if (protocol.equals("TLSv1.3")) {
                tls13Supported = true;
                break;
            }
        }
        assertTrue(tls13Supported, "TLS 1.3 should be in enabled protocols");
        
        // Cleanup
        keystoreFile.delete();
    }

    @Test
    public void testSSLContextCaching() throws Exception {
        // Setup: Create keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, keystorePassword.toCharArray());
        
        File keystoreFile = File.createTempFile("test-", ".p12");
        try (FileOutputStream stream = new FileOutputStream(keystoreFile)) {
            keyStore.store(stream, keystorePassword.toCharArray());
        }
        
        // Test: Multiple calls should create SSLContext
        SSLContext context1 = SSLContextFactory.createSSLContext(
            keystoreFile.getAbsolutePath(),
            keystorePassword
        );
        
        SSLContext context2 = SSLContextFactory.createSSLContext(
            keystoreFile.getAbsolutePath(),
            keystorePassword
        );
        
        // Assert: Both are valid (may or may not be same instance)
        assertNotNull(context1, "First context should be created");
        assertNotNull(context2, "Second context should be created");
        assertEquals(context1.getProtocol(), context2.getProtocol(), "Should have same protocol");
        
        // Cleanup
        keystoreFile.delete();
    }

    @Test
    public void testSSLContextWithSystemProperties() throws Exception {
        // Setup: Create keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, keystorePassword.toCharArray());
        
        File keystoreFile = File.createTempFile("test-", ".p12");
        try (FileOutputStream stream = new FileOutputStream(keystoreFile)) {
            keyStore.store(stream, keystorePassword.toCharArray());
        }
        
        try {
            // Set system properties
            System.setProperty("keystore.path", keystoreFile.getAbsolutePath());
            System.setProperty("keystore.password", keystorePassword);
            
            // Test: Create SSL context using system properties pattern
            String path = System.getProperty("keystore.path", "./keystore.p12");
            String password = System.getProperty("keystore.password", "distributed-ide-default");
            
            SSLContext sslContext = SSLContextFactory.createSSLContext(path, password);
            
            // Assert
            assertNotNull(sslContext, "SSLContext created with system properties");
            
        } finally {
            // Cleanup system properties
            System.clearProperty("keystore.path");
            System.clearProperty("keystore.password");
            keystoreFile.delete();
        }
    }
}

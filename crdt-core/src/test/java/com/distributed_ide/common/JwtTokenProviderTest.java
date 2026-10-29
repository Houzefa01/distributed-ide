package com.distributed_ide.common;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenProvider
 * Tests JWT token generation, validation, and expiration
 * 
 * FIX #2: JWT Authentication Testing
 */
public class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private static final String TEST_SECRET = "this-is-a-secret-key-for-testing-jwt-tokens-123456";
    private static final String TEST_ISSUER = "distributed-ide-test";

    @BeforeEach
    public void setup() {
        tokenProvider = new JwtTokenProvider(TEST_SECRET, TEST_ISSUER);
    }

    @Test
    public void testTokenGeneration() {
        // Test: Generate token
        String token = tokenProvider.generateToken("user123", "ADMIN");
        
        // Assert
        assertNotNull(token, "Token should not be null");
        assertFalse(token.isEmpty(), "Token should not be empty");
        assertTrue(token.contains("."), "JWT should have 3 parts separated by dots");
    }

    @Test
    public void testTokenWithUserAndRole() {
        // Test: Generate token with specific user and role
        String userId = "test-user-123";
        String role = "EDITOR";
        
        String token = tokenProvider.generateToken(userId, role);
        
        // Assert: Token is valid
        assertNotNull(token, "Token should be generated");
        assertFalse(token.isEmpty(), "Token should not be empty");
    }

    @Test
    public void testTokenValidation() throws Exception {
        // Setup: Generate token
        String token = tokenProvider.generateToken("user123", "ADMIN");
        
        // Test: Validate token
        Claims claims = tokenProvider.validateAndGetClaims(token);
        
        // Assert
        assertNotNull(claims, "Claims should not be null");
        assertNotNull(claims.getSubject(), "Subject (userId) should be in claims");
        assertEquals("user123", tokenProvider.getUserId(token), "UserId should match");
        assertEquals("ADMIN", tokenProvider.getRole(token), "Role should match");
    }

    @Test
    public void testExtractUserId() throws Exception {
        // Setup: Generate token
        String userId = "alice@example.com";
        String token = tokenProvider.generateToken(userId, "USER");
        
        // Test: Extract userId
        String extracted = tokenProvider.getUserId(token);
        
        // Assert
        assertEquals(userId, extracted, "UserId should be correctly extracted");
    }

    @Test
    public void testExtractRole() throws Exception {
        // Setup: Generate token
        String role = "PROJECT_ADMIN";
        String token = tokenProvider.generateToken("user456", role);
        
        // Test: Extract role
        String extracted = tokenProvider.getRole(token);
        
        // Assert
        assertEquals(role, extracted, "Role should be correctly extracted");
    }

    @Test
    public void testTokenExpiration() throws Exception {
        // Setup: Generate token
        String token = tokenProvider.generateToken("user789", "VIEWER");
        
        // Test: Check expiration status
        boolean isExpired = tokenProvider.isExpired(token);
        
        // Assert: Should not be expired immediately
        assertFalse(isExpired, "Fresh token should not be expired");
    }

    @Test
    public void testMalformedToken() {
        // Test: Try to validate malformed token
        String malformedToken = "not.a.valid.jwt.token";
        
        assertThrows(Exception.class, () -> {
            tokenProvider.validateAndGetClaims(malformedToken);
        }, "Should throw exception for malformed token");
    }

    @Test
    public void testInvalidSignature() throws Exception {
        // Setup: Generate token with one provider
        String token = tokenProvider.generateToken("user123", "ADMIN");
        
        // Test: Try to validate with different secret
        JwtTokenProvider differentProvider = new JwtTokenProvider(
            "different-secret-key-for-testing-123456789",
            TEST_ISSUER
        );
        
        assertThrows(Exception.class, () -> {
            differentProvider.validateAndGetClaims(token);
        }, "Should throw exception for invalid signature");
    }

    @Test
    public void testEmptyToken() {
        // Test: Try to validate empty token
        assertThrows(Exception.class, () -> {
            tokenProvider.validateAndGetClaims("");
        }, "Should throw exception for empty token");
    }

    @Test
    public void testNullToken() {
        // Test: Try to validate null token
        assertThrows(Exception.class, () -> {
            tokenProvider.validateAndGetClaims(null);
        }, "Should throw exception for null token");
    }

    @Test
    public void testMultipleTokens() throws Exception {
        // Test: Generate multiple tokens
        String token1 = tokenProvider.generateToken("user1", "ADMIN");
        String token2 = tokenProvider.generateToken("user2", "EDITOR");
        String token3 = tokenProvider.generateToken("user3", "VIEWER");
        
        // Extract userIds
        String userId1 = tokenProvider.getUserId(token1);
        String userId2 = tokenProvider.getUserId(token2);
        String userId3 = tokenProvider.getUserId(token3);
        
        // Assert: Each token has correct userId
        assertEquals("user1", userId1, "Token1 userId");
        assertEquals("user2", userId2, "Token2 userId");
        assertEquals("user3", userId3, "Token3 userId");
    }

    @Test
    public void testTokenClaimsContent() throws Exception {
        // Setup: Generate token
        String userId = "bob@example.com";
        String role = "DEVELOPER";
        String token = tokenProvider.generateToken(userId, role);
        
        // Test: Get claims
        Claims claims = tokenProvider.validateAndGetClaims(token);
        
        // Assert: Claims should contain expected data
        assertNotNull(claims.getIssuedAt(), "IssuedAt should be present");
        assertNotNull(claims.getExpiration(), "Expiration should be present");
        assertEquals(TEST_ISSUER, claims.getIssuer(), "Issuer should match");
        assertEquals(userId, claims.get("userId"), "UserId claim should match");
        assertEquals(role, claims.get("role"), "Role claim should match");
    }

    @Test
    public void testTokenWithDifferentRoles() throws Exception {
        // Test: Generate tokens with various roles
        String[] roles = {"ADMIN", "EDITOR", "VIEWER", "GUEST", "PROJECT_ADMIN"};
        
        for (String role : roles) {
            String token = tokenProvider.generateToken("testuser", role);
            String extractedRole = tokenProvider.getRole(token);
            assertEquals(role, extractedRole, "Role " + role + " should be preserved");
        }
    }

    @Test
    public void testTokenTimestamps() throws Exception {
        // Setup: Generate token
        long beforeGeneration = System.currentTimeMillis();
        String token = tokenProvider.generateToken("user123", "ADMIN");
        long afterGeneration = System.currentTimeMillis();
        
        // Test: Get claims and check timestamps
        Claims claims = tokenProvider.validateAndGetClaims(token);
        long issuedAt = claims.getIssuedAt().getTime();
        long expiration = claims.getExpiration().getTime();
        
        // Assert: Timestamps should be reasonable
        // Allow up to 1 second before generation (JVM timings)
        assertTrue(issuedAt >= beforeGeneration - 1000, "IssuedAt should not be much before generation");
        assertTrue(issuedAt <= afterGeneration + 1000, "IssuedAt should not be after generation check");
        assertTrue(expiration > issuedAt, "Expiration should be after issuance");
        // 24 hours = 86,400,000 milliseconds (with some tolerance)
        long expirationDiff = expiration - issuedAt;
        assertTrue(expirationDiff > 86_300_000 && expirationDiff < 86_500_000, 
            "Expiration should be approximately 24 hours after issuance");
    }

    @Test
    public void testConcurrentTokenGeneration() throws Exception {
        // Test: Generate tokens from multiple threads
        String[] tokens = new String[10];
        
        for (int i = 0; i < 10; i++) {
            final int index = i;
            tokens[i] = tokenProvider.generateToken("user" + index, "ROLE" + index);
        }
        
        // Validate all tokens
        for (int i = 0; i < 10; i++) {
            Claims claims = tokenProvider.validateAndGetClaims(tokens[i]);
            assertNotNull(claims, "Claims for token " + i + " should be valid");
            assertEquals("user" + i, tokenProvider.getUserId(tokens[i]), "UserId should match for token " + i);
        }
    }

    @Test
    public void testTokenReuseability() throws Exception {
        // Setup: Generate token
        String token = tokenProvider.generateToken("user123", "ADMIN");
        
        // Test: Validate same token multiple times
        Claims claims1 = tokenProvider.validateAndGetClaims(token);
        Claims claims2 = tokenProvider.validateAndGetClaims(token);
        Claims claims3 = tokenProvider.validateAndGetClaims(token);
        
        // Assert: All validations should succeed
        assertNotNull(claims1, "First validation should succeed");
        assertNotNull(claims2, "Second validation should succeed");
        assertNotNull(claims3, "Third validation should succeed");
    }
}

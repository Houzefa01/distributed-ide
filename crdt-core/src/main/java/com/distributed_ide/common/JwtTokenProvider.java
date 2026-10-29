package com.distributed_ide.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT token generation and validation using JJWT library
 * Uses HS256 (HMAC with SHA-256) for signing and verification
 * 
 * FIX #2 IMPLEMENTATION: JWT Authentication
 */
public class JwtTokenProvider {
    
    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenProvider.class);
    
    private static final String ALGORITHM = "HS256";  // HMAC with SHA-256
    private static final long EXPIRATION_MS = 86_400_000;  // 24 hours
    
    private final SecretKey secretKey;
    private final String issuer;
    
    /**
     * Initialize JWT provider with secret key and issuer
     * 
     * @param secret    Signing secret (min 32 chars for HS256)
     * @param issuer    Token issuer name
     */
    public JwtTokenProvider(String secret, String issuer) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.issuer = issuer;
    }
    
    /**
     * Generate JWT token for user
     * 
     * @param userId    Unique user identifier
     * @param role      User role (ADMIN, EDITOR, VIEWER)
     * @return          Signed JWT token string
     */
    public String generateToken(String userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("userId", userId);
        
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_MS);
        
        String token = Jwts.builder()
            .setClaims(claims)
            .setSubject(userId)
            .setIssuer(issuer)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact();
        
        LOG.debug("✅ Generated JWT token for user: {}", userId);
        return token;
    }
    
    /**
     * Validate JWT token and extract claims
     * 
     * @param token     JWT token string
     * @return          Claims if token is valid
     * @throws Exception if token is invalid, expired, or malformed
     */
    public Claims validateAndGetClaims(String token) throws Exception {
        try {
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
                
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            LOG.warn("❌ JWT token expired: {}", e.getMessage());
            throw new SecurityException("Token expired");
            
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            LOG.warn("❌ Malformed JWT token: {}", e.getMessage());
            throw new SecurityException("Invalid token format");
            
        } catch (io.jsonwebtoken.SignatureException e) {
            LOG.warn("❌ JWT signature validation failed: {}", e.getMessage());
            throw new SecurityException("Invalid token signature");
            
        } catch (Exception e) {
            LOG.warn("❌ JWT validation failed: {}", e.getMessage());
            throw new SecurityException("Token validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Extract user role from token
     */
    public String getRole(String token) throws Exception {
        Claims claims = validateAndGetClaims(token);
        return (String) claims.get("role");
    }
    
    /**
     * Extract user ID from token
     */
    public String getUserId(String token) throws Exception {
        Claims claims = validateAndGetClaims(token);
        return claims.getSubject();
    }
    
    /**
     * Check if token is expired without throwing exception
     */
    public boolean isExpired(String token) {
        try {
            Claims claims = validateAndGetClaims(token);
            return false;  // Valid
        } catch (Exception e) {
            return true;  // Invalid or expired
        }
    }
}

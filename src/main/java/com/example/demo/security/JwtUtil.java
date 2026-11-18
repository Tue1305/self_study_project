package com.example.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    private static final String DEFAULT_SECRET = "mySuperSecretKeyForJwtTokenGenerationPurposeThatIs32BytesLong";

    @org.springframework.beans.factory.annotation.Value("${jwt.secret:" + DEFAULT_SECRET + "}")
    private String secretKey = DEFAULT_SECRET;

    public void setSecretKey(String secretKey) {
        if (secretKey != null && !secretKey.isBlank()) {
            this.secretKey = secretKey.trim();
        }
    }

    private Key getSigningKey() {
        // Use UTF-8 explicitly to avoid platform-encoding character length mismatches
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails, "default");
    }

    public String generateToken(UserDetails userDetails, String tenantId) {
        if (userDetails == null || userDetails.getUsername() == null) {
            throw new IllegalArgumentException("UserDetails or username cannot be null");
        }

        // Automatically extract the first authority/role from UserDetails
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_USER");

        return createToken(userDetails.getUsername(), role, tenantId);
    }

    public String generateToken(String subject, String role, String tenantId) {
        return createToken(subject, role, tenantId);
    }

    private String createToken(String subject, String role) {
        return createToken(subject, role, "default");
    }

    private String createToken(String subject, String role, String tenantId) {
        String cleanTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim().toLowerCase() : "default";
        return Jwts.builder()
                .setSubject(subject)
                .claim("role", role) // Embeds the role into the token claims
                .claim("tenant_id", cleanTenant) // Embeds tenant_id into token claims
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 10)) // 10 Hours
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractTenantId(String token) {
        try {
            return extractClaim(token, claims -> claims.get("tenant_id", String.class));
        } catch (Exception e) {
            return null;
        }
    }

    public String extractRole(String token) {
        try {
            return extractClaim(token, claims -> claims.get("role", String.class));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}
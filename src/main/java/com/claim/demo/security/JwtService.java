package com.claim.demo.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.claim.demo.dto.UserDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final String issuer;
    private final long expirationMillis;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer:claims-processing-api}") String issuer,
            @Value("${security.jwt.expiration-ms:900000}") long expirationMillis) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
        }
        if (expirationMillis <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_MS must be greater than zero");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.issuer = issuer;
        this.expirationMillis = expirationMillis;
        this.verifier = JWT.require(algorithm).withIssuer(issuer).build();
    }

    public String createToken(UserDTO user) {
        Instant issuedAt = Instant.now();
        return JWT.create()
                .withSubject(user.getUsername())
                .withIssuer(issuer)
                .withIssuedAt(Date.from(issuedAt))
                .withExpiresAt(Date.from(issuedAt.plusMillis(expirationMillis)))
                .withClaim("role", user.getRole())
                .sign(algorithm);
    }

    public String verifyAndGetUsername(String token) {
        DecodedJWT decodedToken = verifier.verify(token);
        return decodedToken.getSubject();
    }
}

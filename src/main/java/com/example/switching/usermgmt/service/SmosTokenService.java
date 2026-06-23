package com.example.switching.usermgmt.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.example.switching.usermgmt.entity.UserEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class SmosTokenService {
    public static final String TOKEN_PREFIX = "smos.";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final long MAX_CLOCK_SKEW_SECONDS = 60;
    private final ObjectMapper mapper; private final byte[] secret; private final long ttlSeconds;

    public SmosTokenService(ObjectMapper mapper,
            @Value("${switching.smos.jwt-secret:}") String secret,
            @Value("${switching.smos.access-token-ttl-seconds:3600}") long ttlSeconds) {
        if (secret == null || secret.length() < 32) throw new IllegalStateException("SMOS_JWT_SECRET must contain at least 32 characters when SMOS is enabled");
        if (ttlSeconds < 60 || ttlSeconds > 3600) throw new IllegalStateException("SMOS access token TTL must be between 60 and 3600 seconds");
        this.mapper=mapper; this.secret=secret.getBytes(StandardCharsets.UTF_8); this.ttlSeconds=ttlSeconds;
    }
    public long ttlSeconds(){ return ttlSeconds; }

    public String issue(UserEntity user,Set<String> roles,Set<String> permissions) {
        try {
            Instant now=Instant.now(); String jti=UUID.randomUUID().toString();
            Map<String,Object> header=Map.of("alg","HS256","typ","JWT");
            Map<String,Object> payload=new LinkedHashMap<>();
            payload.put("iss","switching-smos"); payload.put("aud","switching-operator-api"); payload.put("jti",jti);
            payload.put("sub",user.getUsername()); payload.put("uid",user.getId());
            if (user.getParticipantId() != null) payload.put("participantId", user.getParticipantId());
            payload.put("roles",roles); payload.put("permissions",permissions);
            payload.put("iat",now.getEpochSecond()); payload.put("nbf",now.minusSeconds(MAX_CLOCK_SKEW_SECONDS).getEpochSecond());
            payload.put("exp",now.plusSeconds(ttlSeconds).getEpochSecond());
            String body=encode(mapper.writeValueAsBytes(header))+"."+encode(mapper.writeValueAsBytes(payload));
            return TOKEN_PREFIX+body+"."+encode(sign(body));
        } catch(Exception ex){ throw new IllegalStateException("Unable to issue SMOS access token",ex); }
    }

    public SmosTokenClaims validate(String token) {
        try {
            if(token==null || !token.startsWith(TOKEN_PREFIX)) throw new IllegalArgumentException("Not a SMOS token");
            String[] parts=token.substring(TOKEN_PREFIX.length()).split("\\.");
            if(parts.length!=3) throw new IllegalArgumentException("Malformed token");
            String body=parts[0]+"."+parts[1];
            if(!MessageDigest.isEqual(sign(body),DECODER.decode(parts[2]))) throw new IllegalArgumentException("Invalid token signature");
            JsonNode header=mapper.readTree(DECODER.decode(parts[0]));
            if(!"HS256".equals(header.path("alg").asText()) || !"JWT".equals(header.path("typ").asText())) throw new IllegalArgumentException("Unsupported token header");
            JsonNode payload=mapper.readTree(DECODER.decode(parts[1]));
            if(!"switching-smos".equals(payload.path("iss").asText()) || !"switching-operator-api".equals(payload.path("aud").asText())) throw new IllegalArgumentException("Invalid token issuer or audience");
            String subject=payload.path("sub").asText(), tokenId=payload.path("jti").asText(); long uid=payload.path("uid").asLong(-1);
            if(subject.isBlank() || tokenId.isBlank() || uid < 0) throw new IllegalArgumentException("Invalid token identity claims");
            Instant now=Instant.now(); Instant issued=Instant.ofEpochSecond(payload.path("iat").asLong()); Instant notBefore=Instant.ofEpochSecond(payload.path("nbf").asLong()); Instant expires=Instant.ofEpochSecond(payload.path("exp").asLong());
            if(issued.isAfter(now.plusSeconds(MAX_CLOCK_SKEW_SECONDS))) throw new IllegalArgumentException("Token issued in the future");
            if(notBefore.isAfter(now.plusSeconds(MAX_CLOCK_SKEW_SECONDS))) throw new IllegalArgumentException("Token not active");
            if(!expires.isAfter(now.minusSeconds(MAX_CLOCK_SKEW_SECONDS))) throw new IllegalArgumentException("Token expired");
            if(expires.isAfter(issued.plusSeconds(ttlSeconds+MAX_CLOCK_SKEW_SECONDS))) throw new IllegalArgumentException("Token lifetime exceeds policy");
            Long participantId = payload.hasNonNull("participantId") ? payload.path("participantId").asLong() : null;
            return new SmosTokenClaims(uid,subject,strings(payload.path("roles")),strings(payload.path("permissions")),tokenId,participantId,issued,expires);
        } catch(IllegalArgumentException ex){ throw ex; }
        catch(Exception ex){ throw new IllegalArgumentException("Invalid SMOS token",ex); }
    }
    private byte[] sign(String body)throws Exception{ Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256")); return mac.doFinal(body.getBytes(StandardCharsets.US_ASCII)); }
    private static String encode(byte[] bytes){ return ENCODER.encodeToString(bytes); }
    private static Set<String> strings(JsonNode node){ Set<String> values=new LinkedHashSet<>(); if(node.isArray()) node.forEach(v->values.add(v.asText())); return Set.copyOf(values); }
}

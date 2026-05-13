package com.payments.merchant_service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ApiKeyGenerator {

    public String generateApiKey() {
        final String API_KEY_PREFIX = "sk_live";
        String randomPart = UUID.randomUUID().toString().replace("-","");
        return API_KEY_PREFIX + randomPart;
    }

    public String hashApiKey(String rawApiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawApiKey.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean verify(String rawApiKey, String hash) {
        return hashApiKey(rawApiKey).equals(hash);
    }
}

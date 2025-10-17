package org.qbitspark.nexgatenotificationserver.security;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Slf4j
public class HmacUtils {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    // Generate HMAC signature: timestamp + body + url
    public static String generateSignature(String timestamp, String body, String url, String secretKey) {
        try {
            String message = timestamp + body + url;

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);

            byte[] hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("HMAC algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Invalid secret key", e);
        }
    }
}
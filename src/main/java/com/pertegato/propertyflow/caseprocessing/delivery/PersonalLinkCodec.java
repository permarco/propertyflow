package com.pertegato.propertyflow.caseprocessing.delivery;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Keeps restoration of personal links at the delivery boundary. Callers must check access validity before restoration.
 */
@Component
@Profile("propertyflow-backend")
public final class PersonalLinkCodec {

    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_CHARACTERS = 43;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int ENCRYPTED_BYTES = NONCE_BYTES + TOKEN_CHARACTERS + TAG_BITS / Byte.SIZE;
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final String INVALID_KEY = "A valid personal-link encryption key is required.";
    private static final String INVALID_LINK = "A valid personal link and access reference are required.";
    private static final String CRYPTO_FAILURE = "The personal link could not be processed.";

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public PersonalLinkCodec(@Value("${propertyflow.links.encryption-key}") String base64Key) {
        if (base64Key == null) {
            throw new IllegalArgumentException(INVALID_KEY);
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(INVALID_KEY);
        }
        try {
            if (decoded.length != TOKEN_BYTES) {
                throw new IllegalArgumentException(INVALID_KEY);
            }
            key = new SecretKeySpec(decoded, "AES");
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        if (!isWellFormed(token)) {
            throw new IllegalArgumentException(INVALID_LINK);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(CRYPTO_FAILURE);
        }
    }

    public String encrypt(UUID accessId, String token) {
        if (accessId == null || !isWellFormed(token)) {
            throw new IllegalArgumentException(INVALID_LINK);
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, accessId, nonce);
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.US_ASCII));
            byte[] envelope = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, envelope, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(CRYPTO_FAILURE);
        }
    }

    public String restore(UUID accessId, String encryptedToken) {
        if (accessId == null || encryptedToken == null) {
            throw new IllegalStateException(CRYPTO_FAILURE);
        }
        try {
            byte[] envelope = Base64.getDecoder().decode(encryptedToken);
            if (envelope.length != ENCRYPTED_BYTES) {
                throw new IllegalStateException(CRYPTO_FAILURE);
            }
            byte[] nonce = Arrays.copyOf(envelope, NONCE_BYTES);
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, accessId, nonce);
            byte[] plaintext = cipher.doFinal(envelope, NONCE_BYTES, envelope.length - NONCE_BYTES);
            try {
                String token = new String(plaintext, StandardCharsets.US_ASCII);
                if (!isWellFormed(token)) {
                    throw new IllegalStateException(CRYPTO_FAILURE);
                }
                return token;
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException(CRYPTO_FAILURE);
        }
    }

    public boolean isWellFormed(String token) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            return false;
        }
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        return decoded.length == TOKEN_BYTES
                && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(token);
    }

    private Cipher cipher(int mode, UUID accessId, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(accessId.toString().getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}

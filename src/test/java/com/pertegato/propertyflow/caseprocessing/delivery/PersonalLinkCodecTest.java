package com.pertegato.propertyflow.caseprocessing.delivery;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalLinkCodecTest {

    private static final String TOKEN = "A".repeat(43);
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final UUID ACCESS_ID = UUID.fromString("f7ab611f-047f-4e79-94d0-dfc75aa07874");
    private static final String CRYPTO_FAILURE = "The personal link could not be processed.";

    private final PersonalLinkCodec codec = new PersonalLinkCodec(KEY);

    @Test
    void generatedTokensHaveCanonicalUrlSafeFormatAndAreDistinct() {
        Set<String> tokens = new HashSet<>();
        for (int index = 0; index < 256; index++) {
            String token = codec.generateToken();
            assertEquals(43, token.length());
            assertEquals(32, Base64.getUrlDecoder().decode(token).length);
            assertTrue(codec.isWellFormed(token));
            assertTrue(tokens.add(token));
        }
    }

    @Test
    void rejectsInvalidFormatAndNonCanonicalUnusedBits() {
        assertTrue(codec.isWellFormed(TOKEN));
        String[] invalidTokens = {
                null,
                "",
                "A".repeat(42),
                "A".repeat(44),
                TOKEN + "=",
                " " + TOKEN,
                "+" + "A".repeat(42),
                "/" + "A".repeat(42),
                "ä" + "A".repeat(42),
                "A".repeat(42) + "B"
        };
        for (String token : invalidTokens) {
            assertFalse(codec.isWellFormed(token));
        }
        assertTrue(Arrays.equals(Base64.getUrlDecoder().decode(TOKEN),
                Base64.getUrlDecoder().decode("A".repeat(42) + "B")));
    }

    @Test
    void hashesCanonicalTokensUsingStableSha256() {
        String expected = "0f007385b6f9d4b7eeb2748605afe1a984a0a3bfa3f014d09e2a784ce9e5cd1a";
        assertEquals(expected, codec.hash(TOKEN));
        assertEquals(expected, new PersonalLinkCodec(KEY).hash(TOKEN));
        assertThrows(IllegalArgumentException.class, () -> codec.hash("invalid"));
    }

    @Test
    void restoresTokenAcrossCodecInstancesWithSameKey() {
        String encrypted = codec.encrypt(ACCESS_ID, TOKEN);
        assertNotEquals(TOKEN, encrypted);
        assertEquals(71, Base64.getDecoder().decode(encrypted).length);
        assertEquals(TOKEN, new PersonalLinkCodec(KEY).restore(ACCESS_ID, encrypted));
    }

    @Test
    void repeatedEncryptionUsesIndependentNonces() {
        String first = codec.encrypt(ACCESS_ID, TOKEN);
        String second = codec.encrypt(ACCESS_ID, TOKEN);
        assertNotEquals(first, second);
        assertFalse(Arrays.equals(Arrays.copyOf(Base64.getDecoder().decode(first), 12),
                Arrays.copyOf(Base64.getDecoder().decode(second), 12)));
        assertEquals(TOKEN, codec.restore(ACCESS_ID, first));
        assertEquals(TOKEN, codec.restore(ACCESS_ID, second));
    }

    @Test
    void rejectsWrongKeyAndCopiedCiphertextUnderDifferentAccessId() {
        String encrypted = codec.encrypt(ACCESS_ID, TOKEN);
        byte[] otherKey = new byte[32];
        Arrays.fill(otherKey, (byte) 1);
        PersonalLinkCodec otherCodec = new PersonalLinkCodec(Base64.getEncoder().encodeToString(otherKey));
        assertNeutralFailure(() -> otherCodec.restore(ACCESS_ID, encrypted));
        assertNeutralFailure(() -> codec.restore(UUID.randomUUID(), encrypted));
    }

    @Test
    void detectsTamperingInNonceCiphertextAndAuthenticationTag() {
        byte[] envelope = Base64.getDecoder().decode(codec.encrypt(ACCESS_ID, TOKEN));
        for (int position : new int[] {0, 12, envelope.length - 1}) {
            byte[] corrupted = envelope.clone();
            corrupted[position] ^= 1;
            String tampered = Base64.getEncoder().encodeToString(corrupted);
            assertNeutralFailure(() -> codec.restore(ACCESS_ID, tampered));
        }
    }

    @Test
    void rejectsMissingMalformedAndTruncatedStoredCopiesWithoutLeakingInput() {
        String encrypted = codec.encrypt(ACCESS_ID, TOKEN);
        String[] invalidCopies = {
                null,
                "",
                "invalid " + TOKEN,
                Base64.getEncoder().encodeToString(new byte[12]),
                encrypted.substring(0, encrypted.length() - 4)
        };
        for (String copy : invalidCopies) {
            assertNeutralFailure(() -> codec.restore(ACCESS_ID, copy));
        }
        assertNeutralFailure(() -> codec.restore(null, encrypted));
    }

    @Test
    void requiresAnExactly256BitBase64KeyWithoutLeakingConfiguration() {
        String[] invalidKeys = {
                null,
                "",
                "secret-invalid-base64",
                Base64.getEncoder().encodeToString(new byte[16]),
                Base64.getEncoder().encodeToString(new byte[24]),
                Base64.getEncoder().encodeToString(new byte[31]),
                Base64.getEncoder().encodeToString(new byte[33])
        };
        for (String key : invalidKeys) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new PersonalLinkCodec(key));
            assertEquals("A valid personal-link encryption key is required.", exception.getMessage());
            assertNull(exception.getCause());
        }
        assertFalse(codec.toString().contains(KEY));
        assertFalse(codec.toString().contains(TOKEN));
    }

    @Test
    void encryptionRejectsMissingIdentityAndInvalidToken() {
        assertThrows(IllegalArgumentException.class, () -> codec.encrypt(null, TOKEN));
        assertThrows(IllegalArgumentException.class, () -> codec.encrypt(ACCESS_ID, "invalid"));
        assertThrows(IllegalArgumentException.class, () -> codec.encrypt(ACCESS_ID, null));
    }

    private void assertNeutralFailure(org.junit.jupiter.api.function.Executable operation) {
        IllegalStateException exception = assertThrows(IllegalStateException.class, operation);
        assertEquals(CRYPTO_FAILURE, exception.getMessage());
        assertNull(exception.getCause());
    }
}

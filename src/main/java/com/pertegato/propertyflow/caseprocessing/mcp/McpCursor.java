package com.pertegato.propertyflow.caseprocessing.mcp;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class McpCursor {

    private static final String HMAC = "HmacSHA256";
    private final SecretKeySpec key;

    McpCursor() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        this.key = new SecretKeySpec(secret, HMAC);
    }

    String issue(McpAccountContext account, String query, int offset) {
        byte[] position = ByteBuffer.allocate(Integer.BYTES).putInt(offset).array();
        byte[] signature = sign(account, query, position);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(ByteBuffer.allocate(position.length + signature.length).put(position).put(signature).array());
    }

    int offset(McpAccountContext account, String query, String cursor) {
        if (cursor == null) {
            return 0;
        }
        if (cursor.length() != 48) {
            throw new IllegalArgumentException("Ungültiger Fortsetzungswert.");
        }
        byte[] value = Base64.getUrlDecoder().decode(cursor);
        byte[] position = Arrays.copyOf(value, Integer.BYTES);
        int offset = ByteBuffer.wrap(position).getInt();
        if (offset < 0 || !MessageDigest.isEqual(Arrays.copyOfRange(value, Integer.BYTES, value.length), sign(account, query, position))) {
            throw new IllegalArgumentException("Ungültiger Fortsetzungswert.");
        }
        return offset;
    }

    private byte[] sign(McpAccountContext account, String query, byte[] position) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            mac.update((account.delegationId() + ":" + query).getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(position);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Der Fortsetzungswert kann nicht geprüft werden.", exception);
        }
    }
}

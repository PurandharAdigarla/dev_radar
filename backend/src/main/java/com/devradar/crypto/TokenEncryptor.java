package com.devradar.crypto;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryptor for short secrets (GitHub access tokens).
 * Output format (base64): [12-byte IV][ciphertext+16-byte GCM tag]
 */
@Component
public class TokenEncryptor {

    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    private final SecretKey key;

    public TokenEncryptor(@Qualifier("githubTokenKey") SecretKey key) {
        this.key = key;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(IV_BYTES + ct.length);
            buf.put(iv).put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new RuntimeException("encrypt failed", e);
        }
    }

    public String decrypt(String ciphertextB64) {
        try {
            byte[] all = Base64.getDecoder().decode(ciphertextB64);
            byte[] iv = new byte[IV_BYTES];
            byte[] ct = new byte[all.length - IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            System.arraycopy(all, IV_BYTES, ct, 0, ct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("decrypt failed", e);
        }
    }
}

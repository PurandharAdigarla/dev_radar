package com.devradar.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEncryptorTest {

    private final TokenEncryptor enc = new TokenEncryptor(
        new SecretKeySpec(Base64.getDecoder().decode("UEjGRJjRBYGZQRdsB7Cln1mLG0qlxPEAU+Vq/Sx0iYE="), "AES")
    );

    @Test
    void encryptDecrypt_roundTrip() {
        String secret = "ghp_abcdef1234567890ABCDEF";
        String ct = enc.encrypt(secret);
        assertThat(ct).isNotEqualTo(secret);
        assertThat(enc.decrypt(ct)).isEqualTo(secret);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        String pt = "same-plaintext";
        String c1 = enc.encrypt(pt);
        String c2 = enc.encrypt(pt);
        assertThat(c1).isNotEqualTo(c2);
        assertThat(enc.decrypt(c1)).isEqualTo(pt);
        assertThat(enc.decrypt(c2)).isEqualTo(pt);
    }

    @Test
    void decrypt_tamperedCiphertext_throws() {
        String ct = enc.encrypt("secret");
        byte[] bytes = Base64.getDecoder().decode(ct);
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> enc.decrypt(tampered));
    }
}

package com.mathio.lms.model;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class Secrets {
    public static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] key() {
        try {
            byte[] encryptionKey =
                    Base64.getDecoder().decode(Environment.get("APP_ENCRYPTION_KEY", ""));
            if (encryptionKey.length != 32) throw new Exception();
            return encryptionKey;
        } catch (Exception e) {
            throw new ApiException(503, "Chưa cấu hình APP_ENCRYPTION_KEY (32 bytes base64).");
        }
    }

    public static String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key(), "AES"),
                    new GCMParameterSpec(128, iv));
            return Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder()
                            .encodeToString(
                                    cipher.doFinal(
                                            value.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public static String decrypt(String value) {
        try {
            String[] parts = value.split(":");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key(), "AES"),
                    new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(
                    cipher.doFinal(Base64.getDecoder().decode(parts[1])),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(503, "Không thể giải mã cấu hình AI.");
        }
    }

    private Secrets() {}
}

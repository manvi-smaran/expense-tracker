package com.expensetracker.server;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Password hashing (PBKDF2, JDK built-in) and opaque bearer tokens. */
public final class Security {
    private static final int ITERATIONS = 210_000;   // OWASP guidance for PBKDF2-HMAC-SHA256
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Verified against when the email is unknown, so timing doesn't reveal which emails exist. */
    private static final String DUMMY_HASH = hashPassword("not-a-real-password");

    private Security() {}

    public static String hashPassword(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + b64(salt) + "$" + b64(hash);
    }

    public static boolean verifyPassword(String password, String stored) {
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
            return false;
        }
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = pbkdf2(password, Base64.getDecoder().decode(parts[2]),
                Integer.parseInt(parts[1]));
        return MessageDigest.isEqual(expected, actual);
    }

    public static void burnTime(String password) {
        verifyPassword(password, DUMMY_HASH);
    }

    /** 256 random bits, URL-safe. Only its SHA-256 is stored, so a DB leak can't be replayed. */
    public static String newToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}

package com.aceos.security;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class Passwords {
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {}

    public static void validate(String password) {
        if (password == null
                || password.codePointCount(0, password.length()) < 15
                || password.codePointCount(0, password.length()) > 128)
            throw new IllegalArgumentException("Use a password of 15–128 characters");
        if (password.codePoints().anyMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF))
            throw new IllegalArgumentException("Password contains an invalid Unicode character");
    }

    public static String temporarySecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String password) {
        validate(password);
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] output = derive(password, salt);
        return "$argon2id$v=19$m=19456,t=2,p=1$"
                + Base64.getEncoder().withoutPadding().encodeToString(salt)
                + "$"
                + Base64.getEncoder().withoutPadding().encodeToString(output);
    }

    public static boolean matches(String password, String encoded) {
        if (password == null || password.length() > 512 || encoded == null) return false;
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 6
                    || !parts[1].equals("argon2id")
                    || !parts[2].equals("v=19")
                    || !parts[3].equals("m=19456,t=2,p=1")) return false;
            byte[] actual = derive(password, Base64.getDecoder().decode(parts[4]));
            boolean match = MessageDigest.isEqual(actual, Base64.getDecoder().decode(parts[5]));
            Arrays.fill(actual, (byte) 0);
            return match;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt) {
        if (password.codePoints().anyMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF))
            throw new IllegalArgumentException("Invalid Unicode");
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(
                new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                        .withMemoryAsKB(19456)
                        .withIterations(2)
                        .withParallelism(1)
                        .withSalt(salt)
                        .build());
        byte[] input = password.getBytes(StandardCharsets.UTF_8), output = new byte[32];
        try {
            generator.generateBytes(input, output);
            return output;
        } finally {
            Arrays.fill(input, (byte) 0);
        }
    }
}

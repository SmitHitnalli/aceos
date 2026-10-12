package com.aceos.security;

public final class LegacyVerificationGuard {
    private LegacyVerificationGuard() {}

    public static void retired() {
        throw new IllegalStateException(
                "v1 verification entry points are retired. Run mvn verify with the isolated"
                    + " aceos_next_test configuration.");
    }
}

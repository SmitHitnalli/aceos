package com.aceos.security;

import static org.junit.jupiter.api.Assertions.*;

import com.aceos.model.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class PasswordsTest {
    @Test
    void saltedHashesAndLongPassphrases() {
        String text = "A long private passphrase " + "界".repeat(60);
        String first = Passwords.hash(text), second = Passwords.hash(text);
        assertNotEquals(first, second);
        assertTrue(Passwords.matches(text, first));
        assertFalse(Passwords.matches(text + "!", first));
        assertFalse(Passwords.matches(text, "broken"));
        assertThrows(IllegalArgumentException.class, () -> Passwords.hash("short"));
        assertThrows(IllegalArgumentException.class, () -> Passwords.hash("a".repeat(129)));
    }

    @Test
    void sessionsExpireAndRevoke() {
        Session session =
                new Session(
                        new User(1, "Local", "local@example.org", UserRole.REQUESTER), 1, false);
        assertTrue(session.validAt(Instant.now()));
        assertFalse(session.validAt(Instant.now().plusSeconds(1801)));
        session.revoke();
        assertFalse(session.validAt(Instant.now()));
    }

    @Test
    void temporaryCredentialsDoNotPrintSecrets() {
        IssuedCredential result = new IssuedCredential(1, "SECRET", Instant.now());
        assertFalse(result.toString().contains("SECRET"));
        assertEquals(32, Passwords.temporarySecret().length());
    }
}

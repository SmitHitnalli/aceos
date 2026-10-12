package com.aceos;

import com.aceos.model.User;
import com.aceos.model.UserRole;
import com.aceos.repository.UserRepository;

import java.sql.SQLException;

public class UserRepositoryTest {

    public static void main(String[] args) throws SQLException {
        com.aceos.security.LegacyVerificationGuard.retired();
        UserRepository repository = new UserRepository();

        String uniqueEmail =
                "repository-test-" + System.currentTimeMillis() + "@aceos.test";

        User unsavedUser = new User(
                "Repository Test User",
                uniqueEmail,
                UserRole.REQUESTER
        );

        User savedUser = repository.save(unsavedUser);

        System.out.println("Generated ID: " + savedUser.getUserId());

        User loadedUser = repository
                .findById(savedUser.getUserId())
                .orElseThrow(() -> new IllegalStateException(
                        "The saved user could not be retrieved"
                ));

        System.out.println("Loaded name: " + loadedUser.getName());
        System.out.println("Loaded email: " + loadedUser.getEmail());
        System.out.println("Loaded role: " + loadedUser.getRole());
    }
}
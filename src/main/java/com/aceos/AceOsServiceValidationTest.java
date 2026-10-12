package com.aceos;

import com.aceos.model.ComplaintPriority;
import com.aceos.model.UserRole;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

public class AceOsServiceValidationTest {

    public static void main(String[] args)
            throws AceOsServiceException {
        com.aceos.security.LegacyVerificationGuard.retired();
        AceOsService service = new AceOsService();

        expectFailure(
                "User name is required",
                () -> service.registerUser(
                        " ",
                        "valid@aceos.test",
                        UserRole.REQUESTER
                )
        );

        expectFailure(
                "Email must be in a valid format",
                () -> service.registerUser(
                        "Validation User",
                        "invalid-email",
                        UserRole.REQUESTER
                )
        );

        expectFailure(
                "Asset code must not exceed 50 characters",
                () -> service.registerAsset(
                        "A".repeat(51),
                        "Test Asset",
                        "Test Category",
                        "Test Location"
                )
        );

        expectFailure(
                "Asset ID must be positive",
                () -> service.getAsset(0)
        );

        expectFailure(
                "Complaint priority is required",
                () -> service.submitComplaint(
                        1,
                        1,
                        "Valid complaint description",
                        null
                )
        );

        expectFailure(
                "Maintenance action is required",
                () -> service.completeWorkOrder(
                        1,
                        " "
                )
        );

        String uniqueEmail =
                "validation-"
                        + System.currentTimeMillis()
                        + "@aceos.test";

        service.registerUser(
                "Duplicate Validation User",
                uniqueEmail,
                UserRole.REQUESTER
        );

        expectFailure(
                "A record with the same unique value already exists",
                () -> service.registerUser(
                        "Duplicate Validation User",
                        uniqueEmail,
                        UserRole.REQUESTER
                )
        );

        expectFailure(
                "User not found: 2000000000",
                () -> service.getUser(2_000_000_000)
        );

        System.out.println(
                "AceOS service validation verification passed"
        );
        System.out.println(
                "Local validation cases passed: 6"
        );
        System.out.println(
                "Database error translation cases passed: 2"
        );
    }

    private static void expectFailure(
            String expectedMessage,
            ServiceOperation operation) {
        try {
            operation.execute();

            throw new IllegalStateException(
                    "Expected failure: " + expectedMessage
            );
        } catch (AceOsServiceException exception) {
            if (!expectedMessage.equals(exception.getMessage())) {
                throw new IllegalStateException(
                        "Expected message: "
                                + expectedMessage
                                + ", but received: "
                                + exception.getMessage(),
                        exception
                );
            }
        }
    }

    @FunctionalInterface
    private interface ServiceOperation {

        void execute() throws AceOsServiceException;
    }
}
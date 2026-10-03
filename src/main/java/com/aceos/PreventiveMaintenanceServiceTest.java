package com.aceos;

import com.aceos.model.Asset;
import com.aceos.model.MaintenanceScheduleStatus;
import com.aceos.model.PreventiveMaintenanceSchedule;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

import java.time.LocalDate;

public class PreventiveMaintenanceServiceTest {

    public static void main(String[] args)
            throws AceOsServiceException {

        AceOsService service = new AceOsService();

        expectFailure(
                "Asset ID must be positive",
                () -> service.createPreventiveMaintenanceSchedule(
                        0,
                        "Inspect equipment",
                        30,
                        LocalDate.now().plusDays(7)
                )
        );

        expectFailure(
                "Maintenance task is required",
                () -> service.createPreventiveMaintenanceSchedule(
                        1,
                        " ",
                        30,
                        LocalDate.now().plusDays(7)
                )
        );

        expectFailure(
                "Frequency must be greater than zero",
                () -> service.createPreventiveMaintenanceSchedule(
                        1,
                        "Inspect equipment",
                        0,
                        LocalDate.now().plusDays(7)
                )
        );

        expectFailure(
                "Next due date cannot be in the past",
                () -> service.createPreventiveMaintenanceSchedule(
                        1,
                        "Inspect equipment",
                        30,
                        LocalDate.now().minusDays(1)
                )
        );

        Asset asset = service.registerAsset(
                "SERVICE-SCHEDULE-TEST-"
                        + System.currentTimeMillis(),
                "Service Test Generator",
                "Generator",
                "Test Utility Room"
        );

        LocalDate dueDate = LocalDate.now().plusDays(30);

        PreventiveMaintenanceSchedule schedule =
                service.createPreventiveMaintenanceSchedule(
                        asset.getAssetId(),
                        "  Inspect generator belts  ",
                        60,
                        dueDate
                );

        require(
                schedule.getMaintenanceScheduleId() > 0,
                "MySQL did not generate a schedule ID"
        );

        require(
                schedule.getAsset().getAssetId()
                        == asset.getAssetId(),
                "The scheduled asset does not match"
        );

        require(
                schedule.getTaskDescription()
                        .equals("Inspect generator belts"),
                "The task description was not normalized"
        );

        require(
                schedule.getFrequencyDays() == 60,
                "The schedule frequency does not match"
        );

        require(
                schedule.getNextDueDate().equals(dueDate),
                "The next due date does not match"
        );

        require(
                schedule.getStatus()
                        == MaintenanceScheduleStatus.ACTIVE,
                "A new schedule must be active"
        );

        System.out.println(
                "Preventive maintenance service verification passed"
        );
        System.out.println(
                "Generated schedule ID: "
                        + schedule.getMaintenanceScheduleId()
        );
        System.out.println(
                "Scheduled asset ID: "
                        + schedule.getAsset().getAssetId()
        );
        System.out.println(
                "Next due date: "
                        + schedule.getNextDueDate()
        );
        System.out.println(
                "Validation cases passed: 4"
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

    private static void require(
            boolean condition,
            String message) {

        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @FunctionalInterface
    private interface ServiceOperation {

        void execute() throws AceOsServiceException;
    }
}
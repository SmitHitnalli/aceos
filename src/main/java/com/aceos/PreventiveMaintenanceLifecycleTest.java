package com.aceos;

import com.aceos.model.Asset;
import com.aceos.model.MaintenanceScheduleStatus;
import com.aceos.model.PreventiveMaintenanceSchedule;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

import java.time.LocalDate;

public class PreventiveMaintenanceLifecycleTest {

    public static void main(String[] args)
            throws AceOsServiceException {

        AceOsService service = new AceOsService();

        Asset asset = service.registerAsset(
                "SCHEDULE-LIFECYCLE-"
                        + System.currentTimeMillis(),
                "Lifecycle Test Generator",
                "Generator",
                "Test Plant Room"
        );

        PreventiveMaintenanceSchedule created =
                service.createPreventiveMaintenanceSchedule(
                        asset.getAssetId(),
                        "Inspect generator coolant",
                        30,
                        LocalDate.now().plusDays(5)
                );

        int scheduleId =
                created.getMaintenanceScheduleId();

        PreventiveMaintenanceSchedule paused =
                service.pausePreventiveMaintenanceSchedule(
                        scheduleId
                );

        require(
                paused.getStatus()
                        == MaintenanceScheduleStatus.PAUSED,
                "The schedule was not paused"
        );

        expectFailure(
                "Only an active schedule can be paused",
                () -> service
                        .pausePreventiveMaintenanceSchedule(
                                scheduleId
                        )
        );

        expectFailure(
                "A paused schedule cannot be completed",
                () -> service
                        .completePreventiveMaintenanceSchedule(
                                scheduleId,
                                LocalDate.now()
                        )
        );

        PreventiveMaintenanceSchedule activated =
                service.activatePreventiveMaintenanceSchedule(
                        scheduleId
                );

        require(
                activated.getStatus()
                        == MaintenanceScheduleStatus.ACTIVE,
                "The schedule was not activated"
        );

        expectFailure(
                "Only a paused schedule can be activated",
                () -> service
                        .activatePreventiveMaintenanceSchedule(
                                scheduleId
                        )
        );

        expectFailure(
                "Completion date cannot be in the future",
                () -> service
                        .completePreventiveMaintenanceSchedule(
                                scheduleId,
                                LocalDate.now().plusDays(1)
                        )
        );

        LocalDate completionDate = LocalDate.now();

        PreventiveMaintenanceSchedule completed =
                service.completePreventiveMaintenanceSchedule(
                        scheduleId,
                        completionDate
                );

        LocalDate expectedNextDueDate =
                completionDate.plusDays(30);

        require(
                completed.getNextDueDate()
                        .equals(expectedNextDueDate),
                "Completion did not calculate "
                        + "the next due date correctly"
        );

        PreventiveMaintenanceSchedule loaded =
                service.getPreventiveMaintenanceSchedule(
                        scheduleId
                );

        require(
                loaded.getStatus()
                        == MaintenanceScheduleStatus.ACTIVE,
                "The persisted status does not match"
        );

        require(
                loaded.getNextDueDate()
                        .equals(expectedNextDueDate),
                "The persisted due date does not match"
        );

        System.out.println(
                "Preventive maintenance lifecycle "
                        + "verification passed"
        );
        System.out.println(
                "Schedule ID: " + scheduleId
        );
        System.out.println(
                "Final status: " + loaded.getStatus()
        );
        System.out.println(
                "Next due date: "
                        + loaded.getNextDueDate()
        );
        System.out.println(
                "Protected invalid operations: 4"
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
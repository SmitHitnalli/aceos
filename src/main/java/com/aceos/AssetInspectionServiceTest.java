package com.aceos;

import com.aceos.model.Asset;
import com.aceos.model.AssetInspection;
import com.aceos.model.InspectionStatus;
import com.aceos.model.User;
import com.aceos.model.UserRole;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

import java.time.LocalDate;
import java.util.List;

public class AssetInspectionServiceTest {

    public static void main(String[] args)
            throws AceOsServiceException {
        com.aceos.security.LegacyVerificationGuard.retired();
        AceOsService service = new AceOsService();

        expectFailure(
                "Asset ID must be positive",
                () -> service.scheduleAssetInspection(
                        0,
                        "Electrical safety",
                        LocalDate.now().plusDays(1)
                )
        );
        expectFailure(
                "Inspection type is required",
                () -> service.scheduleAssetInspection(
                        1,
                        " ",
                        LocalDate.now().plusDays(1)
                )
        );
        expectFailure(
                "Scheduled date cannot be in the past",
                () -> service.scheduleAssetInspection(
                        1,
                        "Electrical safety",
                        LocalDate.now().minusDays(1)
                )
        );
        expectFailure(
                "Inspection result must be PASSED or FAILED",
                () -> service.completeAssetInspection(
                        1,
                        1,
                        InspectionStatus.SCHEDULED,
                        "Valid notes",
                        LocalDate.now()
                )
        );

        long uniqueValue = System.currentTimeMillis();

        Asset asset = service.registerAsset(
                "INSPECTION-TEST-" + uniqueValue,
                "Inspection Test Lift",
                "Lift",
                "Test Academic Block"
        );
        User technician = service.registerUser(
                "Inspection Technician",
                "inspection-technician-"
                        + uniqueValue
                        + "@aceos.test",
                UserRole.TECHNICIAN
        );
        User requester = service.registerUser(
                "Inspection Requester",
                "inspection-requester-"
                        + uniqueValue
                        + "@aceos.test",
                UserRole.REQUESTER
        );

        LocalDate scheduledDate =
                LocalDate.now().plusDays(1);

        AssetInspection scheduled =
                service.scheduleAssetInspection(
                        asset.getAssetId(),
                        "Lift safety inspection",
                        scheduledDate
                );

        require(
                scheduled.getInspectionId() > 0,
                "MySQL did not generate an inspection ID"
        );
        require(
                scheduled.getStatus()
                        == InspectionStatus.SCHEDULED,
                "A new inspection was not scheduled"
        );
        require(
                !scheduled.isOverdueOn(LocalDate.now()),
                "The inspection became overdue too early"
        );

        expectFailure(
                "Only a technician can complete an inspection",
                () -> service.completeAssetInspection(
                        scheduled.getInspectionId(),
                        requester.getUserId(),
                        InspectionStatus.PASSED,
                        "Requester must not complete this",
                        LocalDate.now()
                )
        );

        AssetInspection passed =
                service.completeAssetInspection(
                        scheduled.getInspectionId(),
                        technician.getUserId(),
                        InspectionStatus.PASSED,
                        "  All safety checks passed  ",
                        LocalDate.now()
                );

        require(
                passed.getStatus() == InspectionStatus.PASSED,
                "The inspection did not pass"
        );
        require(
                passed.isCompliant(),
                "A passed inspection was not compliant"
        );
        require(
                passed.getInspectedBy().getUserId()
                        == technician.getUserId(),
                "The inspector was not preserved"
        );
        require(
                passed.getNotes().equals(
                        "All safety checks passed"
                ),
                "The notes were not normalized"
        );

        expectFailure(
                "Only a scheduled inspection can be completed",
                () -> service.completeAssetInspection(
                        passed.getInspectionId(),
                        technician.getUserId(),
                        InspectionStatus.PASSED,
                        "Repeated completion",
                        LocalDate.now()
                )
        );

        AssetInspection failedScheduled =
                service.scheduleAssetInspection(
                        asset.getAssetId(),
                        "Emergency alarm inspection",
                        scheduledDate
                );
        AssetInspection failed =
                service.completeAssetInspection(
                        failedScheduled.getInspectionId(),
                        technician.getUserId(),
                        InspectionStatus.FAILED,
                        "Alarm did not activate",
                        LocalDate.now()
                );

        require(
                failed.getStatus() == InspectionStatus.FAILED,
                "The failed result was not persisted"
        );
        require(
                !failed.isCompliant(),
                "A failed inspection was marked compliant"
        );

        AssetInspection loaded =
                service.getAssetInspection(
                        passed.getInspectionId()
                );
        require(
                loaded.getStatus() == InspectionStatus.PASSED,
                "The retrieved result does not match"
        );

        List<AssetInspection> inspections =
                service.getAllAssetInspections();
        require(
                inspections.stream().anyMatch(
                        inspection -> inspection.getInspectionId()
                                == passed.getInspectionId()
                ),
                "The passed inspection was missing from the list"
        );
        require(
                inspections.stream().anyMatch(
                        inspection -> inspection.getInspectionId()
                                == failed.getInspectionId()
                ),
                "The failed inspection was missing from the list"
        );

        System.out.println(
                "Asset inspection service verification passed"
        );
        System.out.println(
                "Passed inspection ID: "
                        + passed.getInspectionId()
        );
        System.out.println(
                "Failed inspection ID: "
                        + failed.getInspectionId()
        );
        System.out.println(
                "Protected invalid operations: 6"
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

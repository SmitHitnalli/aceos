package com.aceos;

import com.aceos.model.Asset;
import com.aceos.model.AssetStatus;
import com.aceos.model.MaintenanceScheduleStatus;
import com.aceos.model.PreventiveMaintenanceSchedule;
import com.aceos.repository.AssetRepository;
import com.aceos.repository.PreventiveMaintenanceScheduleRepository;

import java.sql.SQLException;
import java.time.LocalDate;

public class PreventiveMaintenanceScheduleRepositoryTest {

    public static void main(String[] args) throws SQLException {
        AssetRepository assetRepository =
                new AssetRepository();

        PreventiveMaintenanceScheduleRepository
                scheduleRepository =
                new PreventiveMaintenanceScheduleRepository();

        Asset asset = assetRepository.save(
                new Asset(
                        "PREVENTIVE-TEST-"
                                + System.currentTimeMillis(),
                        "Preventive Test Air Conditioner",
                        "Air Conditioner",
                        "Test Building, Room 201",
                        AssetStatus.ACTIVE
                )
        );

        LocalDate dueDate = LocalDate.now().plusDays(7);

        PreventiveMaintenanceSchedule savedSchedule =
                scheduleRepository.save(
                        new PreventiveMaintenanceSchedule(
                                asset,
                                "Clean air filters",
                                90,
                                dueDate
                        )
                );

        require(
                savedSchedule.getMaintenanceScheduleId() > 0,
                "MySQL did not generate a schedule ID"
        );

        PreventiveMaintenanceSchedule loadedSchedule =
                scheduleRepository
                        .findById(
                                savedSchedule
                                        .getMaintenanceScheduleId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "The saved schedule "
                                                + "could not be retrieved"
                                )
                        );

        require(
                loadedSchedule.getAsset().getAssetId()
                        == asset.getAssetId(),
                "The retrieved asset does not match"
        );

        require(
                loadedSchedule.getTaskDescription()
                        .equals("Clean air filters"),
                "The task description does not match"
        );

        require(
                loadedSchedule.getFrequencyDays() == 90,
                "The frequency does not match"
        );

        require(
                loadedSchedule.getNextDueDate()
                        .equals(dueDate),
                "The due date does not match"
        );

        require(
                loadedSchedule.getStatus()
                        == MaintenanceScheduleStatus.ACTIVE,
                "The schedule status does not match"
        );

        require(
                !loadedSchedule.isDueOn(
                        dueDate.minusDays(1)
                ),
                "The schedule became due too early"
        );

        require(
                loadedSchedule.isDueOn(dueDate),
                "The schedule was not due on its due date"
        );

        System.out.println(
                "Preventive maintenance schedule "
                        + "repository verification passed"
        );
        System.out.println(
                "Generated schedule ID: "
                        + loadedSchedule
                                .getMaintenanceScheduleId()
        );
        System.out.println(
                "Related asset ID: "
                        + loadedSchedule
                                .getAsset()
                                .getAssetId()
        );
        System.out.println(
                "Next due date: "
                        + loadedSchedule.getNextDueDate()
        );
        System.out.println(
                "Status: "
                        + loadedSchedule.getStatus()
        );
    }

    private static void require(
            boolean condition,
            String message) {

        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
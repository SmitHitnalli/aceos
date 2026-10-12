package com.aceos;

import com.aceos.model.Asset;
import com.aceos.model.AssetStatus;
import com.aceos.model.Complaint;
import com.aceos.model.ComplaintPriority;
import com.aceos.model.ComplaintStatus;
import com.aceos.model.User;
import com.aceos.model.UserRole;
import com.aceos.repository.AssetRepository;
import com.aceos.repository.ComplaintRepository;
import com.aceos.repository.UserRepository;

import java.sql.SQLException;

public class ComplaintRepositoryTest {

    public static void main(String[] args) throws SQLException {
        com.aceos.security.LegacyVerificationGuard.retired();
        AssetRepository assetRepository =
                new AssetRepository();
        UserRepository userRepository =
                new UserRepository();
        ComplaintRepository complaintRepository =
                new ComplaintRepository();

        long uniqueValue = System.currentTimeMillis();

        Asset savedAsset = assetRepository.save(
                new Asset(
                        "COMPLAINT-TEST-" + uniqueValue,
                        "Complaint Test Air Conditioner",
                        "Air Conditioner",
                        "Test Building, Room 202",
                        AssetStatus.ACTIVE
                )
        );

        User savedRequester = userRepository.save(
                new User(
                        "Complaint Test Requester",
                        "complaint-test-"
                                + uniqueValue
                                + "@aceos.test",
                        UserRole.REQUESTER
                )
        );

        Complaint unsavedComplaint = new Complaint(
                savedAsset,
                savedRequester,
                "The air conditioner is not cooling the room",
                ComplaintPriority.HIGH
        );

        Complaint savedComplaint =
                complaintRepository.save(unsavedComplaint);

        require(
                savedComplaint.getComplaintId() > 0,
                "MySQL did not generate a valid complaint ID"
        );

        Complaint loadedComplaint = complaintRepository
                .findById(savedComplaint.getComplaintId())
                .orElseThrow(() -> new IllegalStateException(
                        "The saved complaint could not be retrieved"
                ));

        require(
                loadedComplaint.getAsset().getAssetId()
                        == savedAsset.getAssetId(),
                "The retrieved asset ID does not match"
        );

        require(
                loadedComplaint.getAsset().getAssetCode().equals(
                        savedAsset.getAssetCode()
                ),
                "The retrieved asset code does not match"
        );

        require(
                loadedComplaint.getReportedBy().getUserId()
                        == savedRequester.getUserId(),
                "The retrieved requester ID does not match"
        );

        require(
                loadedComplaint.getReportedBy().getEmail().equals(
                        savedRequester.getEmail()
                ),
                "The retrieved requester email does not match"
        );

        require(
                loadedComplaint.getDescription().equals(
                        savedComplaint.getDescription()
                ),
                "The retrieved description does not match"
        );

        require(
                loadedComplaint.getPriority()
                        == ComplaintPriority.HIGH,
                "The retrieved priority does not match"
        );

        require(
                loadedComplaint.getStatus()
                        == ComplaintStatus.SUBMITTED,
                "The retrieved status does not match"
        );

        require(
                loadedComplaint.getReportedAt().equals(
                        savedComplaint.getReportedAt()
                ),
                "The retrieved report time does not match"
        );

        System.out.println(
                "Complaint repository verification passed"
        );
        System.out.println(
                "Generated complaint ID: "
                        + loadedComplaint.getComplaintId()
        );
        System.out.println(
                "Loaded asset: "
                        + loadedComplaint.getAsset().getAssetCode()
        );
        System.out.println(
                "Loaded requester: "
                        + loadedComplaint.getReportedBy().getEmail()
        );
        System.out.println(
                "Loaded priority: "
                        + loadedComplaint.getPriority()
        );
        System.out.println(
                "Loaded status: "
                        + loadedComplaint.getStatus()
        );
        System.out.println(
                "Loaded report time: "
                        + loadedComplaint.getReportedAt()
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
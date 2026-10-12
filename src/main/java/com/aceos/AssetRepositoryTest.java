package com.aceos;

import com.aceos.model.Asset;
import com.aceos.model.AssetStatus;
import com.aceos.repository.AssetRepository;

import java.sql.SQLException;

public class AssetRepositoryTest {

    public static void main(String[] args) throws SQLException {
        com.aceos.security.LegacyVerificationGuard.retired();
        AssetRepository repository = new AssetRepository();

        String uniqueAssetCode =
                "REPOSITORY-TEST-" + System.currentTimeMillis();

        Asset unsavedAsset = new Asset(
                uniqueAssetCode,
                "Repository Test Air Conditioner",
                "Air Conditioner",
                "Test Building, Room 101",
                AssetStatus.ACTIVE
        );

        Asset savedAsset = repository.save(unsavedAsset);

        require(
                savedAsset.getAssetId() > 0,
                "MySQL did not generate a valid asset ID"
        );

        Asset loadedAsset = repository
                .findById(savedAsset.getAssetId())
                .orElseThrow(() -> new IllegalStateException(
                        "The saved asset could not be retrieved"
                ));

        require(
                loadedAsset.getAssetCode().equals(
                        savedAsset.getAssetCode()
                ),
                "The retrieved asset code does not match"
        );

        require(
                loadedAsset.getName().equals(savedAsset.getName()),
                "The retrieved asset name does not match"
        );

        require(
                loadedAsset.getCategory().equals(savedAsset.getCategory()),
                "The retrieved asset category does not match"
        );

        require(
                loadedAsset.getLocation().equals(savedAsset.getLocation()),
                "The retrieved asset location does not match"
        );

        require(
                loadedAsset.getStatus() == savedAsset.getStatus(),
                "The retrieved asset status does not match"
        );

        System.out.println("Asset repository verification passed");
        System.out.println(
                "Generated ID: " + loadedAsset.getAssetId()
        );
        System.out.println(
                "Loaded code: " + loadedAsset.getAssetCode()
        );
        System.out.println(
                "Loaded name: " + loadedAsset.getName()
        );
        System.out.println(
                "Loaded category: " + loadedAsset.getCategory()
        );
        System.out.println(
                "Loaded location: " + loadedAsset.getLocation()
        );
        System.out.println(
                "Loaded status: " + loadedAsset.getStatus()
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
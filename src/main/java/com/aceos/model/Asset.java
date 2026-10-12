package com.aceos.model;

public class Asset {

    private int assetId;
    private String assetCode;
    private String name;
    private String category;
    private String location;
    private AssetStatus status;
    private ManualLifecycle manualLifecycle;

    public Asset(String assetCode, String name, String category, String location, AssetStatus status) {
        this(0, assetCode, name, category, location, status);
    }

    public Asset(int assetId, String assetCode, String name, String category, String location, AssetStatus status) {
        this(assetId, assetCode, name, category, location, status,
                status == AssetStatus.UNDER_MAINTENANCE ? ManualLifecycle.ACTIVE : ManualLifecycle.valueOf(status.name()));
    }

    public Asset(int assetId, String assetCode, String name, String category, String location,
            AssetStatus status, ManualLifecycle manualLifecycle) {
        this.assetId = assetId;
        this.assetCode = assetCode;
        this.name = name;
        this.category = category;
        this.location = location;
        this.status = status;
        this.manualLifecycle = manualLifecycle;
    }

    public int getAssetId() {
        return assetId;
    }

    public void setAssetId(int assetId) {
        this.assetId = assetId;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public ManualLifecycle getManualLifecycle() {
        return manualLifecycle;
    }

    public void setStatus(AssetStatus status) {
        this.status = status;
    }
}

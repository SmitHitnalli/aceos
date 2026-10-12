package com.aceos.ui.view;

import com.aceos.model.UserRole;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

/** Administrator scheduling or the technician's scheduled inspection queue. */
public final class AssetInspectionManagementView extends InspectionFlowView {
    public AssetInspectionManagementView(AceOsService service) {
        super(service, "SCHEDULED", null, administrator(service));
    }
    private static boolean administrator(AceOsService service) {
        try { return service.signedInUser().getRole() == UserRole.ADMINISTRATOR; }
        catch (AceOsServiceException e) { return false; }
    }
}

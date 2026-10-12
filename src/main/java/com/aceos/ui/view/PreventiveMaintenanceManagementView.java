package com.aceos.ui.view;

import com.aceos.service.AceOsService;

/** Compatibility entry point; actions belong to selected schedule details. */
public class PreventiveMaintenanceManagementView extends PreventiveScheduleFlowView {
    public PreventiveMaintenanceManagementView(AceOsService service) {
        super(service, null, null, false);
    }
}

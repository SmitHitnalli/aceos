package com.aceos.ui.view;

import com.aceos.service.AceOsService;

/** Compatibility entry point for the connected authorized schedule directory. */
public class PreventiveMaintenanceDirectoryView extends PreventiveScheduleFlowView {
    public PreventiveMaintenanceDirectoryView(AceOsService service) {
        super(service, null, null, false);
    }
}

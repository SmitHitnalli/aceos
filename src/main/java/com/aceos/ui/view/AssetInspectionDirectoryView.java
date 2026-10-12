package com.aceos.ui.view;

import com.aceos.service.AceOsService;

/** Compatibility entry point for the connected authorized inspection list. */
public final class AssetInspectionDirectoryView extends InspectionFlowView {
    public AssetInspectionDirectoryView(AceOsService service) {
        super(service, null, null, false);
    }
}

package com.aceos.ui.view;

import com.aceos.service.AceOsService;
import javafx.scene.layout.BorderPane;

/** Compatibility entry point for the connected administrator review queue. */
public final class ComplaintReviewView extends BorderPane {
    public ComplaintReviewView(AceOsService service) {
        setCenter(new RepairFlowView(service,false,"SUBMITTED"));
    }
}

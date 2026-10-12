package com.aceos.ui.view;

import com.aceos.service.AceOsService;
import com.aceos.ui.FxTasks;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Minimal F1 ownership controls; connected selectors arrive with Phase 3. */
public final class AssignmentView extends VBox {
    public AssignmentView(AceOsService service) {
        setSpacing(12);
        setPadding(new Insets(24));
        Label title = new Label("Reassign work / assign inspections");
        title.getStyleClass().add("title");
        ComboBox<String> kind = new ComboBox<>();
        kind.getItems().addAll("Scheduled inspection", "Assigned work order");
        kind.setValue("Scheduled inspection");
        TextField record = new TextField(), technician = new TextField();
        Label status = new Label();
        status.setWrapText(true);
        Button save = new Button("Save assignment");
        save.setOnAction(
                e -> {
                    try {
                        int id = Integer.parseInt(record.getText().trim()),
                                target = Integer.parseInt(technician.getText().trim());
                        boolean inspection = kind.getValue().equals("Scheduled inspection");
                        save.setDisable(true);
                        status.setText("Saving…");
                        FxTasks.run(
                                () -> {
                                    if (inspection) service.assignInspection(id, target);
                                    else service.reassignWorkOrder(id, target);
                                    return true;
                                },
                                ok -> status.setText("Assignment saved"),
                                error -> status.setText(error.getMessage()),
                                () -> save.setDisable(false));
                    } catch (NumberFormatException error) {
                        status.setText("Use whole-number IDs from the directories");
                    }
                });
        getChildren()
                .addAll(
                        title,
                        new Label(
                                "Only scheduled inspections and unstarted jobs can be reassigned."),
                        kind,
                        new Label("Inspection / work order ID"),
                        record,
                        new Label("Active technician ID"),
                        technician,
                        save,
                        status);
    }
}

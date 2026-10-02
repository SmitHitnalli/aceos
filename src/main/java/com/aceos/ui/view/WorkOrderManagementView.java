package com.aceos.ui.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class WorkOrderManagementView extends VBox {

    public WorkOrderManagementView() {
        setSpacing(20);
        setPadding(new Insets(40));
        setMaxWidth(720);

        Label title = new Label("Work-Order Assignment");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "Assign an accepted complaint "
                        + "to a maintenance technician."
        );
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        Label guidance = new Label(
                "The complaint must be ACCEPTED, and "
                        + "the selected user must be a TECHNICIAN."
        );
        guidance.setWrapText(true);
        guidance.setStyle(
                "-fx-text-fill: #64748b;"
        );

        TextField complaintIdField = new TextField();
        complaintIdField.setPromptText(
                "Enter the accepted complaint ID"
        );

        TextField technicianIdField = new TextField();
        technicianIdField.setPromptText(
                "Enter the technician user ID"
        );

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(16);

        form.add(new Label("Complaint ID"), 0, 0);
        form.add(complaintIdField, 1, 0);

        form.add(new Label("Technician ID"), 0, 1);
        form.add(technicianIdField, 1, 1);

        GridPane.setHgrow(
                complaintIdField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                technicianIdField,
                Priority.ALWAYS
        );

        Button assignButton =
                new Button("Assign Work Order");
        assignButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle(
                "-fx-text-fill: #b45309;"
        );

        assignButton.setOnAction(event ->
                statusLabel.setText(
                        "The assignment form works, "
                                + "but the work order has "
                                + "not been saved yet."
                )
        );

        getChildren().addAll(
                title,
                description,
                guidance,
                form,
                assignButton,
                statusLabel
        );
    }
}
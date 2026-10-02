package com.aceos.ui.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class WorkOrderVerificationView extends VBox {

    public WorkOrderVerificationView() {
        setSpacing(20);
        setPadding(new Insets(40));
        setMaxWidth(720);

        Label title = new Label(
                "Maintenance Verification"
        );
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "Verify completed maintenance work "
                        + "as an administrator."
        );
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        Label guidance = new Label(
                "Only work orders with COMPLETED status "
                        + "can be verified."
        );
        guidance.setStyle(
                "-fx-text-fill: #64748b;"
        );

        TextField workOrderIdField =
                new TextField();
        workOrderIdField.setPromptText(
                "Enter the completed work-order ID"
        );

        TextField administratorIdField =
                new TextField();
        administratorIdField.setPromptText(
                "Enter the administrator user ID"
        );

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(16);

        form.add(
                new Label("Work Order ID"),
                0,
                0
        );
        form.add(workOrderIdField, 1, 0);

        form.add(
                new Label("Administrator ID"),
                0,
                1
        );
        form.add(administratorIdField, 1, 1);

        GridPane.setHgrow(
                workOrderIdField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                administratorIdField,
                Priority.ALWAYS
        );

        Button verifyButton =
                new Button("Verify Maintenance");
        verifyButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle(
                "-fx-text-fill: #b45309;"
        );

        verifyButton.setOnAction(event ->
                statusLabel.setText(
                        "The verification control works, "
                                + "but the result has not "
                                + "been saved yet."
                )
        );

        getChildren().addAll(
                title,
                description,
                guidance,
                form,
                verifyButton,
                statusLabel
        );
    }
}
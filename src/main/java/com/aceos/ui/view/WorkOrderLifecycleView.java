package com.aceos.ui.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class WorkOrderLifecycleView extends VBox {

    public WorkOrderLifecycleView() {
        setSpacing(20);
        setPadding(new Insets(40));
        setMaxWidth(720);

        Label title = new Label("Work-Order Lifecycle");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "Start assigned maintenance work and "
                        + "record its completion."
        );
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        Label guidance = new Label(
                "Starting changes ASSIGNED to IN_PROGRESS. "
                        + "Completion requires an IN_PROGRESS "
                        + "work order and a maintenance action."
        );
        guidance.setWrapText(true);
        guidance.setStyle(
                "-fx-text-fill: #64748b;"
        );

        TextField workOrderIdField =
                new TextField();
        workOrderIdField.setPromptText(
                "Enter the work-order ID"
        );

        TextArea actionTakenField =
                new TextArea();
        actionTakenField.setPromptText(
                "Describe the maintenance action taken"
        );
        actionTakenField.setPrefRowCount(5);
        actionTakenField.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(16);

        form.add(new Label("Work-Order ID"), 0, 0);
        form.add(workOrderIdField, 1, 0);

        form.add(new Label("Action Taken"), 0, 1);
        form.add(actionTakenField, 1, 1);

        GridPane.setHgrow(
                workOrderIdField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                actionTakenField,
                Priority.ALWAYS
        );

        Button startButton =
                new Button("Start Work");
        startButton.setMinHeight(40);

        Button completeButton =
                new Button("Complete Work");
        completeButton.setMinHeight(40);

        HBox actions = new HBox(
                12,
                startButton,
                completeButton
        );

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle(
                "-fx-text-fill: #b45309;"
        );

        startButton.setOnAction(event ->
                statusLabel.setText(
                        "The start control works, "
                                + "but the status has not "
                                + "been saved yet."
                )
        );

        completeButton.setOnAction(event ->
                statusLabel.setText(
                        "The completion control works, "
                                + "but the result has not "
                                + "been saved yet."
                )
        );

        getChildren().addAll(
                title,
                description,
                guidance,
                form,
                actions,
                statusLabel
        );
    }
}
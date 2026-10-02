package com.aceos.ui.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ComplaintReviewView extends VBox {

    public ComplaintReviewView() {
        setSpacing(20);
        setPadding(new Insets(40));
        setMaxWidth(720);

        Label title = new Label("Complaint Review");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "Accept or reject a submitted complaint "
                        + "as an administrator."
        );
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        Label guidance = new Label(
                "Only complaints with SUBMITTED status "
                        + "can be reviewed."
        );
        guidance.setStyle(
                "-fx-text-fill: #64748b;"
        );

        TextField complaintIdField = new TextField();
        complaintIdField.setPromptText(
                "Enter the complaint ID"
        );

        TextField administratorIdField =
                new TextField();
        administratorIdField.setPromptText(
                "Enter the administrator user ID"
        );

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(16);

        form.add(new Label("Complaint ID"), 0, 0);
        form.add(complaintIdField, 1, 0);

        form.add(new Label("Administrator ID"), 0, 1);
        form.add(administratorIdField, 1, 1);

        GridPane.setHgrow(
                complaintIdField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                administratorIdField,
                Priority.ALWAYS
        );

        Button acceptButton =
                new Button("Accept Complaint");
        acceptButton.setMinHeight(40);

        Button rejectButton =
                new Button("Reject Complaint");
        rejectButton.setMinHeight(40);

        HBox actions = new HBox(
                12,
                acceptButton,
                rejectButton
        );

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle(
                "-fx-text-fill: #b45309;"
        );

        acceptButton.setOnAction(event ->
                statusLabel.setText(
                        "The acceptance control works, "
                                + "but the review has not "
                                + "been saved yet."
                )
        );

        rejectButton.setOnAction(event ->
                statusLabel.setText(
                        "The rejection control works, "
                                + "but the review has not "
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
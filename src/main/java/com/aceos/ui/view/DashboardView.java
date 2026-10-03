package com.aceos.ui.view;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class DashboardView extends VBox {

    public DashboardView() {
        setSpacing(24);
        setPadding(new Insets(40));

        Label title = new Label("AceOS Dashboard");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "Manage institutional assets and track "
                        + "maintenance from complaint to verification."
        );
        description.setWrapText(true);
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        Label status = new Label(
                "CORE WORKFLOW READY"
        );
        status.setPadding(
                new Insets(8, 14, 8, 14)
        );
        status.setStyle(
                "-fx-background-color: #dcfce7;"
                        + "-fx-text-fill: #166534;"
                        + "-fx-font-weight: bold;"
                        + "-fx-background-radius: 16px;"
        );

        Label workflowTitle =
                new Label("Maintenance Workflow");
        workflowTitle.setStyle(
                "-fx-font-size: 19px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        FlowPane workflow = new FlowPane();
        workflow.setHgap(14);
        workflow.setVgap(14);

        workflow.getChildren().addAll(
                createWorkflowCard(
                        "1",
                        "Register",
                        "Create users and register assets."
                ),
                createWorkflowCard(
                        "2",
                        "Report",
                        "Submit and review asset complaints."
                ),
                createWorkflowCard(
                        "3",
                        "Assign",
                        "Assign accepted work to a technician."
                ),
                createWorkflowCard(
                        "4",
                        "Maintain",
                        "Start work, complete it, and record the action."
                ),
                createWorkflowCard(
                        "5",
                        "Verify",
                        "Verify the repair and close the workflow."
                )
        );

        getChildren().addAll(
                title,
                description,
                status,
                workflowTitle,
                workflow
        );
    }

    private VBox createWorkflowCard(
            String step,
            String heading,
            String details) {
        Label stepLabel = new Label(step);
        stepLabel.setStyle(
                "-fx-font-size: 18px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: white;"
                        + "-fx-background-color: #2563eb;"
                        + "-fx-background-radius: 18px;"
                        + "-fx-padding: 6px 12px;"
        );

        Label headingLabel = new Label(heading);
        headingLabel.setStyle(
                "-fx-font-size: 16px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label detailsLabel = new Label(details);
        detailsLabel.setWrapText(true);
        detailsLabel.setStyle(
                "-fx-text-fill: #475569;"
        );

        VBox card = new VBox(
                12,
                stepLabel,
                headingLabel,
                detailsLabel
        );
        card.setPadding(new Insets(18));
        card.setPrefWidth(190);
        card.setMinHeight(145);
        card.setStyle(
                "-fx-background-color: white;"
                        + "-fx-border-color: #cbd5e1;"
                        + "-fx-border-radius: 8px;"
                        + "-fx-background-radius: 8px;"
        );

        return card;
    }
}
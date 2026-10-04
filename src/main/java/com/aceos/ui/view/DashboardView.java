package com.aceos.ui.view;

import com.aceos.model.DashboardStatistics;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class DashboardView extends ScrollPane {

    private final AceOsService service;

    private final Label totalAssetsValue = new Label("-");
    private final Label activeAssetsValue = new Label("-");
    private final Label openComplaintsValue = new Label("-");
    private final Label activeWorkOrdersValue = new Label("-");
    private final Label overdueSchedulesValue = new Label("-");
    private final Label failedInspectionsValue = new Label("-");
    private final Label feedback = new Label();

    public DashboardView(AceOsService service) {
        this.service = service;

        VBox content = new VBox(20);
        content.setPadding(new Insets(40));
        content.setStyle("-fx-background-color: #f8fafc;");

        Label title = new Label("AceOS Dashboard");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "Live operational overview and maintenance workflow."
        );
        description.setWrapText(true);
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        Label status = new Label("CORE WORKFLOW READY");
        status.setPadding(new Insets(8, 14, 8, 14));
        status.setStyle(
                "-fx-background-color: #dcfce7;"
                        + "-fx-text-fill: #166534;"
                        + "-fx-font-weight: bold;"
                        + "-fx-background-radius: 16px;"
        );

        Label statisticsTitle =
                new Label("Live Operations");
        statisticsTitle.setStyle(
                "-fx-font-size: 19px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Button refreshButton =
                new Button("Refresh Statistics");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox statisticsHeader = new HBox(
                12,
                statisticsTitle,
                spacer,
                refreshButton
        );
        statisticsHeader.setAlignment(Pos.CENTER_LEFT);

        FlowPane statistics = new FlowPane();
        statistics.setHgap(12);
        statistics.setVgap(12);

        statistics.getChildren().addAll(
                createStatisticsCard(
                        "Total Assets",
                        totalAssetsValue
                ),
                createStatisticsCard(
                        "Active Assets",
                        activeAssetsValue
                ),
                createStatisticsCard(
                        "Open Complaints",
                        openComplaintsValue
                ),
                createStatisticsCard(
                        "Active Work Orders",
                        activeWorkOrdersValue
                ),
                createStatisticsCard(
                        "Overdue Schedules",
                        overdueSchedulesValue
                ),
                createStatisticsCard(
                        "Failed Inspections",
                        failedInspectionsValue
                )
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

        refreshButton.setOnAction(
                event -> loadStatistics()
        );

        content.getChildren().addAll(
                title,
                description,
                status,
                statisticsHeader,
                feedback,
                statistics,
                workflowTitle,
                workflow
        );

        setContent(content);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setStyle(
                "-fx-background: #f8fafc;"
                        + "-fx-background-color: #f8fafc;"
        );

        loadStatistics();
    }

    private void loadStatistics() {
        try {
            DashboardStatistics statistics =
                    service.getDashboardStatistics();

            totalAssetsValue.setText(
                    String.valueOf(
                            statistics.getTotalAssets()
                    )
            );
            activeAssetsValue.setText(
                    String.valueOf(
                            statistics.getActiveAssets()
                    )
            );
            openComplaintsValue.setText(
                    String.valueOf(
                            statistics.getOpenComplaints()
                    )
            );
            activeWorkOrdersValue.setText(
                    String.valueOf(
                            statistics.getActiveWorkOrders()
                    )
            );
            overdueSchedulesValue.setText(
                    String.valueOf(
                            statistics.getOverdueSchedules()
                    )
            );
            failedInspectionsValue.setText(
                    String.valueOf(
                            statistics.getFailedInspections()
                    )
            );

            feedback.setText(
                    "Dashboard statistics refreshed."
            );
            feedback.setStyle("-fx-text-fill: #166534;");
        } catch (AceOsServiceException exception) {
            feedback.setText(exception.getMessage());
            feedback.setStyle("-fx-text-fill: #dc2626;");
        }
    }

    private VBox createStatisticsCard(
            String heading,
            Label valueLabel) {
        valueLabel.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #2563eb;"
        );

        Label headingLabel = new Label(heading);
        headingLabel.setWrapText(true);
        headingLabel.setStyle(
                "-fx-text-fill: #475569;"
                        + "-fx-font-weight: bold;"
        );

        VBox card = new VBox(
                8,
                valueLabel,
                headingLabel
        );
        card.setPadding(new Insets(16));
        card.setPrefWidth(155);
        card.setMinHeight(95);
        card.setStyle(
                "-fx-background-color: white;"
                        + "-fx-border-color: #cbd5e1;"
                        + "-fx-border-radius: 8px;"
                        + "-fx-background-radius: 8px;"
        );

        return card;
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
        detailsLabel.setStyle("-fx-text-fill: #475569;");

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
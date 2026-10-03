package com.aceos.ui.view;

import com.aceos.model.User;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

public class UserDirectoryView extends VBox {

    public UserDirectoryView(
            AceOsService service) {
        setSpacing(18);
        setPadding(new Insets(40));

        Label title = new Label("User Directory");
        title.setStyle(
                "-fx-font-size: 28px;"
                + "-fx-font-weight: bold;"
                + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "View user IDs and roles for "
                + "complaints, assignments, and verification."
        );
        description.setWrapText(true);
        description.setStyle(
                "-fx-font-size: 15px;"
                + "-fx-text-fill: #475569;"
        );

        TableView<User> table = new TableView<>();
        table.setPlaceholder(
                new Label("No users found")
        );
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        TableColumn<User, Number> idColumn
                = new TableColumn<>("ID");
        idColumn.setCellValueFactory(data
                -> new ReadOnlyIntegerWrapper(
                        data.getValue().getUserId()
                )
        );

        TableColumn<User, String> nameColumn
                = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(data
                -> new ReadOnlyStringWrapper(
                        data.getValue().getName()
                )
        );

        TableColumn<User, String> emailColumn
                = new TableColumn<>("Email");
        emailColumn.setCellValueFactory(data
                -> new ReadOnlyStringWrapper(
                        data.getValue().getEmail()
                )
        );

        TableColumn<User, String> roleColumn
                = new TableColumn<>("Role");
        roleColumn.setCellValueFactory(data
                -> new ReadOnlyStringWrapper(
                        data.getValue()
                                .getRole()
                                .name()
                )
        );

        table.getColumns().add(idColumn);
        table.getColumns().add(nameColumn);
        table.getColumns().add(emailColumn);
        table.getColumns().add(roleColumn);
        table.setPrefHeight(420);

        Button refreshButton
                = new Button("Refresh Users");
        refreshButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        refreshButton.setOnAction(event
                -> loadUsers(
                        service,
                        table,
                        refreshButton,
                        statusLabel
                )
        );

        getChildren().addAll(
                title,
                description,
                table,
                refreshButton,
                statusLabel
        );

        loadUsers(
                service,
                table,
                refreshButton,
                statusLabel
        );
    }

    private void loadUsers(
            AceOsService service,
            TableView<User> table,
            Button refreshButton,
            Label statusLabel) {
        refreshButton.setDisable(true);

        try {
            table.getItems().setAll(
                    service.getAllUsers()
            );

            statusLabel.setStyle(
                    "-fx-text-fill: #15803d;"
            );
            statusLabel.setText(
                    table.getItems().size()
                    + " users loaded"
            );
        } catch (AceOsServiceException exception) {
            statusLabel.setStyle(
                    "-fx-text-fill: #b91c1c;"
            );
            statusLabel.setText(
                    exception.getMessage()
            );
        } finally {
            refreshButton.setDisable(false);
        }
    }
}

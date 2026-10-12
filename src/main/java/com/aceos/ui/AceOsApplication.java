package com.aceos.ui;

import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;
import com.aceos.ui.view.AccountView;
import com.aceos.ui.view.LoginView;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class AceOsApplication extends Application {
    private AceOsService service = new AceOsService();
    private BorderPane root;

    @Override
    public void start(Stage stage) {
        root = new BorderPane();
        showLogin();
        Scene scene = new Scene(root, 1000, 650);
        scene.getStylesheets().add(getClass().getResource("foundation.css").toExternalForm());
        stage.setTitle("AceOS - Asset Operations");
        stage.setMinWidth(800);
        stage.setMinHeight(520);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> { if (!NavigationGuard.allowLeave(root)) event.consume(); });
        stage.show();
    }

    private void showLogin() {
        root.setCenter(new LoginView(service, this::showWorkspace));
    }

    private void logout() {
        service.signOut();
        service = new AceOsService();
        showLogin();
    }

    private void showWorkspace() {
        if (service.passwordChangeRequired()) {
            root.setCenter(new AccountView(service, true, this::showWorkspace, this::logout));
            return;
        }
        try {
            root.setCenter(new WorkspaceView(service, this::logout, this::showWorkspace));
        } catch (AceOsServiceException e) {
            logout();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

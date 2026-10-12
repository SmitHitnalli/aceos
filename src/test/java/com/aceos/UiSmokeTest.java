package com.aceos;

import static org.junit.jupiter.api.Assertions.*;

import com.aceos.service.AceOsService;
import com.aceos.ui.view.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;

import javax.imageio.ImageIO;

@EnabledIfEnvironmentVariable(named = "ACEOS_UI_TEST", matches = "true")
class UiSmokeTest {
    @BeforeAll
    static void toolkit() {
        Platform.startup(() -> {});
        Platform.setImplicitExit(false);
    }

    @Test
    void loginAndPasswordScreensAtSupportedSizes() throws Exception {
        FutureTask<Void> task =
                new FutureTask<>(
                        () -> {
                            for (int[] size : new int[][] {{800, 520}, {1280, 800}}) {
                                snapshot(
                                        new LoginView(new AceOsService(), () -> {}), size, "login");
                                snapshot(
                                        new AccountView(
                                                new AceOsService(), true, () -> {}, () -> {}),
                                        size,
                                        "password-change");
                            }
                            return null;
                        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }

    @Test
    void failedLoginRetainsInputAndRestoresButton() throws Exception {
        java.util.concurrent.atomic.AtomicReference<LoginView> reference =
                new java.util.concurrent.atomic.AtomicReference<>();
        FutureTask<Void> setup =
                new FutureTask<>(
                        () -> {
                            LoginView root =
                                    new LoginView(
                                            new AceOsService(),
                                            () -> fail("Unknown user must not sign in"));
                            reference.set(root);
                            Scene scene = new Scene(root, 800, 520);
                            root.applyCss();
                            root.layout();
                            ((javafx.scene.control.TextField) root.lookup("#login-email"))
                                    .setText("missing@aceos.local");
                            ((javafx.scene.control.PasswordField) root.lookup("#login-password"))
                                    .setText("A private local test passphrase");
                            ((javafx.scene.control.Button) root.lookup("#login-submit")).fire();
                            assertTrue(root.lookup("#login-submit").isDisabled());
                            return null;
                        });
        Platform.runLater(setup);
        setup.get(10, TimeUnit.SECONDS);
        boolean finished = false;
        for (int attempt = 0; attempt < 100; attempt++) {
            FutureTask<Boolean> check =
                    new FutureTask<>(() -> !reference.get().lookup("#login-submit").isDisabled());
            Platform.runLater(check);
            if (check.get(2, TimeUnit.SECONDS)) {
                finished = true;
                break;
            }
            Thread.sleep(50);
        }
        assertTrue(finished, "Login callback should complete");
        FutureTask<Void> verify =
                new FutureTask<>(
                        () -> {
                            assertEquals(
                                    "missing@aceos.local",
                                    ((javafx.scene.control.TextField)
                                                    reference.get().lookup("#login-email"))
                                            .getText());
                            assertFalse(
                                    ((javafx.scene.control.PasswordField)
                                                    reference.get().lookup("#login-password"))
                                            .getText()
                                            .isEmpty());
                            return null;
                        });
        Platform.runLater(verify);
        verify.get(10, TimeUnit.SECONDS);
    }

    private void snapshot(Region root, int[] size, String name) throws Exception {
        Scene scene = new Scene(root, size[0], size[1]);
        scene.getStylesheets()
                .add(getClass().getResource("/com/aceos/ui/foundation.css").toExternalForm());
        root.resize(size[0], size[1]);
        root.applyCss();
        root.layout();
        for (var node : root.lookupAll(".text-input")) {
            var bounds = node.localToScene(node.getBoundsInLocal());
            assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= size[0]);
            assertTrue(
                    bounds.getMinY() >= 0 && bounds.getMaxY() <= size[1],
                    name + " fields should fit");
        }
        var image = scene.snapshot(null);
        BufferedImage output =
                new BufferedImage(
                        (int) image.getWidth(),
                        (int) image.getHeight(),
                        BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++)
            for (int x = 0; x < output.getWidth(); x++)
                output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        Path file = Path.of("target", "ui-checks", name + "-" + size[0] + ".png");
        Files.createDirectories(file.getParent());
        ImageIO.write(output, "png", file.toFile());
    }
}

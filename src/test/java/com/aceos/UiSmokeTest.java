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

    @Test
    void f2ControlsAndAssetStatusesAtSupportedSizes() throws Exception {
        var example = new com.aceos.model.Asset(101, "AC-101", "Classroom AC", "HVAC", "Room 101",
                com.aceos.model.AssetStatus.OUT_OF_SERVICE, com.aceos.model.ManualLifecycle.ACTIVE);
        AceOsService preview = new AceOsService() {
            @Override public com.aceos.model.Asset getAsset(int id) { return example; }
            @Override public java.util.List<com.aceos.model.SafetyHold> getSafetyHolds(int id) {
                return java.util.List.of(new com.aceos.model.SafetyHold(12, 101, 8,
                        java.time.LocalDateTime.now(), null, null, null, null, null));
            }
            @Override public java.util.List<com.aceos.model.Asset> getAllAssets() {
                return java.util.List.of(example,
                        new com.aceos.model.Asset(102, "AC-102", "Lab AC", "HVAC", "Lab", com.aceos.model.AssetStatus.UNDER_MAINTENANCE),
                        new com.aceos.model.Asset(103, "AC-103", "Office AC", "HVAC", "Office", com.aceos.model.AssetStatus.ACTIVE),
                        new com.aceos.model.Asset(104, "AC-104", "Old AC", "HVAC", "Store", com.aceos.model.AssetStatus.RETIRED));
            }
            @Override public com.aceos.model.Asset changeAssetLifecycle(int id, com.aceos.model.ManualLifecycle expected,
                    com.aceos.model.ManualLifecycle next, String reason) throws com.aceos.service.AceOsServiceException {
                throw new com.aceos.service.AceOsServiceException("Example failure: refresh and retry");
            }
        };
        for (int[] size : new int[][] {{800, 520}, {1280, 800}}) {
            var controls = new java.util.concurrent.atomic.AtomicReference<AssetAvailabilityView>();
            var directory = new java.util.concurrent.atomic.AtomicReference<AssetDirectoryView>();
            FutureTask<Void> setup = new FutureTask<>(() -> {
                controls.set(new AssetAvailabilityView(preview)); directory.set(new AssetDirectoryView(preview));
                new Scene(controls.get(), size[0], size[1]); controls.get().applyCss(); controls.get().layout();
                ((javafx.scene.control.TextField) controls.get().lookup("#availability-asset-id")).setText("101");
                ((javafx.scene.control.Button) controls.get().lookup("#availability-load")).fire();
                return null;
            });
            Platform.runLater(setup); setup.get(10, TimeUnit.SECONDS);
            // Directory has no scene yet; wait directly on the backing table items.
            awaitFx(() -> !controls.get().isDisabled()
                    && ((javafx.scene.control.TableView<?>) directory.get().getChildren().get(3)).getItems().size() == 4, true);
            FutureTask<Void> verify = new FutureTask<>(() -> {
                assertTrue(((javafx.scene.control.Label) controls.get().lookup("#availability-summary")).getText().contains("OUT_OF_SERVICE"));
                assertFalse(controls.get().lookup("#availability-clear").isDisabled());
                snapshot(controls.get(), size, "f2-availability"); snapshot(directory.get(), size, "f2-asset-directory");
                ((javafx.scene.control.TextField) controls.get().lookup("#availability-reason")).setText("Keep my reason on failure");
                ((javafx.scene.control.Button) controls.get().lookup("#availability-change")).fire();
                return null;
            });
            Platform.runLater(verify); verify.get(20, TimeUnit.SECONDS);
            awaitFx(() -> !controls.get().isDisabled(), true);
            FutureTask<Void> recovery = new FutureTask<>(() -> {
                assertEquals("Keep my reason on failure", ((javafx.scene.control.TextField) controls.get().lookup("#availability-reason")).getText());
                assertTrue(((javafx.scene.control.Label) controls.get().lookup("#availability-feedback")).getText().contains("Example failure"));
                return null;
            });
            Platform.runLater(recovery); recovery.get(10, TimeUnit.SECONDS);
        }
    }

    private void awaitFx(java.util.function.BooleanSupplier condition, boolean required) throws Exception {
        for (int i = 0; i < 100; i++) {
            FutureTask<Boolean> check = new FutureTask<>(condition::getAsBoolean);
            Platform.runLater(check);
            if (check.get(2, TimeUnit.SECONDS)) return;
            Thread.sleep(25);
        }
        if (required) fail("Background callback must finish");
    }

    private void snapshot(Region root, int[] size, String name) throws Exception {
        Scene scene = root.getScene() == null ? new Scene(root, size[0], size[1]) : root.getScene();
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

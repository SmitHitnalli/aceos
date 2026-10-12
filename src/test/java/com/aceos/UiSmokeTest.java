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

    private static final class AssetPreview extends AceOsService {
        final com.aceos.model.UserRole role;
        final com.aceos.model.Asset asset = new com.aceos.model.Asset(101, "AC-101", "Classroom air conditioner", "Cooling", "Room 101", com.aceos.model.AssetStatus.OUT_OF_SERVICE, com.aceos.model.ManualLifecycle.ACTIVE);
        volatile boolean failRead, failWrite, empty, cleared;
        volatile CountDownLatch gate;
        final java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger(), writes = new java.util.concurrent.atomic.AtomicInteger();
        AssetPreview(com.aceos.model.UserRole role) { this.role = role; }
        @Override public com.aceos.model.User signedInUser() { return new com.aceos.model.User(1, "Example user", "example@aceos.local", role); }
        @Override public com.aceos.model.DashboardSummary getDashboardSummary() {
            background(); return new com.aceos.model.DashboardSummary(java.util.Map.of(), java.util.List.of());
        }
        void background() { assertFalse(Platform.isFxApplicationThread()); }
        @Override public java.util.List<com.aceos.model.Asset> getAllAssets() throws com.aceos.service.AceOsServiceException {
            background(); waitForGate(); if (failRead) throw new com.aceos.service.AceOsServiceException("Example read failure");
            return empty ? java.util.List.of() : java.util.List.of(asset);
        }
        void waitForGate() { if (gate != null) try { gate.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); } }
        @Override public com.aceos.model.AssetProfile getAssetProfile(int id) throws com.aceos.service.AceOsServiceException {
            background(); assertEquals(101, id); reads.incrementAndGet(); waitForGate();
            if (failRead) throw new com.aceos.service.AceOsServiceException("Example read failure");
            var time = java.time.LocalDateTime.of(2026, 10, 12, 12, 0);
            boolean admin = role == com.aceos.model.UserRole.ADMINISTRATOR;
            return new com.aceos.model.AssetProfile(asset, role, java.util.List.of(new com.aceos.security.MaintenanceSummary(9, 51, "Cable replaced and cooling tested. Technical work remains visible without unrelated requester details.", time)),
                admin ? java.util.List.of(new com.aceos.model.SafetyHold(12, 101, 8, time, cleared ? 1 : null, cleared ? "Verified correction" : null, null, cleared ? 51 : null, cleared ? time.plusHours(1) : null),
                new com.aceos.model.SafetyHold(13, 101, 9, time.plusMinutes(20), null, null, null, null, null)) : java.util.List.of(),
                admin ? java.util.List.of(new com.aceos.model.AssetProfile.LifecycleEvent(com.aceos.model.ManualLifecycle.OUT_OF_SERVICE, com.aceos.model.ManualLifecycle.ACTIVE, "Manual stop lifted; safety holds remain", 1, time)) : java.util.List.of(),
                admin && !cleared ? java.util.List.of(new com.aceos.model.AssetProfile.ClearanceEvidence(12, 51, false, "Cable replaced", time.plusHours(1))) : java.util.List.of());
        }
        void write() throws com.aceos.service.AceOsServiceException {
            background(); writes.incrementAndGet(); waitForGate(); if (failWrite) throw new com.aceos.service.AceOsServiceException("Example stale action");
        }
        @Override public com.aceos.model.Asset clearSafetyHold(int id, int h, String reason, Integer i, Integer w) throws com.aceos.service.AceOsServiceException {
            assertEquals(101, id); assertEquals(12, h); assertNull(i); assertEquals(51, w); assertEquals("Verified correction", reason);
            write(); cleared = true; return asset;
        }
        @Override public com.aceos.model.Asset changeAssetLifecycle(int id, com.aceos.model.ManualLifecycle expected, com.aceos.model.ManualLifecycle next, String reason) throws com.aceos.service.AceOsServiceException {
            assertEquals(101, id); assertEquals(com.aceos.model.ManualLifecycle.ACTIVE, expected); assertEquals(com.aceos.model.ManualLifecycle.OUT_OF_SERVICE, next);
            assertEquals("Keep my reason", reason); write(); return asset;
        }
    }

    @Test
    void assetProfilesAtSupportedSizesAndExactDirectoryNavigation() throws Exception {
        for (var role : com.aceos.model.UserRole.values()) {
            for (int[] size : new int[][] {{800, 520}, {1280, 800}}) {
                var service = new AssetPreview(role);
                var root = new java.util.concurrent.atomic.AtomicReference<com.aceos.ui.WorkspaceView>();
                fx(() -> {
                    root.set(new com.aceos.ui.WorkspaceView(service, () -> {}, () -> {}));
                    new Scene(root.get(), size[0], size[1]); root.get().resize(size[0], size[1]);
                    root.get().navigate("assets"); root.get().applyCss(); root.get().layout(); return null;
                });
                awaitControl(root.get(), "asset-refresh");
                fx(() -> {
                    var table = (javafx.scene.control.TableView<?>) root.get().lookup("#asset-table");
                    assertEquals(1, table.getItems().size()); table.getSelectionModel().selectFirst();
                    snapshot(root.get(), size, "asset-directory-" + role, true);
                    table.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.ENTER, false, false, false, false)); return null;
                });
                awaitControl(root.get(), "asset-profile-refresh");
                fx(() -> {
                    var profile = (AssetProfileView) ((AssetDirectoryView)root.get().getCenter()).getCenter();
                    assertTrue(profile.lookupAll(".label").stream().anyMatch(n -> ((javafx.scene.control.Label)n).getText().contains("Effective availability: OUT_OF_SERVICE")));
                    assertEquals(role == com.aceos.model.UserRole.ADMINISTRATOR, profile.lookup("#availability-actions") != null);
                    snapshot(root.get(), size, "asset-profile-" + role, true);
                    if (role == com.aceos.model.UserRole.ADMINISTRATOR) {
                        profile.setVvalue(1); profile.layout();
                        var bounds = profile.lookup("#availability-clear").localToScene(profile.lookup("#availability-clear").getBoundsInLocal());
                        assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= size[1], "Hold action must be reachable by scrolling");
                        snapshot(root.get(), size, "asset-controls", true);
                    }
                    fire(root.get(), "asset-profile-back"); return null;
                });
                awaitControl(root.get(), "asset-refresh");
            }
        }
    }

    @Test
    void assetActionsRetainInputBlockStaleDuplicatesAndUseSelectedEvidence() throws Exception {
        var service = new AssetPreview(com.aceos.model.UserRole.ADMINISTRATOR);
        var root = new java.util.concurrent.atomic.AtomicReference<AssetProfileView>();
        fx(() -> { root.set(new AssetProfileView(service, 101, () -> {})); new Scene(root.get(), 800, 520); return null; });
        awaitControl(root.get(), "asset-profile-refresh");
        service.failWrite = true; service.gate = new CountDownLatch(1);
        fx(() -> {
            ((javafx.scene.control.ComboBox<com.aceos.model.ManualLifecycle>) root.get().lookup("#availability-lifecycle")).setValue(com.aceos.model.ManualLifecycle.OUT_OF_SERVICE);
            ((javafx.scene.control.TextArea) root.get().lookup("#availability-reason")).setText("Keep my reason");
            fire(root.get(), "availability-change"); fire(root.get(), "availability-change"); fire(root.get(), "asset-profile-refresh"); return null;
        });
        service.gate.countDown(); awaitControl(root.get(), "asset-profile-refresh");
        fx(() -> {
            assertEquals(1, service.writes.get()); assertEquals("Keep my reason", ((javafx.scene.control.TextArea) root.get().lookup("#availability-reason")).getText());
            assertTrue(root.get().lookup("#availability-change").isDisabled()); assertTrue(root.get().lookup("#availability-actions").isDisabled());
            assertTrue(((javafx.scene.control.Label)root.get().lookup("#asset-profile-feedback")).getText().contains("Refresh before"));
            service.failWrite = false; service.gate = null; fire(root.get(), "asset-profile-refresh"); return null;
        });
        awaitControl(root.get(), "asset-profile-refresh");
        fx(() -> {
            var holds = (javafx.scene.control.ComboBox<?>)root.get().lookup("#availability-hold");
            holds.getSelectionModel().selectLast();
            assertTrue(((javafx.scene.control.ComboBox<?>)root.get().lookup("#availability-evidence")).getItems().isEmpty());
            assertTrue(root.get().lookup("#availability-clear").isDisabled());
            holds.getSelectionModel().selectFirst();
            ((javafx.scene.control.ComboBox<?>)root.get().lookup("#availability-evidence")).getSelectionModel().selectFirst();
            ((javafx.scene.control.TextArea)root.get().lookup("#availability-reason")).setText("Verified correction");
            fire(root.get(), "availability-clear"); fire(root.get(), "availability-clear"); return null;
        });
        awaitControl(root.get(), "asset-profile-refresh");
        fx(() -> {
            assertEquals(2, service.writes.get()); assertTrue(service.cleared);
            assertEquals("", ((javafx.scene.control.TextArea)root.get().lookup("#availability-reason")).getText());
            assertEquals(1, ((javafx.scene.control.ComboBox<?>)root.get().lookup("#availability-hold")).getItems().size());
            assertTrue(root.get().lookup("#availability-clear").isDisabled()); return null;
        });
    }

    @Test
    void assetDirectoryAndProfileFailureRetryEmptyAndSearchRecovery() throws Exception {
        var service = new AssetPreview(com.aceos.model.UserRole.REQUESTER); service.failRead = true;
        var root = new java.util.concurrent.atomic.AtomicReference<AssetDirectoryView>();
        fx(() -> { root.set(new AssetDirectoryView(service)); new Scene(root.get(), 800, 520); return null; });
        awaitControl(root.get(), "asset-refresh");
        fx(() -> {
            assertTrue(((javafx.scene.control.TableView<?>)root.get().lookup("#asset-table")).getItems().isEmpty());
            assertEquals("Retry", ((javafx.scene.control.Button)root.get().lookup("#asset-refresh")).getText());
            service.failRead = false; service.empty = true; fire(root.get(), "asset-refresh"); return null;
        });
        awaitControl(root.get(), "asset-refresh");
        fx(() -> {
            assertTrue(((javafx.scene.control.Label)root.get().lookup("#asset-feedback")).getText().contains("No assets"));
            service.empty = false; fire(root.get(), "asset-refresh"); return null;
        });
        awaitControl(root.get(), "asset-refresh");
        fx(() -> {
            var search = (javafx.scene.control.TextField)root.get().lookup("#asset-search"); search.setText("missing");
            var table = (javafx.scene.control.TableView<?>)root.get().lookup("#asset-table"); assertTrue(table.getItems().isEmpty());
            search.setText("room 101"); assertEquals(1, table.getItems().size());
            table.getSelectionModel().selectFirst(); service.failRead = true; fire(root.get(), "asset-open"); return null;
        });
        awaitControl(root.get(), "asset-profile-refresh");
        fx(() -> {
            assertTrue(((javafx.scene.layout.VBox)root.get().lookup("#asset-profile-details")).getChildren().isEmpty());
            assertNull(root.get().lookup("#availability-actions"));
            service.failRead = false; fire(root.get(), "asset-profile-refresh"); return null;
        });
        awaitControl(root.get(), "asset-profile-refresh");
        assertEquals(2, service.reads.get());
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

    @Test
    void phase2RoleShellNavigationAndResponsiveDashboards() throws Exception {
        for (var role : com.aceos.model.UserRole.values()) {
            var summary = new com.aceos.model.DashboardSummary(
                    java.util.Map.of("open", 2, "resolved", 1, "assigned", 2, "started", 1,
                            "completed", 1, "inspections", 2, "review", 3, "assignment", 1,
                            "verification", 1, "holds", 1),
                    java.util.List.of(new com.aceos.model.DashboardSummary.Item(
                            role == com.aceos.model.UserRole.TECHNICIAN ? "Work order" : "Complaint",
                            42, "Classroom air conditioner", role == com.aceos.model.UserRole.TECHNICIAN ? "ASSIGNED" : "SUBMITTED",
                            "HIGH · Air conditioner is not cooling in Room 101")));
            AceOsService preview = new AceOsService() {
                @Override public com.aceos.model.User signedInUser() {
                    return new com.aceos.model.User(3, "Example user", "example@aceos.local", role);
                }
                @Override public com.aceos.model.DashboardSummary getDashboardSummary() {
                    assertFalse(Platform.isFxApplicationThread(), "Database work must be off the UI thread");
                    return summary;
                }
            };
            for (int[] size : new int[][] {{800, 520}, {1280, 800}}) {
                var reference = new java.util.concurrent.atomic.AtomicReference<com.aceos.ui.WorkspaceView>();
                var signedOut = new java.util.concurrent.atomic.AtomicBoolean();
                FutureTask<Void> setup = new FutureTask<>(() -> {
                    var workspace = new com.aceos.ui.WorkspaceView(preview, () -> signedOut.set(true), () -> {});
                    reference.set(workspace);
                    var scene = new Scene(workspace, size[0], size[1]);
                    scene.getStylesheets().add(getClass().getResource("/com/aceos/ui/foundation.css").toExternalForm());
                    workspace.applyCss(); workspace.layout();
                    ((javafx.scene.control.CheckBox) workspace.lookup("#shell-reduced-motion")).fire();
                    assertEquals(1, workspace.getCenter().getOpacity(), "Reduced motion stops the current transition");
                    assertEquals(size[0] >= 960, workspace.getLeft() != null);
                    return null;
                });
                Platform.runLater(setup); setup.get(10, TimeUnit.SECONDS);
                awaitControl(reference.get(), "dashboard-refresh");
                FutureTask<Void> verify = new FutureTask<>(() -> {
                    var workspace = reference.get();
                    snapshot(workspace, size, "phase2-" + role.name().toLowerCase());
                    for (String id : java.util.List.of("shell-account", "shell-logout", "shell-menu", "shell-reduced-motion", "dashboard-refresh")) {
                        var control = workspace.lookup("#" + id);
                        var bounds = control.localToScene(control.getBoundsInLocal());
                        assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= size[0], id + " fits horizontally");
                        assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= size[1], id + " fits vertically");
                        assertTrue(control.isFocusTraversable(), id + " has keyboard access");
                    }
                    var dashboard = workspace.lookup("#role-dashboard");
                    var actions = dashboard.lookupAll(".button").stream()
                            .filter(n -> n.getId() != null && n.getId().startsWith("dashboard-action-"))
                            .map(n -> n.getId().substring("dashboard-action-".length())).distinct().toList();
                    for (String destination : actions) {
                        workspace.navigate(destination);
                        assertFalse(workspace.getCenter() instanceof DashboardView);
                        ((javafx.scene.control.Button) workspace.lookup("#shell-menu")).fire();
                        if (workspace.getLeft() == null) ((javafx.scene.control.Button) workspace.lookup("#shell-menu")).fire();
                        workspace.applyCss(); workspace.layout();
                        assertTrue(workspace.lookup("#nav-" + destination).getStyleClass().contains("nav-active"));
                    }
                    if (role != com.aceos.model.UserRole.ADMINISTRATOR) {
                        assertThrows(IllegalArgumentException.class, () -> workspace.navigate("accounts"));
                        assertThrows(IllegalArgumentException.class, () -> workspace.navigate("verify"));
                    }
                    if (role == com.aceos.model.UserRole.REQUESTER)
                        assertThrows(IllegalArgumentException.class, () -> workspace.navigate("complete-inspection"));
                    ((javafx.scene.control.Button) workspace.lookup("#shell-account")).fire();
                    assertEquals(1, workspace.getCenter().getOpacity());
                    assertTrue(workspace.lookupAll(".nav-active").isEmpty());
                    ((javafx.scene.control.Button) workspace.lookup("#shell-logout")).fire();
                    assertTrue(signedOut.get());
                    return null;
                });
                Platform.runLater(verify); verify.get(30, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void phase2LoadingFailureRetryAndEmptyState() throws Exception {
        var gate = new CountDownLatch(1);
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var reference = new java.util.concurrent.atomic.AtomicReference<DashboardView>();
        AceOsService preview = new AceOsService() {
            @Override public com.aceos.model.User signedInUser() {
                return new com.aceos.model.User(3, "Requester", "requester@aceos.local", com.aceos.model.UserRole.REQUESTER);
            }
            @Override public com.aceos.model.DashboardSummary getDashboardSummary() throws com.aceos.service.AceOsServiceException {
                assertFalse(Platform.isFxApplicationThread());
                if (attempts.incrementAndGet() == 1) {
                    try { gate.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    throw new com.aceos.service.AceOsServiceException("Example connection failure");
                }
                return new com.aceos.model.DashboardSummary(java.util.Map.of("open", 0, "resolved", 0), java.util.List.of());
            }
        };
        FutureTask<Void> setup = new FutureTask<>(() -> {
            var dashboard = new DashboardView(preview, route -> {});
            reference.set(dashboard);
            new Scene(dashboard, 800, 520);
            dashboard.applyCss(); dashboard.layout();
            assertTrue(dashboard.lookup("#dashboard-refresh").isDisabled());
            assertTrue(((javafx.scene.control.Label) dashboard.lookup("#dashboard-feedback")).getText().contains("Loading"));
            return null;
        });
        Platform.runLater(setup); setup.get(10, TimeUnit.SECONDS); gate.countDown();
        awaitControl(reference.get(), "dashboard-refresh");
        FutureTask<Void> retry = new FutureTask<>(() -> {
            var dashboard = reference.get();
            var button = (javafx.scene.control.Button) dashboard.lookup("#dashboard-refresh");
            assertEquals("Retry", button.getText());
            assertTrue(((javafx.scene.control.Label) dashboard.lookup("#dashboard-feedback")).getText().contains("Example connection failure"));
            assertEquals("—", ((javafx.scene.control.Label) dashboard.lookup("#dashboard-count-open")).getText());
            snapshot(dashboard, new int[] {800, 520}, "phase2-error");
            button.fire(); button.fire();
            assertTrue(button.isDisabled());
            return null;
        });
        Platform.runLater(retry); retry.get(10, TimeUnit.SECONDS);
        awaitControl(reference.get(), "dashboard-refresh");
        FutureTask<Void> verify = new FutureTask<>(() -> {
            var dashboard = reference.get();
            assertEquals(2, attempts.get(), "Duplicate refresh must be suppressed");
            assertEquals("0", ((javafx.scene.control.Label) dashboard.lookup("#dashboard-count-open")).getText());
            assertTrue(dashboard.lookupAll(".label").stream().anyMatch(n -> ((javafx.scene.control.Label) n).getText().contains("No complaints yet")));
            snapshot(dashboard, new int[] {800, 520}, "phase2-empty");
            return null;
        });
        Platform.runLater(verify); verify.get(10, TimeUnit.SECONDS);
    }

    private static final class FlowPreview extends AceOsService {
        final com.aceos.model.UserRole role;
        final com.aceos.model.Asset asset = new com.aceos.model.Asset(11, "AC-101", "Classroom air conditioner", "Cooling", "Room 101", com.aceos.model.AssetStatus.ACTIVE);
        final com.aceos.model.User requester = new com.aceos.model.User(3, "Example requester", "requester@aceos.local", com.aceos.model.UserRole.REQUESTER);
        final com.aceos.model.User technician = new com.aceos.model.User(5, "Example technician", "technician@aceos.local", com.aceos.model.UserRole.TECHNICIAN);
        volatile com.aceos.model.Complaint complaint;
        volatile com.aceos.model.WorkOrder work;
        volatile boolean failWrite, failRead;
        final java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        volatile CountDownLatch gate;
        volatile boolean techniciansAvailable = true;
        FlowPreview(com.aceos.model.UserRole role, boolean assigned) {
            this.role = role;
            complaint = new com.aceos.model.Complaint(42, asset, requester, "Air conditioner is not cooling. Please inspect the cable and compressor.", com.aceos.model.ComplaintPriority.HIGH);
            if (assigned) { complaint.accept(new com.aceos.model.User(1, "Admin", "admin@aceos.local", com.aceos.model.UserRole.ADMINISTRATOR)); work = new com.aceos.model.WorkOrder(51, complaint, technician); }
        }
        void background() { assertFalse(Platform.isFxApplicationThread()); }
        @Override public com.aceos.model.User signedInUser() { return new com.aceos.model.User(1, "Example user", "example@aceos.local", role); }
        @Override public java.util.List<com.aceos.model.Asset> getAllAssets() { background(); return java.util.List.of(asset); }
        @Override public java.util.List<com.aceos.model.User> getActiveTechnicians() { background(); return techniciansAvailable ? java.util.List.of(technician) : java.util.List.of(); }
        @Override public java.util.List<com.aceos.model.Complaint> getAllComplaints() throws com.aceos.service.AceOsServiceException {
            background(); reads.incrementAndGet();
            if (gate != null) try { gate.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); }
            if (failRead) throw new com.aceos.service.AceOsServiceException("Example read failure");
            return java.util.List.of(complaint);
        }
        @Override public java.util.List<com.aceos.model.WorkOrder> getAllWorkOrders() { background(); return work == null ? java.util.List.of() : java.util.List.of(work); }
        @Override public com.aceos.model.RepairDetail getRepairDetail(int id) { background(); assertEquals(42, id); return detail(); }
        @Override public com.aceos.model.RepairDetail getWorkDetail(int id) { background(); assertEquals(51, id); return detail(); }
        com.aceos.model.RepairDetail detail() {
            return new com.aceos.model.RepairDetail(complaint, work, work != null && work.getCompletedAt() != null ? "Cable replaced and cooling tested" : null,
                    java.util.List.of(new com.aceos.model.RepairDetail.Event("Complaint reported", complaint.getReportedAt())));
        }
        void write() throws com.aceos.service.AceOsServiceException {
            background(); writes.incrementAndGet();
            if (gate != null) try { gate.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); }
            if (failWrite) throw new com.aceos.service.AceOsServiceException("Example stale action");
        }
        @Override public com.aceos.model.MaintenanceRecord completeWorkOrder(int id, String input) throws com.aceos.service.AceOsServiceException {
            write(); assertEquals("Cable replaced", input); work.complete(); return null;
        }
        @Override public com.aceos.model.WorkOrder assignWorkOrder(int id, int technicianId) throws com.aceos.service.AceOsServiceException {
            write(); assertEquals(42, id); assertEquals(5, technicianId);
            work = new com.aceos.model.WorkOrder(51, complaint, technician); return work;
        }
        @Override public com.aceos.model.WorkOrder verifyWorkOrder(int id) throws com.aceos.service.AceOsServiceException {
            write(); assertEquals(51, id);
            work.verify(new com.aceos.model.User(1, "Admin", "admin@aceos.local", com.aceos.model.UserRole.ADMINISTRATOR)); return work;
        }
        @Override public com.aceos.model.Complaint submitComplaint(int id, String input, com.aceos.model.ComplaintPriority priority) throws com.aceos.service.AceOsServiceException {
            write(); assertEquals(11, id); assertEquals("Cooling failure", input); assertEquals(com.aceos.model.ComplaintPriority.HIGH, priority); return complaint;
        }
        @Override public com.aceos.model.DashboardSummary getDashboardSummary() {
            background(); return new com.aceos.model.DashboardSummary(java.util.Map.of(), java.util.List.of(
                    new com.aceos.model.DashboardSummary.Item(role == com.aceos.model.UserRole.TECHNICIAN ? "Work order" : "Complaint",
                            role == com.aceos.model.UserRole.TECHNICIAN ? 51 : 42, "AC-101", "SUBMITTED", "Cooling failure")));
        }
    }

    private void fx(Callable<Void> operation) throws Exception {
        FutureTask<Void> task = new FutureTask<>(operation); Platform.runLater(task); task.get(15, TimeUnit.SECONDS);
    }
    private void fire(Region root, String id) { ((javafx.scene.control.Button) root.lookup("#" + id)).fire(); }

    private void awaitControl(Region root, String id) throws Exception {
        awaitFx(() -> {
            root.applyCss(); root.layout();
            var control = root.lookup("#" + id);
            return control != null && !control.isDisabled();
        }, true);
    }

    @Test
    void phase3ConnectedRoleLayoutsAndRecordNavigation() throws Exception {
        for (var role : com.aceos.model.UserRole.values()) for (int[] size : new int[][] {{800, 520}, {1280, 800}}) {
            var preview = new FlowPreview(role, role != com.aceos.model.UserRole.ADMINISTRATOR);
            var reference = new java.util.concurrent.atomic.AtomicReference<com.aceos.ui.WorkspaceView>();
            fx(() -> {
                var workspace = new com.aceos.ui.WorkspaceView(preview, () -> {}, () -> {}); reference.set(workspace);
                new Scene(workspace, size[0], size[1]); return null;
            });
            awaitControl(reference.get(), "dashboard-refresh");
            fx(() -> { fire(reference.get(), role == com.aceos.model.UserRole.TECHNICIAN ? "dashboard-record-work-order-51" : "dashboard-record-complaint-42"); return null; });
            awaitControl(reference.get(), "repair-refresh");
            fx(() -> {
                var root = reference.get();
                assertTrue(root.lookup("#repair-detail") != null, "Recent dashboard row opens its exact record");
                assertEquals(role == com.aceos.model.UserRole.ADMINISTRATOR, root.lookup("#repair-accept") != null);
                assertEquals(role == com.aceos.model.UserRole.TECHNICIAN, root.lookup("#repair-start") != null);
                snapshot(root, size, "phase3-detail-" + role.name().toLowerCase());
                fire(root, "repair-back"); return null;
            });
            awaitControl(reference.get(), "repair-refresh");
            fx(() -> {
                var root = reference.get(); var table = (javafx.scene.control.TableView<?>) root.lookup("#repair-table");
                assertEquals(1, table.getItems().size());
                ((javafx.scene.control.TextField) root.lookup("#repair-search")).setText("no matching record");
                assertTrue(table.getItems().isEmpty());
                ((javafx.scene.control.TextField) root.lookup("#repair-search")).clear();
                var status = (javafx.scene.control.ComboBox<String>) root.lookup("#repair-status");
                status.setValue(role == com.aceos.model.UserRole.TECHNICIAN ? "VERIFIED" : "REJECTED");
                assertTrue(table.getItems().isEmpty());
                status.setValue("ALL"); assertEquals(1, table.getItems().size());
                table.getSelectionModel().selectFirst();
                table.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.ENTER, false, false, false, false));
                return null;
            });
            awaitControl(reference.get(), "repair-refresh");
            fx(() -> { assertNotNull(reference.get().lookup("#repair-detail")); return null; });
        }
    }

    @Test
    void phase3CompletionFailureRetainsInputAndBlocksStaleDuplicateActions() throws Exception {
        var preview = new FlowPreview(com.aceos.model.UserRole.TECHNICIAN, true); preview.work.start(); preview.failWrite = true;
        var reference = new java.util.concurrent.atomic.AtomicReference<RepairFlowView>();
        fx(() -> { var root = new RepairFlowView(preview, true, null, 51); reference.set(root); new Scene(root, 800, 520); return null; });
        awaitControl(reference.get(), "repair-refresh");
        preview.gate = new CountDownLatch(1);
        fx(() -> {
            var root = reference.get(); ((javafx.scene.control.TextArea) root.lookup("#repair-completion")).setText("Cable replaced");
            fire(root, "repair-complete"); fire(root, "repair-complete");
            assertTrue(root.lookup("#repair-complete").isDisabled()); assertTrue(root.lookup("#repair-refresh").isDisabled()); return null;
        });
        preview.gate.countDown();
        awaitControl(reference.get(), "repair-refresh");
        fx(() -> {
            var root = reference.get(); assertEquals(1, preview.writes.get());
            assertEquals("Cable replaced", ((javafx.scene.control.TextArea) root.lookup("#repair-completion")).getText());
            assertTrue(root.lookup("#repair-complete").isDisabled());
            assertTrue(((javafx.scene.control.Label) root.lookup("#repair-feedback")).getText().contains("Example stale action"));
            fire(root, "repair-refresh"); return null;
        });
        awaitControl(reference.get(), "repair-refresh");
        preview.failWrite = false;
        fx(() -> { assertFalse(reference.get().lookup("#repair-complete").isDisabled()); fire(reference.get(), "repair-complete"); return null; });
        awaitControl(reference.get(), "repair-refresh");
        fx(() -> { assertEquals(2, preview.writes.get()); assertNull(reference.get().lookup("#repair-complete")); return null; });
    }

    @Test
    void phase3ReportSearchRecoveryAndListRetry() throws Exception {
        var preview = new FlowPreview(com.aceos.model.UserRole.REQUESTER, false); preview.failWrite = true;
        var reference = new java.util.concurrent.atomic.AtomicReference<ComplaintManagementView>();
        fx(() -> { var root = new ComplaintManagementView(preview); reference.set(root); new Scene(root, 1280, 800); return null; });
        awaitControl(reference.get(), "report-refresh");
        fx(() -> {
            var root = reference.get();
            var search = (javafx.scene.control.TextField) root.lookup("#report-asset-search"); search.setText("Room 101");
            var choices = (javafx.scene.control.ComboBox<?>) root.lookup("#report-asset-choice"); assertEquals(1, choices.getItems().size()); choices.getSelectionModel().selectFirst();
            ((javafx.scene.control.TextArea) root.lookup("#report-description")).setText("Cooling failure");
            ((javafx.scene.control.ComboBox<com.aceos.model.ComplaintPriority>) root.lookup("#report-priority")).setValue(com.aceos.model.ComplaintPriority.HIGH);
            snapshot(root, new int[] {1280, 800}, "phase3-report");
            fire(root, "report-submit"); fire(root, "report-submit"); return null;
        });
        awaitControl(reference.get(), "report-submit");
        fx(() -> {
            var root = reference.get(); assertEquals(1, preview.writes.get());
            assertEquals("Cooling failure", ((javafx.scene.control.TextArea) root.lookup("#report-description")).getText());
            assertNotNull(((javafx.scene.control.ComboBox<?>) root.lookup("#report-asset-choice")).getValue());
            return null;
        });
        preview.failWrite = false;
        fx(() -> { fire(reference.get(), "report-submit"); return null; });
        awaitControl(reference.get(), "report-submit");
        fx(() -> { assertTrue(((javafx.scene.control.TextArea) reference.get().lookup("#report-description")).getText().isEmpty()); assertTrue(reference.get().lookup("#report-progress").isVisible()); return null; });
        preview.failRead = true; preview.gate = new CountDownLatch(1);
        var list = new java.util.concurrent.atomic.AtomicReference<RepairFlowView>();
        fx(() -> { var root = new RepairFlowView(preview, false, null); list.set(root); new Scene(root, 800, 520); root.applyCss(); root.layout(); fire(root, "repair-refresh"); return null; });
        preview.gate.countDown();
        awaitControl(list.get(), "repair-refresh");
        fx(() -> { assertEquals(1, preview.reads.get()); assertEquals("Retry", ((javafx.scene.control.Button) list.get().lookup("#repair-refresh")).getText()); return null; });
        preview.failRead = false;
        fx(() -> { fire(list.get(), "repair-refresh"); fire(list.get(), "repair-refresh"); return null; });
        awaitControl(list.get(), "repair-refresh");
        fx(() -> { assertEquals(2, preview.reads.get()); assertEquals(1, ((javafx.scene.control.TableView<?>) list.get().lookup("#repair-table")).getItems().size()); snapshot(list.get(), new int[] {800, 520}, "phase3-list"); return null; });
    }

    @Test
    void phase3AssignmentVerificationAndScrollableForms() throws Exception {
        for (int[] size : new int[][] {{800, 520}, {1280, 800}}) {
            var preview = new FlowPreview(com.aceos.model.UserRole.ADMINISTRATOR, false);
            preview.complaint.accept(preview.signedInUser());
            var reference = new java.util.concurrent.atomic.AtomicReference<RepairFlowView>();
            fx(() -> { var root = new RepairFlowView(preview, false, null, 42); reference.set(root); new Scene(root, size[0], size[1]); return null; });
            awaitControl(reference.get(), "repair-refresh");
            fx(() -> {
                var root = reference.get();
                var search = (javafx.scene.control.TextField) root.lookup("#repair-technician-search"); search.setText("technician@aceos");
                var choice = (javafx.scene.control.ComboBox<?>) root.lookup("#repair-technician-choice");
                assertEquals(1, choice.getItems().size()); choice.getSelectionModel().selectFirst();
                snapshot(root, size, "phase3-assignment-top", true);
                root.setVvalue(1); root.layout();
                snapshot(root, size, "phase3-assignment-bottom", true);
                var button = root.lookup("#repair-assign"); var bounds = button.localToScene(button.getBoundsInLocal());
                assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= size[1], "Assignment action is reachable by scrolling");
                assertTrue(button.isFocusTraversable()); fire(root, "repair-assign"); return null;
            });
            awaitControl(reference.get(), "repair-refresh");
            fx(() -> { assertEquals(1, preview.writes.get()); assertEquals(com.aceos.model.WorkOrderStatus.ASSIGNED, preview.work.getStatus()); return null; });
            preview.work.start(); preview.work.complete();
            fx(() -> { fire(reference.get(), "repair-refresh"); return null; });
            awaitControl(reference.get(), "repair-refresh");
            fx(() -> { assertNotNull(reference.get().lookup("#repair-verify")); fire(reference.get(), "repair-verify"); return null; });
            awaitControl(reference.get(), "repair-refresh");
            fx(() -> { assertEquals(com.aceos.model.ComplaintStatus.RESOLVED, preview.complaint.getStatus()); assertNull(reference.get().lookup("#repair-verify")); return null; });
            var empty = new FlowPreview(com.aceos.model.UserRole.ADMINISTRATOR, false);
            empty.complaint.accept(empty.signedInUser()); empty.techniciansAvailable = false;
            fx(() -> { var root = new RepairFlowView(empty, false, null, 42); reference.set(root); new Scene(root, size[0], size[1]); return null; });
            awaitControl(reference.get(), "repair-refresh");
            fx(() -> { assertTrue(reference.get().lookup("#repair-assign").isDisabled()); return null; });
            var report = new java.util.concurrent.atomic.AtomicReference<ComplaintManagementView>();
            fx(() -> { var root = new ComplaintManagementView(new FlowPreview(com.aceos.model.UserRole.REQUESTER, false)); report.set(root); new Scene(root, size[0], size[1]); return null; });
            awaitControl(report.get(), "report-refresh");
            fx(() -> {
                var root = report.get(); snapshot(root, size, "phase3-report-top", true);
                root.setVvalue(1); root.layout(); snapshot(root, size, "phase3-report-bottom", true);
                var button = root.lookup("#report-submit"); var bounds = button.localToScene(button.getBoundsInLocal());
                assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= size[1], "Submission action is reachable by scrolling"); return null;
            });
        }
    }

    private static final class InspectionPreview extends AceOsService {
        final com.aceos.model.UserRole role;
        final com.aceos.model.Asset asset = new com.aceos.model.Asset(11, "AC-101", "Classroom air conditioner", "Cooling", "Room 101", com.aceos.model.AssetStatus.ACTIVE);
        final com.aceos.model.User technician = new com.aceos.model.User(5, "Example technician", "tech@aceos.local", com.aceos.model.UserRole.TECHNICIAN);
        volatile com.aceos.model.AssetInspection inspection;
        volatile boolean failRead, failWrite, empty, noTechnicians;
        volatile CountDownLatch gate;
        final java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
        InspectionPreview(com.aceos.model.UserRole role) {
            this.role = role;
            inspection = new com.aceos.model.AssetInspection(71, asset, "Electrical safety", java.time.LocalDate.now(), com.aceos.model.InspectionStatus.SCHEDULED, null, null, null);
            inspection.setAssignedTechnicianId(5);
        }
        void read() throws com.aceos.service.AceOsServiceException {
            assertFalse(Platform.isFxApplicationThread());
            if (failRead) throw new com.aceos.service.AceOsServiceException("Read failed");
        }
        void write() throws com.aceos.service.AceOsServiceException {
            assertFalse(Platform.isFxApplicationThread()); writes.incrementAndGet();
            if (gate != null) try { gate.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); }
            if (failWrite) throw new com.aceos.service.AceOsServiceException("Write failed");
        }
        @Override public com.aceos.model.User signedInUser() { return role == com.aceos.model.UserRole.TECHNICIAN ? technician : new com.aceos.model.User(1, "Administrator", "admin@aceos.local", role); }
        @Override public java.util.List<com.aceos.model.AssetInspection> getAllAssetInspections() throws com.aceos.service.AceOsServiceException { read(); return empty ? java.util.List.of() : java.util.List.of(inspection); }
        @Override public com.aceos.model.AssetInspection getAssetInspection(int id) throws com.aceos.service.AceOsServiceException { read(); assertEquals(71, id); return inspection; }
        @Override public java.util.List<com.aceos.model.Asset> getAllAssets() throws com.aceos.service.AceOsServiceException { read(); return empty ? java.util.List.of() : java.util.List.of(asset); }
        @Override public java.util.List<com.aceos.model.User> getActiveTechnicians() throws com.aceos.service.AceOsServiceException { read(); return noTechnicians ? java.util.List.of() : java.util.List.of(technician); }
        @Override public com.aceos.model.AssetInspection scheduleAssetInspection(int id, String type, java.time.LocalDate date) throws com.aceos.service.AceOsServiceException {
            assertEquals(11, id); write(); inspection = new com.aceos.model.AssetInspection(71, asset, type, date, com.aceos.model.InspectionStatus.SCHEDULED, null, null, null); return inspection;
        }
        @Override public void assignInspection(int id, int tech) throws com.aceos.service.AceOsServiceException { assertEquals(71, id); assertEquals(5, tech); write(); inspection.setAssignedTechnicianId(tech); }
        @Override public com.aceos.model.AssetInspection completeAssetInspection(int id, com.aceos.model.InspectionStatus result, String notes, java.time.LocalDate date) throws com.aceos.service.AceOsServiceException {
            assertEquals(71, id); write(); inspection.complete(technician, result, notes, date); return inspection;
        }
        @Override public com.aceos.model.DashboardSummary getDashboardSummary() {
            return new com.aceos.model.DashboardSummary(java.util.Map.of(), java.util.List.of(new com.aceos.model.DashboardSummary.Item("Inspection", 71, "AC-101", "SCHEDULED", "Electrical safety")));
        }
    }

    @Test
    void inspectionLayoutsExactNavigationSchedulingAndAssignment() throws Exception {
        for (var role : new com.aceos.model.UserRole[] {com.aceos.model.UserRole.ADMINISTRATOR, com.aceos.model.UserRole.TECHNICIAN}) {
            for (int[] size : new int[][] {{800,520}, {1280,800}}) {
                var service = new InspectionPreview(role);
                var root = new java.util.concurrent.atomic.AtomicReference<com.aceos.ui.WorkspaceView>();
                fx(() -> { root.set(new com.aceos.ui.WorkspaceView(service, () -> {}, () -> {})); new Scene(root.get(), size[0], size[1]); return null; });
                awaitControl(root.get(), "dashboard-refresh");
                fx(() -> { fire(root.get(), "dashboard-record-inspection-71"); return null; });
                awaitControl(root.get(), "inspection-refresh");
                fx(() -> {
                    assertTrue(((javafx.scene.layout.VBox)root.get().lookup("#inspection-detail")).getChildren().stream().anyMatch(n -> n instanceof javafx.scene.control.Label l && l.getText().contains("Electrical safety")));
                    snapshot(root.get(), size, "inspection-detail-" + role, true);
                    var flow = (InspectionFlowView)root.get().getCenter(); flow.setVvalue(1); flow.layout();
                    var bounds = flow.lookup("#inspection-save").localToScene(flow.lookup("#inspection-save").getBoundsInLocal());
                    assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= size[1]);
                    snapshot(root.get(), size, "inspection-actions-" + role, true);
                    fire(root.get(), "inspection-back"); return null;
                });
                awaitControl(root.get(), "inspection-refresh");
                fx(() -> {
                    var table = (javafx.scene.control.TableView<?>)root.get().lookup("#inspection-table"); table.getSelectionModel().selectFirst();
                    snapshot(root.get(), size, "inspection-list-" + role, true);
                    table.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",javafx.scene.input.KeyCode.ENTER,false,false,false,false)); return null;
                });
                awaitControl(root.get(), "inspection-refresh");
                if (role == com.aceos.model.UserRole.ADMINISTRATOR) {
                    fx(() -> { root.get().navigate("schedule-inspection"); return null; });
                    awaitControl(root.get(), "inspection-refresh");
                    fx(() -> {
                        ((javafx.scene.control.ComboBox<?>)root.get().lookup("#inspection-asset-choice")).getSelectionModel().selectFirst();
                        ((javafx.scene.control.TextField)root.get().lookup("#inspection-type")).setText("Fire safety");
                        snapshot(root.get(), size, "inspection-schedule", true);
                        var flow = (InspectionFlowView)root.get().getCenter(); flow.setVvalue(1); flow.layout();
                        var bounds = flow.lookup("#inspection-create").localToScene(flow.lookup("#inspection-create").getBoundsInLocal());
                        assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= size[1]);
                        snapshot(root.get(), size, "inspection-schedule-action", true);
                        fire(root.get(), "inspection-create"); fire(root.get(), "inspection-create"); return null;
                    });
                    awaitControl(root.get(), "inspection-refresh");
                    fx(() -> { assertEquals(1, service.writes.get()); ((javafx.scene.control.ComboBox<?>)root.get().lookup("#inspection-technician-choice")).getSelectionModel().selectFirst(); fire(root.get(), "inspection-save"); return null; });
                    awaitControl(root.get(), "inspection-refresh"); assertEquals(2, service.writes.get());
                }
            }
        }
    }

    @Test
    void inspectionFailureRetainsInputBlocksDuplicatesAndRecovers() throws Exception {
        var service = new InspectionPreview(com.aceos.model.UserRole.TECHNICIAN);
        var root = new java.util.concurrent.atomic.AtomicReference<InspectionFlowView>();
        fx(() -> { root.set(new InspectionFlowView(service, null, 71, false)); new Scene(root.get(),800,520); return null; });
        awaitControl(root.get(), "inspection-refresh"); service.failWrite = true; service.gate = new CountDownLatch(1);
        fx(() -> {
            ((javafx.scene.control.TextArea)root.get().lookup("#inspection-notes")).setText("Cable insulation failed");
            ((javafx.scene.control.ComboBox<com.aceos.model.InspectionStatus>)root.get().lookup("#inspection-result")).setValue(com.aceos.model.InspectionStatus.FAILED);
            fire(root.get(), "inspection-save"); fire(root.get(), "inspection-save"); fire(root.get(), "inspection-refresh"); return null;
        });
        service.gate.countDown(); awaitControl(root.get(), "inspection-refresh");
        fx(() -> {
            assertEquals(1, service.writes.get()); assertTrue(root.get().lookup("#inspection-save").isDisabled());
            assertEquals("Cable insulation failed", ((javafx.scene.control.TextArea)root.get().lookup("#inspection-notes")).getText());
            service.failWrite = false; service.gate = null; fire(root.get(), "inspection-refresh"); return null;
        });
        awaitControl(root.get(), "inspection-refresh"); fx(() -> { fire(root.get(), "inspection-save"); return null; });
        awaitControl(root.get(), "inspection-refresh");
        fx(() -> {
            assertEquals(2, service.writes.get()); assertNull(root.get().lookup("#inspection-save"));
            assertTrue(((javafx.scene.control.Label)root.get().lookup("#inspection-feedback")).getText().contains("safety hold"));
            service.failRead = true; fire(root.get(), "inspection-back"); return null;
        });
        awaitControl(root.get(), "inspection-refresh");
        fx(() -> { assertTrue(((javafx.scene.control.TableView<?>)root.get().lookup("#inspection-table")).getItems().isEmpty()); service.failRead = false; service.empty = true; fire(root.get(), "inspection-refresh"); return null; });
        awaitControl(root.get(), "inspection-refresh");
        fx(() -> { assertTrue(((javafx.scene.control.TableView<?>)root.get().lookup("#inspection-table")).getItems().isEmpty()); service.empty = false; fire(root.get(), "inspection-refresh"); return null; });
        awaitControl(root.get(), "inspection-refresh");
        fx(() -> {
            var search = (javafx.scene.control.TextField)root.get().lookup("#inspection-search"); var table = (javafx.scene.control.TableView<?>)root.get().lookup("#inspection-table");
            search.setText("missing"); assertTrue(table.getItems().isEmpty()); search.setText("Room 101"); assertEquals(1,table.getItems().size()); return null;
        });
        var adminService = new InspectionPreview(com.aceos.model.UserRole.ADMINISTRATOR); adminService.noTechnicians = true;
        fx(() -> { root.set(new InspectionFlowView(adminService,null,71,false)); new Scene(root.get(),800,520); return null; });
        awaitControl(root.get(), "inspection-refresh"); fx(() -> { assertTrue(root.get().lookup("#inspection-save").isDisabled()); return null; });
    }

    @Test
    void inspectionAdministratorFailuresRetainSelectionsAndRequireRefresh() throws Exception {
        var service = new InspectionPreview(com.aceos.model.UserRole.ADMINISTRATOR);
        var root = new java.util.concurrent.atomic.AtomicReference<InspectionFlowView>();
        fx(() -> { root.set(new InspectionFlowView(service,null,null,true)); new Scene(root.get(),800,520); return null; });
        awaitControl(root.get(), "inspection-refresh"); service.failWrite = true;
        fx(() -> {
            ((javafx.scene.control.TextField)root.get().lookup("#inspection-asset-search")).setText("missing");
            var assets = (javafx.scene.control.ComboBox<?>)root.get().lookup("#inspection-asset-choice"); assertTrue(assets.getItems().isEmpty());
            ((javafx.scene.control.TextField)root.get().lookup("#inspection-asset-search")).setText("Room 101"); assets.getSelectionModel().selectFirst();
            ((javafx.scene.control.TextField)root.get().lookup("#inspection-type")).setText("Keep this type"); fire(root.get(), "inspection-create"); return null;
        });
        awaitControl(root.get(), "inspection-refresh");
        fx(() -> {
            assertEquals("Keep this type", ((javafx.scene.control.TextField)root.get().lookup("#inspection-type")).getText());
            assertNotNull(((javafx.scene.control.ComboBox<?>)root.get().lookup("#inspection-asset-choice")).getValue());
            assertTrue(root.get().lookup("#inspection-create").isDisabled()); service.failWrite = false; fire(root.get(), "inspection-refresh"); return null;
        });
        awaitControl(root.get(), "inspection-refresh"); fx(() -> { fire(root.get(), "inspection-create"); return null; });
        awaitControl(root.get(), "inspection-refresh"); service.failWrite = true; service.gate = new CountDownLatch(1);
        fx(() -> { ((javafx.scene.control.ComboBox<?>)root.get().lookup("#inspection-technician-choice")).getSelectionModel().selectFirst(); fire(root.get(), "inspection-save"); fire(root.get(), "inspection-save"); return null; });
        service.gate.countDown(); awaitControl(root.get(), "inspection-refresh");
        fx(() -> {
            assertEquals(3,service.writes.get()); assertNotNull(((javafx.scene.control.ComboBox<?>)root.get().lookup("#inspection-technician-choice")).getValue());
            assertTrue(root.get().lookup("#inspection-save").isDisabled()); service.failWrite = false; service.gate = null; fire(root.get(), "inspection-refresh"); return null;
        });
        awaitControl(root.get(), "inspection-refresh"); fx(() -> { fire(root.get(), "inspection-save"); return null; });
        awaitControl(root.get(), "inspection-refresh"); assertEquals(4,service.writes.get());
    }

    private static final class SchedulePreview extends AceOsService {
        final com.aceos.model.UserRole role;
        final com.aceos.model.Asset asset = new com.aceos.model.Asset(11, "AC-101", "Classroom air conditioner", "Cooling", "Room 101", com.aceos.model.AssetStatus.OUT_OF_SERVICE);
        volatile com.aceos.model.PreventiveMaintenanceSchedule schedule;
        volatile boolean failRead, failWrite, empty;
        volatile CountDownLatch gate;
        final java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
        SchedulePreview(com.aceos.model.UserRole role) {
            this.role = role;
            schedule = new com.aceos.model.PreventiveMaintenanceSchedule(81, asset, "Clean filters", 30, java.time.LocalDate.now().minusDays(1), com.aceos.model.MaintenanceScheduleStatus.ACTIVE);
        }
        void read() throws com.aceos.service.AceOsServiceException { assertFalse(Platform.isFxApplicationThread()); if (failRead) throw new com.aceos.service.AceOsServiceException("Read failed"); }
        void write() throws com.aceos.service.AceOsServiceException {
            assertFalse(Platform.isFxApplicationThread()); writes.incrementAndGet();
            if (gate != null) try { gate.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); }
            if (failWrite) throw new com.aceos.service.AceOsServiceException("Write failed");
        }
        @Override public com.aceos.model.User signedInUser() { return new com.aceos.model.User(1, "Example user", "example@aceos.local", role); }
        @Override public java.util.List<com.aceos.model.PreventiveMaintenanceSchedule> getAllPreventiveMaintenanceSchedules() throws com.aceos.service.AceOsServiceException { read(); return empty ? java.util.List.of() : java.util.List.of(schedule); }
        @Override public com.aceos.model.PreventiveMaintenanceSchedule getPreventiveMaintenanceSchedule(int id) throws com.aceos.service.AceOsServiceException { read(); assertEquals(81, id); return schedule; }
        @Override public java.util.List<com.aceos.model.Asset> getAllAssets() throws com.aceos.service.AceOsServiceException {
            read(); return empty ? java.util.List.of() : java.util.List.of(asset, new com.aceos.model.Asset(12,"RETIRED","Old asset","Cooling","Old room",com.aceos.model.AssetStatus.RETIRED));
        }
        @Override public com.aceos.model.PreventiveMaintenanceSchedule createPreventiveMaintenanceSchedule(int id, String task, int days, java.time.LocalDate due) throws com.aceos.service.AceOsServiceException {
            assertEquals(11,id); write(); schedule = new com.aceos.model.PreventiveMaintenanceSchedule(81,asset,task,days,due,com.aceos.model.MaintenanceScheduleStatus.ACTIVE); return schedule;
        }
        @Override public com.aceos.model.PreventiveMaintenanceSchedule pausePreventiveMaintenanceSchedule(int id) throws com.aceos.service.AceOsServiceException { assertEquals(81,id); write(); schedule.pause(); return schedule; }
        @Override public com.aceos.model.PreventiveMaintenanceSchedule activatePreventiveMaintenanceSchedule(int id) throws com.aceos.service.AceOsServiceException { assertEquals(81,id); write(); schedule.activate(); return schedule; }
        @Override public com.aceos.model.PreventiveMaintenanceSchedule completePreventiveMaintenanceSchedule(int id, java.time.LocalDate date) throws com.aceos.service.AceOsServiceException { assertEquals(81,id); write(); schedule.recordCompletion(date); return schedule; }
        @Override public com.aceos.model.DashboardSummary getDashboardSummary() { return new com.aceos.model.DashboardSummary(java.util.Map.of("overdueSchedules", 1), java.util.List.of()); }
    }

    @Test
    void scheduleLayoutsExactNavigationCreationAndRoleActions() throws Exception {
        for (var role : new com.aceos.model.UserRole[] {com.aceos.model.UserRole.ADMINISTRATOR, com.aceos.model.UserRole.TECHNICIAN}) {
            for (int[] size : new int[][] {{800,520}, {1280,800}}) {
                var service = new SchedulePreview(role);
                var root = new java.util.concurrent.atomic.AtomicReference<com.aceos.ui.WorkspaceView>();
                fx(() -> { root.set(new com.aceos.ui.WorkspaceView(service, () -> {}, () -> {})); new Scene(root.get(),size[0],size[1]); return null; });
                awaitControl(root.get(), "dashboard-refresh");
                fx(() -> { fire(root.get(), role == com.aceos.model.UserRole.ADMINISTRATOR ? "dashboard-action-overdue-schedules" : "dashboard-action-schedule-directory"); return null; });
                awaitControl(root.get(), "schedule-refresh");
                fx(() -> {
                    assertEquals(role == com.aceos.model.UserRole.ADMINISTRATOR ? "OVERDUE" : "ALL", ((javafx.scene.control.ComboBox<?>)root.get().lookup("#schedule-filter")).getValue());
                    var table = (javafx.scene.control.TableView<?>)root.get().lookup("#schedule-table"); assertEquals(1,table.getItems().size()); table.getSelectionModel().selectFirst();
                    snapshot(root.get(),size,"schedule-list-" + role,true);
                    table.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",javafx.scene.input.KeyCode.ENTER,false,false,false,false)); return null;
                });
                awaitControl(root.get(), "schedule-refresh");
                fx(() -> {
                    assertTrue(((javafx.scene.layout.VBox)root.get().lookup("#schedule-detail")).getChildren().stream().anyMatch(n -> n instanceof javafx.scene.control.Label l && l.getText().contains("Clean filters")));
                    snapshot(root.get(),size,"schedule-detail-" + role,true);
                    if (role == com.aceos.model.UserRole.TECHNICIAN) { assertNull(root.get().lookup("#schedule-toggle")); assertNull(root.get().lookup("#schedule-complete")); }
                    else {
                        var flow = (PreventiveScheduleFlowView)root.get().getCenter(); flow.setVvalue(1); flow.layout();
                        var bounds = flow.lookup("#schedule-complete").localToScene(flow.lookup("#schedule-complete").getBoundsInLocal()); assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= size[1]);
                        snapshot(root.get(),size,"schedule-actions",true); fire(root.get(),"schedule-toggle");
                    }
                    return null;
                });
                if (role == com.aceos.model.UserRole.ADMINISTRATOR) {
                    awaitControl(root.get(), "schedule-refresh"); fx(() -> { assertNull(root.get().lookup("#schedule-complete")); fire(root.get(),"schedule-toggle"); return null; });
                    awaitControl(root.get(), "schedule-refresh"); fx(() -> { fire(root.get(),"schedule-complete"); return null; });
                    awaitControl(root.get(), "schedule-refresh"); assertEquals(java.time.LocalDate.now().plusDays(30),service.schedule.getNextDueDate());
                    fx(() -> { root.get().navigate("create-schedule"); return null; }); awaitControl(root.get(),"schedule-refresh");
                    fx(() -> {
                        var choices = (javafx.scene.control.ComboBox<?>)root.get().lookup("#schedule-asset-choice"); assertEquals(1,choices.getItems().size()); choices.getSelectionModel().selectFirst();
                        ((javafx.scene.control.TextField)root.get().lookup("#schedule-task")).setText("Inspect and clean filters"); ((javafx.scene.control.TextField)root.get().lookup("#schedule-frequency")).setText("60");
                        snapshot(root.get(),size,"schedule-create",true);
                        var flow = (PreventiveScheduleFlowView)root.get().getCenter(); flow.setVvalue(1); flow.layout();
                        var bounds = flow.lookup("#schedule-create").localToScene(flow.lookup("#schedule-create").getBoundsInLocal()); assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= size[1]);
                        snapshot(root.get(),size,"schedule-create-action",true); fire(root.get(),"schedule-create"); fire(root.get(),"schedule-create"); return null;
                    });
                    awaitControl(root.get(),"schedule-refresh"); assertEquals(4,service.writes.get()); assertEquals(60,service.schedule.getFrequencyDays());
                }
            }
        }
        var requester = new SchedulePreview(com.aceos.model.UserRole.REQUESTER);
        fx(() -> { var w = new com.aceos.ui.WorkspaceView(requester,() -> {},() -> {}); assertThrows(IllegalArgumentException.class, () -> w.navigate("schedule-directory")); var denied = new PreventiveScheduleFlowView(requester,null,null,true); assertNull(denied.lookup("#schedule-create")); return null; });
    }

    @Test
    void scheduleCreationFailureRetainsChoicesAndReadRecovery() throws Exception {
        var service = new SchedulePreview(com.aceos.model.UserRole.ADMINISTRATOR);
        var root = new java.util.concurrent.atomic.AtomicReference<PreventiveScheduleFlowView>();
        fx(() -> { root.set(new PreventiveScheduleFlowView(service,null,null,true)); new Scene(root.get(),800,520); return null; }); awaitControl(root.get(),"schedule-refresh");
        service.failWrite = true; service.gate = new CountDownLatch(1);
        fx(() -> {
            var search = (javafx.scene.control.TextField)root.get().lookup("#schedule-asset-search"); var choices = (javafx.scene.control.ComboBox<?>)root.get().lookup("#schedule-asset-choice");
            search.setText("missing"); assertTrue(choices.getItems().isEmpty()); search.setText("Room 101"); choices.getSelectionModel().selectFirst();
            ((javafx.scene.control.TextField)root.get().lookup("#schedule-task")).setText("Retained task"); ((javafx.scene.control.TextField)root.get().lookup("#schedule-frequency")).setText("45");
            fire(root.get(),"schedule-create"); fire(root.get(),"schedule-create"); fire(root.get(),"schedule-refresh"); return null;
        }); service.gate.countDown(); awaitControl(root.get(),"schedule-refresh");
        fx(() -> {
            assertEquals(1,service.writes.get()); assertTrue(root.get().lookup("#schedule-create").isDisabled());
            assertEquals("Retained task",((javafx.scene.control.TextField)root.get().lookup("#schedule-task")).getText()); assertEquals("45",((javafx.scene.control.TextField)root.get().lookup("#schedule-frequency")).getText());
            assertNotNull(((javafx.scene.control.ComboBox<?>)root.get().lookup("#schedule-asset-choice")).getValue());
            service.failWrite = false; service.gate = null; fire(root.get(),"schedule-refresh"); return null;
        }); awaitControl(root.get(),"schedule-refresh"); fx(() -> { fire(root.get(),"schedule-create"); return null; }); awaitControl(root.get(),"schedule-refresh"); assertEquals(2,service.writes.get());
        service.failRead = true; fx(() -> { fire(root.get(),"schedule-back"); return null; }); awaitControl(root.get(),"schedule-refresh");
        fx(() -> { assertTrue(((javafx.scene.control.TableView<?>)root.get().lookup("#schedule-table")).getItems().isEmpty()); assertTrue(root.get().lookup("#schedule-open").isDisabled()); service.failRead = false; service.empty = true; fire(root.get(),"schedule-refresh"); return null; }); awaitControl(root.get(),"schedule-refresh");
        fx(() -> { assertTrue(((javafx.scene.control.TableView<?>)root.get().lookup("#schedule-table")).getItems().isEmpty()); service.empty = false; fire(root.get(),"schedule-refresh"); return null; }); awaitControl(root.get(),"schedule-refresh");
        fx(() -> {
            var search = (javafx.scene.control.TextField)root.get().lookup("#schedule-search"); var table = (javafx.scene.control.TableView<?>)root.get().lookup("#schedule-table");
            search.setText("missing"); assertTrue(table.getItems().isEmpty()); search.setText("Room 101"); assertEquals(1,table.getItems().size());
            ((javafx.scene.control.ComboBox<String>)root.get().lookup("#schedule-filter")).setValue("PAUSED"); assertTrue(table.getItems().isEmpty());
            ((javafx.scene.control.ComboBox<String>)root.get().lookup("#schedule-filter")).setValue("UPCOMING"); assertEquals(1,table.getItems().size()); return null;
        });
        service.empty = true; fx(() -> { root.set(new PreventiveScheduleFlowView(service,null,null,true)); new Scene(root.get(),800,520); return null; }); awaitControl(root.get(),"schedule-refresh");
        fx(() -> { assertTrue(root.get().lookup("#schedule-create").isDisabled()); return null; });
    }

    @Test
    void scheduleLifecycleFailuresBlockStaleActionsAndRetainCompletionDate() throws Exception {
        var service = new SchedulePreview(com.aceos.model.UserRole.ADMINISTRATOR); var root = new java.util.concurrent.atomic.AtomicReference<PreventiveScheduleFlowView>();
        fx(() -> { root.set(new PreventiveScheduleFlowView(service,null,81,false)); new Scene(root.get(),800,520); return null; }); awaitControl(root.get(),"schedule-refresh");
        var date = java.time.LocalDate.now().minusDays(2);
        service.failWrite = true; service.gate = new CountDownLatch(1);
        fx(() -> { ((javafx.scene.control.DatePicker)root.get().lookup("#schedule-completed-date")).setValue(date); fire(root.get(),"schedule-complete"); fire(root.get(),"schedule-complete"); fire(root.get(),"schedule-toggle"); return null; }); service.gate.countDown(); awaitControl(root.get(),"schedule-refresh");
        fx(() -> { assertEquals(1,service.writes.get()); assertTrue(root.get().lookup("#schedule-complete").isDisabled()); assertTrue(root.get().lookup("#schedule-toggle").isDisabled()); assertEquals(date,((javafx.scene.control.DatePicker)root.get().lookup("#schedule-completed-date")).getValue()); service.failWrite = false; service.gate = null; fire(root.get(),"schedule-refresh"); return null; }); awaitControl(root.get(),"schedule-refresh");
        fx(() -> { assertEquals(date,((javafx.scene.control.DatePicker)root.get().lookup("#schedule-completed-date")).getValue()); fire(root.get(),"schedule-complete"); return null; }); awaitControl(root.get(),"schedule-refresh"); assertEquals(date.plusDays(30),service.schedule.getNextDueDate());
        // A concurrent pause is revealed by refresh after a failed action.
        service.failWrite = true; fx(() -> { fire(root.get(),"schedule-toggle"); return null; }); awaitControl(root.get(),"schedule-refresh"); service.schedule.pause(); service.failWrite = false;
        fx(() -> { assertTrue(root.get().lookup("#schedule-toggle").isDisabled()); fire(root.get(),"schedule-refresh"); return null; }); awaitControl(root.get(),"schedule-refresh");
        fx(() -> { assertNull(root.get().lookup("#schedule-complete")); assertEquals("Activate schedule",((javafx.scene.control.Button)root.get().lookup("#schedule-toggle")).getText()); service.failWrite = true; fire(root.get(),"schedule-toggle"); return null; }); awaitControl(root.get(),"schedule-refresh");
        fx(() -> { assertTrue(root.get().lookup("#schedule-toggle").isDisabled()); service.failWrite = false; fire(root.get(),"schedule-refresh"); return null; }); awaitControl(root.get(),"schedule-refresh");
        fx(() -> { fire(root.get(),"schedule-toggle"); return null; }); awaitControl(root.get(),"schedule-refresh"); assertEquals(com.aceos.model.MaintenanceScheduleStatus.ACTIVE,service.schedule.getStatus());
        service.failRead = true; fx(() -> { fire(root.get(),"schedule-refresh"); return null; }); awaitControl(root.get(),"schedule-refresh"); fx(() -> { assertNull(root.get().lookup("#schedule-complete")); assertNull(root.get().lookup("#schedule-toggle")); return null; });
    }

    private static final class RegistrationPreview extends AceOsService {
        volatile boolean fail = true, failRead;
        volatile CountDownLatch gate;
        final java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
        @Override public com.aceos.model.User signedInUser() { return new com.aceos.model.User(1,"Admin","admin@aceos.local",com.aceos.model.UserRole.ADMINISTRATOR); }
        @Override public com.aceos.model.Asset registerAsset(String c, String n, String cat, String loc) throws com.aceos.service.AceOsServiceException {
            assertFalse(Platform.isFxApplicationThread()); writes.incrementAndGet();
            assertEquals("CODE",c); assertEquals("Projector",n); assertEquals("Equipment",cat); assertEquals("Room 301",loc);
            if (gate != null) try { gate.await(5,TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); }
            if (fail) throw new com.aceos.service.AceOsServiceException("Duplicate code or session denied");
            return new com.aceos.model.Asset(303,c,n,cat,loc,com.aceos.model.AssetStatus.ACTIVE);
        }
        @Override public com.aceos.model.AssetProfile getAssetProfile(int id) throws com.aceos.service.AceOsServiceException {
            assertFalse(Platform.isFxApplicationThread()); assertEquals(303,id);
            if (failRead) throw new com.aceos.service.AceOsServiceException("Read failed");
            return new com.aceos.model.AssetProfile(new com.aceos.model.Asset(id,"CODE","Projector","Equipment","Room 301",com.aceos.model.AssetStatus.ACTIVE),com.aceos.model.UserRole.ADMINISTRATOR,java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of());
        }
        @Override public java.util.List<com.aceos.model.Asset> getAllAssets() { assertFalse(Platform.isFxApplicationThread()); return java.util.List.of(); }
        @Override public com.aceos.model.DashboardSummary getDashboardSummary() { return new com.aceos.model.DashboardSummary(java.util.Map.of(),java.util.List.of()); }
    }

    @Test
    void registrationRecoveryDuplicatesLayoutsAndExactProfile() throws Exception {
        for (int[] size : new int[][] {{800,520},{1280,800}}) {
            var service = new RegistrationPreview(); service.gate = new CountDownLatch(1);
            var reference = new java.util.concurrent.atomic.AtomicReference<AssetManagementView>();
            fx(() -> {
                var root = new AssetManagementView(service); reference.set(root); new Scene(root,size[0],size[1]);
                root.applyCss(); root.layout();
                String[] ids = {"code","name","category","location"}, values = {"CODE","Projector","Equipment","Room 301"};
                for (int i=0;i<4;i++) ((javafx.scene.control.TextField)root.lookup("#registration-" + ids[i])).setText(values[i]);
                snapshot(root,size,"registration-top",true);
                var scroll = (javafx.scene.control.ScrollPane)root.getCenter(); scroll.setVvalue(1); root.layout();
                snapshot(root,size,"registration-action",true);
                var bounds = root.lookup("#registration-save").localToScene(root.lookup("#registration-save").getBoundsInLocal());
                assertTrue(bounds.getMinY()>=0 && bounds.getMaxY()<=size[1]);
                fire(root,"registration-save"); fire(root,"registration-save");
                assertTrue(root.lookup("#registration-code").isDisabled()); return null;
            });
            service.gate.countDown(); awaitControl(reference.get(),"registration-save");
            fx(() -> {
                assertEquals(1,service.writes.get());
                assertEquals("Room 301",((javafx.scene.control.TextField)reference.get().lookup("#registration-location")).getText());
                assertTrue(((javafx.scene.control.Label)reference.get().lookup("#registration-feedback")).getText().contains("retained"));
                service.fail=false; service.gate=null; fire(reference.get(),"registration-save"); return null;
            }); awaitControl(reference.get(),"registration-another");
            fx(() -> {
                assertEquals(2,service.writes.get()); assertTrue(reference.get().lookup("#registration-save").isDisabled());
                snapshot(reference.get(),size,"registration-success",true);
                var scroll = (javafx.scene.control.ScrollPane)reference.get().getCenter(); scroll.setVvalue(1); reference.get().layout();
                snapshot(reference.get(),size,"registration-success-actions",true);
                for (String id : new String[]{"registration-open","registration-another"}) {
                    var node = reference.get().lookup("#" + id); var bounds = node.localToScene(node.getBoundsInLocal());
                    assertTrue(bounds.getMinY()>=0 && bounds.getMaxY()<=size[1]);
                }
                fire(reference.get(),"registration-another");
                assertEquals("",((javafx.scene.control.TextField)reference.get().lookup("#registration-code")).getText());
                String[] ids = {"code","name","category","location"}, values = {"CODE","Projector","Equipment","Room 301"};
                for (int i=0;i<4;i++) ((javafx.scene.control.TextField)reference.get().lookup("#registration-" + ids[i])).setText(values[i]);
                fire(reference.get(),"registration-save"); return null;
            }); awaitControl(reference.get(),"registration-another");
            fx(() -> { service.failRead=true;
                fire(reference.get(),"registration-open"); return null;
            }); awaitControl(reference.get(),"asset-profile-refresh");
            fx(() -> { assertTrue(((javafx.scene.control.Label)reference.get().lookup("#asset-profile-feedback")).getText().contains("Read failed")); service.failRead=false; fire(reference.get(),"asset-profile-refresh"); return null; });
            awaitControl(reference.get(),"asset-profile-refresh");
            fx(() -> { assertTrue(((javafx.scene.control.Label)reference.get().lookup("#asset-profile-details .label")).getText().length()>0); fire(reference.get(),"asset-profile-back"); return null; });
            awaitControl(reference.get(),"asset-refresh");
            fx(() -> {
                for (var role : new com.aceos.model.UserRole[]{com.aceos.model.UserRole.REQUESTER,com.aceos.model.UserRole.TECHNICIAN})
                    assertNull(new AssetManagementView(new AssetPreview(role)).lookup("#registration-save"));
                assertNull(new AssetManagementView(new AceOsService()).lookup("#registration-save")); return null;
            });
        }
    }

    @Test
    void registrationShellLayoutsAndRoleRoutes() throws Exception {
        for (int[] size : new int[][] {{800,520},{1280,800}}) {
            fx(() -> {
                var root = new com.aceos.ui.WorkspaceView(new RegistrationPreview(),() -> {},() -> {});
                new Scene(root,size[0],size[1]); root.resize(size[0],size[1]); root.navigate("register");
                snapshot(root,size,"registration-shell-top",true);
                var scroll = (javafx.scene.control.ScrollPane)((AssetManagementView)root.lookup("#asset-registration")).getCenter();
                scroll.setVvalue(1); root.layout(); snapshot(root,size,"registration-shell-action",true);
                var button = root.lookup("#registration-save"); var bounds = button.localToScene(button.getBoundsInLocal());
                assertTrue(bounds.getMinY()>=0 && bounds.getMaxY()<=size[1]);
                for (var role : new com.aceos.model.UserRole[]{com.aceos.model.UserRole.REQUESTER,com.aceos.model.UserRole.TECHNICIAN}) {
                    var denied = new com.aceos.ui.WorkspaceView(new AssetPreview(role),() -> {},() -> {});
                    assertThrows(IllegalArgumentException.class, () -> denied.navigate("register"));
                    assertNull(denied.lookup("#asset-registration"));
                }
                return null;
            });
        }
    }

    private void snapshot(Region root, int[] size, String name) throws Exception {
        snapshot(root, size, name, false);
    }

    private void snapshot(Region root, int[] size, String name, boolean scrollable) throws Exception {
        Scene scene = root.getScene() == null ? new Scene(root, size[0], size[1]) : root.getScene();
        scene.getStylesheets()
                .add(getClass().getResource("/com/aceos/ui/foundation.css").toExternalForm());
        root.resize(size[0], size[1]);
        root.applyCss();
        root.layout();
        for (var node : root.lookupAll(".text-input")) {
            var bounds = node.localToScene(node.getBoundsInLocal());
            assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= size[0]);
            if (!scrollable) assertTrue(
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

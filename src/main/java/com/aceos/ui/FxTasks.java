package com.aceos.ui;

import com.aceos.service.AceOsServiceException;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class FxTasks {
    private FxTasks() {}

    public static <T> void run(
            Callable<T> operation,
            Consumer<T> success,
            Consumer<AceOsServiceException> failure,
            Runnable finished) {
        Thread.ofVirtual()
                .name("aceos-database")
                .start(
                        () -> {
                            T result = null;
                            AceOsServiceException error = null;
                            try {
                                result = operation.call();
                            } catch (Exception e) {
                                error =
                                        e instanceof AceOsServiceException a
                                                ? a
                                                : new AceOsServiceException(
                                                        "The operation could not finish. Retry or"
                                                            + " check local setup.",
                                                        e);
                            }
                            final T value = result;
                            final AceOsServiceException problem = error;
                            Platform.runLater(
                                    () -> {
                                        try {
                                            if (problem == null) success.accept(value);
                                            else failure.accept(problem);
                                        } finally {
                                            finished.run();
                                        }
                                    });
                        });
    }
}

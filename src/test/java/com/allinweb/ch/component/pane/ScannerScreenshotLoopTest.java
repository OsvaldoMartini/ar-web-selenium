package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScannerScreenshotLoopTest {

    @Test
    void startSchedulesLoopAtFixedRate() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future)
                .when(scheduler)
                .scheduleAtFixedRate(any(Runnable.class), eq(0L), eq(500L), eq(TimeUnit.MILLISECONDS));
        RecordingOperations operations = new RecordingOperations();
        ScannerScreenshotLoop loop = new ScannerScreenshotLoop(scheduler, operations);

        loop.start();

        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(0L), eq(500L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void startIgnoresDuplicateStartWhenLoopIsRunning() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isCancelled()).thenReturn(false);
        when(future.isDone()).thenReturn(false);
        doReturn(future)
                .when(scheduler)
                .scheduleAtFixedRate(any(Runnable.class), eq(0L), eq(500L), eq(TimeUnit.MILLISECONDS));
        RecordingOperations operations = new RecordingOperations();
        ScannerScreenshotLoop loop = new ScannerScreenshotLoop(scheduler, operations);

        loop.start();
        loop.start();

        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(0L), eq(500L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void tickStopsLoopWhenJobIsNoLongerRunning() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future)
                .when(scheduler)
                .scheduleAtFixedRate(taskCaptor.capture(), eq(0L), eq(500L), eq(TimeUnit.MILLISECONDS));
        RecordingOperations operations = new RecordingOperations();
        operations.jobRunning = false;
        ScannerScreenshotLoop loop = new ScannerScreenshotLoop(scheduler, operations);

        loop.start();
        taskCaptor.getValue().run();

        verify(future).cancel(true);
        assertEquals(0, operations.screenshotSends);
    }

    @Test
    void tickSendsScreenshotWhenJobIsRunning() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future)
                .when(scheduler)
                .scheduleAtFixedRate(taskCaptor.capture(), eq(0L), eq(500L), eq(TimeUnit.MILLISECONDS));
        RecordingOperations operations = new RecordingOperations();
        operations.jobRunning = true;
        ScannerScreenshotLoop loop = new ScannerScreenshotLoop(scheduler, operations);

        loop.start();
        taskCaptor.getValue().run();

        verify(future, never()).cancel(true);
        assertEquals(1, operations.screenshotSends);
    }

    @Test
    void tickReportsScreenshotSendError() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future)
                .when(scheduler)
                .scheduleAtFixedRate(taskCaptor.capture(), eq(0L), eq(500L), eq(TimeUnit.MILLISECONDS));
        RecordingOperations operations = new RecordingOperations();
        operations.jobRunning = true;
        operations.screenshotSendFailure = new IllegalStateException("boom");
        ScannerScreenshotLoop loop = new ScannerScreenshotLoop(scheduler, operations);

        loop.start();
        taskCaptor.getValue().run();

        assertEquals(operations.screenshotSendFailure, operations.reportedError);
    }

    private static final class RecordingOperations implements ScannerScreenshotLoop.Operations {
        private boolean jobRunning;
        private int screenshotSends;
        private RuntimeException screenshotSendFailure;
        private Exception reportedError;

        @Override
        public boolean isJobRunning() {
            return jobRunning;
        }

        @Override
        public void sendScreenshotIfAvailable() {
            screenshotSends++;
            if (screenshotSendFailure != null) {
                throw screenshotSendFailure;
            }
        }

        @Override
        public void reportScreenshotLoopError(Exception error) {
            reportedError = error;
        }
    }
}

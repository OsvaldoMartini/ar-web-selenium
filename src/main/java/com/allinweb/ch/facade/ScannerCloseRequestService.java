package com.allinweb.ch.facade;

/** Coordinates scanner shell shutdown without depending on UI event types. */
public final class ScannerCloseRequestService {
    public void close(CloseRequest request) {
        request.interruptThreads();
        try {
            request.closeBrowserRuntime();
        } catch (Exception error) {
            request.closeFailed(error);
        } finally {
            try {
                request.shutdownExecutors();
            } catch (Exception error) {
                request.closeFailed(error);
            }
        }
    }

    public interface CloseRequest {
        void interruptThreads();

        void closeBrowserRuntime();

        void shutdownExecutors();

        void closeFailed(Exception error);
    }
}

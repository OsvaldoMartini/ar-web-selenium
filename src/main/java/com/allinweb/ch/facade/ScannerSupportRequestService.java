package com.allinweb.ch.facade;

public final class ScannerSupportRequestService {
    private static final String NO_BROWSER = "(no browser)";

    private final ScannerSupportRequestPublisher publisher;
    private final ScannerBrowserUrlService browserUrlService;

    public ScannerSupportRequestService() {
        this(new ScannerSupportRequestPublisher(), new ScannerBrowserUrlService());
    }

    ScannerSupportRequestService(ScannerSupportRequestPublisher publisher, ScannerBrowserUrlService browserUrlService) {
        this.publisher = publisher;
        this.browserUrlService = browserUrlService;
    }

    public String requestSupport(int homeBankingId, ScannerBrowserUrlService.Browser browser) {
        publisher.publishSupportRequest(homeBankingId, browserUrlService.currentUrlOr(NO_BROWSER, browser));
        return publisher.destinationSessionId();
    }

    public String requestElementsSupport(int homeBankingId, ScannerBrowserUrlService.Browser browser) {
        publisher.publishElementsSupportRequest(homeBankingId, browserUrlService.currentUrlOr(NO_BROWSER, browser));
        return publisher.destinationSessionId();
    }
}

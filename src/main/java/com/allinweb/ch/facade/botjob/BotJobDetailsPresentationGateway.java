package com.allinweb.ch.facade.botjob;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import java.io.File;
import java.util.function.BooleanSupplier;

/** Desktop presentation boundary for the React-backed Bot Job Details workspace. */
public interface BotJobDetailsPresentationGateway {
    void execute(Runnable operation);

    void showSurface(String sessionId, BotJobDetailsReactSessionContext.Context context);

    void showMainDashboard();

    void openOrganizations();

    void openScanner(HomeBankingLoadDTO homeBanking, BotJobLoadDTO botJob, BlockLoadDTO block);

    void closeScannerWebDrivers();

    void closeScanner();

    Integer currentScannerBotJobId();

    long startTestRun(
            BotJobLoadDTO botJob,
            int blockOrderNumber,
            String endpointUrl,
            boolean runSingleBlock,
            BooleanSupplier cancellationRequested);

    void cancelTestRunStartup();

    boolean stopTestRun(long executionId);

    boolean isTestRunComplete(long executionId);

    String testRunTerminalOutcome(long executionId);

    File chooseTransferFolder(String configuredPath);

    File chooseReport(File reportFolder);

    default void updateTitle(int homeBankingId, int botJobId) {}
}

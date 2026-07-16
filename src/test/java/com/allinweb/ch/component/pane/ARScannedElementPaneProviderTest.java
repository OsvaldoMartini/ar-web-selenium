package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.component.pane.base.IARPane;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ARScannedElementPaneProviderTest {
    private final ARScannedElementPaneProvider provider = ARScannedElementPaneProvider.getInstance();

    @AfterEach
    void cleanup() {
        provider.reset();
    }

    @Test
    void returnsInstalledPaneSupplierResult() {
        provider.installPaneSupplier(() -> null);

        assertNull(provider.currentPane());
    }

    @Test
    void exposesPaneViewOnlyWhenSupplierResultIsJavaFxPane() {
        PanePortView pane = new PanePortView();
        provider.installPaneSupplier(() -> pane);

        assertSame(pane, provider.currentPaneView());
    }

    @Test
    void returnsNoPaneViewForNonJavaFxPort() {
        provider.installPaneSupplier(() -> new NonPanePort());

        assertNull(provider.currentPaneView());
    }

    private static final class PanePortView extends NonPanePort implements IARPane {
        @Override
        public Pane createPane() {
            return new Pane();
        }

        @Override
        public Pane getPaneReference() {
            return null;
        }

        @Override
        public void initUIComponents() {}

        @Override
        public void initUIBehaviour() {}

        @Override
        public void clear() {}
    }

    private static class NonPanePort implements ARScannedElementPanePort {
        @Override
        public void initialize(com.allinweb.ch.driver.ARWebDriver arWebDriver, com.allinweb.ch.model.BotJobLoadDTO botJob, int portSocketInitial) {}

        @Override
        public void refreshBlocks(boolean keepSelection) {}

        @Override
        public boolean isJobRunning() {
            return false;
        }

        @Override
        public void closeLaunchWindowIfPresent() {}

        @Override
        public void destroy() {}

        @Override
        public void checkRunningProcess() {}

        @Override
        public void setTargetSelected(com.allinweb.ch.model.TargetElement target) {}

        @Override
        public com.allinweb.ch.model.TargetElement targetSelected() {
            return null;
        }

        @Override
        public void itPrintsElementDTO() {}

        @Override
        public void testingActions(com.allinweb.ch.model.TargetElement target, String actionType, String defaultValue) {}

        @Override
        public boolean isRealBlockSelectedForInsert() {
            return false;
        }

        @Override
        public void ensureBlockSelectedOrPrompt(Runnable afterSelection) {}

        @Override
        public int validateBlockDB(String tableName, int ownerId, String actionLabel) {
            return 0;
        }

        @Override
        public void prepareToInsertElementDTO(
                java.util.List<com.allinweb.ch.model.InstructionLoad> instructionList,
                int currentBlockId,
                int nextOrder,
                com.allinweb.ch.model.TargetElement target,
                boolean fromElementDto) {}

        @Override
        public void rememberPreviousXPath(String xpath) {}

        @Override
        public void applyActionDefaults(com.allinweb.ch.model.TargetElement targetElement) {}

        @Override
        public com.allinweb.ch.facade.ScannerTargetContext scannerTargetContext() {
            return null;
        }
    }
}

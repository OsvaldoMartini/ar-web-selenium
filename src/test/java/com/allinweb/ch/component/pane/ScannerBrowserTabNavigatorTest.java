package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerBrowserTabNavigatorTest {

    @Test
    void switchLeftIgnoresMissingDriver() {
        RecordingOperations operations = new RecordingOperations();
        ScannerBrowserTabNavigator navigator = new ScannerBrowserTabNavigator(operations);

        navigator.switchLeft();

        assertEquals(0, operations.switchCalls);
    }

    @Test
    void switchLeftMovesToPreviousTabAndUpdatesTitle() {
        RecordingOperations operations = new RecordingOperations();
        operations.hasCurrentDriver = true;
        operations.currentWindowHandleCount = 3;
        operations.currentTabIndex = 2;
        ScannerBrowserTabNavigator navigator = new ScannerBrowserTabNavigator(operations);

        navigator.switchLeft();

        assertEquals(1, operations.currentTabIndex);
        assertEquals("second", operations.switchedHandle);
        assertEquals(1, operations.titleUpdates);
    }

    @Test
    void switchRightMovesToNextTabAndUpdatesTitle() {
        RecordingOperations operations = new RecordingOperations();
        operations.hasCurrentDriver = true;
        operations.currentWindowHandleCount = 3;
        operations.currentTabIndex = 0;
        ScannerBrowserTabNavigator navigator = new ScannerBrowserTabNavigator(operations);

        navigator.switchRight();

        assertEquals(1, operations.currentTabIndex);
        assertEquals("second", operations.switchedHandle);
        assertEquals(1, operations.titleUpdates);
    }

    @Test
    void handleWindowHandlesChangeSwitchesToNewestKnownHandle() {
        RecordingOperations operations = new RecordingOperations();
        operations.hasCurrentDriver = true;
        operations.currentWindowHandleCount = 3;
        operations.knownWindowHandles = List.of("first", "second");
        operations.updatedWindowHandles = List.of("first", "second", "third");
        ScannerBrowserTabNavigator navigator = new ScannerBrowserTabNavigator(operations);

        navigator.handleWindowHandlesChange();

        assertEquals(1, operations.updateWindowHandlesListCalls);
        assertEquals(2, operations.currentTabIndex);
        assertEquals("third", operations.switchedHandle);
        assertEquals(1, operations.titleUpdates);
    }

    @Test
    void handleWindowHandlesChangeSkipsWhenHandleCountUnchanged() {
        RecordingOperations operations = new RecordingOperations();
        operations.hasCurrentDriver = true;
        operations.currentWindowHandleCount = 2;
        operations.knownWindowHandles = List.of("first", "second");
        ScannerBrowserTabNavigator navigator = new ScannerBrowserTabNavigator(operations);

        navigator.handleWindowHandlesChange();

        assertEquals(0, operations.updateWindowHandlesListCalls);
        assertEquals(0, operations.switchCalls);
    }

    private static final class RecordingOperations implements ScannerBrowserTabNavigator.Operations {
        private boolean hasCurrentDriver;
        private int currentWindowHandleCount;
        private int currentTabIndex;
        private List<String> knownWindowHandles = List.of("first", "second", "third");
        private List<String> updatedWindowHandles = List.of("first", "second", "third");
        private String switchedHandle;
        private int switchCalls;
        private int titleUpdates;
        private int updateWindowHandlesListCalls;

        @Override
        public boolean hasCurrentDriver() {
            return hasCurrentDriver;
        }

        @Override
        public int currentWindowHandleCount() {
            return currentWindowHandleCount;
        }

        @Override
        public int knownWindowHandleCount() {
            return knownWindowHandles.size();
        }

        @Override
        public int currentTabIndex() {
            return currentTabIndex;
        }

        @Override
        public void setCurrentTabIndex(int currentTabIndex) {
            this.currentTabIndex = currentTabIndex;
        }

        @Override
        public String windowHandleAt(int index) {
            return knownWindowHandles.get(index);
        }

        @Override
        public void switchToWindow(String windowHandle) {
            switchCalls++;
            switchedHandle = windowHandle;
        }

        @Override
        public String currentUrl() {
            return "https://current.example";
        }

        @Override
        public void updateSceneTitleWithCurrentUrl(String currentUrl) {
            titleUpdates++;
        }

        @Override
        public void updateWindowHandlesList() {
            updateWindowHandlesListCalls++;
            knownWindowHandles = updatedWindowHandles;
        }
    }
}

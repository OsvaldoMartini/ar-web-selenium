package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.ScannerElementTestActionService;
import com.allinweb.ch.facade.ScannerTargetContext;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.TargetElement;
import java.util.List;
import javafx.stage.Stage;

/** Narrow scene-facing port for the legacy AR Web Factory scanner pane. */
public interface ARScannedElementPanePort extends IARPane, ScannerElementTestActionService.PanePort {
    void initialize(ARWebDriver arWebDriver, BotJobLoadDTO botJob, int portSocketInitial);

    void setStage(Stage stage);

    void refreshBlocks(boolean keepSelection);

    boolean isJobRunning();

    void closeLaunchWindowIfPresent();

    void destroy();

    void checkRunningProcess();

    void setTargetSelected(TargetElement target);

    TargetElement targetSelected();

    void itPrintsElementDTO();

    void testingActions(TargetElement target, String actionType, String defaultValue);

    boolean isRealBlockSelectedForInsert();

    void ensureBlockSelectedOrPrompt(Runnable afterSelection);

    int validateBlockDB(String tableName, int ownerId, String actionLabel);

    void prepareToInsertElementDTO(
            List<InstructionLoad> instructionList,
            int currentBlockId,
            int nextOrder,
            TargetElement target,
            boolean fromElementDto);

    void rememberPreviousXPath(String xpath);

    void applyActionDefaults(TargetElement targetElement);

    ScannerTargetContext scannerTargetContext();
}

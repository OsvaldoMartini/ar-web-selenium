package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.HomeUrlDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ARSaveClonePane extends ARPane {

    protected static volatile ARSaveClonePane instance;

    // Private constructor to prevent instantiation
    private ARSaveClonePane() {
        // Initialize if necessary
        super();
    }

    public static ARSaveClonePane getInstance() {
        if (instance == null) {
            synchronized (ARSaveClonePane.class) {
                if (instance == null) {
                    instance = new ARSaveClonePane();
                }
            }
        }
        return instance;
    }

    public void initialize(BotJobLoadDTO selecBotJobDTO, List<BotJobLoadDTO> botJobList) {
        this.selecBotJobDTO = selecBotJobDTO;
        this.botJobList = botJobList;
    }

    private static final ARPropertyManager arPropertyManager;
    private static final PerformMessage performMessage;
    private static final PerformDataBase performDataBase;

    // Static block to initialize
    static {
        arPropertyManager = ARPropertyManager.getInstance();
        performMessage = PerformMessage.getInstance();
        performDataBase = PerformDataBase.getInstance();
    }

    private BotJobLoadDTO selecBotJobDTO;
    private List<BotJobLoadDTO> botJobList;
    // UI

    private Label nameLabel;
    private Label descriptionLabel;
    private Label defaultURLLabel;

    private TextField nameField;
    private TextField descriptionField;
    private TextField newUrl;

    private Button saveButton;

    private Pane mainPane;

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        // Labels
        nameLabel = new Label("Name of new Bot Job:");
        descriptionLabel = new Label("Description:");
        defaultURLLabel = new Label("URL Bot Job OR INSERT NEW");

        String labelStyle = "-fx-text-fill: blue; -fx-font-weight: bold; -fx-font-size: 14;";
        nameLabel.setStyle(labelStyle);
        descriptionLabel.setStyle(labelStyle);
        defaultURLLabel.setStyle(labelStyle);

        // Text fields
        nameField = new TextField(selecBotJobDTO.getName().trim());
        descriptionField = new TextField("Description");
        newUrl = new TextField(selecBotJobDTO.getHomeBankingLoadDTO().getUrl());

        nameField.setPrefWidth(400);
        descriptionField.setPrefWidth(400);
        newUrl.setPrefWidth(400);

        // Button
        saveButton = new Button("Clone Bot Job");
        saveButton.setPrefWidth(200);
        saveButton.setStyle("-fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;");

        // VBoxes for each field group
        VBox nameBox = new VBox(5, nameLabel, nameField);
        VBox descriptionBox = new VBox(5, descriptionLabel, descriptionField);
        VBox urlBox = new VBox(5, defaultURLLabel, newUrl);

        // Centered button
        HBox buttonBox = new HBox(saveButton);
        buttonBox.setAlignment(Pos.CENTER);

        // Main vertical layout
        VBox formVBox = new VBox(20); // spacing between sections
        formVBox.setAlignment(Pos.TOP_LEFT);
        formVBox.setPadding(new Insets(20));
        formVBox.getChildren().addAll(nameBox, descriptionBox, urlBox, buttonBox);

        // Anchor the VBox
        AnchorPane.setTopAnchor(formVBox, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(formVBox, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(formVBox, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(formVBox, ARConstants.SPACE_M);

        mainPane = new AnchorPane(formVBox);
    }

    @Override
    public void initUIBehaviour() {

        saveButton.setOnMouseClicked(e -> {
            String newBotJobName = nameField.getText().trim();
            String newDescription = descriptionField.getText().trim();

            // Clean Spaces
            Platform.runLater(() -> nameField.setText(newBotJobName));

            if (Strings.isNullOrEmpty(nameField.getText().trim())) {
                performMessage.errorMessage(
                        "Select a new Bot Job name", "There is NOT a name defined", null, null, null, 0);
                return;
            }

            BotJobLoadDTO existBotJob = botJobList.stream()
                    .filter(botJob -> botJob.getName().equals(newBotJobName))
                    .findFirst()
                    .orElse(null); //

            if (existBotJob != null) {
                performMessage.errorMessage(
                        "Bot Job Name Already Exists",
                        "The name you have entered is already in use.",
                        "Please choose a different name and try again.",
                        null,
                        null,
                        0);
                return;
            }

            String excelPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_EXCEL);
            String originalFilePath =
                    excelPath + "\\" + selecBotJobDTO.getName().trim() + ".xlsx";
            String newFilePath = excelPath + "\\" + newBotJobName + ".xlsx";
            boolean excelCreation = duplicateExcelFile(originalFilePath, newFilePath);
            if (!excelCreation) {
                performMessage.errorMessage(
                        "Error Duplicating File Excel",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Excel File Name:</span> ❌",
                        "<span style='color: #E65100; font-weight: bold;'>" + newBotJobName + ".xlsx" + "</span> ",
                        null,
                        null,
                        0);

                return;
            }

            if (!Strings.isNullOrEmpty(newUrl.getText())) {

                Stage stage = (Stage) ((Button) e.getSource()).getScene().getWindow();
                List<HomeUrlDTO> homeUrlDTOList = performDataBase.loadAllHomeURLByHomeId(
                        selecBotJobDTO.getHomeBankingLoadDTO().getId());

                if (!newUrl.getText()
                        .trim()
                        .equals(selecBotJobDTO.getHomeBankingLoadDTO().getUrl())) {

                    // Check if homeURLList contains a HomeUrlDTO with matching id and url
                    Optional<HomeUrlDTO> matchHomeUrl = homeUrlDTOList.stream()
                            .filter(homeUrl -> homeUrl.getId() != null
                                    && selecBotJobDTO.getHomeBankingId().equals(homeUrl.getHomeBankingId())
                                    && newUrl.getText().trim().equals(homeUrl.getUrl()))
                            .findFirst();

                    if (matchHomeUrl.isPresent()) {
                        HomeUrlDTO matchedHomeUrl = matchHomeUrl.get();
                        // Do something with matchedHomeUrl
                        System.out.println("Found matching HomeUrlDTO: id=" + matchedHomeUrl.getId() + ", url="
                                + matchedHomeUrl.getUrl());

                        cloneNewBotJob(matchedHomeUrl, newBotJobName, newDescription, stage);

                    } else {
                        System.out.println("No matching HomeUrlDTO found.");
                        try (Connection conn = performDataBase.getConnection()) {
                            int newHomeUrlId = performDataBase.loadNexHomeUrlData() + 1;

                            ErrorMessage errorMessage = performDataBase.insertHomeUrlChild(
                                    conn,
                                    selecBotJobDTO.getHomeBankingId(),
                                    newUrl.getText().trim(),
                                    newHomeUrlId);

                            if (errorMessage != null) {
                                performMessage.errorMessage(
                                        "Clone Bot Job Failed",
                                        errorMessage.getErrorTitle(),
                                        errorMessage.getErrorHeader(),
                                        "Verify  [INSERT] or [UPDATE] or [SELECT]",
                                        null,
                                        0);
                            } else {

                                HomeUrlDTO homeUrlDTO = new HomeUrlDTO();
                                homeUrlDTO.setId(newHomeUrlId);
                                homeUrlDTO.setHomeBankingId(selecBotJobDTO.getHomeBankingId());
                                homeUrlDTO.setUrl(newUrl.getText().trim());
                                cloneNewBotJob(homeUrlDTO, newBotJobName, newDescription, stage);
                            }

                        } catch (SQLException error) {
                            System.out.println(error.getMessage());
                        }
                    }

                } else {

                    // Check if homeURLList contains a HomeUrlDTO with matching id and url
                    Optional<HomeUrlDTO> matchHomeUrl = homeUrlDTOList.stream()
                            .filter(homeUrl -> homeUrl.getId() != null
                                    && selecBotJobDTO.getHomeBankingId().equals(homeUrl.getHomeBankingId())
                                    && newUrl.getText().trim().equals(homeUrl.getUrl()))
                            .findFirst();

                    if (matchHomeUrl.isPresent()) {
                        HomeUrlDTO matchedHomeUrl = matchHomeUrl.get();
                        // Do something with matchedHomeUrl
                        System.out.println("Found matching HomeUrlDTO: id=" + matchedHomeUrl.getId() + ", url="
                                + matchedHomeUrl.getUrl());

                        cloneNewBotJob(matchedHomeUrl, newBotJobName, newDescription, stage);
                    }
                }

            } else {
                performMessage.errorMessage(
                        "URL Field cannot be empty",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>There is NOT a URL defined</span>",
                        null,
                        null,
                        null,
                        0);
            }
        });
    }

    private void cloneNewBotJob(HomeUrlDTO homeUrlDTO, String newBotJobName, String newDescription, Stage stage) {
        try (Connection conn = performDataBase.getConnection()) {
            int newBotJobId = performDataBase.getMaxId(conn, "bot_job") + 1;

            if (newBotJobId > -1) {

                String[] arrayTables = {"block", "instruction", "reference", "complex_instruction", "variable"};
                ErrorMessage errorMessage = performDataBase.duplicateBotJobById(
                        conn,
                        homeUrlDTO.getHomeBankingId(),
                        homeUrlDTO.getId(),
                        selecBotJobDTO.getId(),
                        newBotJobId,
                        newBotJobName,
                        newDescription,
                        arrayTables);

                if (errorMessage == null) {
                    performMessage.showCustomModalDialogDragWin11(
                            "Success: Bot Job Duplicated",
                            "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Bot Job Duplication Successful!</span> ✅",
                            "<span style='color: #1976D2;'>New Bot Job Details:</span>",
                            "<span style='font-weight: bold;'>ID:</span> " + newBotJobId + "<br>"
                                    + "<span style='font-weight: bold;'>Name:</span> '" + newBotJobName + "'",
                            "<span style='font-style: italic;'>Description: " + newDescription + "</span>",
                            false,
                            "OK",
                            null,
                            0);

                } else {

                    String errorType = "Database error";
                    String errorDetail = "Verify  [INSERT] or [UPDATE] or [SELECT]";

                    performMessage.errorMessage(
                            "Error Encountered",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Operation Failed!</span> ❌",
                            "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> " + errorType,
                            "<span style='font-style: italic;'>Detail:</span> " + errorDetail,
                            null,
                            0);
                }
            } else {
                performMessage.errorMessage(
                        "Access Denied",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Access Denied!</span> 🔒",
                        "<span style='color: #E65100;'>Cannot access the file.</span>",
                        "<span style='font-style: italic;'>Verify if the file is currently open in another application.</span>",
                        "<span style='font-style: italic;'>Please close the file in other applications and try again.</span>",
                        0);
            }

            ARLogger.getInstance(ARSaveClonePane.class).finer("ARSaveClonePane Close()");
            Platform.runLater(() -> {
                stage.close();
            });
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
    }

    //    private void clearBotJob(BotJobDTO botJob) {
    //        Queue<BlockDTO> blocks = new LinkedList<>(botJob.getBlocks());
    //        PerformDataBase..removeAllEntity(blocks, BlockDTO.class);
    //    }

    private boolean duplicateExcelFile(String originalFilePath, String newFilePath) {
        try {
            // Load the existing Excel file
            FileInputStream fis = new FileInputStream(new File(originalFilePath));
            Workbook workbook = new XSSFWorkbook(fis);

            // Create a new file output stream for the new file (to a restricted folder)
            FileOutputStream fos = new FileOutputStream(new File(newFilePath));

            // Write the workbook data to the new file
            workbook.write(fos);

            // Close all streams
            fos.close();
            fis.close();
            return true;
        } catch (IOException e) {
            String errorMessage = "Error occurred while copying the Excel file.";
            String errorDetails = "An error occurred while attempting to clone the file.";

            // Check if the exception message contains "Access is denied"
            if (e.getMessage() != null && e.getMessage().contains("Access is denied")) {
                errorDetails =
                        "Access Denied: You do not have permission to write to this location. Please check your permissions.";
            } else if (e instanceof FileNotFoundException) {
                File file = new File(newFilePath);
                if (!file.exists()
                        && file.getParentFile() != null
                        && !file.getParentFile().canWrite()) {
                    errorDetails =
                            "You don't have permission to write in the specified folder. Please check the folder's write permissions.";
                } else {
                    errorDetails = "The specified file path is invalid or the file is already in use.";
                }
            }

            // Show the error message
            performMessage.errorMessage("Excel File Cloning Error", errorMessage, errorDetails, null, null, 0);
            return false;
        }
    }
}

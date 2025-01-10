package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.model.BlockDetailsDTO;
import com.allinweb.ch.component.pane.base.ABRPane;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.persistence.HomeBankingDTO;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import com.google.common.base.Strings;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javax.swing.*;

public class ABRExcelFilePane extends ABRPane {

    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;
    // Static block to initialize
    static {
        performDataBase = PerformDataBase.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    private static final ABRComponentBuilder builder = new ABRComponentBuilder();

    // UI Components
    Label pathExportLabel;
    Label fileExportLabel;

    TextField pathExport;
    TextField fileExport;

    Button pathExportButton;
    Button pathDeleteButton;

    Button saveButton;
    Button cancelButton;

    VBox pathGroup;

    AnchorPane mainPane;

    double buttonWidth = 200;

    private BlockDetailsDTO blockExcelDTO;

    public ABRExcelFilePane(BlockDetailsDTO blockExcelDTO) {
        this.blockExcelDTO = blockExcelDTO;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        ObservableList<HomeBankingDTO> homeBankingList =
                ABRSharedResources.getInstance().getEntityList(HomeBankingDTO.class);

        pathExportLabel = new Label("Export Path:");

        String excelPath = blockExcelDTO.getExportFile() != null
                        && !blockExcelDTO.getExportFile().isEmpty()
                ? blockExcelDTO.getExportFile()
                : "";

        String directory = "";
        String fileName = "";
        if (!excelPath.isEmpty()) {
            try {

                Path path = Paths.get(excelPath);
                directory = path.getParent() != null ? path.getParent().toString() : ""; // Get the directory path
                fileName = path.getFileName() != null ? path.getFileName().toString() : ""; // Get the file name

                if (fileName.equalsIgnoreCase("No Excel Export File")) {
                    fileName = "";
                }

                ABRLogger.getInstance(ABRScannedElementPane.class).info("Identified Directory: " + directory);
                ABRLogger.getInstance(ABRScannedElementPane.class).info("Identified File Name: " + fileName);
            } catch (Exception ex) {
                ABRLogger.getInstance(ABRScannedElementPane.class).severe("Excel Path  \nError: " + ex.getMessage());
            }

        } else {
            ABRLogger.getInstance(ABRScannedElementPane.class).info("No export file path provided.");
        }

        pathExport = createPathTextField(directory);
        pathExportButton = createPathButton();
        pathDeleteButton = createDeleteButton();
        fileExportLabel = new Label("File Name");
        fileExport = createPathTextField(fileName);
        //        AnchorPane exportGroup = new AnchorPane(pathExport, pathExportButton);

        GridPane gridPaneExport = new GridPane();
        //        gridPaneLog.setVgap(10);
        gridPaneExport.setHgap(10);
        // Set column constraints for pathLog (80%), sizeLog (15%), and pathLogButton (5%)
        ColumnConstraints colExp1 = new ColumnConstraints();
        colExp1.setPercentWidth(60);

        ColumnConstraints colExp2 = new ColumnConstraints();
        colExp2.setPercentWidth(30);

        ColumnConstraints colExp3 = new ColumnConstraints();
        colExp3.setPercentWidth(5);

        ColumnConstraints colExp4 = new ColumnConstraints();
        colExp4.setPercentWidth(5);

        gridPaneExport.getColumnConstraints().addAll(colExp1, colExp2, colExp3, colExp4);

        // Add labels in the first row
        gridPaneExport.add(pathExportLabel, 0, 0);
        gridPaneExport.add(fileExportLabel, 1, 0);

        // Add text fields in the second row
        gridPaneExport.add(pathExport, 0, 1);
        gridPaneExport.add(fileExport, 1, 1);

        // Add button in the second row, third column
        gridPaneExport.add(pathExportButton, 2, 1);

        // Add button in the second row, fourth column
        gridPaneExport.add(pathDeleteButton, 3, 1);

        // Set margin for pathLogButton to create spacing from right border
        GridPane.setMargin(pathExportButton, new Insets(0, 0, 0, 5));

        GridPane gridPaneButton = new GridPane();
        gridPaneButton.setHgap(5);

        // Set column constraints for each column to take up 33.33% of the grid width
        ColumnConstraints col1Button = new ColumnConstraints();
        col1Button.setPercentWidth(20);

        ColumnConstraints col2Button = new ColumnConstraints();
        col2Button.setPercentWidth(20);

        ColumnConstraints col3Button = new ColumnConstraints();
        col3Button.setPercentWidth(20);

        ColumnConstraints col4Button = new ColumnConstraints();
        col4Button.setPercentWidth(20);

        ColumnConstraints col5Button = new ColumnConstraints();
        col5Button.setPercentWidth(20);

        gridPaneButton.getColumnConstraints().addAll(col1Button, col2Button, col3Button, col4Button, col5Button);

        String css = getClass().getResource("/button.css").toExternalForm();

        saveButton = builder.buildButton("OK", ABRConstants.SPACE_L, Insets.EMPTY);
        saveButton.getStyleClass().add("ok-button");

        cancelButton = builder.buildButton("Close", ABRConstants.SPACE_L, Insets.EMPTY);
        cancelButton.getStyleClass().add("cancel-button");

        // Create HBox for instruction and cancel buttons
        HBox instructionButtonsRow = new HBox(10, saveButton, cancelButton);
        saveButton.setPrefWidth(buttonWidth);
        cancelButton.setPrefWidth(buttonWidth);
        instructionButtonsRow.setAlignment(Pos.BASELINE_RIGHT); // Align buttons to the right

        pathGroup = new VBox(gridPaneExport, gridPaneButton);

        //        AnchorPane.setTopAnchor(pathGroup, ABRConstants.SPACE_L + ABRConstants.SPACE_M);
        //        AnchorPane.setLeftAnchor(pathGroup, ABRConstants.SPACE_M);
        //        AnchorPane.setRightAnchor(pathGroup, ABRConstants.SPACE_M);

        //        mainPane = new AnchorPane(title, pathGroup, instructionButtonsRow);

        // Combine all HBoxes into a VBox for vertical alignment
        VBox vbox = new VBox(20);
        vbox.getChildren()
                .addAll(
                        pathGroup, // ComboBoxes row
                        instructionButtonsRow // Add Instruction and Cancel Buttons row
                        );
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(10)); // Padding around the VBox

        // Adjust VBox properties for better alignment
        VBox.setVgrow(vbox, Priority.ALWAYS);

        // Use AnchorPane to ensure the VBox resizes with the window
        mainPane = new AnchorPane(vbox);
        mainPane.getStylesheets().add(css);

        AnchorPane.setTopAnchor(vbox, 0.0);
        AnchorPane.setBottomAnchor(vbox, 0.0);
        AnchorPane.setLeftAnchor(vbox, 0.0);
        AnchorPane.setRightAnchor(vbox, 0.0);
    }

    @Override
    public void initUIBehaviour() {
        pathExportButton.setOnMouseClicked(e -> openChooserFor(pathExport, true));
        pathDeleteButton.setOnMouseClicked(e -> {
            pathExport.setText("");
            fileExport.setText("");
            saveConfigurations();
        });

        saveButton.setOnMouseClicked(e -> saveConfigurations());
        cancelButton.setOnMouseClicked((e) -> {
            Stage stage = (Stage) ((Button) e.getSource()).getScene().getWindow();
            stage.close();
        });
    }

    private void saveConfigurations() {

        String exportFile = "";

        if (Strings.isNullOrEmpty(pathExport.getText())) {
            fileExport.setText("");
        } else {
            String filePath = fileExport.getText().trim();
            fileExport.setText(filePath);

            String fileName = fileExport.getText().trim(); // Get the trimmed input

            if (Strings.isNullOrEmpty(fileName)) {

                Text variableText1Styled = new Text("File Name  Is Empty!");
                variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");

                VBox combinedTextContainer = new VBox();
                combinedTextContainer.setSpacing(5); // Add some sp

                combinedTextContainer.getChildren().add(variableText1Styled);

                performMessage.showAlertCombinedVBOX(
                        Alert.AlertType.ERROR, "Excel File Name", "Type  the File Name!", null, combinedTextContainer);
                return;
            }

            // Find the last dot (.) in the file name to identify an extension
            int lastDotIndex = fileName.lastIndexOf('.');
            if (lastDotIndex > 0) {
                // Remove the existing extension
                fileName = fileName.substring(0, lastDotIndex);
            }
            // Append .xlsx extension
            fileName += ".xlsx";
            // Set the corrected file name back to the text field
            fileExport.setText(fileName);

            exportFile = pathExport.getText() + "/" + fileExport.getText();
        }

        exportFile = exportFile.replace("\\", "/");

        boolean updateBlock = performDataBase.updateBlockExportFile(
                blockExcelDTO.getBotJobId(), blockExcelDTO.getBlockId(), exportFile);

        ABRSharedResources.getInstance().changeDbConnection();

        Text variableText1Styled = new Text(String.format("Export File \"%s\" Updated", exportFile));
        variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");

        if (!updateBlock) {
            variableText1Styled = new Text(String.format("Export File \"%s\" NOT Updated!", exportFile));
            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
        }

        VBox combinedTextContainer = new VBox();
        combinedTextContainer.setSpacing(5); // Add some sp

        combinedTextContainer.getChildren().add(variableText1Styled);

        performMessage.showAlertCombinedVBOX(
                updateBlock ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                "Update Bot-Job",
                updateBlock ? "Bot-Job Updated successfully!" : "Bot-Job NOT Update!\"",
                null,
                combinedTextContainer);
    }

    private TextField createPathTextField(ABRPropertyEnum property) {
        TextField textField = new TextField();
        textField.setText(ABRPropertyManager.getInstance().getProperty(property));
        AnchorPane.setTopAnchor(textField, ABRConstants.SPACE_ZERO);
        AnchorPane.setBottomAnchor(textField, ABRConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(textField, ABRConstants.SPACE_XL);
        AnchorPane.setLeftAnchor(textField, ABRConstants.SPACE_ZERO);
        return textField;
    }

    private TextField createPathTextField(String text) {
        TextField textField = new TextField();
        textField.setText(text);
        AnchorPane.setTopAnchor(textField, ABRConstants.SPACE_ZERO);
        AnchorPane.setBottomAnchor(textField, ABRConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(textField, ABRConstants.SPACE_XL);
        AnchorPane.setLeftAnchor(textField, ABRConstants.SPACE_ZERO);
        return textField;
    }

    private Button createPathButton() {
        Button button = builder.buildButton(
                "", ABRConstants.SPACE_L, ABRConstants.ICON_EXCEL2, ABRConstants.SPACE_M, new Insets(5D));
        button.setMaxWidth(ABRConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
    }

    private Button createDeleteButton() {
        Button button = builder.buildButton(
                "", ABRConstants.SPACE_L, ABRConstants.ICON_CROSS2, ABRConstants.SPACE_M, new Insets(5D));
        button.setMaxWidth(ABRConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
    }

    private void openChooserFor(TextField field, boolean isDirectory) {
        File startingPoint = new File(System.getProperty("user.dir"));
        String chosenPath = isDirectory ? openDirectoryChooserFor(startingPoint) : openFileChooserFor(startingPoint);
        field.setText(chosenPath);
    }

    private String openDirectoryChooserFor(File startingDirectory) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setInitialDirectory(startingDirectory);
        File chosenPath = chooser.showDialog(new Stage());
        return chosenPath.getAbsolutePath();
    }

    private String openFileChooserFor(File startingDirectory) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialDirectory(startingDirectory);
        File chosenPath = chooser.showOpenDialog(new Stage());
        return chosenPath.getAbsolutePath();
    }
}

package com.allinweb.ch.driver;

import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.scene.ARAlertScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.core.ARSharedResources;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.persistence.SearchReturn;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.openqa.selenium.By;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;

public class ARWebElementNew {

    private final ARComponentBuilder componentBuilder = new ARComponentBuilder();

    private BooleanProperty hiddenElement = new SimpleBooleanProperty(false);
    private BooleanProperty outputElement = new SimpleBooleanProperty(false);
    private BooleanProperty insertElement = new SimpleBooleanProperty(false);
    private BooleanProperty clickElement = new SimpleBooleanProperty(false);
    private BooleanProperty setValueElem = new SimpleBooleanProperty(false);
    private BooleanProperty getValueElem = new SimpleBooleanProperty(false);
    private BooleanProperty checkValueElem = new SimpleBooleanProperty(false);
    private BooleanProperty holdValueElem = new SimpleBooleanProperty(false);
    private BooleanProperty editingElement = new SimpleBooleanProperty(false);
    private BooleanProperty textElement = new SimpleBooleanProperty(false);
    private BooleanProperty toBeAddedElement = new SimpleBooleanProperty(false);
    private BooleanProperty isIdElement = new SimpleBooleanProperty(false);
    private StringProperty[] operationsElement = new StringProperty[0];

    private boolean isCheckValidator;

    private Integer instructionId;
    private Integer botJobId;
    private String instrName;
    private String instrOperation;

    private String elementId;
    private WebElement element;

    private SearchReturn searchReturn;
    private String mainXPath;
    private String mainCoordinates;
    private String nameFieldTitle;
    private String iFrameXPath;
    private String tagNameDefined;

    private String attributeValue;
    private WebElementTagNameEnum tagType;

    private String innerHTML;

    private Map<String, String> savedReferences = new HashMap<>();

    // graphic attributes
    private AnchorPane graphicRepresentation;
    private HBox elementPanel;
    private HBox actionPanel;

    private Label nameLabel;
    private Label operationLabel1;
    private Label operationLabel2;
    private Label operationLabel3;
    private Label spaceLabel;

    private TextField nameField;
    private String iFrame;

    private Button blockButton;
    private Button moveUpButton;
    private Button moveDownButton;
    private Button moreOptionsButton;
    private Button saveButton;
    private Button deleteButton;

    private ImageView hiddenImage;
    private ImageView outputImage;
    private ImageView clickImage;
    private ImageView insertImage;
    private ImageView textImage;
    private ImageView setImage;
    private ImageView getImage;
    private ImageView checkImage;
    private ImageView holdImage;

    // event handlers
    private Map<EventType, List<EventHandler>> eventHandlerMap = new HashMap<>();

    // Very important sequence on initiation
    private static ARPriorities arPriorities;
    private static final PerformMessage performMessage;
    // Static block to initialize
    static {
        arPriorities = ARPriorities.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    public ARWebElementNew(WebElement element, int jobId, WebElementTagNameEnum typeSearch, String iFrameXPath) {
        arPriorities.setJobId(jobId);
        this.tagType = typeSearch;
        this.iFrameXPath = iFrameXPath;
        initFromWebElement(element);
    }

    public ARWebElementNew(WebElement element, int jobId, WebElementTagNameEnum typeSearch) {
        arPriorities.setJobId(jobId);
        this.tagType = typeSearch;
        initFromWebElement(element);
    }

    public ARWebElementNew(SearchReturn searchReturn, int jobId) {
        arPriorities.setJobId(jobId);
        this.searchReturn = searchReturn;
        this.tagType = searchReturn.getTagType();
        this.attributeValue = searchReturn.getAttributeValue();
        this.tagNameDefined = searchReturn.getOriginalTagName();
        this.iFrameXPath = !Strings.isNullOrEmpty(searchReturn.getiFrameXPath()) ? searchReturn.getiFrameXPath() : null;

        //        this.attributeValue = element.getAttribute(searchReturn.getAttributeType());
        initFromWebElement(searchReturn.getElement());
    }

    public ARWebElementNew(
            Map.Entry<String, WebElement> entry, String attributeName, int jobId, WebElementTagNameEnum typeElement) {
        arPriorities.setJobId(jobId);
        WebElement element = entry.getValue();
        this.mainXPath = entry.getKey();
        this.attributeValue = element.getAttribute(attributeName);
        if (typeElement != null) {
            tagType = typeElement;
        }
        initFromWebElement(element);
    }

    public ARWebElementNew(WebElement element, String priority) {
        updatePriorities(priority, null);
        initFromWebElement(element);
    }

    private void updatePriorities(String priority, BlockLoopInstructionDTO instruction) {
        botJobId = instruction.getBlock().getBotJobDTO().getId();
        if (arPriorities.getJobId() == null) {
            arPriorities.setJobId(botJobId);
            if (instruction.getBlock().getBotJobDTO().getHomeBanking().getPriority() != null) {
                arPriorities.loadPrioritiesFromString(
                        instruction.getBlock().getBotJobDTO().getHomeBanking().getPriority());
            } else {
                arPriorities.loadPriorities();
            }
        } else if (arPriorities.getJobId() != botJobId) {
            arPriorities.setJobId(botJobId);
            if (instruction.getBlock().getBotJobDTO().getHomeBanking().getPriority() != null) {
                arPriorities.loadPrioritiesFromString(
                        instruction.getBlock().getBotJobDTO().getHomeBanking().getPriority());
            } else {
                arPriorities.loadPriorities();
            }
        }
    }

    private void initFromWebElement(WebElement element) {
        initUI();
        try {
            if (searchReturn != null
                    && searchReturn.getxPathWorkedFirst().equalsIgnoreCase(ARConstants.ABSOLUT_XPATH)) {
                savedReferences.put(
                        "absolutXPath",
                        searchReturn.getAbsolutXPath()); // Creates Seq to Fin element Via Instructions - 1
                savedReferences.put(
                        "currentXPath",
                        searchReturn.getCurrentXPath()); // Creates Seq to Fin element Via Instructions - 2
                savedReferences.put(
                        "customXPath",
                        searchReturn.getCustomXPath()); // Creates Seq to Fin element Via Instructions - 2
            } else if (searchReturn.getxPathWorkedFirst().equalsIgnoreCase(ARConstants.REGULAR_XPATH)) {
                savedReferences.put(
                        "currentXPath",
                        searchReturn.getCurrentXPath()); // Creates Seq to Fin element Via Instructions - 1
                savedReferences.put(
                        "absolutXPath",
                        searchReturn.getAbsolutXPath()); // Creates Seq to Fin element Via Instructions - 2
                savedReferences.put(
                        "customXPath",
                        searchReturn.getCustomXPath()); // Creates Seq to Fin element Via Instructions - 2
            }
            try {
                Rectangle coordinates = element.getRect();
                savedReferences.put(
                        "coordinates",
                        (coordinates.getX() + (coordinates.getWidth() / 2)) + ","
                                + (coordinates.getY() + (coordinates.getHeight() / 2)));
            } catch (Exception coords) {
                // Split the string into X and Y values
                if (Strings.isNullOrEmpty(searchReturn.getCoords())) {
                    String[] parts = searchReturn.getCoords().split(",");
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);

                    // Create a Rectangle object (assuming you want to use a width and height)
                    // For this example, I am using arbitrary width and height
                    int width = 100; // Replace with actual width
                    int height = 100; // Replace with actual height
                    Rectangle coordinates = new Rectangle(x, y, width, height);

                    // Calculate the new coordinates (center of the rectangle)
                    String newCoordinates = (coordinates.getX() + (coordinates.getWidth() / 2)) + ","
                            + (coordinates.getY() + (coordinates.getHeight() / 2));

                    // Store the result in savedReferences map
                    Map<String, String> savedReferences = new HashMap<>();
                    savedReferences.put("coordinates", newCoordinates);
                }
            }

        } catch (Exception ex) {
            throw ex;
        }

        if (searchReturn != null && !Strings.isNullOrEmpty(searchReturn.getDefinedName())) {
            nameLabel.setText(searchReturn.getDefinedName());
            nameField.setText(searchReturn.getDefinedName());
        } else {
            nameLabel.setText(ARConstants.DEFAULT_VALUE_NO_IDENTIFICATION);
            nameField.setText(ARConstants.DEFAULT_VALUE_NO_IDENTIFICATION);
        }

        // Identify if the element is an INPUT, BUTTON, or LABEL
        nameFieldTitle = nameField.getText();

        boolean isElementHidden = element.getAttribute("type") != null
                && element.getAttribute("type").equalsIgnoreCase("hidden");

        boolean isInput = this.tagNameDefined.equalsIgnoreCase("INPUT") && element.getAttribute("type") != null;
        boolean isButton = this.tagNameDefined.equalsIgnoreCase("BUTTON");
        boolean isLabel = this.tagNameDefined.equalsIgnoreCase("LABEL") && !Strings.isNullOrEmpty(element.getText());

        hiddenElement.setValue(false);
        outputElement.setValue(false);
        insertElement.setValue(false);
        clickElement.setValue(false);
        textElement.setValue(false);
        setValueElem.setValue(false);
        getValueElem.setValue(false);
        checkValueElem.setValue(false);
        holdValueElem.setValue(false);

        // Now proceed with the rest of your code
        if (!isElementHidden) {
            hiddenElement.setValue(false);

            if (tagType != null) {
                if (tagType.equals(WebElementTagNameEnum.BUTTON)) {
                    // Handle the button case (if BUTTON is forced)
                    clickElement.setValue(true);
                } else if (tagType.getValue().equalsIgnoreCase(ARConstants.HOLD)) {
                    // Handle the SET_VALUE case
                    holdValueElem.setValue(true);
                } else if (tagType.getValue().equalsIgnoreCase(ARConstants.SET_VALUE)) {
                    // Handle the SET_VALUE case
                    setValueElem.setValue(true);
                } else if (tagType.getValue().equalsIgnoreCase(ARConstants.GET_VALUE)) {
                    // Handle the GET_VALUE case
                    getValueElem.setValue(true);
                } else if (tagType.getValue().equalsIgnoreCase(ARConstants.CHECK_VALUE)) {
                    // Handle the CHECK_VALUE case
                    checkValueElem.setValue(true);
                } else if (tagType.getValue().equalsIgnoreCase(ARConstants.OUTPUT)) {
                    // Handle the OUTPUT case
                    outputElement.setValue(true);
                } else {
                    // Handle other cases like INPUT
                    outputElement.setValue(false);
                    clickElement.setValue(false);
                    textElement.setValue(false);

                    tagType = WebElementTagNameEnum.INPUT;
                    insertElement.setValue(true);
                }
            } else {
                // Default behavior based on element type
                if (isInput) {
                    insertElement.setValue(true); // Element is INPUT
                    tagType = WebElementTagNameEnum.INPUT;
                } else if (isButton) {
                    clickElement.setValue(true); // Element is BUTTON
                    tagType = WebElementTagNameEnum.BUTTON;
                } else if (isLabel) {
                    tagType = WebElementTagNameEnum.OUTPUT;
                    outputElement.setValue(true); // Element is LABEL (just text)
                } else {
                    // Handle other types of elements as needed
                    outputElement.setValue(false);
                    clickElement.setValue(true);
                    textElement.setValue(false);
                    insertElement.setValue(false);
                    tagType = WebElementTagNameEnum.BUTTON;
                }
            }
        } else {
            outputElement.setValue(false);
            clickElement.setValue(false);
            textElement.setValue(false);
            insertElement.setValue(false);
            hiddenElement.setValue(true);
            tagType = WebElementTagNameEnum.HIDDEN;
        }

        // this is goign to be done before and match the case
        //        xPath = ARWebUtil.extractWebElementXPath(element);
        elementId = ((RemoteWebElement) element).getId();
        this.element = element;
        toBeAddedElement.setValue(true);
    }

    private void initUI() {
        initUIComponents();
        initUIBehaviour();
    }

    private void initUIComponents() {
        initElementPanel();
        initActionPanel();

        graphicRepresentation = new AnchorPane(elementPanel, actionPanel);

        // graphicRepresentation.setBackground(new Background(new BackgroundFill(Color.RED, CornerRadii.EMPTY,
        // Insets.EMPTY)));;
        // graphicRepresentation.backgroundProperty().setValue(Background.fill(Color.WHITE));
    }

    private void initElementPanel() {
        hiddenImage = componentBuilder.buildImageView(ARConstants.ICON_HIDDEN, ARConstants.SPACE_M);
        outputImage = componentBuilder.buildImageView(ARConstants.ICON_OUTPUT, ARConstants.SPACE_M);
        clickImage = componentBuilder.buildImageView(ARConstants.ICON_CLICK, ARConstants.SPACE_M);
        insertImage = componentBuilder.buildImageView(ARConstants.ICON_INSERT, ARConstants.SPACE_M);
        textImage = componentBuilder.buildImageView(ARConstants.ICON_TEXT, ARConstants.SPACE_M);

        setImage = componentBuilder.buildImageView(ARConstants.ICON_SET_VALUE, ARConstants.SPACE_M);
        getImage = componentBuilder.buildImageView(ARConstants.ICON_GET_VALUE, ARConstants.SPACE_M);
        checkImage = componentBuilder.buildImageView(ARConstants.ICON_CHECK, ARConstants.SPACE_M);
        holdImage = componentBuilder.buildImageView(ARConstants.ICON_WAIT, ARConstants.SPACE_M);

        saveButton = componentBuilder.buildButton("  Save  ", ARConstants.SPACE_M, Insets.EMPTY);
        saveButton.setMaxHeight(ARConstants.SPACE_L);

        nameField = new TextField();
        nameField.setMaxHeight(ARConstants.SPACE_L);

        nameLabel = new Label();
        nameLabel.setMaxHeight(ARConstants.SPACE_L);

        StackPane nameGroup = new StackPane(nameLabel, nameField);

        HBox nameFieldsGroup = new HBox(nameGroup, saveButton);
        StackPane actionGroup = new StackPane(
                hiddenImage,
                outputImage,
                clickImage,
                insertImage,
                textImage,
                setImage,
                getImage,
                checkImage,
                holdImage);
        elementPanel = new HBox(actionGroup, nameFieldsGroup);
        elementPanel.setSpacing(ARConstants.SPACE_XS);

        AnchorPane.setLeftAnchor(elementPanel, ARConstants.SPACE_XS);
        AnchorPane.setTopAnchor(elementPanel, ARConstants.SPACE_XS);
        AnchorPane.setBottomAnchor(elementPanel, ARConstants.SPACE_XS);
    }

    private void initActionPanel() {
        moreOptionsButton = componentBuilder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_EDIT, ARConstants.SPACE_M, Insets.EMPTY);
        blockButton = componentBuilder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_BLOCK, ARConstants.SPACE_M, Insets.EMPTY);
        moveUpButton = componentBuilder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_UP, ARConstants.SPACE_M, Insets.EMPTY);
        moveDownButton = componentBuilder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_DOWN, ARConstants.SPACE_M, Insets.EMPTY);
        deleteButton = componentBuilder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_CROSS, ARConstants.SPACE_M, Insets.EMPTY);

        blockButton.setPrefWidth(ARConstants.SPACE_L);

        spaceLabel = new Label("                  ");

        // Create description label
        actionPanel = new HBox();
        if (operationsElement != null && operationsElement.length > 1) {
            //            variableBox.getChildren().addAll(saveOperatorButton);
            String complement = "";
            if (!isCheckValidator) {
                complement = ": "; // comboBoxOperator.getValue().getText();
            }

            if (operationsElement.length > 1) {

                operationLabel1 = new Label(operationsElement[0].get() + complement);
                operationLabel1.setTextFill(Color.BLUE);

                if (isCheckValidator) {
                    operationLabel2 = new Label(operationsElement[1].get());
                    operationLabel2.setTextFill(Color.ORANGE);
                    if (operationsElement.length > 2) {
                        operationLabel3 = new Label(operationsElement[2].get());
                    } else {
                        operationLabel3 = new Label("Empty");
                    }
                    operationLabel3.setTextFill(Color.BLUE);
                    operationLabel3.setStyle("-fx-font-size: 14px;");
                    operationLabel3.setStyle("-fx-font-weight: bold;");
                } else {
                    operationLabel2 = new Label(operationsElement[1].get());
                    operationLabel2.setTextFill(Color.ORANGE);
                }

                // Optionally, you can set additional styles or properties
                operationLabel1.setStyle("-fx-font-size: 14px;");
                operationLabel1.setStyle("-fx-font-weight: bold;");
                operationLabel2.setStyle("-fx-font-size: 14px;");
                operationLabel2.setStyle("-fx-font-weight: bold;");

                if (isCheckValidator) {
                    actionPanel
                            .getChildren()
                            .addAll(
                                    operationLabel1,
                                    operationLabel2,
                                    operationLabel3,
                                    spaceLabel,
                                    blockButton,
                                    moveUpButton,
                                    moveDownButton,
                                    //                                    moreOptionsButton,
                                    deleteButton);
                } else {
                    actionPanel
                            .getChildren()
                            .addAll(
                                    operationLabel1,
                                    operationLabel2,
                                    spaceLabel,
                                    blockButton,
                                    moveUpButton,
                                    moveDownButton,
                                    //                                    moreOptionsButton,
                                    deleteButton);
                }

            } else {
                operationLabel1 = new Label(operationsElement[0].get());
                operationLabel1.setTextFill(Color.BLUE);

                actionPanel
                        .getChildren()
                        .addAll(
                                operationLabel1,
                                spaceLabel,
                                blockButton,
                                moveUpButton,
                                moveDownButton,
                                //                                moreOptionsButton,
                                deleteButton);

                // Optionally, you can set additional styles or properties
                operationLabel1.setStyle("-fx-font-size: 14px;");
                operationLabel1.setStyle("-fx-font-weight: bold;");
            }

        } else {
            blockButton.setPrefWidth(ARConstants.SPACE_L);
            actionPanel
                    .getChildren()
                    .addAll(
                            blockButton,
                            moveUpButton,
                            moveDownButton,
                            //                            moreOptionsButton,
                            deleteButton);
        }

        actionPanel.setSpacing(ARConstants.SPACE_XS);
        actionPanel.setAlignment(Pos.CENTER_RIGHT);

        AnchorPane.setTopAnchor(actionPanel, ARConstants.SPACE_XS);
        AnchorPane.setBottomAnchor(actionPanel, ARConstants.SPACE_XS);
        AnchorPane.setRightAnchor(actionPanel, ARConstants.SPACE_XS);
    }

    private void initUIBehaviour() {
        hiddenImage.visibleProperty().bind(hiddenElement);

        insertImage.visibleProperty().bind(insertElement);

        outputImage.visibleProperty().bind(outputElement);

        clickImage.visibleProperty().bind(clickElement);

        textImage.visibleProperty().bind(textElement);

        setImage.visibleProperty().bind(setValueElem);
        getImage.visibleProperty().bind(getValueElem);
        checkImage.visibleProperty().bind(checkValueElem);

        holdImage.visibleProperty().bind(holdValueElem);

        nameLabel.visibleProperty().bind(editingElement.not());
        nameField.visibleProperty().bind(editingElement);
        saveButton.visibleProperty().bind(editingElement);
        moveUpButton.visibleProperty().bind(toBeAddedElement.not());
        blockButton.visibleProperty().bind(toBeAddedElement.not());
        moveDownButton.visibleProperty().bind(toBeAddedElement.not());
        deleteButton.visibleProperty().bind(toBeAddedElement.not());

        moreOptionsButton.setOnAction(e -> editingElement.setValue(!editingElement.getValue()));
        this.blockButton.setOnAction((e) -> {
            BlockLoopInstructionDTO item = (BlockLoopInstructionDTO)
                    ARSharedResources.getInstance().getEntityById(BlockLoopInstructionDTO.class, this.instructionId);
            ObservableList<BlockLoopInstructionDTO> list = ARSharedResources.getInstance()
                    .getEntityList(
                            BlockLoopInstructionDTO.class,
                            Comparator.comparingInt(BlockLoopInstructionDTO::getInstructionOrderNumber),
                            (instruction) -> {
                                return instruction.getBlock().getId()
                                        == item.getBlock().getId();
                            });
            System.out.println(list.size() + "Size");
            int index = list.indexOf(item);
            System.out.println(index + "indexof");
            List<BlockLoopInstructionDTO> items = list.subList(index, list.size());
            BlockDTO previousBlock = item.getBlock();
            BlockDTO defaultBlock = new BlockDTO();
            defaultBlock.setBotJob(item.getBlock().getBotJobDTO());
            defaultBlock.setBlockLoopInstructionDTOS(items);
            ARSharedResources.getInstance().addEntity(defaultBlock, BlockDTO.class, () -> {
                System.out.println("added : " + defaultBlock.getId());
                defaultBlock.setBlockOrderNumber(defaultBlock.getId() - 1);
                ARSharedResources.getInstance().updateEntity(defaultBlock, BlockDTO.class, () -> {
                    ARSharedResources.getInstance().refreshEntity(defaultBlock, BlockDTO.class, () -> {
                        ARSharedResources.getInstance().refreshEntity(previousBlock, BlockDTO.class);
                    });
                });
            });
        });
        moveUpButton.setOnAction(e -> switchInstruction(-1));

        moveDownButton.setOnAction(e -> switchInstruction(1));
        saveButton.setOnAction(e -> {
            editingElement.setValue(false);
            nameLabel.setText(nameField.getText());
            ARLogger.getInstance(ARWebElement.class).info("saving instruction with id: " + instructionId);
            if (instructionId != null && instructionId != 0) {
                BlockLoopInstructionDTO instruction =
                        ARSharedResources.getInstance().getEntityById(BlockLoopInstructionDTO.class, instructionId);
                instruction.setName(nameLabel.getText());
                String action = instruction.getActions();
                if (action.contains(ARConstants.INSERT)) {
                    instruction.setActions(action.split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER)[0]
                            + ARConstants.ACTION_SPECIFICATIONS_SPLITTER
                            + nameLabel.getText());
                }
                ARSharedResources.getInstance().updateEntity(instruction, BlockLoopInstructionDTO.class);
            }
        });
        deleteButton.setOnAction(e -> {
            String msgDelete = instrOperation != null ? " -> " + instrOperation : "";
            boolean delete = showConfirmationDialog(instrName, msgDelete);

            if (delete) {
                BlockLoopInstructionDTO instruction =
                        ARSharedResources.getInstance().getEntityById(BlockLoopInstructionDTO.class, instructionId);
                int instructionIndex = instruction.getInstructionOrderNumber();
                try {
                    if (existVariables(instructionId)) {
                        new ARAlertScene(
                                Alert.AlertType.INFORMATION,
                                "Not possible delete \"" + instrName + "\"" + instrOperation != null
                                        ? instrOperation
                                        : "",
                                "\nThe element cannot be deleted!\nRemove all VARIABLES relations first!",
                                ButtonType.OK);
                        delete = false;
                    }
                    if (delete) {
                        deleteInstrReference(instructionId);
                        deleteBlockInstruction(instructionId);
                        forceDeleteOrphan();
                        forceDeleteFatherNoChild(instructionId);
                        Platform.runLater(() -> {
                            new ARAlertScene(
                                    Alert.AlertType.INFORMATION,
                                    "Successful deletion \"" + instrName + "\"" + msgDelete,
                                    "The element has been deleted successfully",
                                    ButtonType.OK);
                        });
                    }
                } catch (SQLException ex) {
                    ARLogger.getInstance(Thread.class)
                            .finer("An exception has occurred deleting Instruction id: " + instructionId + " -> Cause: "
                                    + ex.getMessage());
                    Platform.runLater(() -> {
                        new ARAlertScene(
                                Alert.AlertType.INFORMATION,
                                "Not possible delete \"" + instrName + "\"" + instrOperation != null
                                        ? instrOperation
                                        : "",
                                "\nThe element cannot be deleted!\nRemove all VARIABLES relations first!",
                                ButtonType.OK);
                    });
                    // Force exit
                    delete = false;
                }
                BlockDTO block = instruction.getBlock();

                if (delete) {
                    try {
                        instruction
                                .getInstructionReferenceDTOList()
                                .forEach(ref -> ref.setBlockLoopInstructionDTO(null));
                    } catch (Exception ef) {
                        ARLogger.getInstance(Thread.class)
                                .severe("getInstructionReferenceDTOList -> Cause: " + ef.getMessage());
                    }
                    //                    try {
                    //                        ARSharedResources.getInstance()
                    //                                .removeEntity(
                    //                                        instruction,
                    //                                        BlockLoopInstructionDTO.class,
                    //                                        () -> {
                    //                                            Queue<BlockLoopInstructionDTO> instructionQueue =
                    //                                                    block.getBlockLoopInstructions().stream()
                    //                                                            .filter(i ->
                    //                                                                    i.getInstructionOrderNumber()
                    // > instructionIndex)
                    //
                    // .collect(Collectors.toCollection(LinkedBlockingQueue::new));
                    //                                            instructionQueue.forEach(instr ->
                    // instr.setInstructionOrderNumber(
                    //                                                    instr.getInstructionOrderNumber() - 1));
                    //                                        },
                    //                                        ex -> {
                    //                                            Platform.runLater(() -> {
                    //                                                new ARAlertScene(
                    //                                                        Alert.AlertType.INFORMATION,
                    //                                                        "Not possible delete \"" + instrName +
                    // "\"" + instrOperation
                    //                                                                        != null
                    //                                                                ? instrOperation
                    //                                                                : "",
                    //                                                        "\nThe element cannot be deleted!\nRemove
                    // all VARIABLES relations first!",
                    //                                                        ButtonType.OK);
                    //                                            });
                    //                                        });
                    //                    } catch (Exception ex) {
                    //                        ARLogger.getInstance(Thread.class)
                    //                                .finer("Error deleting for: " + instructionId + " -> Cause: " +
                    // ex.getMessage());
                    //                    }
                }
            }
        });
        EventHandler<MouseEvent> mouseEventEventHandler = mouseEvent -> {
            editingElement.setValue(false);
        };
        graphicRepresentation.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseEventEventHandler);
    }

    private void switchInstruction(int directionQuantity) {
        //        try {
        //            BlockLoopInstructionDTO currentInstruction =
        //                    ARSharedResources.getInstance().getEntityById(BlockLoopInstructionDTO.class,
        // instructionId);
        //            int order = currentInstruction.getInstructionOrderNumber();
        //            BlockDTO block = ARSharedResources.getInstance()
        //                    .getEntityById(BlockDTO.class, currentInstruction.getBlock().getId());
        //            List<BlockLoopInstructionDTO> instructionList = block.getBlockLoopInstructions();
        //            BlockLoopInstructionDTO instructionToChange = instructionList.stream()
        //                    .filter(i -> i.getInstructionOrderNumber() == order + directionQuantity)
        //                    .findFirst()
        //                    .orElseThrow();
        //            currentInstruction.setInstructionOrderNumber(order + directionQuantity);
        //            instructionToChange.setInstructionOrderNumber(order);
        //            ARSharedResources.getInstance()
        //                    .updateEntity(
        //                            currentInstruction, BlockLoopInstructionDTO.class, () ->
        // ARSharedResources.getInstance()
        //                                    .updateEntity(instructionToChange, BlockLoopInstructionDTO.class));
        //
        //        } catch (Exception ex) {
        //            ARLogger.getInstance(Thread.class).severe("Error switch Instruction -> Cause: " +
        // ex.getMessage());
        //        }
    }

    public BlockLoopInstructionDTO buildBlockLoopInstruction(
            WebElementTagNameEnum forceTag, String actionReq, boolean identityHover, Integer orderNumber) {
        BlockLoopInstructionDTO loop = new BlockLoopInstructionDTO();
        loop.setActionCustomMaxWaitSec(30);
        loop.setDescription("loop desc");
        loop.setCodified(false);
        loop.setInstructionOrderNumber(orderNumber);
        loop.setOptional(false);
        loop.setActive(true);
        loop.setPath(mainXPath);
        String action;
        // TODO: Make a better thing than this
        if (isIdElement.get()) {
            action = ARConstants.EXTRACT_FIELD + ARConstants.ACTION_SPECIFICATIONS_SPLITTER + "EXTERNAL_REFERENCE";
        } else {
            if (identityHover) {
                action = actionReq.equalsIgnoreCase("INPUT")
                        ? ARConstants.INSERT + ARConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText()
                        : actionReq.equalsIgnoreCase("OUTPUT")
                                ? ARConstants.OUTPUT + ARConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText()
                                : actionReq.equalsIgnoreCase("OTHER")
                                        ? ARConstants.OTHER
                                                + ARConstants.ACTION_SPECIFICATIONS_SPLITTER
                                                + nameLabel.getText()
                                        : actionReq.equalsIgnoreCase("click")
                                                ? ARConstants.CLICK
                                                : clickElement.get()
                                                        ? ARConstants.CLICK
                                                        : ARConstants.INSERT
                                                                + ARConstants.ACTION_SPECIFICATIONS_SPLITTER
                                                                + nameLabel.getText();
            } else {

                if (tagType != null) {
                    if (tagType.equals(WebElementTagNameEnum.INPUT)) {
                        action = ARConstants.INSERT + ARConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText();
                    } else if (tagType.equals(WebElementTagNameEnum.HIDDEN)) {
                        action = ARConstants.INSERT
                                + ARConstants.ACTION_SPECIFICATIONS_SPLITTER
                                + nameLabel.getText()
                                + ARConstants.ACTION_SPECIFICATIONS_SPLITTER
                                + ARConstants.HIDDEN;
                    } else if (tagType.equals(WebElementTagNameEnum.BUTTON)) {
                        action = ARConstants.CLICK;

                    } else if (tagType.getValue().equalsIgnoreCase(ARConstants.OUTPUT)) {
                        // Handle the OUTPUT case
                        action = ARConstants.OUTPUT + ARConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText();
                    } else {
                        action = ARConstants.OUTPUT + ARConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText();
                    }
                } else {
                    action = clickElement.get()
                            ? ARConstants.CLICK
                            : ARConstants.INSERT + ARConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText();
                }
            }
        }
        loop.setActions(action);
        loop.setName(nameLabel.getText());
        loop.setExportToAR(true);
        return loop;
    }

    public <T extends Event> void addEventHandler(EventType<T> eventType, EventHandler<? super T> handler) {
        if (!eventHandlerMap.containsKey(eventType)) {
            eventHandlerMap.put(eventType, new ArrayList<>());
        }
        eventHandlerMap.get(eventType).add(handler);
        graphicRepresentation.addEventHandler(eventType, handler);
    }

    public void removeAllHandlers() {
        for (EventType eventType : eventHandlerMap.keySet()) {
            List<EventHandler> handlerList = eventHandlerMap.get(eventType);
            for (EventHandler handler : handlerList) {
                graphicRepresentation.removeEventHandler(eventType, handler);
            }
        }
    }

    public void setCallbackOnMouseClick(ARCallback callback) {
        moveUpButton.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> callback.execute());
        moveDownButton.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> callback.execute());
        deleteButton.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> callback.execute());
    }

    public String getTagNameDefined() {
        return tagNameDefined;
    }

    public void setTagNameDefined(String tagNameDefined) {
        this.tagNameDefined = tagNameDefined;
    }

    public String getiFrameXPath() {
        return iFrameXPath;
    }

    public void setiFrameXPath(String iFrameXPath) {
        this.iFrameXPath = iFrameXPath;
    }

    public String getMainXPath() {
        return mainXPath;
    }

    public void setMainXPath(String mainXPath) {
        this.mainXPath = mainXPath;
    }

    public String getMainCoordinates() {
        return mainCoordinates;
    }

    public void setMainCoordinates(String mainCoordinates) {
        this.mainCoordinates = mainCoordinates;
    }

    public String getNameFieldTitle() {
        return nameFieldTitle;
    }

    public void setNameFieldTitle(String nameFieldTitle) {
        this.nameFieldTitle = nameFieldTitle;
    }

    public Node getGraphicRepresentation() {
        return graphicRepresentation;
    }

    public boolean isClickable() {
        return clickElement.get();
    }

    public boolean isNotClickable() {
        return clickElement.not().get();
    }

    public Integer getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(Integer instructionId) {
        this.instructionId = instructionId;
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    public WebElement getElement() {
        return element;
    }

    public void setElement(WebElement element) {
        this.element = element;
    }

    public Map<String, String> getSavedReferences() {
        return savedReferences;
    }

    public void setSavedReferences(Map<String, String> savedReferences) {
        this.savedReferences = savedReferences;
    }

    public WebElementTagNameEnum gettagType() {
        return tagType;
    }

    public void settagType(WebElementTagNameEnum tagType) {
        this.tagType = tagType;
    }

    /**
     * Extracts the file extension from the given string, considering it may be a path.
     *
     * @param input The string from which to extract the file extension.
     * @return The file extension if present and the string is identified as a file, otherwise an empty string.
     */
    public static String extractFileExtension(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // Find the last slash in the string
        int lastIndexOfSlash = input.lastIndexOf('/');

        // Get the substring after the last slash
        String lastSegment = lastIndexOfSlash == -1 ? input : input.substring(lastIndexOfSlash + 1);

        // If the last segment contains a period, it is considered a file
        int lastIndexOfDot = lastSegment.lastIndexOf('.');
        if (lastIndexOfDot == -1 || lastIndexOfDot == lastSegment.length() - 1) {
            return "";
        }

        // Extract the substring after the last period
        return lastSegment.substring(lastIndexOfDot + 1);
    }

    // Utility methods for better readability and reusability

    private boolean isValidString(String value) {
        return value != null && !value.isBlank();
    }

    private void setElementText(String nameLabelText, String nameFieldText) {
        nameLabel.setText(nameLabelText);
        nameField.setText(nameFieldText);
    }

    // Recursive method to find text or placeholder text
    public static String getTextRecursively(WebElement element, String tagNameDefined) {
        String text = element.getText();
        if (!text.isEmpty()) {
            return text;
        }

        // Check if the element is an input field and look for placeholder
        if (tagNameDefined.equalsIgnoreCase("input") || tagNameDefined.equalsIgnoreCase("textarea")) {
            String placeholder = element.getAttribute("placeholder");
            if (placeholder != null && !placeholder.isEmpty()) {
                return placeholder;
            }
        }

        // Recursively search child elements for text
        List<WebElement> children = element.findElements(By.xpath("./*"));
        for (WebElement child : children) {
            String childText = getTextRecursively(child, tagNameDefined);
            if (!childText.isEmpty()) {
                return childText;
            }
        }

        return "";
    }

    // Recursive method to find text or placeholder text
    public static String getTextRecursivelyByParent(WebElement element, String tagNameDefined) {
        // Check if the element has text
        String text = element.getText();
        if (!text.isEmpty()) {
            return text;
        }

        // Check if the element is an input field or textarea and look for placeholder
        if (tagNameDefined.equalsIgnoreCase("input") || tagNameDefined.equalsIgnoreCase("textarea")) {
            String placeholder = element.getAttribute("placeholder");
            if (placeholder != null && !placeholder.isEmpty()) {
                return placeholder;
            }
        }

        // Recursively check parent element
        WebElement parent = element.findElement(By.xpath(".."));
        if (parent != null && !parent.getTagName().equalsIgnoreCase("html")) {
            return getTextRecursively(parent, tagNameDefined);
        }

        // If no text is found, return empty string
        return "";
    }

    // Recursive method to extract all text content
    public static String extractAllText(WebElement element, String tagNameDefined) {
        StringBuilder textContent = new StringBuilder();
        extractTextRecursively(element, textContent, tagNameDefined);
        return textContent.toString().trim();
    }

    private static void extractTextRecursively(WebElement element, StringBuilder textContent, String tagNameDefined) {
        // Get the text content of the current element
        String text = element.getText();
        if (!text.isEmpty()) {
            textContent.append(text).append(" ");
        }

        // Get the placeholder if the element is an input or textarea
        if (tagNameDefined.equalsIgnoreCase("input") || tagNameDefined.equalsIgnoreCase("textarea")) {
            String placeholder = element.getAttribute("placeholder");
            if (placeholder != null && !placeholder.isEmpty()) {
                textContent.append(placeholder).append(" ");
            }
        }

        // Recursively extract text from child elements
        List<WebElement> children = element.findElements(By.xpath("./*"));
        for (WebElement child : children) {
            extractTextRecursively(child, textContent, tagNameDefined);
        }
    }

    private boolean showConfirmationDialog(String name, String operation) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation Dialog");
        alert.setHeaderText("Delete Confirmation");
        alert.setContentText("Are you sure you want to delete the record for \n\"" + name + "\"" + operation + "  ?");

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void deleteBlockInstruction(int instructionId) throws SQLException {
        String deleteBlockInstruction = "delete FROM block_loop_instruction " + " where id = " + instructionId;

        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement()) {
            int rowsAffected = stmt.executeUpdate(deleteBlockInstruction);
            if (rowsAffected > 0) {
                ARLogger.getInstance(Thread.class).finer("Data deleted successfully for: " + instructionId);
            } else {
                ARLogger.getInstance(Thread.class).finer("No matching record found to delete for: " + instructionId);
            }
        }
    }

    private void deleteInstrReference(int instructionId) throws SQLException {
        String deleteSQL =
                "delete FROM instruction_reference " + " where block_loop_instruction_id =  " + instructionId;

        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement()) {
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ARLogger.getInstance(Thread.class).finer("Data deleted successfully for: " + instructionId);
            } else {
                ARLogger.getInstance(Thread.class).finer("No matching record found to delete for: " + instructionId);
            }
        }
    }

    private boolean existVariables(int instructionId) throws SQLException {
        String query = "select id FROM variable " + " where block_loop_instruction_id =  " + instructionId;
        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                return true;
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return false;
    }

    private void forceDeleteOrphan() throws SQLException {
        String deleteSQL = "delete FROM instruction_reference " + " where block_loop_instruction_id is null ";

        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement()) {
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ARLogger.getInstance(Thread.class).finer("Data deleted successfully for: " + instructionId);
            } else {
                ARLogger.getInstance(Thread.class).finer("No matching record found to delete for: " + instructionId);
            }
        }
    }

    private void forceDeleteFatherNoChild(int instructionId) throws SQLException {
        String deleteSQL = "DELETE FROM block_loop_instruction " + "WHERE id IN ( "
                + "    SELECT bli.id "
                + "    FROM block_loop_instruction bli "
                + "    LEFT JOIN instruction_reference irl ON irl.block_loop_instruction_id = bli.id "
                + "    WHERE irl.id IS NULL "
                + "    AND bli.name NOT IN ('Check', 'GetValue', 'SetValue')"
                + ") ";

        try (Statement stmt = ARSharedResources.getInstance().getConnection().createStatement()) {
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ARLogger.getInstance(Thread.class).finer("Data deleted successfully for: " + instructionId);
            } else {
                ARLogger.getInstance(Thread.class).finer("No matching record found to delete for: " + instructionId);
            }
        }
    }
}

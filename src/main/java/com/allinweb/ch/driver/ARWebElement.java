package com.allinweb.ch.driver;

import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.model.AttributeData;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.persistence.InstructionDTO;
import com.allinweb.ch.persistence.TargetElement;
import com.allinweb.ch.util.*;
import com.allinweb.ch.util.Priority;
import com.google.common.base.Strings;
import java.util.*;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
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

public class ARWebElement {

    private final ARComponentBuilder componentBuilder = new ARComponentBuilder();

    private BooleanProperty hiddenElement = new SimpleBooleanProperty(false);
    private BooleanProperty outputElement = new SimpleBooleanProperty(false);
    private BooleanProperty iFrameElement = new SimpleBooleanProperty(false);
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

    private String elementId;
    private WebElement element;

    private TargetElement targetElement;

    private Map<String, String> savedReferences = new HashMap<>();

    // graphic attributes
    private AnchorPane graphicRepresentation;
    private HBox elementPanel;
    private HBox actionPanel;

    private Label nameLabel;
    private TextField nameField;
    private Label operationLabel1;
    private Label operationLabel2;
    private Label operationLabel3;
    private Label spaceLabel;

    private String nameFieldTitle;

    private ImageView hiddenImage;
    private ImageView outputImage;
    private ImageView iFrameImage;
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

    public String getNameFieldTitle() {
        return nameFieldTitle;
    }

    public void setNameFieldTitle(String nameFieldTitle) {
        this.nameFieldTitle = nameFieldTitle;
    }

    public TargetElement getTargetElement() {
        return targetElement;
    }

    public void setTargetElement(TargetElement targetElement) {
        this.targetElement = targetElement;
    }

    public ARWebElement(TargetElement targetElement, int jobId) {
        arPriorities.setJobId(jobId);
        this.targetElement = targetElement;
        initFromWebElement(targetElement.getElement());
    }

    public ARWebElement(WebElement element, String priority) {
        updatePriorities(priority, null);
        this.targetElement = targetElement;
        initFromWebElement(targetElement.getElement());
    }

    public ARWebElement(InstructionDTO instruction) {
        botJobId = instruction.getBlock().getBotJobDTO().getId();
        updatePriorities(null, instruction);
        initFromBlockLoopInstruction(instruction);
    }

    private void updatePriorities(String priority, InstructionDTO instruction) {
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
            if (arPriorities.getAllPriorityList().size() == 0) {
                arPriorities.loadPriorities();
                ARLogger.getInstance(Thread.class).finer("Reloaded arPriorities.loadPriorities()");
            }

            if (targetElement.getMainXPath() == null && arPriorities.getJobId() != null) {
                for (Priority priority : arPriorities.getAllPriorityList()) {
                    try {
                        switch (priority.getPriorityType()) {
                            case attributeID -> {
                                if (Strings.isNullOrEmpty(targetElement.getAttribId())) {
                                    String attributeValue = element.getAttribute(
                                            priority.getName().get(0));
                                    if (attributeValue != null && !attributeValue.isBlank()) {
                                        savedReferences.put(priority.getName().get(0), attributeValue);
                                        targetElement.setAttribName(
                                                priority.getName().get(0));
                                        targetElement.setAttributeValue(attributeValue);
                                    }

                                } else {
                                    String attributeValue = element.getAttribute(targetElement.getAttribId());
                                    if (attributeValue != null && !attributeValue.isBlank()) {
                                        savedReferences.put(priority.getName().get(0), attributeValue);
                                        targetElement.setAttributeValue(attributeValue);
                                    }
                                }
                            }

                            case attributeName -> {
                                if (Strings.isNullOrEmpty(targetElement.getAttribName())) {
                                    String attributeValue = element.getAttribute(
                                            priority.getName().get(0));
                                    if (attributeValue != null && !attributeValue.isBlank()) {
                                        savedReferences.put(priority.getName().get(0), attributeValue);
                                        targetElement.setAttribName(
                                                priority.getName().get(0));
                                        targetElement.setAttributeValue(attributeValue);
                                    }

                                } else {
                                    String attributeValue = element.getAttribute(targetElement.getAttribName());
                                    if (attributeValue != null && !attributeValue.isBlank()) {
                                        savedReferences.put(priority.getName().get(0), attributeValue);
                                        targetElement.setAttributeValue(attributeValue);
                                    }
                                }
                            }
                            case xpath, ByXPath -> {
                                if (Strings.isNullOrEmpty(targetElement.getMainXPath())) {
                                    targetElement.setMainXPath(ARWebUtil.extractWebElementXPath(element));
                                    savedReferences.put(priority.getName().get(0), targetElement.getMainXPath());
                                } else {
                                    savedReferences.put(priority.getName().get(0), targetElement.getMainXPath());
                                }
                            }
                            case coordinates -> {
                                Rectangle coord = element.getRect();
                                String coordTemp = (coord.getX() + (coord.getWidth() / 2)) + ","
                                        + (coord.getY() + (coord.getHeight() / 2));
                                savedReferences.put(priority.getName().get(0), coordTemp);

                                if (Strings.isNullOrEmpty(targetElement.getMainCoordinates())) {
                                    targetElement.setMainCoordinates(coordTemp);
                                }
                                if (Strings.isNullOrEmpty(targetElement.getCoords())) {
                                    targetElement.setCoords(coordTemp);
                                }
                            }
                        }
                    } catch (EnumConstantNotPresentException ex) {
                        throw ex;
                    }
                }
            } else {
                // Most Important to find any kind of element

                if (targetElement != null
                        && targetElement.getXPathWorkedFirst().equalsIgnoreCase(ARConstants.REGULAR_XPATH)) {
                    savedReferences.put("currentXPath", targetElement.getCurrentXPath());
                    savedReferences.put("customXPath", targetElement.getCustomXPath());
                    for (AttributeData attrb : targetElement.getAttributeData()) {
                        savedReferences.put(
                                attrb.getName().trim(), attrb.getValue().trim());
                    }
                    if (!Strings.isNullOrEmpty(targetElement.getAttribId())) {
                        savedReferences.put("attributeID", targetElement.getAttribId());
                    }
                    if (!Strings.isNullOrEmpty(targetElement.getAttribName())) {
                        savedReferences.put("attributeName", targetElement.getAttribName());
                    }
                    if (!Strings.isNullOrEmpty(targetElement.getSearchAttributeValue())) {
                        savedReferences.put("searchAttribute", targetElement.getSearchAttributeValue());
                    }
                } else if (targetElement.getXPathWorkedFirst().equalsIgnoreCase(ARConstants.CUSTOM_XPATH)) {
                    savedReferences.put("currentXPath", targetElement.getCurrentXPath());
                    savedReferences.put("customXPath", targetElement.getCustomXPath());
                    for (AttributeData attrb : targetElement.getAttributeData()) {
                        savedReferences.put(
                                attrb.getName().trim(), attrb.getValue().trim());
                    }

                    if (!Strings.isNullOrEmpty(targetElement.getAttribId())) {
                        savedReferences.put("attributeID", targetElement.getAttribId());
                    }
                    if (!Strings.isNullOrEmpty(targetElement.getAttribName())) {
                        savedReferences.put("attributeName", targetElement.getAttribName());
                    }
                    if (!Strings.isNullOrEmpty(targetElement.getSearchAttributeValue())) {
                        savedReferences.put("searchAttribute", targetElement.getSearchAttributeValue());
                    }
                } else if (targetElement != null && !Strings.isNullOrEmpty(targetElement.getMainXPath())) {
                    savedReferences.put("xpath", targetElement.getCurrentXPath());
                } else if (!Strings.isNullOrEmpty(targetElement.getAttribId())) {
                    savedReferences.put("attributeID", targetElement.getAttribId());
                } else if (!Strings.isNullOrEmpty(targetElement.getAttribName())) {
                    savedReferences.put("attributeName", targetElement.getAttribName());
                } else if (!Strings.isNullOrEmpty(targetElement.getSearchAttributeValue())) {
                    savedReferences.put("searchAttribute", targetElement.getSearchAttributeValue());
                } else if (targetElement.getAttributeData().length > 0) {
                    for (AttributeData attrb : targetElement.getAttributeData()) {
                        savedReferences.put(
                                attrb.getName().trim(), attrb.getValue().trim());
                    }

                } else if (!Strings.isNullOrEmpty(targetElement.getAttributeValue())) {
                    savedReferences.put("attribute", targetElement.getAttributeValue());
                } else { // In case of Dynamic Creation
                    savedReferences.put("xpath", ARWebUtil.extractWebElementXPath(element));
                }

                try {
                    Rectangle coordinates = element.getRect();
                    savedReferences.put(
                            "coordinates",
                            (coordinates.getX() + (coordinates.getWidth() / 2)) + ","
                                    + (coordinates.getY() + (coordinates.getHeight() / 2)));
                } catch (Exception coords) {
                    // Split the string into X and Y values
                    if (Strings.isNullOrEmpty(targetElement.getCoords())) {
                        String[] parts = targetElement.getCoords().split(",");
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
            }

        } catch (Exception ex) {
            throw ex;
        }

        hiddenElement.setValue(false);
        outputElement.setValue(false);
        iFrameElement.setValue(false);
        insertElement.setValue(false);
        clickElement.setValue(false);
        textElement.setValue(false);

        // Now proceed with the rest of your code
        if (!targetElement.getIsElementHidden()) {
            if (targetElement.getTagType() != null) {
                if (targetElement.getTagType().equals(WebElementTagNameEnum.BUTTON)) {
                    // Handle the button case (if BUTTON is forced)
                    clickElement.setValue(true);
                } else if (targetElement.getTagType().getValue().equalsIgnoreCase(ARConstants.SET_VALUE)) {
                    // Handle the SET_VALUE case
                    setValueElem.setValue(true);
                } else if (targetElement.getTagType().getValue().equalsIgnoreCase(ARConstants.GET_VALUE)) {
                    // Handle the GET_VALUE case
                    getValueElem.setValue(true);
                } else if (targetElement.getTagType().getValue().equalsIgnoreCase(ARConstants.CHECK_VALUE)) {
                    // Handle the CHECK_VALUE case
                    checkValueElem.setValue(true);
                } else if (targetElement.getTagType().getValue().equalsIgnoreCase(ARConstants.OUTPUT)) {
                    // Handle the OUTPUT case
                    outputElement.setValue(true);
                } else if (targetElement.getTagType().getValue().equalsIgnoreCase(ARConstants.IFRAME)) {
                    // Handle the OUTPUT case
                    iFrameElement.setValue(true);
                } else {
                    // Handle other cases like INPUT
                    outputElement.setValue(false);
                    clickElement.setValue(false);
                    textElement.setValue(false);
                    iFrameElement.setValue(false);

                    targetElement.setTagType(WebElementTagNameEnum.INPUT);
                    insertElement.setValue(true);
                }
            } else {

                // Handle other types of elements as needed
                outputElement.setValue(false);
                iFrameElement.setValue(false);
                clickElement.setValue(true);
                textElement.setValue(false);
                insertElement.setValue(false);
                targetElement.setTagType(WebElementTagNameEnum.BUTTON);
            }
        } else {
            outputElement.setValue(false);
            iFrameElement.setValue(false);
            clickElement.setValue(false);
            textElement.setValue(false);
            insertElement.setValue(false);
            hiddenElement.setValue(true);
            targetElement.setTagType(WebElementTagNameEnum.HIDDEN);
        }

        nameLabel.setText(targetElement.getNameLabel());
        nameField.setText(targetElement.getNameField());

        nameFieldTitle = targetElement.getNameField();

        elementId = ((RemoteWebElement) element).getId();

        this.element = element;
        toBeAddedElement.setValue(true);
    }

    private void initFromBlockLoopInstruction(InstructionDTO instruction) {

        // Split the description string
        if (instruction.getOperation() != null) {
            String[] descriptionArray = instruction.getOperation().split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER);

            // Initialize the descriptions array with the length of the descriptionArray
            operationsElement = new StringProperty[descriptionArray.length];

            // Convert each string to a StringProperty
            for (int i = 0; i < descriptionArray.length; i++) {
                operationsElement[i] = new SimpleStringProperty(descriptionArray[i]);
            }
        }
        String[] actionReference = instruction.getActions().split(ARConstants.ACTION_SPECIFICATIONS_SPLITTER);

        isCheckValidator = actionReference[0].equalsIgnoreCase(ARConstants.CHECK_VALUE);

        instructionId = instruction.getId();
        initUI();

        nameLabel.setText(instruction.getName());
        nameField.setText(instruction.getName());

        if (actionReference.length > 1) {
            nameLabel.setText(actionReference[1]);
            nameField.setText(actionReference[1]);
        }

        if (actionReference[0].equalsIgnoreCase(ARConstants.HIDDEN)) {
            hiddenElement.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ARConstants.OUTPUT)) {
            outputElement.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ARConstants.IFRAME)) {
            iFrameElement.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ARConstants.CLICK)) {
            clickElement.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ARConstants.INSERT)) {
            textElement.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ARConstants.SET_VALUE)) {
            setValueElem.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ARConstants.GET_VALUE)) {
            getValueElem.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ARConstants.CHECK_VALUE)) {
            checkValueElem.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ARConstants.HOLD)) {
            holdValueElem.setValue(true);
        }

        toBeAddedElement.setValue(false);
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
        iFrameImage = componentBuilder.buildImageView(ARConstants.ICON_iFRAME1, ARConstants.SPACE_M);
        clickImage = componentBuilder.buildImageView(ARConstants.ICON_CLICK, ARConstants.SPACE_M);
        insertImage = componentBuilder.buildImageView(ARConstants.ICON_INSERT, ARConstants.SPACE_M);
        textImage = componentBuilder.buildImageView(ARConstants.ICON_TEXT, ARConstants.SPACE_M);

        setImage = componentBuilder.buildImageView(ARConstants.ICON_SET_VALUE, ARConstants.SPACE_M);
        getImage = componentBuilder.buildImageView(ARConstants.ICON_GET_VALUE, ARConstants.SPACE_M);
        checkImage = componentBuilder.buildImageView(ARConstants.ICON_CHECK, ARConstants.SPACE_M);
        holdImage = componentBuilder.buildImageView(ARConstants.ICON_WAIT, ARConstants.SPACE_M);

        nameField = new TextField();
        nameField.setMaxHeight(ARConstants.SPACE_L);

        nameLabel = new Label();
        nameLabel.setMaxHeight(ARConstants.SPACE_L);

        StackPane nameGroup = new StackPane(nameLabel, nameField);

        HBox nameFieldsGroup = new HBox(nameGroup);
        StackPane actionGroup = new StackPane(
                hiddenImage,
                outputImage,
                iFrameImage,
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
                    actionPanel.getChildren().addAll(operationLabel1, operationLabel2, operationLabel3, spaceLabel);
                } else {
                    actionPanel.getChildren().addAll(operationLabel1, operationLabel2, spaceLabel);
                }

            } else {
                operationLabel1 = new Label(operationsElement[0].get());
                operationLabel1.setTextFill(Color.BLUE);

                actionPanel.getChildren().addAll(operationLabel1, spaceLabel);

                // Optionally, you can set additional styles or properties
                operationLabel1.setStyle("-fx-font-size: 14px;");
                operationLabel1.setStyle("-fx-font-weight: bold;");
            }

        } else {
            //            blockButton.setPrefWidth(ARConstants.SPACE_L);
            //            actionPanel
            //                    .getChildren()
            //                    .addAll(
            //                            blockButton,
            //                            moveUpButton,
            //                            moveDownButton,
            //                            //                            moreOptionsButton,
            //                            deleteButton);
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

        iFrameImage.visibleProperty().bind(iFrameElement);

        clickImage.visibleProperty().bind(clickElement);

        textImage.visibleProperty().bind(textElement);

        setImage.visibleProperty().bind(setValueElem);
        getImage.visibleProperty().bind(getValueElem);
        checkImage.visibleProperty().bind(checkValueElem);

        holdImage.visibleProperty().bind(holdValueElem);

        nameLabel.visibleProperty().bind(editingElement.not());
        nameField.visibleProperty().bind(editingElement);

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

    public InstructionDTO buildNewInstruction(
            WebElementTagNameEnum forceTag, String actionReq, boolean identityHover, Integer orderNumber) {
        InstructionDTO loop = new InstructionDTO();
        loop.setActionCustomMaxWaitSec(30);
        loop.setDescription("loop desc");
        loop.setCodified(false);
        loop.setInstructionOrderNumber(orderNumber);
        loop.setOptional(false);
        loop.setActive(true);
        loop.setPath(targetElement.getMainXPath());
        String action;
        // TODO: Make a better thing than this
        if (isIdElement.get()) {
            action = ARConstants.EXTRACT_FIELD + ARConstants.ACTION_SPECIFICATIONS_SPLITTER + "EXTERNAL_REFERENCE";
        } else {
            if (identityHover) {
                switch (actionReq.toUpperCase()) {
                    case ARConstants.INSERT:
                        if (forceTag.equals(WebElementTagNameEnum.INPUT_ENTER)) {
                            action = ARConstants.INSERT_ENTER
                                    + ARConstants.ACTION_SPECIFICATIONS_SPLITTER
                                    + nameLabel.getText();
                        } else {
                            action = ARConstants.INSERT
                                    + ARConstants.ACTION_SPECIFICATIONS_SPLITTER
                                    + nameLabel.getText();
                        }
                        break;

                    case ARConstants.OUTPUT:
                        action = ARConstants.OUTPUT + ARConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText();
                        break;

                    case ARConstants.OTHER:
                        action = ARConstants.OTHER + ARConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText();
                        break;

                    case ARConstants.CLICK:
                        action = ARConstants.CLICK;
                        break;

                    default:
                        // For all other cases, check clickElement.get() and handle accordingly
                        action = clickElement.get()
                                ? ARConstants.CLICK
                                : ARConstants.INSERT + ARConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText();
                        break;
                }

            } else {

                if (targetElement.getTagType() != null) {
                    switch (targetElement.getTagType()) {
                        case INPUT:
                            if (forceTag.equals(WebElementTagNameEnum.INPUT_ENTER)) {
                                action = ARConstants.INSERT_ENTER
                                        + ARConstants.ACTION_SPECIFICATIONS_SPLITTER
                                        + nameLabel.getText();
                            } else {
                                action = ARConstants.INSERT
                                        + ARConstants.ACTION_SPECIFICATIONS_SPLITTER
                                        + nameLabel.getText();
                            }
                            break;

                        case HIDDEN:
                            action = ARConstants.INSERT
                                    + ARConstants.ACTION_SPECIFICATIONS_SPLITTER
                                    + nameLabel.getText()
                                    + ARConstants.ACTION_SPECIFICATIONS_SPLITTER
                                    + ARConstants.HIDDEN;
                            break;

                        case BUTTON:
                            action = ARConstants.CLICK;
                            break;

                        default:
                            if (targetElement.getTagType().getValue().equalsIgnoreCase(ARConstants.OUTPUT)) {
                                action = ARConstants.OUTPUT
                                        + ARConstants.ACTION_SPECIFICATIONS_SPLITTER
                                        + nameLabel.getText();
                            } else {
                                action = ARConstants.OUTPUT
                                        + ARConstants.ACTION_SPECIFICATIONS_SPLITTER
                                        + nameLabel.getText();
                            }
                            break;
                    }
                } else {
                    // Handle case where targetElement.getTagType() is null
                    action = clickElement.get()
                            ? ARConstants.CLICK
                            : ARConstants.INSERT + ARConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText();
                }
            }
        }
        loop.setActions(action);
        loop.setName(nameLabel.getText());
        loop.setExportToABR(true);
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
}

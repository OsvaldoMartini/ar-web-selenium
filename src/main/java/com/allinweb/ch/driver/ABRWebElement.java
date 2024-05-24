package com.allinweb.ch.driver;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementAttributeTypeValueEnum;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.util.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;

public class ABRWebElement {

    private final ABRComponentBuilder componentBuilder = new ABRComponentBuilder();

    private BooleanProperty clickElement = new SimpleBooleanProperty(false);
    private BooleanProperty editingElement = new SimpleBooleanProperty(false);
    private BooleanProperty toBeAddedElement = new SimpleBooleanProperty(false);
    private BooleanProperty isIdElement = new SimpleBooleanProperty(false);

    private Integer instructionId;

    private String elementId;
    private WebElement element;

    private String xPath;
    private String innerHTML;

    private Map<String, String> savedReferences = new HashMap<>();

    // graphic attributes
    private AnchorPane graphicRepresentation;
    private HBox elementPanel;
    private HBox actionPanel;

    private Label nameLabel;

    private TextField nameField;

    private Button blockButton;
    private Button moveUpButton;
    private Button moveDownButton;
    private Button moreOptionsButton;
    private Button saveButton;
    private Button deleteButton;

    private ImageView clickImage;
    private ImageView insertImage;

    // event handlers
    private Map<EventType, List<EventHandler>> eventHandlerMap = new HashMap<>();

    // Very important sequence on initiation
    private static ABRPriorities abrPriorities;
    // Static block to initialize
    static {
        abrPriorities = ABRPriorities.getInstance();
    }

    public ABRWebElement(WebElement element) {
        initFromWebElement(element);
    }

    public ABRWebElement(WebElement element, String priority) {
        updatePriorities(priority, null);
        initFromWebElement(element);
    }

    public ABRWebElement(BlockLoopInstructionDTO instruction) {
        updatePriorities(null, instruction);
        initFromBlockLoopInstruction(instruction);
    }

    private void updatePriorities(String priority, BlockLoopInstructionDTO instruction) {
        if (abrPriorities.getJobId() == null) {
            abrPriorities.setJobId(instruction.getBlock().getBotJob().getId());
            if (instruction.getBlock().getBotJob().getHomeBanking().getPriority() != null) {
                abrPriorities.loadPrioritiesFromString(
                        instruction.getBlock().getBotJob().getHomeBanking().getPriority());
            } else {
                abrPriorities.loadPriorities();
            }
        } else if (abrPriorities.getJobId()
                != instruction.getBlock().getBotJob().getId()) {
            abrPriorities.setJobId(instruction.getBlock().getBotJob().getId());
            if (instruction.getBlock().getBotJob().getHomeBanking().getPriority() != null) {
                abrPriorities.loadPrioritiesFromString(
                        instruction.getBlock().getBotJob().getHomeBanking().getPriority());
            } else {
                abrPriorities.loadPriorities();
            }
        }
    }

    private void initFromWebElement(WebElement element) {
        initUI();
        boolean isAnchor = element.getTagName().equals(WebElementTagNameEnum.ANCHOR.getValue());
        boolean isOption = element.getTagName().equals(WebElementTagNameEnum.OPTION.getValue());

        //        if (abrPriorities.getJobId() != null) {
        //            for (Priority priority : abrPriorities.getAllPriorityList()) {
        //                switch (priority.getPriorityType()) {
        //                    case attribute -> {
        //                        String attributeValue =
        //                                element.getAttribute(priority.getName().get(0));
        //                        if (attributeValue != null && !attributeValue.isBlank()) {
        //                            savedReferences.put(priority.getName().get(0), attributeValue);
        //                        }
        //                    }
        //                    case xpath -> savedReferences.put(
        //                            priority.getName().get(0), ABRWebUtil.extractWebElementXPath(element));
        //                    case coordinates -> {
        //                        Rectangle coordinates = element.getRect();
        //                        savedReferences.put(
        //                                priority.getName().get(0),
        //                                (coordinates.getX() + (coordinates.getWidth() / 2)) + ","
        //                                        + (coordinates.getY() + (coordinates.getHeight() / 2)));
        //                    }
        //                }
        //            }
        //        }

        String ariaLabelValue = element.getAttribute(WebElementAttributeEnum.ARIA_LABEL.getValue());
        String innerHTMLValue = element.getAttribute(WebElementAttributeEnum.INNER_HTML.getValue());
        String formControlNameAttributeValue =
                element.getAttribute(WebElementAttributeEnum.FORM_CONTROL_NAME.getValue());
        String testIdAttributeValue = element.getAttribute(WebElementAttributeEnum.TEST_ID.getValue());
        String idAttributeValue = element.getAttribute(WebElementAttributeEnum.ID.getValue());
        String nameAttributeValue = element.getAttribute(WebElementAttributeEnum.NAME.getValue());
        String valueAttributeValue = element.getAttribute(WebElementAttributeEnum.VALUE.getValue());
        String valueHRefFile = extractFileExtension(element.getAttribute(WebElementAttributeEnum.HREF.getValue()));

        clickElement.setValue(isClickable(element));

        boolean hasAriaLabel = ariaLabelValue != null && !ariaLabelValue.isBlank();
        boolean hasInnerHTML = innerHTMLValue != null && !innerHTMLValue.isBlank();
        boolean hasInnerHTMLTag = hasInnerHTML && (innerHTMLValue.contains("<") || innerHTMLValue.contains(">"));
        boolean hasFormControlName = formControlNameAttributeValue != null && !formControlNameAttributeValue.isBlank();
        boolean hasTestId = testIdAttributeValue != null && !testIdAttributeValue.isBlank();
        boolean hasName = nameAttributeValue != null && !nameAttributeValue.isBlank();
        boolean hasId = idAttributeValue != null && !idAttributeValue.isBlank();
        boolean hasValue = valueAttributeValue != null && !valueAttributeValue.isBlank();
        boolean hasHRefFile = valueHRefFile != null && !valueHRefFile.isBlank();

        if (isOption && hasValue) {
            nameLabel.setText(valueAttributeValue);
            nameField.setText(valueAttributeValue);
        } else if (hasFormControlName) {
            nameLabel.setText(formControlNameAttributeValue);
            nameField.setText(formControlNameAttributeValue);
        } else if (hasTestId) {
            nameLabel.setText(testIdAttributeValue);
            nameField.setText(testIdAttributeValue);
        } else if (hasName) {
            nameLabel.setText(nameAttributeValue);
            nameField.setText(nameAttributeValue);
        } else if (hasAriaLabel) {
            nameLabel.setText(ariaLabelValue);
            nameField.setText(ariaLabelValue);
        } else if (isAnchor && hasInnerHTML && !hasInnerHTMLTag) {
            nameLabel.setText(innerHTMLValue);
            nameField.setText(innerHTMLValue);
        } else if (hasId) {
            nameLabel.setText(idAttributeValue);
            nameField.setText(idAttributeValue);
        } else if (hasHRefFile) {
            nameLabel.setText(valueHRefFile + " File");
            nameField.setText(valueHRefFile + " File");
        } else {
            nameLabel.setText(ABRConstants.DEFAULT_VALUE_NO_IDENTIFICATION);
            nameField.setText(ABRConstants.DEFAULT_VALUE_NO_IDENTIFICATION);
        }

        String extRef = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_EXT_REFERENCE);
        String extRefSub = extRef.substring(extRef.indexOf("'") + 1, extRef.length() - 1);
        // isIdElement.setValue(hasTestId &&
        // testIdAttributeValue.equals("web-banking-payment-core.payment-details.external-reference"));
        isIdElement.setValue(hasTestId && testIdAttributeValue.equals(extRefSub));

        // this is goign to be done before and match the case
        //        xPath = ABRWebUtil.extractWebElementXPath(element);
        elementId = ((RemoteWebElement) element).getId();
        this.element = element;
        toBeAddedElement.setValue(true);
    }

    private boolean isClickable(WebElement element) {
        List<WebElementTagNameEnum> clickableTags = WebElementTagNameEnum.clickableTags();
        boolean isClickableTag =
                clickableTags.stream().anyMatch(t -> t.getValue().equals(element.getTagName()));
        List<WebElementAttributeTypeValueEnum> clickableValues = WebElementAttributeTypeValueEnum.getClickableValues();
        boolean isClickableValue = clickableValues.stream()
                .anyMatch(v -> v.getValue().equals(element.getAttribute(WebElementAttributeEnum.TYPE.getValue())));
        boolean isInputTag = element.getTagName().equals(WebElementTagNameEnum.INPUT.getValue());
        return (isClickableTag && !isInputTag) || (isInputTag && isClickableValue && isClickableTag);
    }

    private void initFromBlockLoopInstruction(BlockLoopInstructionDTO instruction) {
        initUI();
        instructionId = instruction.getId();
        nameLabel.setText(instruction.getName());
        nameField.setText(instruction.getName());
        xPath = instruction.getPath();
        String[] actionReference = instruction.getActions().split(ABRConstants.ACTION_SPECIFICATIONS_SPLITTER);
        if (actionReference.length > 1) {
            nameLabel.setText(actionReference[1]);
            nameField.setText(actionReference[1]);
        }
        boolean isClickAction = actionReference[0].equals(ABRConstants.CLICK);
        clickElement.setValue(isClickAction);
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
        clickImage = componentBuilder.buildImageView(ABRConstants.ICON_CLICK, ABRConstants.SPACE_M);
        insertImage = componentBuilder.buildImageView(ABRConstants.ICON_INSERT, ABRConstants.SPACE_M);

        saveButton = componentBuilder.buildButton("  Save  ", ABRConstants.SPACE_M, Insets.EMPTY);
        saveButton.setMaxHeight(ABRConstants.SPACE_L);

        nameField = new TextField();
        nameField.setMaxHeight(ABRConstants.SPACE_L);

        nameLabel = new Label();
        nameLabel.setMaxHeight(ABRConstants.SPACE_L);

        StackPane nameGroup = new StackPane(nameLabel, nameField);

        HBox nameFieldsGroup = new HBox(nameGroup, saveButton);
        StackPane actionGroup = new StackPane(clickImage, insertImage);
        elementPanel = new HBox(actionGroup, nameFieldsGroup);
        elementPanel.setSpacing(ABRConstants.SPACE_XS);

        AnchorPane.setLeftAnchor(elementPanel, ABRConstants.SPACE_XS);
        AnchorPane.setTopAnchor(elementPanel, ABRConstants.SPACE_XS);
        AnchorPane.setBottomAnchor(elementPanel, ABRConstants.SPACE_XS);
    }

    private void initActionPanel() {
        moreOptionsButton = componentBuilder.buildButton(
                "", ABRConstants.SPACE_L, ABRConstants.ICON_EDIT, ABRConstants.SPACE_M, Insets.EMPTY);
        blockButton = componentBuilder.buildButton(
                "", ABRConstants.SPACE_L, ABRConstants.ICON_BLOCK, ABRConstants.SPACE_M, Insets.EMPTY);
        moveUpButton = componentBuilder.buildButton(
                "", ABRConstants.SPACE_L, ABRConstants.ICON_UP, ABRConstants.SPACE_M, Insets.EMPTY);
        moveDownButton = componentBuilder.buildButton(
                "", ABRConstants.SPACE_L, ABRConstants.ICON_DOWN, ABRConstants.SPACE_M, Insets.EMPTY);
        deleteButton = componentBuilder.buildButton(
                "", ABRConstants.SPACE_L, ABRConstants.ICON_CROSS, ABRConstants.SPACE_M, Insets.EMPTY);

        blockButton.setPrefWidth(ABRConstants.SPACE_L);
        actionPanel = new HBox(blockButton, moveUpButton, moveDownButton, moreOptionsButton, deleteButton);
        actionPanel.setSpacing(ABRConstants.SPACE_XS);
        actionPanel.setAlignment(Pos.CENTER_RIGHT);

        AnchorPane.setTopAnchor(actionPanel, ABRConstants.SPACE_XS);
        AnchorPane.setBottomAnchor(actionPanel, ABRConstants.SPACE_XS);
        AnchorPane.setRightAnchor(actionPanel, ABRConstants.SPACE_XS);
    }

    private void initUIBehaviour() {
        insertImage.visibleProperty().bind(clickElement.not());
        clickImage.visibleProperty().bind(clickElement);
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
                    ABRSharedResources.getInstance().getEntityById(BlockLoopInstructionDTO.class, this.instructionId);
            ObservableList<BlockLoopInstructionDTO> list = ABRSharedResources.getInstance()
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
            defaultBlock.setBotJob(item.getBlock().getBotJob());
            defaultBlock.setBlockLoopInstructions(items);
            ABRSharedResources.getInstance().addEntity(defaultBlock, BlockDTO.class, () -> {
                System.out.println("added : " + defaultBlock.getId());
                defaultBlock.setBlockOrderNumber(defaultBlock.getId() - 1);
                ABRSharedResources.getInstance().updateEntity(defaultBlock, BlockDTO.class, () -> {
                    ABRSharedResources.getInstance().refreshEntity(defaultBlock, BlockDTO.class, () -> {
                        ABRSharedResources.getInstance().refreshEntity(previousBlock, BlockDTO.class);
                    });
                });
            });
        });
        moveUpButton.setOnAction(e -> switchInstruction(-1));

        moveDownButton.setOnAction(e -> switchInstruction(1));
        saveButton.setOnAction(e -> {
            editingElement.setValue(false);
            nameLabel.setText(nameField.getText());
            ABRLogger.getInstance(ABRWebElement.class).info("saving instruction with id: " + instructionId);
            if (instructionId != null && instructionId != 0) {
                BlockLoopInstructionDTO instruction =
                        ABRSharedResources.getInstance().getEntityById(BlockLoopInstructionDTO.class, instructionId);
                instruction.setName(nameLabel.getText());
                String action = instruction.getActions();
                if (action.contains(ABRConstants.INSERT)) {
                    instruction.setActions(action.split(ABRConstants.ACTION_SPECIFICATIONS_SPLITTER)[0]
                            + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER
                            + nameLabel.getText());
                }
                ABRSharedResources.getInstance().updateEntity(instruction, BlockLoopInstructionDTO.class);
            }
        });
        deleteButton.setOnAction(e -> {
            BlockLoopInstructionDTO instruction =
                    ABRSharedResources.getInstance().getEntityById(BlockLoopInstructionDTO.class, instructionId);
            int instructionIndex = instruction.getInstructionOrderNumber();
            BlockDTO block = instruction.getBlock();
            ABRSharedResources.getInstance().removeEntity(instruction, BlockLoopInstructionDTO.class, () -> {
                Queue<BlockLoopInstructionDTO> instructionQueue = block.getBlockLoopInstructions().stream()
                        .filter(i -> i.getInstructionOrderNumber() > instructionIndex)
                        .collect(Collectors.toCollection(LinkedBlockingQueue::new));
                instructionQueue.forEach(
                        instr -> instr.setInstructionOrderNumber(instr.getInstructionOrderNumber() - 1));
                ABRSharedResources.getInstance()
                        .updateAllEntity(
                                instructionQueue,
                                BlockLoopInstructionDTO.class,
                                () -> new ABRAlertScene(
                                        Alert.AlertType.INFORMATION,
                                        "Successfull deletion",
                                        "The element has been deleted successfully",
                                        ButtonType.OK));
            });
        });
        EventHandler<MouseEvent> mouseEventEventHandler = mouseEvent -> {
            editingElement.setValue(false);
        };
        graphicRepresentation.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseEventEventHandler);
    }

    private void switchInstruction(int directionQuantity) {
        BlockLoopInstructionDTO currentInstruction =
                ABRSharedResources.getInstance().getEntityById(BlockLoopInstructionDTO.class, instructionId);
        int order = currentInstruction.getInstructionOrderNumber();
        BlockDTO block = ABRSharedResources.getInstance()
                .getEntityById(BlockDTO.class, currentInstruction.getBlock().getId());
        List<BlockLoopInstructionDTO> instructionList = block.getBlockLoopInstructions();
        BlockLoopInstructionDTO instructionToChange = instructionList.stream()
                .filter(i -> i.getInstructionOrderNumber() == order + directionQuantity)
                .findFirst()
                .orElseThrow();
        currentInstruction.setInstructionOrderNumber(order + directionQuantity);
        instructionToChange.setInstructionOrderNumber(order);
        ABRSharedResources.getInstance()
                .updateEntity(currentInstruction, BlockLoopInstructionDTO.class, () -> ABRSharedResources.getInstance()
                        .updateEntity(instructionToChange, BlockLoopInstructionDTO.class));
    }

    public BlockLoopInstructionDTO buildBlockLoopInstruction(Integer orderNumber) {
        BlockLoopInstructionDTO loop = new BlockLoopInstructionDTO();
        loop.setActionCustomMaxWaitSec(30);
        loop.setDescription("loop desc");
        loop.setEncrypted(false);
        loop.setInstructionOrderNumber(orderNumber);
        loop.setOptional(false);
        loop.setPath(xPath);
        String action;
        // TODO: Make a better thing than this
        if (isIdElement.get()) {
            action = ABRConstants.EXTRACT + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER + "EXTERNAL_REFERENCE";
        } else {
            action = clickElement.get()
                    ? ABRConstants.CLICK
                    : ABRConstants.INSERT + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText();
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

    public void setCallbackOnMouseClick(ABRCallback callback) {
        moveUpButton.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> callback.execute());
        moveDownButton.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> callback.execute());
        deleteButton.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> callback.execute());
    }

    public String getXPath() {
        return xPath;
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

    public String getxPath() {
        return xPath;
    }

    public void setxPath(String xPath) {
        this.xPath = xPath;
    }

    public Map<String, String> getSavedReferences() {
        return savedReferences;
    }

    public void setSavedReferences(Map<String, String> savedReferences) {
        this.savedReferences = savedReferences;
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

}

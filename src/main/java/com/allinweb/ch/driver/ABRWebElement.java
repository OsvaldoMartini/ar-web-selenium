package com.allinweb.ch.driver;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementAttributeTypeValueEnum;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.persistence.SearchReturn;
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

public class ABRWebElement {

    private final ABRComponentBuilder componentBuilder = new ABRComponentBuilder();

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
    private static ABRPriorities abrPriorities;
    private static final PerformMessage performMessage;
    // Static block to initialize
    static {
        abrPriorities = ABRPriorities.getInstance();
        performMessage = PerformMessage.getInstance();
    }

    public ABRWebElement(WebElement element, int jobId, WebElementTagNameEnum typeSearch, String iFrameXPath) {
        abrPriorities.setJobId(jobId);
        this.tagType = typeSearch;
        this.iFrameXPath = iFrameXPath;
        initFromWebElement(element);
    }

    public ABRWebElement(WebElement element, int jobId, WebElementTagNameEnum typeSearch) {
        this.tagNameDefined = element.getTagName();
        abrPriorities.setJobId(jobId);
        this.tagType = typeSearch;
        initFromWebElement(element);
    }

    public ABRWebElement(SearchReturn searchReturn, int jobId) {
        abrPriorities.setJobId(jobId);
        this.searchReturn = searchReturn;
        this.tagType = searchReturn.getTagType();
        this.attributeValue = searchReturn.getAttributeValue();
        this.tagNameDefined = searchReturn.getOriginalTagName();
        this.mainXPath = searchReturn.getMainXPath();
        this.mainCoordinates = searchReturn.getMainCoordinates();
        this.iFrameXPath = !Strings.isNullOrEmpty(searchReturn.getiFrameXPath()) ? searchReturn.getiFrameXPath() : null;

        //        this.attributeValue = element.getAttribute(searchReturn.getAttributeType());
        initFromWebElement(searchReturn.getElement());
    }

    public ABRWebElement(
            Map.Entry<String, WebElement> entry, String attributeName, int jobId, WebElementTagNameEnum typeElement) {
        abrPriorities.setJobId(jobId);
        WebElement element = entry.getValue();
        this.mainXPath = entry.getKey();
        this.attributeValue = element.getAttribute(attributeName);
        if (searchReturn == null || this.attributeValue == null) {
            this.tagNameDefined = attributeName;

        } else {
            this.tagNameDefined = searchReturn.getOriginalTagName();
        }
        if (typeElement != null) {
            tagType = typeElement;
        }
        initFromWebElement(element);
    }

    public ABRWebElement(WebElement element, String priority) {
        updatePriorities(priority, null);
        if (searchReturn == null) {
            this.tagNameDefined = element.getTagName();
        } else {
            this.tagNameDefined = searchReturn.getOriginalTagName();
        }
        initFromWebElement(element);
    }

    public ABRWebElement(BlockLoopInstructionDTO instruction) {
        botJobId = instruction.getBlock().getBotJobDTO().getId();
        updatePriorities(null, instruction);
        initFromBlockLoopInstruction(instruction);
    }

    private void updatePriorities(String priority, BlockLoopInstructionDTO instruction) {
        botJobId = instruction.getBlock().getBotJobDTO().getId();
        if (abrPriorities.getJobId() == null) {
            abrPriorities.setJobId(botJobId);
            if (instruction.getBlock().getBotJobDTO().getHomeBanking().getPriority() != null) {
                abrPriorities.loadPrioritiesFromString(
                        instruction.getBlock().getBotJobDTO().getHomeBanking().getPriority());
            } else {
                abrPriorities.loadPriorities();
            }
        } else if (abrPriorities.getJobId() != botJobId) {
            abrPriorities.setJobId(botJobId);
            if (instruction.getBlock().getBotJobDTO().getHomeBanking().getPriority() != null) {
                abrPriorities.loadPrioritiesFromString(
                        instruction.getBlock().getBotJobDTO().getHomeBanking().getPriority());
            } else {
                abrPriorities.loadPriorities();
            }
        }
    }

    private void initFromWebElement(WebElement element) {
        initUI();

        try {

            if (this.tagNameDefined == null) {

                performMessage.couldNotFindElement("No TagName");
                //                new ABRAlertScene(
                //                        Alert.AlertType.ERROR,
                //                        "Not possible to identity the Tag Name",
                //                        "Try to Re Scanner or Re Select the Element!",
                //                        ButtonType.OK);

                return;
            }
        } catch (Exception e) {
            performMessage.couldNotFindElement("No TagName");
            //            new ABRAlertScene(
            //                    Alert.AlertType.ERROR,
            //                    "Not possible to identity the Tag Name",
            //                    "Try to Re Scanner or Re Select the Element!",
            //                    ButtonType.OK);

            return;
        }

        boolean isAnchor = this.tagNameDefined.equalsIgnoreCase(WebElementTagNameEnum.ANCHOR.getValue());
        boolean isOption = this.tagNameDefined.equalsIgnoreCase(WebElementTagNameEnum.OPTION.getValue());

        try {
            if (abrPriorities.getAllPriorityList().size() == 0) {
                abrPriorities.loadPriorities();
                ABRLogger.getInstance(Thread.class).finer("Reloaded abrPriorities.loadPriorities()");
            }

            if (searchReturn == null && abrPriorities.getJobId() != null) {
                for (Priority priority : abrPriorities.getAllPriorityList()) {
                    try {
                        switch (priority.getPriorityType()) {
                            case attribute -> {
                                String attributeValue =
                                        element.getAttribute(priority.getName().get(0));
                                if (attributeValue != null && !attributeValue.isBlank()) {
                                    savedReferences.put(priority.getName().get(0), attributeValue);
                                }
                            }
                            case xpath, ByXPath -> {
                                if (Strings.isNullOrEmpty(mainXPath)) {
                                    mainXPath = ABRWebUtil.extractWebElementXPath(element);
                                    savedReferences.put(priority.getName().get(0), mainXPath);
                                } else {

                                    savedReferences.put(priority.getName().get(0), mainXPath);
                                }
                            }

                            case coordinates -> {
                                Rectangle coord = element.getRect();
                                String coordTemp = (coord.getX() + (coord.getWidth() / 2)) + ","
                                        + (coord.getY() + (coord.getHeight() / 2));
                                savedReferences.put(priority.getName().get(0), coordTemp);
                            }
                        }
                    } catch (EnumConstantNotPresentException ex) {
                        throw ex;
                    }
                }
            } else {
                // Most Important to find any kind of element

                if (searchReturn != null
                        && searchReturn.getxPathWorkedFirst().equalsIgnoreCase(ABRConstants.ABSOLUT_XPATH)) {
                    savedReferences.put(
                            "absolutXPath",
                            searchReturn.getAbsolutXPath()); // Creates Seq to Fin element Via Instructions - 1
                    savedReferences.put(
                            "currentXPath",
                            searchReturn.getCurrentXPath()); // Creates Seq to Fin element Via Instructions - 2
                    savedReferences.put(
                            "customXPath",
                            searchReturn.getCustomXPath()); // Creates Seq to Fin element Via Instructions - 2
                } else if (searchReturn.getxPathWorkedFirst().equalsIgnoreCase(ABRConstants.REGULAR_XPATH)) {
                    savedReferences.put(
                            "currentXPath",
                            searchReturn.getCurrentXPath()); // Creates Seq to Fin element Via Instructions - 1
                    savedReferences.put(
                            "absolutXPath",
                            searchReturn.getAbsolutXPath()); // Creates Seq to Fin element Via Instructions - 2
                    savedReferences.put(
                            "customXPath",
                            searchReturn.getCustomXPath()); // Creates Seq to Fin element Via Instructions - 2
                } else if (searchReturn != null && !Strings.isNullOrEmpty(mainXPath)) {
                    savedReferences.put("xpath", searchReturn.getCurrentXPath());
                } else if (!Strings.isNullOrEmpty(attributeValue)) {
                    savedReferences.put("attribute", attributeValue);
                } else { // In case of Dynamic Creation
                    savedReferences.put("xpath", ABRWebUtil.extractWebElementXPath(element));
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
            }

        } catch (Exception ex) {
            throw ex;
        }

        String ariaLabelValue = null;
        String innerHTMLValue = null;
        String formControlNameAttributeValue = null;
        String testIdAttributeValue = null;
        String idAttributeValue = null;
        String nameAttributeValue = null;
        String valueAttributeValue = null;
        String valueHRefFile = null;
        try {

            ariaLabelValue = element.getAttribute(WebElementAttributeEnum.ARIA_LABEL.getValue());
            innerHTMLValue = element.getAttribute(WebElementAttributeEnum.INNER_HTML.getValue());
            formControlNameAttributeValue = element.getAttribute(WebElementAttributeEnum.FORM_CONTROL_NAME.getValue());
            testIdAttributeValue = element.getAttribute(WebElementAttributeEnum.TEST_ID.getValue());
            idAttributeValue = element.getAttribute(WebElementAttributeEnum.ID.getValue());
            nameAttributeValue = element.getAttribute(WebElementAttributeEnum.NAME.getValue());
            valueAttributeValue = element.getAttribute(WebElementAttributeEnum.VALUE.getValue());
            valueHRefFile = extractFileExtension(element.getAttribute(WebElementAttributeEnum.HREF.getValue()));
        } catch (Exception ignore) {

        }

        if (searchReturn == null) {
            searchReturn = new SearchReturn();
            if (nameAttributeValue != null) {
                searchReturn.setAttributeValue(nameAttributeValue);
            } else {
                searchReturn.setAttributeValue(tagNameDefined);
            }
        }

        String textLabel = searchReturn.getAttributeValue();

        if (Strings.isNullOrEmpty(textLabel)) {
            textLabel = extractAllText(element, tagNameDefined);
        }

        if (Strings.isNullOrEmpty(textLabel)) {
            textLabel = getTextRecursively(element, tagNameDefined);
        }

        if (Strings.isNullOrEmpty(textLabel)) {
            textLabel = getTextRecursivelyByParent(element, tagNameDefined);
        }

        boolean hasButton = this.tagNameDefined.equalsIgnoreCase("button") && isClickable() && !textLabel.isBlank();
        boolean hasAriaLabel = ariaLabelValue != null && !ariaLabelValue.isBlank();
        boolean hasInnerHTML = innerHTMLValue != null && !innerHTMLValue.isBlank() && !hasButton;
        boolean hasInnerHTMLTag = hasInnerHTML && (innerHTMLValue.contains("<") || innerHTMLValue.contains(">"));
        boolean hasFormControlName =
                formControlNameAttributeValue != null && !formControlNameAttributeValue.isBlank() && !hasButton;
        boolean hasTestId = testIdAttributeValue != null && !testIdAttributeValue.isBlank() && !hasButton;
        boolean hasName = nameAttributeValue != null && !nameAttributeValue.isBlank() && !hasButton;
        boolean hasId = idAttributeValue != null && !idAttributeValue.isBlank() && !hasButton;
        boolean hasValue = valueAttributeValue != null && !valueAttributeValue.isBlank();
        boolean hasHRefFile = valueHRefFile != null && !valueHRefFile.isBlank();
        boolean hasParagraph = !Strings.isNullOrEmpty(textLabel) && this.tagNameDefined.equalsIgnoreCase("p");
        boolean hasSpan = !Strings.isNullOrEmpty(textLabel) && this.tagNameDefined.equalsIgnoreCase("span");
        boolean hasDiv = !Strings.isNullOrEmpty(textLabel) && this.tagNameDefined.equalsIgnoreCase("div");
        boolean hasLabel = !Strings.isNullOrEmpty(textLabel) && this.tagNameDefined.equalsIgnoreCase("label");

        if ((hasSpan || hasDiv || hasLabel) && !outputElement.getValue()) {
            textElement.setValue(true);
        }

        if (innerHTMLValue != null) {
            innerHTMLValue = innerHTMLValue.replaceAll("  ", "");
            innerHTMLValue = innerHTMLValue.replaceAll("\n", "");
        }

        if (Strings.isNullOrEmpty(searchReturn.getAttributeValue())) {
            if (!Strings.isNullOrEmpty(textLabel)) {
                searchReturn.setAttributeValue(textLabel);
            } else if (!Strings.isNullOrEmpty(valueAttributeValue)) {
                searchReturn.setAttributeValue(valueAttributeValue);
            } else if (!Strings.isNullOrEmpty(testIdAttributeValue)) {
                searchReturn.setAttributeValue(testIdAttributeValue);
            } else if (!Strings.isNullOrEmpty(nameAttributeValue)) {
                searchReturn.setAttributeValue(nameAttributeValue);
            } else if (!Strings.isNullOrEmpty(ariaLabelValue)) {
                searchReturn.setAttributeValue(ariaLabelValue);
            } else if (!Strings.isNullOrEmpty(formControlNameAttributeValue)) {
                searchReturn.setAttributeValue(innerHTMLValue);
            } else if (!Strings.isNullOrEmpty(innerHTMLValue)) {
                searchReturn.setAttributeValue(textLabel);
            } else {
                searchReturn.setAttributeValue(searchReturn.getDefinedName());
            }
        }

        if (searchReturn != null && !Strings.isNullOrEmpty(searchReturn.getDefinedName())) {
            nameLabel.setText(searchReturn.getDefinedName().trim());
            nameField.setText(searchReturn.getDefinedName().trim());
        } else if (searchReturn != null && !Strings.isNullOrEmpty(searchReturn.getAttributeValue())) {
            nameLabel.setText(searchReturn.getAttributeValue().trim());
            nameField.setText(searchReturn.getAttributeValue().trim());
        } else if (isOption && hasValue) {
            nameLabel.setText(valueAttributeValue.trim());
            nameField.setText(valueAttributeValue.trim());
        } else if (hasFormControlName) {
            nameLabel.setText(formControlNameAttributeValue.trim());
            nameField.setText(formControlNameAttributeValue.trim());
        } else if (hasTestId) {
            nameLabel.setText(testIdAttributeValue.trim());
            nameField.setText(testIdAttributeValue.trim());
        } else if (hasName) {
            nameLabel.setText(nameAttributeValue.trim());
            nameField.setText(nameAttributeValue.trim());
        } else if (hasAriaLabel) {
            nameLabel.setText(ariaLabelValue.trim());
            nameField.setText(ariaLabelValue.trim());
        } else if (isAnchor && hasInnerHTML && !hasInnerHTMLTag) {
            nameLabel.setText(innerHTMLValue.trim());
            nameField.setText(innerHTMLValue.trim());
        } else if (hasId) {
            nameLabel.setText(idAttributeValue.trim());
            nameField.setText(idAttributeValue.trim());
        } else if (hasHRefFile) {
            nameLabel.setText(valueHRefFile + " File".trim());
            nameField.setText(valueHRefFile + " File".trim());
        } else if (hasParagraph) {
            nameLabel.setText(textLabel.trim());
            nameField.setText(this.tagNameDefined.trim());
        } else if (hasButton) {
            nameLabel.setText(textLabel.trim());
            nameField.setText(this.tagNameDefined.trim());
        } else if (hasSpan) {
            nameLabel.setText(textLabel.trim());
            nameField.setText(this.tagNameDefined.trim());
        } else if (hasDiv) {
            nameLabel.setText(textLabel.trim());
            nameField.setText(this.tagNameDefined.trim());
        } else if (hasLabel) {
            nameLabel.setText(textLabel.trim());
            nameField.setText(this.tagNameDefined.trim());
        } else if (this.tagNameDefined.equalsIgnoreCase("input")
                || this.tagNameDefined.equalsIgnoreCase("button")
                || this.tagNameDefined.equalsIgnoreCase("output")) {
            nameLabel.setText(textLabel.trim());
            nameField.setText(this.tagNameDefined.trim());
        } else if (!Strings.isNullOrEmpty(element.getText())) {
            nameLabel.setText(element.getText().trim());
            nameField.setText(this.tagNameDefined.trim());
        } else {
            nameLabel.setText(ABRConstants.DEFAULT_VALUE_NO_IDENTIFICATION);
            nameField.setText(ABRConstants.DEFAULT_VALUE_NO_IDENTIFICATION);
        }
        try {

            String extRef = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_EXT_REFERENCE);
            if (extRef != null) {
                String extRefSub = extRef.substring(extRef.indexOf("'") + 1, extRef.length() - 1);
                // isIdElement.setValue(hasTestId &&
                // testIdAttributeValue.equalsIgnoreCase("web-banking-payment-core.payment-details.external-reference"));
                isIdElement.setValue(hasTestId && testIdAttributeValue.equalsIgnoreCase(extRefSub));
            }
        } catch (Exception ex) {
            throw ex;
        }

        // Identify if the element is an INPUT, BUTTON, or LABEL
        nameFieldTitle = nameField.getText();

        boolean isElementHidden = false;
        try {
            isElementHidden = element.getAttribute("type") != null
                    && element.getAttribute("type").equalsIgnoreCase("hidden");
        } catch (Exception ignored) {
        }

        boolean isInput = false;
        boolean isButton = false;
        boolean isLabel = false;

        try {
            isInput = this.tagNameDefined.equalsIgnoreCase("INPUT") && element.getAttribute("type") != null;
            isButton = this.tagNameDefined.equalsIgnoreCase("BUTTON");
            isLabel = this.tagNameDefined.equalsIgnoreCase("LABEL") && !Strings.isNullOrEmpty(element.getText());
        } catch (Exception ignore) {

        }

        hiddenElement.setValue(false);
        outputElement.setValue(false);
        insertElement.setValue(false);
        clickElement.setValue(false);
        textElement.setValue(false);

        // Now proceed with the rest of your code
        if (!isElementHidden) {
            hiddenElement.setValue(false);

            if (tagType != null) {
                if (tagType.equals(WebElementTagNameEnum.BUTTON)) {
                    // Handle the button case (if BUTTON is forced)
                    clickElement.setValue(true);
                } else if (tagType.getValue().equalsIgnoreCase(ABRConstants.SET_VALUE)) {
                    // Handle the SET_VALUE case
                    setValueElem.setValue(true);
                } else if (tagType.getValue().equalsIgnoreCase(ABRConstants.GET_VALUE)) {
                    // Handle the GET_VALUE case
                    getValueElem.setValue(true);
                } else if (tagType.getValue().equalsIgnoreCase(ABRConstants.CHECK_VALUE)) {
                    // Handle the CHECK_VALUE case
                    checkValueElem.setValue(true);
                } else if (tagType.getValue().equalsIgnoreCase(ABRConstants.OUTPUT)) {
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
        //        xPath = ABRWebUtil.extractWebElementXPath(element);
        elementId = ((RemoteWebElement) element).getId();
        this.element = element;
        toBeAddedElement.setValue(true);
    }

    private boolean isClickable(WebElement element, String tagNameDefined) {
        List<WebElementTagNameEnum> clickableTags = WebElementTagNameEnum.clickableTags();
        boolean isClickableTag =
                clickableTags.stream().anyMatch(t -> t.getValue().equalsIgnoreCase(tagNameDefined));
        List<WebElementAttributeTypeValueEnum> clickableValues = WebElementAttributeTypeValueEnum.getClickableValues();
        boolean isClickableValue = clickableValues.stream().anyMatch(v -> v.getValue()
                .equalsIgnoreCase(element.getAttribute(WebElementAttributeEnum.TYPE.getValue())));
        boolean isInputTag = tagNameDefined.equalsIgnoreCase(WebElementTagNameEnum.INPUT.getValue());
        return (isClickableTag && !isInputTag) || (isInputTag && isClickableValue && isClickableTag);
    }

    private void initFromBlockLoopInstruction(BlockLoopInstructionDTO instruction) {

        // Split the description string
        if (instruction.getOperation() != null) {
            String[] descriptionArray = instruction.getOperation().split(ABRConstants.ACTION_SPECIFICATIONS_SPLITTER);

            // Initialize the descriptions array with the length of the descriptionArray
            operationsElement = new StringProperty[descriptionArray.length];

            // Convert each string to a StringProperty
            for (int i = 0; i < descriptionArray.length; i++) {
                operationsElement[i] = new SimpleStringProperty(descriptionArray[i]);
            }
        }
        String[] actionReference = instruction.getActions().split(ABRConstants.ACTION_SPECIFICATIONS_SPLITTER);

        isCheckValidator = actionReference[0].equalsIgnoreCase(ABRConstants.CHECK_VALUE);

        instructionId = instruction.getId();
        instrName = instruction.getName();
        instrOperation = instruction.getOperation();
        initUI();

        nameLabel.setText(instruction.getName());
        nameField.setText(instruction.getName());
        mainXPath = instruction.getPath();
        if (actionReference.length > 1) {
            nameLabel.setText(actionReference[1]);
            nameField.setText(actionReference[1]);
        }

        if (actionReference[0].equalsIgnoreCase(ABRConstants.HIDDEN)) {
            hiddenElement.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ABRConstants.OUTPUT)) {
            outputElement.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ABRConstants.CLICK)) {
            clickElement.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ABRConstants.INSERT)) {
            textElement.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ABRConstants.SET_VALUE)) {
            setValueElem.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ABRConstants.GET_VALUE)) {
            getValueElem.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ABRConstants.CHECK_VALUE)) {
            checkValueElem.setValue(true);
        } else if (actionReference[0].equalsIgnoreCase(ABRConstants.HOLD)) {
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
        hiddenImage = componentBuilder.buildImageView(ABRConstants.ICON_HIDDEN, ABRConstants.SPACE_M);
        outputImage = componentBuilder.buildImageView(ABRConstants.ICON_OUTPUT, ABRConstants.SPACE_M);
        clickImage = componentBuilder.buildImageView(ABRConstants.ICON_CLICK, ABRConstants.SPACE_M);
        insertImage = componentBuilder.buildImageView(ABRConstants.ICON_INSERT, ABRConstants.SPACE_M);
        textImage = componentBuilder.buildImageView(ABRConstants.ICON_TEXT, ABRConstants.SPACE_M);

        setImage = componentBuilder.buildImageView(ABRConstants.ICON_SET_VALUE, ABRConstants.SPACE_M);
        getImage = componentBuilder.buildImageView(ABRConstants.ICON_GET_VALUE, ABRConstants.SPACE_M);
        checkImage = componentBuilder.buildImageView(ABRConstants.ICON_CHECK, ABRConstants.SPACE_M);
        holdImage = componentBuilder.buildImageView(ABRConstants.ICON_WAIT, ABRConstants.SPACE_M);

        nameField = new TextField();
        nameField.setMaxHeight(ABRConstants.SPACE_L);

        nameLabel = new Label();
        nameLabel.setMaxHeight(ABRConstants.SPACE_L);

        StackPane nameGroup = new StackPane(nameLabel, nameField);

        HBox nameFieldsGroup = new HBox(nameGroup);
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
        elementPanel.setSpacing(ABRConstants.SPACE_XS);

        AnchorPane.setLeftAnchor(elementPanel, ABRConstants.SPACE_XS);
        AnchorPane.setTopAnchor(elementPanel, ABRConstants.SPACE_XS);
        AnchorPane.setBottomAnchor(elementPanel, ABRConstants.SPACE_XS);
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
            //            blockButton.setPrefWidth(ABRConstants.SPACE_L);
            //            actionPanel
            //                    .getChildren()
            //                    .addAll(
            //                            blockButton,
            //                            moveUpButton,
            //                            moveDownButton,
            //                            //                            moreOptionsButton,
            //                            deleteButton);
        }

        actionPanel.setSpacing(ABRConstants.SPACE_XS);
        actionPanel.setAlignment(Pos.CENTER_RIGHT);

        AnchorPane.setTopAnchor(actionPanel, ABRConstants.SPACE_XS);
        AnchorPane.setBottomAnchor(actionPanel, ABRConstants.SPACE_XS);
        AnchorPane.setRightAnchor(actionPanel, ABRConstants.SPACE_XS);
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

        EventHandler<MouseEvent> mouseEventEventHandler = mouseEvent -> {
            editingElement.setValue(false);
        };
        graphicRepresentation.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseEventEventHandler);
    }

    private void switchInstruction(int directionQuantity) {
        //        try {
        //            BlockLoopInstructionDTO currentInstruction =
        //                    ABRSharedResources.getInstance().getEntityById(BlockLoopInstructionDTO.class,
        // instructionId);
        //            int order = currentInstruction.getInstructionOrderNumber();
        //            BlockDTO block = ABRSharedResources.getInstance()
        //                    .getEntityById(BlockDTO.class, currentInstruction.getBlock().getId());
        //            List<BlockLoopInstructionDTO> instructionList = block.getBlockLoopInstructions();
        //            BlockLoopInstructionDTO instructionToChange = instructionList.stream()
        //                    .filter(i -> i.getInstructionOrderNumber() == order + directionQuantity)
        //                    .findFirst()
        //                    .orElseThrow();
        //            currentInstruction.setInstructionOrderNumber(order + directionQuantity);
        //            instructionToChange.setInstructionOrderNumber(order);
        //            ABRSharedResources.getInstance()
        //                    .updateEntity(
        //                            currentInstruction, BlockLoopInstructionDTO.class, () ->
        // ABRSharedResources.getInstance()
        //                                    .updateEntity(instructionToChange, BlockLoopInstructionDTO.class));
        //
        //        } catch (Exception ex) {
        //            ABRLogger.getInstance(Thread.class).severe("Error switch Instruction -> Cause: " +
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
            action = ABRConstants.EXTRACT_FIELD + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER + "EXTERNAL_REFERENCE";
        } else {
            if (identityHover) {
                action = actionReq.equalsIgnoreCase("INPUT")
                        ? ABRConstants.INSERT + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText()
                        : actionReq.equalsIgnoreCase("OUTPUT")
                                ? ABRConstants.OUTPUT
                                        + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER
                                        + nameLabel.getText()
                                : actionReq.equalsIgnoreCase("OTHER")
                                        ? ABRConstants.OTHER
                                                + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER
                                                + nameLabel.getText()
                                        : actionReq.equalsIgnoreCase("click")
                                                ? ABRConstants.CLICK
                                                : clickElement.get()
                                                        ? ABRConstants.CLICK
                                                        : ABRConstants.INSERT
                                                                + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER
                                                                + nameLabel.getText();
            } else {

                if (tagType != null) {
                    if (tagType.equals(WebElementTagNameEnum.INPUT)) {
                        action =
                                ABRConstants.INSERT + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText();
                    } else if (tagType.equals(WebElementTagNameEnum.HIDDEN)) {
                        action = ABRConstants.INSERT
                                + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER
                                + nameLabel.getText()
                                + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER
                                + ABRConstants.HIDDEN;
                    } else if (tagType.equals(WebElementTagNameEnum.BUTTON)) {
                        action = ABRConstants.CLICK;

                    } else if (tagType.getValue().equalsIgnoreCase(ABRConstants.OUTPUT)) {
                        // Handle the OUTPUT case
                        action =
                                ABRConstants.OUTPUT + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText();
                    } else {
                        action =
                                ABRConstants.OUTPUT + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText();
                    }
                } else {
                    action = clickElement.get()
                            ? ABRConstants.CLICK
                            : ABRConstants.INSERT + ABRConstants.ACTION_SPECIFICATIONS_SPLITTER + nameLabel.getText();
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

    // CHATGPT   I want select the correct type of element following these conditions
    // TODO MORE INTELLIGENT  LOGIC
    public void selectElementType(WebElement element) {
        // Check element tag names
        boolean isAnchor = tagNameDefined.equalsIgnoreCase(WebElementTagNameEnum.ANCHOR.getValue());
        boolean isOption = tagNameDefined.equalsIgnoreCase(WebElementTagNameEnum.OPTION.getValue());

        // Extract various attributes
        String ariaLabelValue = element.getAttribute(WebElementAttributeEnum.ARIA_LABEL.getValue());
        String innerHTMLValue = element.getAttribute(WebElementAttributeEnum.INNER_HTML.getValue());
        String formControlNameAttributeValue =
                element.getAttribute(WebElementAttributeEnum.FORM_CONTROL_NAME.getValue());
        String testIdAttributeValue = element.getAttribute(WebElementAttributeEnum.TEST_ID.getValue());
        String idAttributeValue = element.getAttribute(WebElementAttributeEnum.ID.getValue());
        String nameAttributeValue = element.getAttribute(WebElementAttributeEnum.NAME.getValue());
        String valueAttributeValue = element.getAttribute(WebElementAttributeEnum.VALUE.getValue());
        String valueHRefFile = extractFileExtension(element.getAttribute(WebElementAttributeEnum.HREF.getValue()));
        String tagname = tagNameDefined;
        String textLabel = element.getText();

        // Determine boolean conditions
        boolean hasButton = this.tagNameDefined.equalsIgnoreCase("button")
                && isClickable(element, tagNameDefined)
                && !textLabel.isBlank();
        boolean hasAriaLabel = isValidString(ariaLabelValue);
        boolean hasInnerHTML = isValidString(innerHTMLValue) && !hasButton;
        boolean hasInnerHTMLTag = hasInnerHTML && (innerHTMLValue.contains("<") || innerHTMLValue.contains(">"));
        boolean hasFormControlName = isValidString(formControlNameAttributeValue);
        boolean hasTestId = isValidString(testIdAttributeValue);
        boolean hasName = isValidString(nameAttributeValue);
        boolean hasId = isValidString(idAttributeValue) && !hasButton;
        boolean hasValue = isValidString(valueAttributeValue);
        boolean hasHRefFile = isValidString(valueHRefFile);
        boolean hasParagraph = !Strings.isNullOrEmpty(textLabel) && this.tagNameDefined.equalsIgnoreCase("p");
        boolean hasSpan = !Strings.isNullOrEmpty(textLabel) && this.tagNameDefined.equalsIgnoreCase("span");
        boolean hasDiv = !Strings.isNullOrEmpty(textLabel) && this.tagNameDefined.equalsIgnoreCase("div");

        // Set nameLabel and nameField based on conditions
        if (isOption && hasValue) {
            setElementText(valueAttributeValue, valueAttributeValue);
        } else if (hasFormControlName) {
            setElementText(formControlNameAttributeValue, formControlNameAttributeValue);
        } else if (hasTestId) {
            setElementText(testIdAttributeValue, testIdAttributeValue);
        } else if (hasName) {
            setElementText(nameAttributeValue, nameAttributeValue);
        } else if (hasAriaLabel) {
            setElementText(ariaLabelValue, ariaLabelValue);
        } else if (isAnchor && hasInnerHTML && !hasInnerHTMLTag) {
            setElementText(innerHTMLValue, innerHTMLValue);
        } else if (hasId) {
            setElementText(idAttributeValue, idAttributeValue);
        } else if (hasHRefFile) {
            setElementText(valueHRefFile + " File", valueHRefFile + " File");
        } else if (hasParagraph) {
            setElementText(textLabel, tagname);
        } else if (hasButton) {
            setElementText(textLabel, tagname);
        } else if (hasSpan) {
            setElementText(textLabel, tagname);
        } else if (hasDiv) {
            setElementText(textLabel, tagname);
        } else {
            setElementText(ABRConstants.DEFAULT_VALUE_NO_IDENTIFICATION, ABRConstants.DEFAULT_VALUE_NO_IDENTIFICATION);
        }
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
}

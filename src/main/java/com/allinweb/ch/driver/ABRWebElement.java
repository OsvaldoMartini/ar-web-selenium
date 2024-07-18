package com.allinweb.ch.driver;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementAttributeTypeValueEnum;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.component.scene.ABRAlertScene;
import com.allinweb.ch.component.scene.ABRElementValueScene;
import com.allinweb.ch.control.ABRComponentBuilder;
import com.allinweb.ch.core.ABRSharedResources;
import com.allinweb.ch.persistence.BlockDTO;
import com.allinweb.ch.persistence.BlockLoopInstructionDTO;
import com.allinweb.ch.persistence.BotJobDTO;
import com.allinweb.ch.persistence.SearchReturn;
import com.allinweb.ch.persistence.VariableUserDTO;
import com.allinweb.ch.util.*;
import com.allinweb.ch.util.Priority;
import com.google.common.base.Strings;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import org.openqa.selenium.By;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;

public class ABRWebElement {

    private static final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    private static final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";

    private static final boolean POSTGRES_DB = true;
    private static final String CONNECTION_POSTGRES = "jdbc:postgresql://";
    private static final String DB_HOST = "localhost"; // or your PostgreSQL server address
    private static final String DB_PORT = "5432"; // default PostgreSQL port
    private static final String DB_NAME = "abr_web"; // your database name
    private static final String USERNAME = "postgres"; // your database username
    private static final String PASSWORD = "martini"; // your database password
    private Connection conn = null;

    private final ABRComponentBuilder componentBuilder = new ABRComponentBuilder();

    private BooleanProperty clickElement = new SimpleBooleanProperty(false);
    private BooleanProperty varButtonElem = new SimpleBooleanProperty(false);
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

    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private ExecutorService executorService;
    private Alert alertToShow;

    private Integer instructionId;
    private Integer botJobId;
    private String instructionName;

    private String elementId;
    private WebElement element;

    private SearchReturn searchReturn;
    private String xPath;
    private String attributeValue;
    private WebElementTagNameEnum forceTagEnum;

    private String innerHTML;

    private Map<String, String> savedReferences = new HashMap<>();

    // graphic attributes
    private AnchorPane graphicRepresentation;
    private HBox elementPanel;
    private HBox actionPanel;

    private StackPane nameGroup;
    private HBox nameFieldsGroup;
    private StackPane actionGroup;

    private Label nameLabel;
    private Label operationLabel1;
    private Label operationLabel2;
    private Label operationLabel3;
    private Label spaceLabel;

    private TextField nameField;

    private Button blockButton;
    private Button moveUpButton;
    private Button moveDownButton;
    private Button moreOptionsButton;
    private Button saveButton;
    private Button deleteButton;
    private Button variableButton;
    private Button addInstructionButton;
    private Button saveOperatorButton;

    private ComboBox<ComboBoxItem> comboBoxInstruc;
    private ObservableList<ComboBoxItem> itemsInstructions;

    private ComboBox<ComboBoxOperator> comboBoxOperator;
    private ObservableList<ComboBoxOperator> operatorsItems;

    private ComboBox<ComboBoxVars> comboBoxVars;
    private ObservableList<ComboBoxVars> variablesItems = FXCollections.observableArrayList();

    private List<VariableUserDTO> variablesList = new ArrayList<>();

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
    // Static block to initialize
    static {
        abrPriorities = ABRPriorities.getInstance();
    }

    public ABRWebElement(WebElement element, int jobId) {
        abrPriorities.setJobId(jobId);
        initFromWebElement(element);
    }

    public ABRWebElement(SearchReturn searchReturn, int jobId) {
        abrPriorities.setJobId(jobId);
        this.searchReturn = searchReturn;
        this.forceTagEnum = searchReturn.getForceTypeEnum();
        this.attributeValue = searchReturn.getAttributeValue();
        //        this.attributeValue = element.getAttribute(searchReturn.getAttributeType());
        initFromWebElement(searchReturn.getElement());
    }

    public ABRWebElement(Map.Entry<String, WebElement> entry, String attribute, int jobId) {
        abrPriorities.setJobId(jobId);
        WebElement element = entry.getValue();
        this.xPath = entry.getKey();
        this.attributeValue = element.getAttribute(attribute);
        initFromWebElement(element);
    }

    public ABRWebElement(WebElement element, String priority) {
        updatePriorities(priority, null);
        initFromWebElement(element);
    }

    public ABRWebElement(BlockLoopInstructionDTO instruction) {
        botJobId = instruction.getBlock().getBotJob().getId();
        updatePriorities(null, instruction);
        initFromBlockLoopInstruction(instruction);
    }

    private void updatePriorities(String priority, BlockLoopInstructionDTO instruction) {
        botJobId = instruction.getBlock().getBotJob().getId();
        if (abrPriorities.getJobId() == null) {
            abrPriorities.setJobId(botJobId);
            if (instruction.getBlock().getBotJob().getHomeBanking().getPriority() != null) {
                abrPriorities.loadPrioritiesFromString(
                        instruction.getBlock().getBotJob().getHomeBanking().getPriority());
            } else {
                abrPriorities.loadPriorities();
            }
        } else if (abrPriorities.getJobId() != botJobId) {
            abrPriorities.setJobId(botJobId);
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
        try {

            if (searchReturn == null && abrPriorities.getJobId() != null) {
                for (Priority priority : abrPriorities.getAllPriorityList()) {
                    switch (priority.getPriorityType()) {
                        case attribute -> {
                            String attributeValue =
                                    element.getAttribute(priority.getName().get(0));
                            if (attributeValue != null && !attributeValue.isBlank()) {
                                savedReferences.put(priority.getName().get(0), attributeValue);
                            }
                        }
                        case xpath -> {
                            if (Strings.isNullOrEmpty(xPath)) {
                                savedReferences.put(
                                        priority.getName().get(0), ABRWebUtil.extractWebElementXPath(element));
                            } else {
                                savedReferences.put(priority.getName().get(0), xPath);
                            }
                        }

                        case coordinates -> {
                            Rectangle coordinates = element.getRect();
                            savedReferences.put(
                                    priority.getName().get(0),
                                    (coordinates.getX() + (coordinates.getWidth() / 2)) + ","
                                            + (coordinates.getY() + (coordinates.getHeight() / 2)));
                        }
                    }
                }
            } else {
                // Most Important to find any kind of element

                if (searchReturn != null && searchReturn.getxPathWorkedFirst().equals(Constants.ABSOLUT_XPATH)) {
                    savedReferences.put(
                            "absolutXPath",
                            searchReturn.getAbsolutXPath()); // Creates Seq to Fin element Via Instructions - 1
                    savedReferences.put(
                            "currentXPath",
                            searchReturn.getCurrentXPath()); // Creates Seq to Fin element Via Instructions - 2
                    savedReferences.put(
                            "customXPath",
                            searchReturn.getCustomXPath()); // Creates Seq to Fin element Via Instructions - 2
                } else if (searchReturn.getxPathWorkedFirst().equals(Constants.REGULAR_XPATH)) {
                    savedReferences.put(
                            "currentXPath",
                            searchReturn.getCurrentXPath()); // Creates Seq to Fin element Via Instructions - 1
                    savedReferences.put(
                            "absolutXPath",
                            searchReturn.getAbsolutXPath()); // Creates Seq to Fin element Via Instructions - 2
                    savedReferences.put(
                            "customXPath",
                            searchReturn.getCustomXPath()); // Creates Seq to Fin element Via Instructions - 2
                } else if (searchReturn != null && !Strings.isNullOrEmpty(xPath)) {
                    savedReferences.put("xpath", searchReturn.getCurrentXPath());
                } else if (!Strings.isNullOrEmpty(attributeValue)) {
                    savedReferences.put("attribute", attributeValue);
                } else { // In case of Dynamic Creation
                    savedReferences.put("xpath", ABRWebUtil.extractWebElementXPath(element));
                }

                Rectangle coordinates = element.getRect();
                savedReferences.put(
                        "coordinates",
                        (coordinates.getX() + (coordinates.getWidth() / 2)) + ","
                                + (coordinates.getY() + (coordinates.getHeight() / 2)));
            }
            if (forceTagEnum != null) {
                if (forceTagEnum.equals(WebElementTagNameEnum.BUTTON)) {
                    // OR BUTTON SOMETHING CLICKABLE
                    clickElement.setValue(true);
                    varButtonElem.setValue(true);
                } else if (forceTagEnum.equals(WebElementTagNameEnum.SET)) {
                    setValueElem.setValue(true);
                } else if (forceTagEnum.equals(WebElementTagNameEnum.GET)) {
                    getValueElem.setValue(true);
                } else if (forceTagEnum.equals(WebElementTagNameEnum.CK)) {
                    checkValueElem.setValue(true);
                } else {
                    // OR INPUT SOMETHING IMPUTABLE
                    clickElement.setValue(false);
                    varButtonElem.setValue(true);
                }

            } else {
                clickElement.setValue(isClickable(element));
            }
        } catch (Exception ex) {
            ABRLogger.getInstance(Thread.class)
                    .finer("An exception has occurred creation of Web Element\n " + ex.getMessage() + " Cause: "
                            + ex.getCause());
        }

        String ariaLabelValue = element.getAttribute(WebElementAttributeEnum.ARIA_LABEL.getValue());
        String innerHTMLValue = element.getAttribute(WebElementAttributeEnum.INNER_HTML.getValue());
        String formControlNameAttributeValue =
                element.getAttribute(WebElementAttributeEnum.FORM_CONTROL_NAME.getValue());
        String testIdAttributeValue = element.getAttribute(WebElementAttributeEnum.TEST_ID.getValue());
        String idAttributeValue = element.getAttribute(WebElementAttributeEnum.ID.getValue());
        String nameAttributeValue = element.getAttribute(WebElementAttributeEnum.NAME.getValue());
        String valueAttributeValue = element.getAttribute(WebElementAttributeEnum.VALUE.getValue());
        String valueHRefFile = extractFileExtension(element.getAttribute(WebElementAttributeEnum.HREF.getValue()));

        String tagName = element.getTagName();
        String textLabel = element.getText();

        if (Strings.isNullOrEmpty(textLabel)) {
            textLabel = extractAllText(element);
        }

        if (Strings.isNullOrEmpty(textLabel)) {
            textLabel = getTextRecursively(element);
        }

        if (Strings.isNullOrEmpty(textLabel)) {
            textLabel = getTextRecursivelyByParent(element);
        }

        boolean hasButton = tagName.equalsIgnoreCase("button") && isClickable() && !textLabel.isBlank();
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
        boolean hasParagraph = !Strings.isNullOrEmpty(textLabel) && tagName.equalsIgnoreCase("p");
        boolean hasSpan = !Strings.isNullOrEmpty(textLabel) && tagName.equalsIgnoreCase("span");
        boolean hasDiv = !Strings.isNullOrEmpty(textLabel) && tagName.equalsIgnoreCase("div");
        boolean hasLabel = !Strings.isNullOrEmpty(textLabel) && tagName.equalsIgnoreCase("label");

        if (hasSpan || hasDiv || hasLabel) {
            textElement.setValue(true);
        }

        if (searchReturn != null && !Strings.isNullOrEmpty(searchReturn.getDefinedName())) {
            nameLabel.setText(searchReturn.getDefinedName());
            nameField.setText(searchReturn.getDefinedName());
        } else if (isOption && hasValue) {
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
        } else if (hasParagraph) {
            nameLabel.setText(textLabel);
            nameField.setText(tagName);
        } else if (hasButton) {
            nameLabel.setText(textLabel);
            nameField.setText(tagName);
        } else if (hasSpan) {
            nameLabel.setText(textLabel);
            nameField.setText(tagName);
        } else if (hasDiv) {
            nameLabel.setText(textLabel);
            nameField.setText(tagName);
        } else if (hasLabel) {
            nameLabel.setText(textLabel);
            nameField.setText(tagName);
        } else {
            nameLabel.setText(ABRConstants.DEFAULT_VALUE_NO_IDENTIFICATION);
            nameField.setText(ABRConstants.DEFAULT_VALUE_NO_IDENTIFICATION);
        }
        try {

            String extRef = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.WEBDRIVER_EXT_REFERENCE);
            if (extRef != null) {
                String extRefSub = extRef.substring(extRef.indexOf("'") + 1, extRef.length() - 1);
                // isIdElement.setValue(hasTestId &&
                // testIdAttributeValue.equals("web-banking-payment-core.payment-details.external-reference"));
                isIdElement.setValue(hasTestId && testIdAttributeValue.equals(extRefSub));
            }
        } catch (Exception e) {
            ABRLogger.getInstance(Thread.class)
                    .finer("an exception has occurred in the thread for extRefSub " + e.getMessage() + " Cause: "
                            + e.getCause());
        }

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

        // Create a label to display the countdown
        Label countdownLabel = new Label(String.valueOf(remainingSeconds));
        countdownLabel.setStyle("-fx-font-size: 24px;");
        countdownLabel.setVisible(false);
        // Create a stack pane to hold the label
        StackPane stackPane = new StackPane(countdownLabel);
        stackPane.setPadding(new Insets(20));
        // Create a dialog for the alert
        alertToShow = new Alert(Alert.AlertType.INFORMATION);
        alertToShow.setTitle("Countdown Alert");
        alertToShow.setHeaderText("Count Down");
        alertToShow.initModality(Modality.APPLICATION_MODAL);
        // Set the content of the alert
        alertToShow.getDialogPane().setContent(stackPane);
        // Create a timeline to update the countdown
        timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> {
            remainingSeconds--;
            countdownLabel.setText(String.valueOf(remainingSeconds));
            if (remainingSeconds <= 0) {
                timeline.stop(); // Stop the timeline when countdown finishes
                alertToShow.close(); // Close the alert dialog
            }
        }));

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

        isCheckValidator = actionReference[0].equals(ABRConstants.CHECK_VALUE);

        // Initialize items with images and text
        operatorsItems = FXCollections.observableArrayList(
                new ComboBoxOperator("Equals", new Image(ABRConstants.ICON_EQUAL), "="),
                new ComboBoxOperator("Greater", new Image(ABRConstants.ICON_GREATER), ">"));

        if (!actionReference[0].equals(ABRConstants.CHECK_VALUE)
                && !actionReference[0].equals(ABRConstants.SET_VALUE)
                && !actionReference[0].equals(ABRConstants.GET_VALUE)) {
            // Initialize items with images and text
            itemsInstructions = FXCollections.observableArrayList(
                    new ComboBoxItem("instruction", new Image(ABRConstants.ICON_BLANK)),
                    new ComboBoxItem("setValue", new Image(ABRConstants.ICON_SET_VALUE_BTN)),
                    new ComboBoxItem("getValue", new Image(ABRConstants.ICON_GET_VALUE_BTN)),
                    new ComboBoxItem("Check", new Image(ABRConstants.ICON_CHECK)));
        }

        instructionId = instruction.getId();
        instructionName = instruction.getName();

        loadJobVariables();

        initUI();

        nameLabel.setText(instruction.getName());
        nameField.setText(instruction.getName());
        xPath = instruction.getPath();
        if (actionReference.length > 1) {
            nameLabel.setText(actionReference[1]);
            nameField.setText(actionReference[1]);
        }

        if (actionReference[0].equals(ABRConstants.CLICK)) {
            clickElement.setValue(true);
            varButtonElem.setValue(true);
        } else if (actionReference[0].equals(ABRConstants.INSERT)) {
            textElement.setValue(true);
            varButtonElem.setValue(true);
        } else if (actionReference[0].equals(ABRConstants.SET_VALUE)) {
            setValueElem.setValue(true);
        } else if (actionReference[0].equals(ABRConstants.GET_VALUE)) {
            getValueElem.setValue(true);
        } else if (actionReference[0].equals(ABRConstants.CHECK_VALUE)) {
            checkValueElem.setValue(true);
        } else if (actionReference[0].equals(ABRConstants.HOLD)) {
            holdValueElem.setValue(true);
        } else {
            varButtonElem.setValue(true);
        }

        toBeAddedElement.setValue(false);
    }

    private void initUI() {
        initUIComponents();
        initUIBehaviour();
    }

    private void initUIComponents() {
        initElementPanel();
        initButtonsGrid();
        defineButtonsGrid();

        graphicRepresentation = new AnchorPane(elementPanel, actionPanel);

        // graphicRepresentation.setBackground(new Background(new BackgroundFill(Color.RED, CornerRadii.EMPTY,
        // Insets.EMPTY)));;
        // graphicRepresentation.backgroundProperty().setValue(Background.fill(Color.WHITE));
    }

    private void defineButtonsGrid() {
        // Create ComboBox Instructions
        if (itemsInstructions != null && itemsInstructions.size() > 0) {

            comboBoxInstruc = new ComboBox<>(itemsInstructions);
            comboBoxInstruc.setPrefWidth(120); // Set preferred width of ComboBox

            // Set cell factory to display images and text
            comboBoxInstruc.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(ComboBoxItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setTextFill(Color.BLACK); // Ensure text is black
                    } else {
                        setText(item.getText());
                        ImageView imageView = new ImageView(item.getImage());
                        imageView.setFitWidth(20); // Set the width for icon size
                        imageView.setFitHeight(20); // Set the height for icon size
                        imageView.setPreserveRatio(true);
                        setGraphic(imageView);
                        setTextFill(Color.BLACK); // Ensure text is black
                    }
                }
            });

            comboBoxInstruc.setCellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(ComboBoxItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setTextFill(Color.BLACK); // Ensure text is black
                    } else {
                        setText(item.getText());
                        ImageView imageView = new ImageView(item.getImage());
                        imageView.setFitWidth(20); // Set the width for icon size
                        imageView.setFitHeight(20); // Set the height for icon size
                        imageView.setPreserveRatio(true);
                        setGraphic(imageView);
                        setTextFill(Color.BLACK); // Ensure text is black
                    }

                    // Add hover effect
                    setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                    setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
                }
            });
            comboBoxInstruc.getSelectionModel().selectFirst();
        }

        // Create ComboBox Operators
        if (operatorsItems != null && operatorsItems.size() > 0) {
            comboBoxOperator = new ComboBox<>(operatorsItems);
            comboBoxOperator.setPrefWidth(50);

            // Set cell factory to display images and text
            comboBoxOperator.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(ComboBoxOperator item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setTextFill(Color.BLACK); // Ensure text is black
                    } else {
                        setText(item.getText());
                        ImageView imageView = new ImageView(item.getImage());
                        imageView.setFitWidth(15); // Set the width for icon size
                        imageView.setFitHeight(15); // Set the height for icon size
                        imageView.setPreserveRatio(true);
                        setGraphic(imageView);
                        setTextFill(Color.BLACK); // Ensure text is black
                    }
                }
            });

            comboBoxOperator.setCellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(ComboBoxOperator item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setTextFill(Color.BLACK); // Ensure text is black
                    } else {
                        setText(item.getText());
                        ImageView imageView = new ImageView(item.getImage());
                        imageView.setFitWidth(15); // Set the width for icon size
                        imageView.setFitHeight(15); // Set the height for icon size
                        imageView.setPreserveRatio(true);
                        setGraphic(imageView);
                        setTextFill(Color.BLACK); // Ensure text is black
                    }

                    // Add hover effect
                    setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                    setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
                }
            });
            comboBoxOperator.getSelectionModel().selectFirst();
        }

        variablesItems.add(new ComboBoxVars("variables", -1, ""));
        if (variablesList != null && variablesList.size() > 0) {
            List<ComboBoxVars> variablesNames = variablesList.stream()
                    .map(variable -> new ComboBoxVars(
                            variable.getType().substring(0, 1) + variable.getName(),
                            variable.getInstructionId(),
                            variable.getValue()))
                    .collect(Collectors.toList());
            variablesItems.addAll(variablesNames);
        }
        comboBoxVars = new ComboBox<>(variablesItems);
        // Set cell factory to display images and text
        comboBoxVars.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }
            }
        });

        comboBoxVars.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ComboBoxVars item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(Color.BLACK); // Ensure text is black
                } else {
                    setText(item.getText());
                    setTextFill(Color.BLACK); // Ensure text is black
                }

                // Add hover effect
                setOnMouseEntered(e -> setStyle("-fx-background-color: lightgray;"));
                setOnMouseExited(e -> setStyle("-fx-background-color: none;"));
            }
        });
        comboBoxVars.getSelectionModel().selectFirst();

        spaceLabel = new Label("                  ");

        // Create description label
        actionPanel = new HBox();
        HBox variableBox = new HBox();
        if (itemsInstructions != null && itemsInstructions.size() > 0) {
            variableBox.getChildren().addAll(comboBoxInstruc, addInstructionButton, comboBoxVars, variableButton);

            comboBoxOperator.setVisible(false);
            spaceLabel.setVisible(false);
            saveOperatorButton.setVisible(false);

            if (variablesItems != null && variablesItems.size() > 1) {
                comboBoxInstruc.setVisible(true);
                addInstructionButton.setVisible(true);
                comboBoxVars.setDisable(false);
            } else {
                comboBoxInstruc.setVisible(false);
                addInstructionButton.setVisible(false);
                comboBoxVars.setDisable(true);
            }
        } else {
            variableBox.getChildren().addAll(saveOperatorButton);
        }

        if (operationsElement != null && operationsElement.length > 1) {

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
                    operationLabel3 = new Label(operationsElement[2].get());
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

                blockButton.setPrefWidth(ABRConstants.SPACE_L);
                if (isCheckValidator) {
                    actionPanel
                            .getChildren()
                            .addAll(
                                    operationLabel1,
                                    operationLabel2,
                                    operationLabel3,
                                    spaceLabel,
                                    variableBox,
                                    comboBoxOperator,
                                    blockButton,
                                    moveUpButton,
                                    moveDownButton,
                                    moreOptionsButton,
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
                                    moreOptionsButton,
                                    deleteButton);
                }

            } else {
                operationLabel1 = new Label(operationsElement[0].get());
                operationLabel1.setTextFill(Color.BLUE);
                blockButton.setPrefWidth(ABRConstants.SPACE_L);

                if (comboBoxOperator != null) {
                    actionPanel
                            .getChildren()
                            .addAll(
                                    operationLabel1,
                                    spaceLabel,
                                    comboBoxOperator,
                                    variableBox,
                                    blockButton,
                                    moveUpButton,
                                    moveDownButton,
                                    moreOptionsButton,
                                    deleteButton);
                } else {
                    actionPanel
                            .getChildren()
                            .addAll(
                                    operationLabel1,
                                    spaceLabel,
                                    variableBox,
                                    blockButton,
                                    moveUpButton,
                                    moveDownButton,
                                    moreOptionsButton,
                                    deleteButton);
                }

                // Optionally, you can set additional styles or properties
                operationLabel1.setStyle("-fx-font-size: 14px;");
                operationLabel1.setStyle("-fx-font-weight: bold;");
            }

        } else {
            blockButton.setPrefWidth(ABRConstants.SPACE_L);
            actionPanel
                    .getChildren()
                    .addAll(variableBox, blockButton, moveUpButton, moveDownButton, moreOptionsButton, deleteButton);
        }

        actionPanel.setSpacing(ABRConstants.SPACE_XS);
        actionPanel.setAlignment(Pos.CENTER_RIGHT);
        AnchorPane.setTopAnchor(actionPanel, ABRConstants.SPACE_XS);
        AnchorPane.setBottomAnchor(actionPanel, ABRConstants.SPACE_XS);
        AnchorPane.setRightAnchor(actionPanel, ABRConstants.SPACE_XS);
    }

    private void initElementPanel() {
        if (clickImage == null) {
            clickImage = componentBuilder.buildImageView(ABRConstants.ICON_CLICK, ABRConstants.SPACE_M);
        }
        if (insertImage == null) {
            insertImage = componentBuilder.buildImageView(ABRConstants.ICON_INSERT, ABRConstants.SPACE_M);
        }
        if (textImage == null) {
            textImage = componentBuilder.buildImageView(ABRConstants.ICON_TEXT, ABRConstants.SPACE_M);
        }
        if (setImage == null) {
            setImage = componentBuilder.buildImageView(ABRConstants.ICON_SET_VALUE, ABRConstants.SPACE_M);
        }
        if (getImage == null) {
            getImage = componentBuilder.buildImageView(ABRConstants.ICON_GET_VALUE, ABRConstants.SPACE_M);
        }
        if (checkImage == null) {
            checkImage = componentBuilder.buildImageView(ABRConstants.ICON_CHECK, ABRConstants.SPACE_M);
        }
        if (holdImage == null) {
            holdImage = componentBuilder.buildImageView(ABRConstants.ICON_WAIT, ABRConstants.SPACE_M);
        }
        if (saveButton == null) {
            saveButton = componentBuilder.buildButton("  Save  ", ABRConstants.SPACE_M, Insets.EMPTY);
            saveButton.setMaxHeight(ABRConstants.SPACE_L);
        }

        if (nameField == null) {
            nameField = new TextField();
            nameField.setMaxHeight(ABRConstants.SPACE_L);
        }

        if (nameLabel == null) {
            nameLabel = new Label();
            nameLabel.setMaxHeight(ABRConstants.SPACE_L);
        }

        if (nameGroup == null) {
            nameGroup = new StackPane(nameLabel, nameField);
        }

        if (nameFieldsGroup == null) {
            nameFieldsGroup = new HBox(nameGroup, saveButton);
        }

        if (actionGroup == null) {
            actionGroup = new StackPane(clickImage, insertImage, textImage, setImage, getImage, checkImage, holdImage);
            elementPanel = new HBox(actionGroup, nameFieldsGroup);
            elementPanel.setSpacing(ABRConstants.SPACE_XS);
        }
        AnchorPane.setLeftAnchor(elementPanel, ABRConstants.SPACE_XS);
        AnchorPane.setTopAnchor(elementPanel, ABRConstants.SPACE_XS);
        AnchorPane.setBottomAnchor(elementPanel, ABRConstants.SPACE_XS);
    }

    private void initButtonsGrid() {
        if (moreOptionsButton == null) {
            moreOptionsButton = componentBuilder.buildButton(
                    "", ABRConstants.SPACE_L, ABRConstants.ICON_EDIT, ABRConstants.SPACE_M, Insets.EMPTY);
        }
        if (blockButton == null) {
            blockButton = componentBuilder.buildButton(
                    "", ABRConstants.SPACE_L, ABRConstants.ICON_BLOCK, ABRConstants.SPACE_M, Insets.EMPTY);
        }
        if (moveUpButton == null) {
            moveUpButton = componentBuilder.buildButton(
                    "", ABRConstants.SPACE_L, ABRConstants.ICON_UP, ABRConstants.SPACE_M, Insets.EMPTY);
        }
        if (moveDownButton == null) {
            moveDownButton = componentBuilder.buildButton(
                    "", ABRConstants.SPACE_L, ABRConstants.ICON_DOWN, ABRConstants.SPACE_M, Insets.EMPTY);
        }
        if (deleteButton == null) {
            deleteButton = componentBuilder.buildButton(
                    "", ABRConstants.SPACE_L, ABRConstants.ICON_CROSS, ABRConstants.SPACE_M, Insets.EMPTY);
        }

        if (variableButton == null) {
            variableButton = componentBuilder.buildButton(
                    "", ABRConstants.SPACE_L, ABRConstants.ICON_VARIABLES, ABRConstants.SPACE_M, Insets.EMPTY);
        }

        if (addInstructionButton == null) {
            addInstructionButton = componentBuilder.buildButton(
                    "", ABRConstants.SPACE_L, ABRConstants.ICON_PLUS, ABRConstants.SPACE_M, Insets.EMPTY);
        }

        if (saveOperatorButton == null) {
            saveOperatorButton = componentBuilder.buildButton(
                    "", ABRConstants.SPACE_L, ABRConstants.ICON_SAVE, ABRConstants.SPACE_M, Insets.EMPTY);
        }
    }

    private void initUIBehaviour() {
        insertImage.visibleProperty().bind(clickElement.not());
        //
        clickImage.visibleProperty().bind(clickElement);

        textImage.visibleProperty().bind(textElement);

        setImage.visibleProperty().bind(setValueElem);
        getImage.visibleProperty().bind(getValueElem);
        checkImage.visibleProperty().bind(checkValueElem);

        holdImage.visibleProperty().bind(holdValueElem);

        nameLabel.visibleProperty().bind(editingElement.not());
        nameField.visibleProperty().bind(editingElement);
        saveButton.visibleProperty().bind(editingElement);

        //        operationLabel.setText(operationsElement.getValue());

        moveUpButton.visibleProperty().bind(toBeAddedElement.not());
        blockButton.visibleProperty().bind(toBeAddedElement.not());
        moveDownButton.visibleProperty().bind(toBeAddedElement.not());
        deleteButton.visibleProperty().bind(toBeAddedElement.not());

        variableButton.visibleProperty().bind(toBeAddedElement.not());

        if (comboBoxOperator != null) {
            comboBoxOperator.visibleProperty().bind(toBeAddedElement.not());
        }

        moreOptionsButton.setOnAction(e -> editingElement.setValue(!editingElement.getValue()));
        variableButton.setOnAction(e -> {
            nameLabel.setText(nameField.getText());
            ABRLogger.getInstance(ABRWebElement.class)
                    .info("creating variable for instruction Name " + instructionName);
            if (instructionId != null && instructionId != 0) {
                // Example usage
                ABRElementValueScene elementValueScene =
                        new ABRElementValueScene(botJobId, instructionId, instructionName);
                elementValueScene.showModal();
                loadJobVariables();
                variablesItems.clear();
                List<ComboBoxVars> variablesNames = variablesList.stream()
                        .map(variable -> new ComboBoxVars(
                                variable.getType().substring(0, 1) + variable.getName(),
                                variable.getInstructionId(),
                                variable.getValue()))
                        .collect(Collectors.toList());
                variablesItems.add(new ComboBoxVars("variables", -1, ""));
                variablesItems.addAll(variablesNames);
                // Set ComboBox to first item
                comboBoxVars.getSelectionModel().selectFirst();

                if (variablesItems.size() > 1) {
                    comboBoxInstruc.setVisible(true);
                    addInstructionButton.setVisible(true);
                    comboBoxVars.setDisable(false);
                } else {
                    comboBoxInstruc.setVisible(false);
                    addInstructionButton.setVisible(false);
                    comboBoxVars.setDisable(true);
                }
            }
        });

        this.addInstructionButton.setOnMouseClicked((e) -> {
            // Check if the current selected index is greater than the first index
            if (comboBoxVars.getSelectionModel().getSelectedIndex() < 1) {
                showAlert("Select the Variable", "Select the Variable to apply!");
                return;
            } else if (comboBoxInstruc.getSelectionModel().getSelectedIndex() < 1) {
                showAlert("Select the Instruction", "Select the Instruction to apply!");
                return;
            }

            if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("setValue")) {
                addInstruction(
                        "SetValue",
                        comboBoxVars.getValue().getText().substring(1).toLowerCase() + ":"
                                + comboBoxVars.getValue().getValue(),
                        comboBoxVars.getValue().getVarId());
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("getValue")) {
                addInstruction(
                        "GetValue",
                        comboBoxVars.getValue().getText().substring(1).toLowerCase() + ":"
                                + comboBoxVars.getValue().getText().toUpperCase(),
                        comboBoxVars.getValue().getVarId());
            } else if (comboBoxInstruc.getValue().getText().equalsIgnoreCase("check")) {
                addInstruction(
                        "Check",
                        comboBoxVars.getValue().getText().substring(1).toLowerCase() + ":"
                                + comboBoxOperator.getValue().getOperator() + ":"
                                + comboBoxVars.getValue().getValue(),
                        comboBoxVars.getValue().getVarId());
            }
        });

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

        if (comboBoxOperator != null) {

            comboBoxOperator.setOnAction(e -> {
                BlockLoopInstructionDTO instruction =
                        ABRSharedResources.getInstance().getEntityById(BlockLoopInstructionDTO.class, instructionId);
                int instructionIndex = instruction.getInstructionOrderNumber();
                BlockDTO block = instruction.getBlock();
                //            if (block.getBotJob().getVariables() != null) {
                //                List<VariableDTO> variables = block.getBotJob().getVariables();
                //                System.out.println(String.format(
                //                        "Block Variables: %s InstructionId: %s  instructionIndex: %s Command: %s",
                //                        variables.size(), instructionId, instructionIndex, e.toString()));
                //            } else {
                //            System.out.println(String.format(
                //                    "Block Name: %s InstructionId: %s  instructionIndex: %s Command: %s",
                //                    block.getName(), instructionId, instructionIndex, comboBox.getValue().item));
                //            //            }
            });
        }

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

    // CHATGPT   I want select the correct type of element following these conditions
    // TODO MORE INTELLIGENT  LOGIC
    public void selectElementType(WebElement element) {
        // Check element tag names
        boolean isAnchor = element.getTagName().equals(WebElementTagNameEnum.ANCHOR.getValue());
        boolean isOption = element.getTagName().equals(WebElementTagNameEnum.OPTION.getValue());

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
        String tagname = element.getTagName();
        String textLabel = element.getText();

        // Determine boolean conditions
        boolean hasButton = tagname.equalsIgnoreCase("button") && isClickable(element) && !textLabel.isBlank();
        boolean hasAriaLabel = isValidString(ariaLabelValue);
        boolean hasInnerHTML = isValidString(innerHTMLValue) && !hasButton;
        boolean hasInnerHTMLTag = hasInnerHTML && (innerHTMLValue.contains("<") || innerHTMLValue.contains(">"));
        boolean hasFormControlName = isValidString(formControlNameAttributeValue);
        boolean hasTestId = isValidString(testIdAttributeValue);
        boolean hasName = isValidString(nameAttributeValue);
        boolean hasId = isValidString(idAttributeValue) && !hasButton;
        boolean hasValue = isValidString(valueAttributeValue);
        boolean hasHRefFile = isValidString(valueHRefFile);
        boolean hasParagraph = !Strings.isNullOrEmpty(textLabel) && tagname.equalsIgnoreCase("p");
        boolean hasSpan = !Strings.isNullOrEmpty(textLabel) && tagname.equalsIgnoreCase("span");
        boolean hasDiv = !Strings.isNullOrEmpty(textLabel) && tagname.equalsIgnoreCase("div");

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
    public static String getTextRecursively(WebElement element) {
        String text = element.getText();
        if (!text.isEmpty()) {
            return text;
        }

        // Check if the element is an input field and look for placeholder
        if (element.getTagName().equals("input") || element.getTagName().equals("textarea")) {
            String placeholder = element.getAttribute("placeholder");
            if (placeholder != null && !placeholder.isEmpty()) {
                return placeholder;
            }
        }

        // Recursively search child elements for text
        List<WebElement> children = element.findElements(By.xpath("./*"));
        for (WebElement child : children) {
            String childText = getTextRecursively(child);
            if (!childText.isEmpty()) {
                return childText;
            }
        }

        return "";
    }

    // Recursive method to find text or placeholder text
    public static String getTextRecursivelyByParent(WebElement element) {
        // Check if the element has text
        String text = element.getText();
        if (!text.isEmpty()) {
            return text;
        }

        // Check if the element is an input field or textarea and look for placeholder
        if (element.getTagName().equals("input") || element.getTagName().equals("textarea")) {
            String placeholder = element.getAttribute("placeholder");
            if (placeholder != null && !placeholder.isEmpty()) {
                return placeholder;
            }
        }

        // Recursively check parent element
        WebElement parent = element.findElement(By.xpath(".."));
        if (parent != null && !parent.getTagName().equals("html")) {
            return getTextRecursively(parent);
        }

        // If no text is found, return empty string
        return "";
    }

    // Recursive method to extract all text content
    public static String extractAllText(WebElement element) {
        StringBuilder textContent = new StringBuilder();
        extractTextRecursively(element, textContent);
        return textContent.toString().trim();
    }

    private static void extractTextRecursively(WebElement element, StringBuilder textContent) {
        // Get the text content of the current element
        String text = element.getText();
        if (!text.isEmpty()) {
            textContent.append(text).append(" ");
        }

        // Get the placeholder if the element is an input or textarea
        if (element.getTagName().equals("input") || element.getTagName().equals("textarea")) {
            String placeholder = element.getAttribute("placeholder");
            if (placeholder != null && !placeholder.isEmpty()) {
                textContent.append(placeholder).append(" ");
            }
        }

        // Recursively extract text from child elements
        List<WebElement> children = element.findElements(By.xpath("./*"));
        for (WebElement child : children) {
            extractTextRecursively(child, textContent);
        }
    }

    private Connection getConnection() {
        if (!POSTGRES_DB) {
            if (conn == null) {
                String dbPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_DB);
                String dbUrl = CONNECTION_TYPE + dbPath + ABRConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;
                try {
                    conn = DriverManager.getConnection(dbUrl);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return conn;
        } else {

            if (conn == null) {
                String dbUrl = CONNECTION_POSTGRES + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
                try {
                    conn = DriverManager.getConnection(dbUrl, USERNAME, PASSWORD);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return conn;
        }
    }

    private void loadJobVariables() {
        variablesList.clear();
        String selectSQL = " SELECT vars.id, vars.type, vars.name, vars.value, COUNT(blk.variable_id) UsedVars "
                + " FROM variable vars "
                + " left join block_loop_instruction blk on blk.variable_id = vars.id "
                + " where bot_job_id = " + botJobId
                + " and  block_loop_instruction_id = " + instructionId
                + " group by vars.id, vars.type, vars.Name, vars.value ";
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                String id = rs.getString("ID");
                String type = rs.getString("type");
                String name = rs.getString("name");
                String value = rs.getString("value");
                String usedVars = rs.getString("UsedVars");
                variablesList.add(new VariableUserDTO(id, type, name, value, botJobId, instructionId, usedVars));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        //        jobUserList.clear();
        //        loadBotJobData();
    }

    private void showAlert(String title, String content) {
        executorService = Executors.newSingleThreadExecutor();
        alertToShow.setAlertType(Alert.AlertType.ERROR);
        alertToShow.setTitle(title);
        alertToShow.setHeaderText(content);

        executorService.execute(() -> {
            timeline.setCycleCount(SECONDS); // Run for SECONDS seconds
            timeline.play(); // Start the timeline

            // Show the alert on the JavaFX Application Thread
            javafx.application.Platform.runLater(() -> alertToShow.showAndWait());
        });

        if (executorService != null) {
            remainingSeconds = SECONDS;
            executorService.shutdown();
        }
    }

    private void addInstruction(String name, String operation, Integer varId) {
        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Are you sure you want to add a " + name + " to the botjob?",
                ButtonType.YES,
                ButtonType.NO);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            Task<Void> waitTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    //                    List<BlockLoopInstructionDTO> instructionList =
                    //                            botJob.getBlocks().get(0).getBlockLoopInstructions();
                    BotJobDTO botJob = ABRSharedResources.getInstance().getEntityById(BotJobDTO.class, botJobId);
                    List<BlockLoopInstructionDTO> instructionList =
                            botJob.getBlocks().get(0).getBlockLoopInstructions();

                    BlockLoopInstructionDTO instruction = new BlockLoopInstructionDTO();
                    instruction.setName(name);
                    instruction.setDescription("loop desc");
                    instruction.setOperation(operation);
                    instruction.setVariableId(varId);
                    instruction.setEncrypted(false);
                    instruction.setExportToABR(true);
                    instruction.setInstructionOrderNumber(instructionList.size());
                    instruction.setOptional(false);
                    if (name.equalsIgnoreCase("setValue")) {
                        instruction.setActions(ABRConstants.SET_VALUE);
                    } else if (name.equalsIgnoreCase("getValue")) {
                        instruction.setActions(ABRConstants.GET_VALUE);
                    } else if (name.equalsIgnoreCase("check")) {
                        instruction.setActions(ABRConstants.CHECK_VALUE);
                    }
                    instruction.setActionCustomMaxWaitSec(30);
                    instruction.setOnHoldSeconds(1);
                    instruction.setBlock(botJob.getBlocks().get(0));
                    instruction.setExportToABR(false);
                    ABRSharedResources.getInstance()
                            .addEntity(
                                    instruction,
                                    BlockLoopInstructionDTO.class,
                                    () -> new ABRAlertScene(
                                            Alert.AlertType.INFORMATION,
                                            "Instruction Added",
                                            "Instruction " + instruction.getName() + " has been added successfully",
                                            ButtonType.OK));
                    return null;
                }
            };
            new Thread(waitTask).start();
        }
    }
}

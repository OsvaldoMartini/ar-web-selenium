package com.allinweb.ch.facade.actions;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.builder.WebElementAttributeTypeValueEnum;
import com.allinweb.ch.builder.WebElementIcon;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.TargetElement;
import com.allinweb.ch.util.ARConstantsEngine;
import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mapping between TargetElement, ElementDTO and InstructionLoad (cluster H): DTO conversion,
 * name/tag-type derivation, action-code building and saved-reference extraction. Pure DTO
 * transformations; bodies moved verbatim from PerformActions.
 */
public final class ElementDtoMapper {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private ElementDtoMapper() {}

    public static ElementDTO convertTargetToElementDTO(TargetElement targetElement) {
        if (targetElement == null) {
            return null;
        }

        ElementDTO elementDTO = new ElementDTO();

        elementDTO.setTagName(targetElement.getTagName());
        elementDTO.setXPath(targetElement.getXPath());
        elementDTO.setSomeText(targetElement.getSomeText());
        elementDTO.setAttribId(targetElement.getAttribId());
        elementDTO.setAttribName(targetElement.getAttribName());
        elementDTO.setCoordinates(targetElement.getCoordinates());
        elementDTO.setAttributeData(targetElement.getAttributeData());
        elementDTO.setCustomXPath(targetElement.getCustomXPath());
        elementDTO.setIFrameXPath(targetElement.getIFrameXPath());
        // Roadmap 3 Phase 3d: keep canonical name and display-only override as
        // independent fields. definedName feeds instruction.name (matching key);
        // clientNamed feeds instruction.client_named (display only). Without these
        // two, clone payloads lost both — pushing the override value into someText
        // and ending up persisted in the wrong column.
        elementDTO.setDefinedName(targetElement.getDefinedName());
        elementDTO.setClientNamed(targetElement.getClientNamed());

        elementDTO.setShadowHost(targetElement.getShadowHost());
        elementDTO.setShadowRoot(targetElement.getShadowRoot());
        elementDTO.setNestedShadow(targetElement.getNestedShadow());
        elementDTO.setCssSelector(targetElement.getCssSelector());

        elementDTO.setAttributeValue(targetElement.getAttributeValue());
        elementDTO.setAttributeType(targetElement.getAttributeType());
        elementDTO.setSearchAttributeValue(
                targetElement.getSearchAttributeValue()); // Assuming this is not directly available in TargetElement
        elementDTO.setAutoScroll(targetElement.getAutoScroll());
        elementDTO.setAutoEnter(targetElement.getAutoEnter());

        // Determine typeElement based on tagType
        if (targetElement.getTagType() != null) {
            switch (targetElement.getTagType()) {
                case BUTTON:
                case ANCHOR:
                case OPTION:
                case MAT_SELECT:
                    elementDTO.setTypeElement("tagName-Found");
                    elementDTO.setTagName("button");
                    break;
                case INPUT:
                case TEXT_AREA:
                    elementDTO.setTypeElement("tagName-Found");
                    elementDTO.setTagName("input");
                    break;
                case OUTPUT:
                case PARAGRAPH:
                case HEADER:
                case LABEL:
                case FOR_LABEL:
                case DIV:
                case STRONG:
                case SPAN:
                    elementDTO.setTypeElement("tagName-Found");
                    elementDTO.setTagName("output");
                    break;
                case IFRAME:
                    elementDTO.setTypeElement("iframe");
                    elementDTO.setTagName("iframe");
                    break;
                default:
                    elementDTO.setTypeElement("tagName-Found"); // Default to tag name
                    break;
            }
        } else {
            elementDTO.setTypeElement("tagName-Found"); // Default to tag name if tagType is null
        }

        return elementDTO;
    }

    public static TargetElement defineSearchReturn(ElementDTO elemenDTO, TargetElement targetDefine) {
        if (targetDefine == null || targetDefine.getElement() == null) {
            if (targetDefine == null) {
                targetDefine = new TargetElement();
            }

            // Reset Previous Values
            targetDefine.setAttribId(elemenDTO.getAttribId());
            targetDefine.setAttribName(elemenDTO.getAttribName());
            targetDefine.setTagName(elemenDTO.getTagName());
            targetDefine.setSomeText(elemenDTO.getSomeText());
            targetDefine.setCoordinates(elemenDTO.getCoordinates());
            // Roadmap 3 Phase 3d: carry the user's display-only override straight from the
            // React picker payload through to the TargetElement so both extractPickClone
            // overloads (single-arg + bot-job-context) end up with it. prepareToInsertElementDTO
            // then writes it onto InstructionLoad.clientNamed and persistInstructionsBatch
            // sends it to the instruction.client_named column on save.
            targetDefine.setClientNamed(elemenDTO.getClientNamed());

            targetDefine.setXPath(elemenDTO.getXPath());
            targetDefine.setCurrentXPath(elemenDTO.getXPath());

            targetDefine.setIFrameXPath(elemenDTO.getIFrameXPath());

            targetDefine.setTagName(elemenDTO.getTagName());

            targetDefine.setShadowHost(elemenDTO.getShadowHost());
            targetDefine.setShadowRoot(elemenDTO.getShadowRoot());
            targetDefine.setCssSelector(elemenDTO.getCssSelector());
            targetDefine.setNestedShadow(elemenDTO.getNestedShadow());

            targetDefine.setAttributeData(elemenDTO.getAttributeData());
            targetDefine.setCustomXPath(elemenDTO.getCustomXPath());

            if (!Strings.isNullOrEmpty(elemenDTO.getAttribId())) {
                targetDefine.setAttributeType("id");
                targetDefine.setAttributeValue(elemenDTO.getAttribId());
            } else if (!Strings.isNullOrEmpty(elemenDTO.getAttribName())) {
                targetDefine.setAttributeType("name");
                targetDefine.setAttributeValue(elemenDTO.getAttribName());
            } else {
                targetDefine.setAttributeType("");
                targetDefine.setAttributeValue("");
            }
            targetDefine.setIFrameElements(null);

            targetDefine.setXPathWorkedFirst(ARConstantsEngine.REGULAR_XPATH);

            // W3C 6 Headers
            String[] validHeaders = {"h1", "h2", "h3", "h4", "h5", "h6"};

            if (elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.INPUT.getValue())
                    || elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.TEXT_AREA.getValue())) {
                targetDefine.setTagType(WebElementTagNameEnum.INPUT);
                targetDefine.setIconType(WebElementIcon.INSERT);
                targetDefine.setTagName("input");
            } else if (elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.BUTTON.getValue())
                    || elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.ANCHOR.getValue())
                    || elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.SELECT.getValue())
                    || elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.OPTION.getValue())
                    || elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.MAT_SELECT.getValue())
                    || elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.MAT_OPTION.getValue())
                    || isClickableAttributeType(elemenDTO.getAttributeType())) {
                targetDefine.setTagType(WebElementTagNameEnum.BUTTON);
                targetDefine.setIconType(WebElementIcon.CLICK);
            } else if (elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.PARAGRAPH.getValue())
                    || elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.HEADER.getValue())
                    || elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.LABEL.getValue())
                    || elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.FOR_LABEL.getValue())
                    || elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.DIV.getValue())
                    || elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.STRONG.getValue())
                    || elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.SPAN.getValue())
                    || Arrays.asList(validHeaders)
                            .contains(elemenDTO.getTagName().toLowerCase())) {
                targetDefine.setTagType(WebElementTagNameEnum.OUTPUT);
                targetDefine.setIconType(WebElementIcon.OUTPUT);
                targetDefine.setTagName("label");
            } else if (elemenDTO.getTagName().equalsIgnoreCase(WebElementTagNameEnum.IFRAME.getValue())) {
                targetDefine.setTagType(WebElementTagNameEnum.IFRAME);
                targetDefine.setIconType(WebElementIcon.IFRAME);
            } else {
                targetDefine.setTagType(WebElementTagNameEnum.OUTPUT);
                targetDefine.setIconType(WebElementIcon.OUTPUT);
                targetDefine.setTagName("label");
            }
        }
        return targetDefine;
    }

    public static ElementDTO buildElementDTO(InstructionLoad instructionDTO) {
        // Reset Previous Values
        ElementDTO elemenDTO = new ElementDTO();
        elemenDTO.setTagName(instructionDTO.getTagName());
        elemenDTO.setCoordinates(instructionDTO.getCoordinates());
        elemenDTO.setXPath(instructionDTO.getXpath());
        elemenDTO.setIFrameXPath(instructionDTO.getIFrameXPath());

        elemenDTO.setShadowHost(instructionDTO.getShadowHost());
        elemenDTO.setShadowRoot(instructionDTO.getShadowRoot());
        elemenDTO.setCssSelector(instructionDTO.getCssSelector());
        //            elemenDTO.setNestedShadow(instructionDTO.getNestedShadow());
        elemenDTO.setCustomXPath(instructionDTO.getXpath());

        return elemenDTO;
    }

    // TODO MORE INTELLIGENT  LOGIC
    public static TargetElement defineNameTitles(TargetElement target) {

        try {
            String tagNameDefined = target.getDefinedName() != null ? target.getDefinedName() : target.getTagName();
            WebElement targetElem = target.getElement();

            // Check element tag names
            boolean isAnchor = target.getTagName().equalsIgnoreCase(WebElementTagNameEnum.ANCHOR.getValue());
            boolean isOption = target.getTagName().equalsIgnoreCase(WebElementTagNameEnum.OPTION.getValue());

            // Extract various attributes

            String labelAttributeValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.LABEL);
            String forLabelAttributeValue =
                    WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.FOR_LABEL);
            String classAttributeValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.CLASS);
            String typeAttributeValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.TYPE);
            String idAttributeValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.ID);
            String titleAttributeValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.TITLE);
            String disabledAttributeValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.DISABLED);
            String styleAttributeValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.STYLE);
            String dataTestIdAttributeValue =
                    WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.DATA_TEST_ID);

            String ariaLabelValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.ARIA_LABEL);
            String innerHTMLValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.INNER_HTML);
            String formControlNameAttributeValue =
                    WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.FORM_CONTROL_NAME);
            String testIdAttributeValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.TEST_ID);
            String nameAttributeValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.NAME);
            String valueAttributeValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.VALUE);

            String hasMatLabelValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.MAT_LABEL);
            String hasMatInputValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.MAT_INPUT);
            String hasInputValue = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.MAT_INPUT);

            String valueHRefFile = WebTextUtils.extractFileExtension(
                    WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.HREF));

            String textLabel = targetElem.getText();

            // Determine boolean conditions
            boolean hasButton = target.getTagName().equalsIgnoreCase("button")
                    && isClickable(targetElem, tagNameDefined)
                    && !textLabel.isBlank();
            boolean hasAriaLabel = WebTextUtils.isValidString(ariaLabelValue);
            boolean hasInnerHTML = WebTextUtils.isValidString(innerHTMLValue) && !hasButton;
            boolean hasInnerHTMLTag = hasInnerHTML && (innerHTMLValue.contains("<") || innerHTMLValue.contains(">"));
            boolean hasFormControlName = WebTextUtils.isValidString(formControlNameAttributeValue);
            boolean hasTestId = WebTextUtils.isValidString(testIdAttributeValue);
            boolean hasName = WebTextUtils.isValidString(nameAttributeValue);
            boolean hasId = WebTextUtils.isValidString(idAttributeValue) && !hasButton;
            boolean hasValue = WebTextUtils.isValidString(valueAttributeValue);
            boolean hasHRefFile = WebTextUtils.isValidString(valueHRefFile);
            boolean hasParagraph =
                    !Strings.isNullOrEmpty(textLabel) && target.getTagName().equalsIgnoreCase("p");
            boolean hasSpan =
                    !Strings.isNullOrEmpty(textLabel) && target.getTagName().equalsIgnoreCase("span");
            boolean hasDiv =
                    !Strings.isNullOrEmpty(textLabel) && target.getTagName().equalsIgnoreCase("div");

            boolean isLabel = !Strings.isNullOrEmpty(labelAttributeValue);
            boolean isForLabel = !Strings.isNullOrEmpty(forLabelAttributeValue);

            boolean isElementHidden;
            try {
                isElementHidden = WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.TYPE) != null
                        && WebTextUtils.extractAttribute(targetElem, WebElementAttributeEnum.TYPE)
                                .equalsIgnoreCase("hidden");

            } catch (Exception ignored) {
                isElementHidden = false;
            }

            target.setIsElementHidden(isElementHidden);

            // Set nameLabel and nameField based on conditions
            if (isLabel) {
                target = setElementText(target, labelAttributeValue, labelAttributeValue);
            } else if (isForLabel) {
                target = setElementText(target, forLabelAttributeValue, forLabelAttributeValue);
            } else if (isOption && hasValue) {
                target = setElementText(target, valueAttributeValue, valueAttributeValue);
            } else if (hasFormControlName) {
                target = setElementText(target, formControlNameAttributeValue, formControlNameAttributeValue);
            } else if (hasTestId) {
                target = setElementText(target, testIdAttributeValue, testIdAttributeValue);
            } else if (hasName) {
                target = setElementText(target, nameAttributeValue, nameAttributeValue);
            } else if (hasAriaLabel) {
                target = setElementText(target, ariaLabelValue, ariaLabelValue);
            } else if (isAnchor && hasInnerHTML && !hasInnerHTMLTag) {
                target = setElementText(target, innerHTMLValue, innerHTMLValue);
            } else if (hasId) {
                target = setElementText(target, idAttributeValue, idAttributeValue);
            } else if (hasHRefFile) {
                target = setElementText(target, valueHRefFile + " File", valueHRefFile + " File");
            } else if (hasParagraph) {
                target = setElementText(target, textLabel, tagNameDefined);
            } else if (hasButton) {
                target = setElementText(target, textLabel, tagNameDefined);
            } else if (hasSpan) {
                target = setElementText(target, textLabel, tagNameDefined);
            } else if (hasDiv) {
                target = setElementText(target, textLabel, tagNameDefined);
            } else if (!Strings.isNullOrEmpty(textLabel) && !Strings.isNullOrEmpty(tagNameDefined)) {
                target = setElementText(target, textLabel, tagNameDefined);
            } else if (!Strings.isNullOrEmpty(hasMatLabelValue)) {
                target = setElementText(target, hasMatLabelValue, hasMatLabelValue);
            } else if (!Strings.isNullOrEmpty(hasMatInputValue)) {
                target = setElementText(target, hasMatInputValue, hasMatInputValue);
            } else if (!Strings.isNullOrEmpty(hasInputValue)) {
                target = setElementText(target, hasInputValue, hasInputValue);
            } else if (!Strings.isNullOrEmpty(dataTestIdAttributeValue)) {
                target = setElementText(target, dataTestIdAttributeValue, dataTestIdAttributeValue);
            } else if (!Strings.isNullOrEmpty(titleAttributeValue)) {
                target = setElementText(target, titleAttributeValue, titleAttributeValue);
            } else if (!Strings.isNullOrEmpty(tagNameDefined) && tagNameDefined.equalsIgnoreCase("iFrame")) {
                target = setElementText(target, target.getTagName(), tagNameDefined);
            } else {
                target = setElementText(target, target.getTagName(), ARConstantsEngine.VALUE_NO_IDENTIFICATION);
            }

        } catch (Exception e) {
            logOperations.warn("Cannot define Target Name Titles");
        }
        return target;
    }

    private static TargetElement setElementText(TargetElement target, String nameLabelText, String nameFieldText) {
        target.setNameLabel(nameLabelText == null ? "" : nameLabelText.trim().replaceAll("\\s+", " "));
        target.setNameField(nameFieldText == null ? "" : nameFieldText.trim().replaceAll("\\s+", " "));

        String nameDefinedPriority = target.getNameLabel();
        if (!Strings.isNullOrEmpty(target.getAttribId())
                || !Strings.isNullOrEmpty(target.getAttribName())
                || !Strings.isNullOrEmpty(target.getSomeText())) {
            nameDefinedPriority = (!Strings.isNullOrEmpty(target.getSomeText())
                    ? WebTextUtils.truncateAndNormalize(target.getSomeText(), 250)
                    : !Strings.isNullOrEmpty(target.getAttribId())
                            ? target.getAttribId()
                            : !Strings.isNullOrEmpty(target.getAttribName())
                                    ? target.getAttribName()
                                    : nameDefinedPriority);
        }

        target.setDefinedName(nameDefinedPriority);

        return target;
    }

    public static TargetElement defineTagType(TargetElement targetTagType) {

        try {
            logOperations.info("Defined Name: " + targetTagType.getDefinedName());
            logOperations.info("Tag Name: " + targetTagType.getTagName());
            logOperations.info("Id: " + targetTagType.getAttribId());
            logOperations.info("Name: " + targetTagType.getAttribName());
            logOperations.info("xPath: " + targetTagType.getCurrentXPath());
            logOperations.info("Absolut xPath: " + targetTagType.getAttributeData());
            logOperations.info("Custom xPath: " + targetTagType.getCustomXPath());
            logOperations.info("iFrame xPath: " + targetTagType.getIFrameXPath());

            if (targetTagType.getCoordinates() != null) {
                String[] coords = targetTagType.getCoordinates().split(",");
                if (coords.length == 2) {
                    String coordLeft = coords[0].trim();
                    String coordRight = coords[1].trim();
                    // Print or use the extracted values
                    logOperations.info("CoordLeft: " + coordLeft);
                    logOperations.info("CoordRight: " + coordRight);
                }
            }

            String tagName = targetTagType.getTagName();
            String attributeType = targetTagType.getAttributeType();

            boolean nativeInput = tagName != null
                    && (tagName.equalsIgnoreCase(WebElementTagNameEnum.INPUT.getValue())
                            || tagName.equalsIgnoreCase(WebElementTagNameEnum.TEXT_AREA.getValue()));
            boolean nativeClickable = tagName != null
                    && (tagName.equalsIgnoreCase(WebElementTagNameEnum.BUTTON.getValue())
                            || tagName.equalsIgnoreCase(WebElementTagNameEnum.ANCHOR.getValue())
                            || tagName.equalsIgnoreCase(WebElementTagNameEnum.DIV.getValue())
                            || tagName.equalsIgnoreCase(WebElementTagNameEnum.SELECT.getValue())
                            || tagName.equalsIgnoreCase(WebElementTagNameEnum.OPTION.getValue())
                            || tagName.equalsIgnoreCase(WebElementTagNameEnum.MAT_SELECT.getValue())
                            || tagName.equalsIgnoreCase(WebElementTagNameEnum.MAT_OPTION.getValue()));

            // Native input/textarea must stay INSERT even when metadata says radio-option.
            if (nativeInput) {
                targetTagType.setTagType(WebElementTagNameEnum.INPUT);
                targetTagType.setIconType(WebElementIcon.INSERT);
            } else if (nativeClickable || isClickableAttributeType(attributeType)) {
                targetTagType.setTagType(WebElementTagNameEnum.BUTTON);
                targetTagType.setIconType(WebElementIcon.CLICK);
            } else {
                targetTagType.setTagType(WebElementTagNameEnum.ALL);
                targetTagType.setIconType(WebElementIcon.TEXT);
            }

            return targetTagType;

        } catch (Exception ex) {

            logOperations.error("Could not find any Web Element with XPath/Id/Attributes values.");
        }
        return null;
    }

    public static boolean isClickable(WebElement element, String tagNameDefined) {
        List<WebElementTagNameEnum> clickableTags = WebElementTagNameEnum.clickableTags();
        boolean isClickableTag =
                clickableTags.stream().anyMatch(t -> t.getValue().equalsIgnoreCase(tagNameDefined));
        List<WebElementAttributeTypeValueEnum> clickableValues = WebElementAttributeTypeValueEnum.getClickableValues();
        boolean isClickableValue = clickableValues.stream().anyMatch(v -> v.getValue()
                .equalsIgnoreCase(element.getAttribute(WebElementAttributeEnum.TYPE.getValue())));
        boolean isInputTag = tagNameDefined.equalsIgnoreCase(WebElementTagNameEnum.INPUT.getValue());
        return (isClickableTag && !isInputTag) || (isInputTag && isClickableValue && isClickableTag);
    }

    private static boolean isClickableAttributeType(String attributeType) {
        if (attributeType == null || attributeType.isBlank()) {
            return false;
        }
        String normalized = attributeType.toLowerCase();
        return normalized.contains("option")
                || normalized.contains("button")
                || normalized.contains("upload")
                || normalized.contains("switch")
                || normalized.contains("menu")
                || normalized.contains("tree")
                || normalized.contains("tab")
                || normalized.contains("calendar")
                || normalized.contains("select")
                || normalized.equals("checkbox")
                || normalized.equals("radio")
                || normalized.equals("combobox")
                || normalized.equals("link");
    }

    public static InstructionLoad buildNewInstruction(
            WebElementTagNameEnum forceTag,
            String actionReq,
            boolean identityHover,
            Integer orderNumber,
            TargetElement targetBuild) {

        InstructionLoad loop = new InstructionLoad();
        loop.setActionCustomMaxWaitSec(30);
        loop.setDescription("loop desc");
        loop.setCodified(false);
        loop.setInstructionOrderNumber(orderNumber);
        loop.setOptional(false);
        loop.setInstructionActive(true);
        loop.setXpath(targetBuild.getXPath());

        loop.setTagName(targetBuild.getTagName());
        loop.setShadowHost(targetBuild.getShadowHost());
        loop.setShadowRoot(targetBuild.getShadowRoot());
        loop.setCssSelector(targetBuild.getCssSelector());

        String action = buildAction(forceTag, actionReq, identityHover, targetBuild);
        loop.setActions(action);
        loop.setExportToABR(true);

        return loop;
    }

    private static String buildAction(
            WebElementTagNameEnum forceTag, String actionReq, boolean identityHover, TargetElement targetBuild) {

        if (identityHover) {
            return handleIdentityHover(actionReq, forceTag, targetBuild.getNameLabel(), targetBuild.getClickElement());
        } else {
            return handleTargetBuildAction(
                    forceTag, targetBuild, targetBuild.getNameLabel(), targetBuild.getClickElement());
        }
    }

    private static String handleIdentityHover(
            String actionReq, WebElementTagNameEnum forceTag, String nameLabel, Boolean clickElement) {
        return switch (actionReq.toUpperCase()) {
            case ARConstantsEngine.INSERT -> buildInsertAction(forceTag, nameLabel);
            case ARConstantsEngine.OUTPUT -> ARConstantsEngine.OUTPUT
                    + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER
                    + nameLabel;
            case ARConstantsEngine.OTHER -> ARConstantsEngine.OTHER
                    + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER
                    + nameLabel;
            case ARConstantsEngine.CLICK -> ARConstantsEngine.CLICK;
            default -> clickElement
                    ? ARConstantsEngine.CLICK
                    : ARConstantsEngine.INSERT + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER + nameLabel;
        };
    }

    private static String buildInsertAction(WebElementTagNameEnum forceTag, String nameLabel) {
        // Action is always plain "I:<field>". The "press ENTER after" behaviour now lives
        // in the force_coordinates flag column ('E' bit), not the action code.
        return ARConstantsEngine.INSERT + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER + nameLabel;
    }

    private static String handleTargetBuildAction(
            WebElementTagNameEnum forceTag, TargetElement targetBuild, String nameLabel, boolean clickElement) {
        if (targetBuild.getTagType() == null) {
            return clickElement
                    ? ARConstantsEngine.CLICK
                    : ARConstantsEngine.INSERT + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER + nameLabel;
        }

        return switch (targetBuild.getTagType()) {
            case INPUT -> buildInsertAction(forceTag, nameLabel);
            case HIDDEN -> ARConstantsEngine.INSERT
                    + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER
                    + nameLabel
                    + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER
                    + ARConstantsEngine.HIDDEN;
            case BUTTON -> ARConstantsEngine.CLICK;
            default -> ARConstantsEngine.OUTPUT + ARConstantsEngine.ACTION_SPECIFICATIONS_SPLITTER + nameLabel;
        };
    }

    public static Map<String, String> defineSavedReferenced(TargetElement targetRefs) {

        // Handle XPath and attribute cases
        processXPathAndAttributes(targetRefs, targetRefs.getSavedReferences());

        // Process coordinates
        processCoordinates(targetRefs, targetRefs.getSavedReferences());

        return targetRefs.getSavedReferences();
    }

    private static void processXPathAndAttributes(TargetElement targetRefs, Map<String, String> savedReferences) {

        // --- existing ---
        addAttributeIfNotNull(savedReferences, "xpath", targetRefs.getXPath());
        addAttributeIfNotNull(savedReferences, "currentXPath", targetRefs.getCurrentXPath());
        addAttributeIfNotNull(savedReferences, "customXPath", targetRefs.getCustomXPath());
        addAttributeIfNotNull(savedReferences, "attributeID", targetRefs.getAttribId());
        addAttributeIfNotNull(savedReferences, "attributeName", targetRefs.getAttribName());
        addAttributeIfNotNull(savedReferences, "searchAttribute", targetRefs.getSearchAttributeValue());
        addAttributeIfNotNull(savedReferences, "attribute", targetRefs.getAttributeValue());
        addAttributeIfNotNull(savedReferences, "someText", targetRefs.getSomeText());

        // --- best locator extraction (from attributeData first, then fall back to attribId/attribName) ---
        AttributeData[] attrs = targetRefs.getAttributeData();

        String id = getAttr(attrs, "id");
        String name = getAttr(attrs, "name");
        String type = getAttr(attrs, "type");
        String optionValue = getAttr(attrs, "option-value");
        String optionText = getAttr(attrs, "option-text");
        String selectXPath = getAttr(attrs, "select-xpath");
        String triggerSelector = getAttr(attrs, "trigger-selector");
        String controlKind = getAttr(attrs, "control.kind");
        String controlRole = getAttr(attrs, "control.role");
        String originalTag = getAttr(attrs, "original-tag");
        String tag = targetRefs.getTagName() != null ? targetRefs.getTagName().toLowerCase() : null;
        String locatorTag = originalTag != null && !originalTag.isBlank() ? originalTag.toLowerCase() : tag;

        if (id == null || id.isBlank()) id = targetRefs.getAttribId();
        if (name == null || name.isBlank()) name = targetRefs.getAttribName();

        addScannerAttributes(savedReferences, attrs);
        addIfNotBlank(savedReferences, "dom.originalTag", originalTag);

        // --- store best locators (ranked) ---
        addIfNotBlank(savedReferences, "locator.best.byId", id); // By.id(...)
        addIfNotBlank(savedReferences, "locator.best.byName", name); // By.name(...)

        // CSS
        if (id != null && !id.isBlank()) {
            addIfNotBlank(savedReferences, "locator.css.id", "#" + id); // By.cssSelector("#id")
            if (tag != null && !tag.isBlank()) {
                addIfNotBlank(savedReferences, "locator.css.tagId", tag + "#" + id); // input#password
            }
            if (locatorTag != null && !locatorTag.isBlank() && !locatorTag.equals(tag)) {
                addIfNotBlank(savedReferences, "locator.css.originalTagId", locatorTag + "#" + id);
            }
        }
        if (tag != null && name != null && !name.isBlank()) {
            addIfNotBlank(savedReferences, "locator.css.name", tag + "[name='" + name + "']"); // input[name='password']
        }
        if (locatorTag != null && !locatorTag.isBlank() && !locatorTag.equals(tag) && name != null && !name.isBlank()) {
            addIfNotBlank(savedReferences, "locator.css.originalTagName", locatorTag + "[name='" + name + "']");
        }

        // XPath
        if (tag == null || tag.isBlank()) tag = "*";
        if (locatorTag == null || locatorTag.isBlank()) locatorTag = tag;

        if (id != null && !id.isBlank()) {
            addIfNotBlank(savedReferences, "locator.xpath.id", "//" + tag + "[@id='" + id + "']");
            if (!locatorTag.equals(tag)) {
                addIfNotBlank(savedReferences, "locator.xpath.originalTagId", "//" + locatorTag + "[@id='" + id + "']");
            }
        }
        if (name != null && !name.isBlank()) {
            addIfNotBlank(savedReferences, "locator.xpath.name", "//" + tag + "[@name='" + name + "']");
            if (!locatorTag.equals(tag)) {
                addIfNotBlank(
                        savedReferences, "locator.xpath.originalTagName", "//" + locatorTag + "[@name='" + name + "']");
            }
        }
        if (name != null && !name.isBlank() && type != null && !type.isBlank()) {
            addIfNotBlank(
                    savedReferences,
                    "locator.xpath.nameType",
                    "//" + tag + "[@name='" + name + "' and @type='" + type + "']");
            if (!locatorTag.equals(tag)) {
                addIfNotBlank(
                        savedReferences,
                        "locator.xpath.originalTagNameType",
                        "//" + locatorTag + "[@name='" + name + "' and @type='" + type + "']");
            }
        }

        // Optional: store the cssSelector you already have (if it’s good)
        addIfNotBlank(savedReferences, "locator.css.generated", targetRefs.getCssSelector());
        addIfNotBlank(savedReferences, "select.option.value", optionValue);
        addIfNotBlank(savedReferences, "select.option.text", optionText);
        addIfNotBlank(savedReferences, "select.native.xpath", selectXPath);
        addIfNotBlank(savedReferences, "select.trigger.css", triggerSelector);
        addIfNotBlank(savedReferences, "control.kind", controlKind);
        addIfNotBlank(savedReferences, "control.role", controlRole);

        // Optional: iframe/shadow metadata if relevant for finding context
        addIfNotBlank(savedReferences, "context.iframeXPath", targetRefs.getIFrameXPath());
        addIfNotBlank(savedReferences, "context.shadowHost", targetRefs.getShadowHost());
        addIfNotBlank(savedReferences, "context.shadowRoot", targetRefs.getShadowRoot());
    }

    private static void addScannerAttributes(Map<String, String> savedReferences, AttributeData[] attrs) {
        if (attrs == null) {
            return;
        }
        for (AttributeData attr : attrs) {
            if (attr == null || attr.getName() == null || attr.getName().isBlank()) {
                continue;
            }
            addIfNotBlank(savedReferences, "AttrData:" + attr.getName(), attr.getValue());
        }
    }

    private static String getAttr(AttributeData[] attrs, String attrName) {
        if (attrs == null) return null;
        for (AttributeData a : attrs) {
            if (a != null && attrName.equalsIgnoreCase(a.getName())) {
                return a.getValue();
            }
        }
        return null;
    }

    private static void addIfNotBlank(Map<String, String> map, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            map.put(key, value);
        }
    }

    private static void addAttributeIfNotNull(Map<String, String> savedReferences, String key, String value) {
        if (!Strings.isNullOrEmpty(value)) {
            savedReferences.put(key, value);
        }
    }

    private static void processCoordinates(TargetElement targetRefs, Map<String, String> savedReferences) {

        savedReferences.put("js_coordinates", targetRefs.getCoordinates());

        String[] parts = targetRefs.getCoordinates().split(",");

        try {
            // Parse coordinates as doubles
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());

            String newCoordinates = (Math.round(x) + 50.0) + "," + (Math.round(y) + 50.0);

            // Computed
            savedReferences.put("cp_coordinates", newCoordinates);
        } catch (NumberFormatException e) {
            //            logOperations.error("Invalid coordinates from Javascript code: " +
            // targetRefs.getCoordinates());
        }
    }
}

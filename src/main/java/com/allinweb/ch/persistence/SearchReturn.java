package com.allinweb.ch.persistence;

import com.allinweb.ch.builder.WebElementIcon;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import java.util.List;
import org.openqa.selenium.WebElement;

public class SearchReturn {
    String currentXPath;
    String iFrameXPath;
    List<String> iFrameElements;
    String mainXPath;
    String mainCoordinates;
    String absolutXPath;
    String customXPath;
    String xPathWorkedFirst;
    String coords;
    String attribId;
    String attribName;
    String attributeType;
    String attributeValue;
    String originalTagName;
    String definedName;
    WebElementTagNameEnum tagType;
    WebElementIcon iconType;
    WebElement element;

    public String getMainCoordinates() {
        return mainCoordinates;
    }

    public void setMainCoordinates(String mainCoordinates) {
        this.mainCoordinates = mainCoordinates;
    }

    public String getMainXPath() {
        return mainXPath;
    }

    public void setMainXPath(String mainXPath) {
        this.mainXPath = mainXPath;
    }

    public WebElementIcon getIconType() {
        return iconType;
    }

    public void setIconType(WebElementIcon iconType) {
        this.iconType = iconType;
    }

    public WebElementTagNameEnum getTagType() {
        return tagType;
    }

    public void setTagType(WebElementTagNameEnum tagType) {
        this.tagType = tagType;
    }

    public String getiFrameXPath() {
        return iFrameXPath;
    }

    public void setiFrameXPath(String iFrameXPath) {
        this.iFrameXPath = iFrameXPath;
    }

    public List<String> getiFrameElements() {
        return iFrameElements;
    }

    public void setiFrameElements(List<String> iFrameElements) {
        this.iFrameElements = iFrameElements;
    }

    public String getAttribId() {
        return attribId;
    }

    public void setAttribId(String attribId) {
        this.attribId = attribId;
    }

    public String getAttribName() {
        return attribName;
    }

    public void setAttribName(String attribName) {
        this.attribName = attribName;
    }

    public String getOriginalTagName() {
        return originalTagName;
    }

    public void setOriginalTagName(String originalTagName) {
        this.originalTagName = originalTagName;
    }

    public String getDefinedName() {
        return definedName;
    }

    public void setDefinedName(String definedName) {
        this.definedName = definedName;
    }

    public String getxPathWorkedFirst() {
        return xPathWorkedFirst;
    }

    public void setxPathWorkedFirst(String xPathWorkedFirst) {
        this.xPathWorkedFirst = xPathWorkedFirst;
    }

    public String getCurrentXPath() {
        return currentXPath;
    }

    public void setCurrentXPath(String currentXPath) {
        this.currentXPath = currentXPath;
    }

    public String getAbsolutXPath() {
        return absolutXPath;
    }

    public void setAbsolutXPath(String absolutXPath) {
        this.absolutXPath = absolutXPath;
    }

    public String getCustomXPath() {
        return customXPath;
    }

    public void setCustomXPath(String customXPath) {
        this.customXPath = customXPath;
    }

    public String getCoords() {
        return coords;
    }

    public void setCoords(String coords) {
        this.coords = coords;
    }

    public String getAttributeType() {
        return attributeType;
    }

    public void setAttributeType(String attributeType) {
        this.attributeType = attributeType;
    }

    public String getAttributeValue() {
        return attributeValue;
    }

    public void setAttributeValue(String attributeValue) {
        this.attributeValue = attributeValue;
    }

    public WebElement getElement() {
        return element;
    }

    public void setElement(WebElement element) {
        this.element = element;
    }
}

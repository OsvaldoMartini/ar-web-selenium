package com.allinweb.ch.persistence;

import com.allinweb.ch.builder.WebElementTagNameEnum;
import org.openqa.selenium.WebElement;

public class SearchReturn {
    String currentXPath;
    String absolutXPath;
    String customXPath;
    String xPathWorkedFirst;
    String coords;
    String attribId;
    String attribName;
    String attributeType;
    String attributeValue;
    String originalTagName;
    WebElementTagNameEnum forceTypeEnum;
    WebElement element;

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

    public WebElementTagNameEnum getForceTypeEnum() {
        return forceTypeEnum;
    }

    public void setForceTypeEnum(WebElementTagNameEnum forceTypeEnum) {
        this.forceTypeEnum = forceTypeEnum;
    }

    public WebElement getElement() {
        return element;
    }

    public void setElement(WebElement element) {
        this.element = element;
    }
}

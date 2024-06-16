package com.allinweb.ch.persistence;

import com.allinweb.ch.builder.WebElementTagNameEnum;
import org.openqa.selenium.WebElement;

public class SearchReturn {
    String currentXPath;
    String coords;
    String attributeType;
    String attributeValue;
    WebElementTagNameEnum forceTypeEnum;
    WebElement element;

    public String getCurrentXPath() {
        return currentXPath;
    }

    public void setCurrentXPath(String currentXPath) {
        this.currentXPath = currentXPath;
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

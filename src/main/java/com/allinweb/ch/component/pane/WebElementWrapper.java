package com.allinweb.ch.component.pane;

import java.util.Objects;
import org.openqa.selenium.WebElement;

public class WebElementWrapper {
    private String text;
    private String href;
    private WebElement webElement;

    // Constructors, getters, setters, etc.

    public WebElementWrapper(String text, String href, WebElement webElement) {
        this.text = text;
        this.href = href;
        this.webElement = webElement;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public WebElement getWebElement() {
        return webElement;
    }

    public void setWebElement(WebElement webElement) {
        this.webElement = webElement;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebElementWrapper that = (WebElementWrapper) o;
        return Objects.equals(text, that.text) && Objects.equals(href, that.href);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, href);
    }

    @Override
    public String toString() {
        return "WebElementWrapper{" + "text='"
                + text + '\'' + ", href='"
                + href + '\'' + ", webElement="
                + webElement + '}';
    }
}

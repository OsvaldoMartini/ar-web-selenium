package com.allinweb.ch.util;

import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class ABRWebUtil {

    public static String extractWebElementXPath(WebElement element) {
        return generateXPath(element, "");
    }

    private static String generateXPath(WebElement element, String current) {
        String tag = element.getTagName();
        if (tag.equals("html")) {
            return "/html" + current;
        }
        WebElement parentElement = element.findElement(By.xpath(".."));
        int count = 0;
        int index = 1;
        List<WebElement> children = parentElement.findElements(By.xpath("*"));
        for (WebElement child : children) {
            String childTag = child.getTagName();
            if (childTag.equals(tag)) {
                if (child.equals(element)) {
                    index = count + 1;
                }
                count++;
            }
        }
        return generateXPath(parentElement, "/" + tag + "[" + index + "]" + current);
    }
}

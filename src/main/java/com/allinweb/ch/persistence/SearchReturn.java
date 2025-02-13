package com.allinweb.ch.persistence;

import com.allinweb.ch.builder.WebElementIcon;
import com.allinweb.ch.builder.WebElementTagNameEnum;
import java.util.List;
import lombok.Data;
import org.openqa.selenium.WebElement;

@Data
public class SearchReturn {
    String currentXPath;
    String iFrameXPath;
    List<String> iFrameElements;
    String mainXPath;
    String mainCoordinates;
    String someText;
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
}

package com.allinweb.ch.component.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ElementDTO {
    private String typeElement;
    private String tagName;
    private String xPath;
    private String text;
    private String attribId;
    private String attribName;
    private String coords;
    private String absoluteXPath;
    private String customXPath;
}

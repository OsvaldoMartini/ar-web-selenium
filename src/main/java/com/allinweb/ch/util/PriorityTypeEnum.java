package com.allinweb.ch.util;

public enum PriorityTypeEnum {
    attribute,
    xpath,
    coordinates,
    ById,
    ByClassName,
    ByName,
    ByTagName,
    ByLinkText,
    ByPartialLinkText,
    ByCssSelector, //      ".nav-menu li";
    ByXPath, //           "//input[@type='text']"
    ByLabels, //           "//input[@type='text']"
    ExecuteScript, //      "return document.getElementById('search-top')");
    createXPath, //         Generates XPath Recursive tom the Elements Found
    dynamic, //         Generates Dynamic Action -> Click, Hover, Etc.
    jsoup; //                JSOUP  Search Library Experimental

    public static PriorityTypeEnum getPriorityType(String priorityType) {
        for (PriorityTypeEnum val : PriorityTypeEnum.values()) {
            if (val.name().equalsIgnoreCase(priorityType)) {
                return val;
            }
        }
        throw new EnumConstantNotPresentException(PriorityTypeEnum.class, priorityType);
    }

    @Override
    public String toString() {
        return this.name();
    }
}

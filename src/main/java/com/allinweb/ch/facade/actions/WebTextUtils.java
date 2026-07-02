package com.allinweb.ch.facade.actions;

import com.allinweb.ch.builder.WebElementAttributeEnum;
import com.allinweb.ch.model.FieldData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure text/format helpers extracted from PerformActions (cluster M + J-formatting).
 * All methods are stateless; bodies moved verbatim from the facade.
 */
public final class WebTextUtils {

    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 30;
    private static final Random RANDOM = new Random();

    private WebTextUtils() {}

    public static String removeTrailingSlash(String xPath) {
        if (xPath != null && xPath.endsWith("/")) {
            return xPath.substring(0, xPath.length() - 1);
        }
        return xPath;
    }

    public static String extractTagName(String xPath) {
        // Find the position of the last '/'
        int lastSlashIndex = xPath.lastIndexOf("/");

        // Extract the substring after the last '/'
        String lastSegment = xPath.substring(lastSlashIndex + 1);

        // If the last segment contains '[', extract the tag name before it
        int bracketIndex = lastSegment.indexOf("[");
        if (bracketIndex != -1) {
            return lastSegment.substring(0, bracketIndex);
        }

        // Return the last segment as the tag name
        return lastSegment;
    }

    public static String convertToCssSelector(String tagName, List<String> priorityToSearch, String attributeValue) {

        for (String priority : priorityToSearch) {
            priority = priority.trim();
            String attributeName;

            if (priority.equalsIgnoreCase("attributeID")) {
                attributeName = "id";
            } else if (priority.equalsIgnoreCase("attributeName")) {
                attributeName = "name";
            } else {
                attributeName = priority; // Use the priority as the attribute name for other cases
            }

            // Create the CSS selector string and add it to the list
            return tagName + "[" + attributeName + "='" + attributeValue.trim() + "']";
        }

        return null;
    }

    public static List<By> convertToCriteriaList(String tagName, List<String> priorityToSearch, String someXPath) {
        // Split the string by commas and trim any leading/trailing whitespace from each element
        List<By> criteriaList = new ArrayList<>();

        for (String priority : priorityToSearch) {
            priority = priority.trim();

            if (priority.equalsIgnoreCase("attributeID")) {
                priority = "id";
            } else if (priority.equalsIgnoreCase("attributeName")) {
                priority = "name";
            }
            // Create the By.cssSelector object and add it to the list
            By criteria = By.cssSelector(tagName + "[" + priority + "='" + someXPath + "']");
            criteriaList.add(criteria);
        }

        return criteriaList;
    }

    public static FieldData insertRandomName(String key) {
        String randomName = generateRandomName();
        return new FieldData(key, randomName);
    }

    public static String generateRandomName() {
        int length = RANDOM.nextInt(MAX_LENGTH - MIN_LENGTH + 1) + MIN_LENGTH;
        StringBuilder nameBuilder = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            char randomChar = CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length()));
            nameBuilder.append(randomChar);
        }

        return nameBuilder.toString();
    }

    public static String truncateAndNormalize(String someText, int limit) {
        return someText;
        //        if (someText == null || someText.isEmpty()) {
        //            return someText;
        //        }
        //
        //        // Remove extra spaces and trim
        //        String normalizedText = someText.trim().replaceAll("\\s+", " ");
        //
        //        if (normalizedText.length() <= limit) {
        //            return normalizedText;
        //        }
        //
        //        return normalizedText.substring(0, limit) + "...";
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

    public static String extractAttribute(WebElement element, WebElementAttributeEnum attributeEnum) {
        return element.getAttribute(attributeEnum.getValue());
    }

    public static String normalizeLocatorValue(String referenceType, String value) {
        if (value == null) return null;

        // If DB stores full css/xpath already, use it as-is.
        // If DB stores only the raw id/name, convert where needed.
        switch (referenceType) {
            case "locator.css.id":
                // stored could be "password" or "#password"
                return value.startsWith("#") ? value : "#" + value;

            default:
                return value;
        }
    }

    public static boolean isValidString(String value) {
        return value != null && !value.isBlank();
    }

    public static Map<String, String> removeCurrencySymbols(Map<String, String> mapExport) {
        // Use LinkedHashMap to preserve the insertion order
        Map<String, String> cleanedMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : mapExport.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String cleanedValue = removeAllCurrencySymbols(value);
            cleanedMap.put(key, cleanedValue);
        }
        return cleanedMap;
    }

    /**
     * Removes all characters that are not numbers or the decimal separator.
     *
     * @param input The string to clean.
     * @return A cleaned version of the string.
     */
    public static String removeAllCurrencySymbols(String input) {
        // Remove all non-numeric and non-decimal characters (e.g., $, €, etc.)
        return input.replaceAll("[^0-9.,]", "");
    }

    public static String formatLocalNumber(String numberString, String localFormat) {
        try {
            String decimalPart = "";
            String integerPart = "";

            // Find last occurrence of "," or "." as decimal separator
            int decimalIndex = Math.max(numberString.lastIndexOf(','), numberString.lastIndexOf('.'));
            if (decimalIndex != -1) {
                decimalPart = numberString.substring(decimalIndex + 1);
                integerPart = numberString.substring(0, decimalIndex).replaceAll("[^0-9]", "");
            } else {
                integerPart = numberString.replaceAll("[^0-9]", "");
            }

            // Determine formatting style
            String groupingSeparator;
            String decimalSeparator;

            if ("US".equalsIgnoreCase(localFormat)) {
                groupingSeparator = ",";
                decimalSeparator = ".";
            } else if ("EU".equalsIgnoreCase(localFormat)) {
                groupingSeparator = ".";
                decimalSeparator = ",";
            } else { // Default
                groupingSeparator = ",";
                decimalSeparator = ".";
            }

            // Rebuild integer part with grouping
            String groupedInteger = insertGroupingSeparators(integerPart, groupingSeparator);

            return decimalPart.isEmpty() ? groupedInteger : groupedInteger + decimalSeparator + decimalPart;

        } catch (Exception e) {
            logOperations.error("Error formatting number: " + numberString + " - " + e.getMessage());
            return numberString;
        }
    }

    private static String insertGroupingSeparators(String number, String separator) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = number.length() - 1; i >= 0; i--) {
            sb.insert(0, number.charAt(i));
            count++;
            if (count % 3 == 0 && i != 0) {
                sb.insert(0, separator);
            }
        }
        return sb.toString();
    }
}

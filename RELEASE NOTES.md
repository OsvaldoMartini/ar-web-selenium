# IMPORTANT CHANGES ABOUT WAITING TIME

This document describes an important refactor in the `clickElement` method, focused on **reducing waiting time**, **improving reliability**, and **making failures more explicit**.

---

## Overview

The previous implementation relied on a shared, potentially long `waitForAction` configuration and chained waits.  
The new implementation introduces a **dedicated short wait** for the click interaction, making UI tests:

- Faster ⏱️
- More predictable 🎯
- Easier to debug 🐛

---

## PREVIOUS IMPLEMENTATION

### Characteristics
- Uses a global `waitForAction`
- Chained `visibilityOf` → `scrollIntoView` → `elementToBeClickable`
- Potentially long and implicit wait times
- Less control over polling and ignored exceptions

### Code

```java
public boolean clickElement(boolean byPassNotFound, WebElement element) throws Exception {
    UtilsMethods.exceptionIfNullWebElement(element);

    try {
        waitForAction.until(ExpectedConditions.visibilityOf(element).andThen(e -> {
            ((JavascriptExecutor) this.currentDriver)
                .executeScript("arguments[0].scrollIntoView(true);", element);
            return waitForAction.until(ExpectedConditions.elementToBeClickable(element));
        }));
    } catch (Exception e) {

        logOperations.warn(
            String.format("Could Not Find TagName \"%s\" -> Cause: %s",
                element.getTagName(), e.getMessage()));

        if (!byPassNotFound) {
            performMessage.couldNotFindElement(element.getTagName());
        }
        return false;
    }
}
````

---

## NEW IMPLEMENTATION

### Key Improvements

* ⏳ **Short, local wait (5 seconds)** only for this interaction
* 🔁 **Custom polling interval** (200 ms)
* 🚫 **Ignored transient exceptions**:

  * `StaleElementReferenceException`
  * `ElementClickInterceptedException`
* 📍 Scrolls element to **center of viewport**
* 🧾 Clear distinction between `TimeoutException` and generic failures

### Code

```java
public boolean clickElement(boolean byPassNotFound, WebElement element) throws Exception {
    UtilsMethods.exceptionIfNullWebElement(element);

    try {
        // short wait only for this interaction (e.g. 5 seconds)
        WebDriverWait quickWait =
            new WebDriverWait(this.currentDriver, Duration.ofSeconds(5));

        quickWait.pollingEvery(Duration.ofMillis(200));
        quickWait.ignoring(StaleElementReferenceException.class);
        quickWait.ignoring(ElementClickInterceptedException.class);

        // wait visible (short), then scroll, then clickable (short)
        quickWait.until(ExpectedConditions.visibilityOf(element));

        ((JavascriptExecutor) this.currentDriver)
            .executeScript(
                "arguments[0].scrollIntoView({block:'center'});", element);

        quickWait.until(ExpectedConditions.elementToBeClickable(element));

    } catch (TimeoutException e) {
        logOperations.warn(String.format(
            "Timeout waiting clickable for tag \"%s\" after 5s -> %s",
            safeTag(element), e.getMessage()));

        if (!byPassNotFound)
            performMessage.couldNotFindElement(safeTag(element));

        return false;

    } catch (Exception e) {
        logOperations.warn(String.format(
            "Could Not Find TagName \"%s\" -> Cause: %s",
            safeTag(element), e.getMessage()));

        if (!byPassNotFound)
            performMessage.couldNotFindElement(safeTag(element));

        return false;
    }
}
```

---

## Why This Change Matters

| Aspect             | Before            | Now                         |
| ------------------ | ----------------- | --------------------------- |
| Wait duration      | Implicit / global | Explicit (5s)               |
| Polling            | Default           | 200 ms                      |
| Exception handling | Generic           | Targeted + timeout-specific |
| Scroll behavior    | Top of viewport   | Centered                    |
| Test speed         | Slower            | Faster                      |

---

## Summary

This refactor makes `clickElement` **self-contained**, **faster**, and **more robust** against flaky UI behavior.
It avoids unnecessary long waits while still handling common Selenium edge cases gracefully.

✅ Recommended for all click interactions where fast feedback is critical.

---


## NEW `locateElement` Code

```java
private WebElement locateElement(
            InstructionLoad currentInstruction, int botJobId, boolean forceCoordinates, boolean byPassFlagLoop) {

        String instructionPath = currentInstruction.getXpath();
        String tagName = null;

        this.currentDriver.switchTo().defaultContent();
        if (this.currentDriver.getWindowHandles().size() > 1) {
            try {
                this.currentDriver.switchTo().window(windowHandlesList.get(currentTabIndex));
            } catch (Exception ignore) {
            }
        }

        try {
            tagName = extractTagName(removeTrailingSlash(instructionPath));
        } catch (Exception e) {
            logOperations.warn(String.format(
                    "Error RemoveTrailingSlash for %s -> xPath %s -> Cause: %s",
                    tagName, instructionPath, e.getMessage()));
        }

        List<ReferenceLoadDTO> instructionReferenceList = currentInstruction.getReferenceLoadDTOList();

        if (instructionReferenceList.isEmpty()) {
            logOperations.warn("#### Not XPath to Be Located! ####");
            return null;
        }

        waitPage();

        //        if (arPriorities.getJobId() == null || !arPriorities.getJobId().equals(botJobId)) {
        //            arPriorities.setJobId(botJobId);
        //            if (currentInstruction.getPriority() != null) {
        //                arPriorities.loadPrioritiesFromString(currentInstruction.getPriority());
        //            } else {
        //                arPriorities.loadPriorities();
        //            }
        //        }

        if (arPriorities.getAllPriorityList() == null
                || arPriorities.getAllPriorityList().isEmpty()
                || arPriorities.getAllPriorityList().size() < 15) {
            arPriorities.loadPrioritiesFromString(DEFAULT_LOCATOR_PRIORITIES);
        }

        WebElement elementFound = null;

        if (!Strings.isNullOrEmpty(currentInstruction.getIFrameXPath())) {
            try {
                WebElement iframe = this.currentDriver.findElement(By.xpath(currentInstruction.getIFrameXPath()));
                this.currentDriver.switchTo().frame(iframe);
            } catch (Exception e) {
                logOperations.warn("iFrame Not Found: " + currentInstruction.getIFrameXPath());
                return null;
            }
        }

        if (!Strings.isNullOrEmpty(currentInstruction.getShadowHost())
                && !Strings.isNullOrEmpty(currentInstruction.getCssSelector())) {
            elementFound = findShadowElementByCssSelector(
                    currentInstruction.getShadowHost(), currentInstruction.getCssSelector());
        }

        int attempts = 0;
        int maxAttempts = forceCoordinates || byPassFlagLoop ? 2 : 5;

        while (elementFound == null && attempts < maxAttempts) {

            for (Priority priority : arPriorities.getAllPriorityList()) {
                if (elementFound != null) break;

                PriorityTypeEnum priorityTypeEnum;
                try {
                    priorityTypeEnum = priority.getPriorityType(); // already returns enum
                } catch (Exception e) {
                    continue;
                }

                // ✅ IMPORTANT: try ALL references matching this priority (not only findFirst)
                List<ReferenceLoadDTO> instructionReferences = instructionReferenceList.stream()
                        .filter(ref ->
                                priority.getName().stream().anyMatch(p -> p.equalsIgnoreCase(ref.getReferenceType())))
                        .toList();

                if (instructionReferences.isEmpty()) {
                    continue;
                }

                for (ReferenceLoadDTO ref : instructionReferences) {
                    if (elementFound != null) break;

                    List<By> criterias = null;
                    String value = ref.getValue();

                    switch (priorityTypeEnum) {
                        case xpath -> criterias = List.of(By.xpath(value));

                        case ById -> criterias = List.of(By.id(normalizeLocatorValue(ref.getReferenceType(), value)));

                        case ByName -> criterias =
                                List.of(By.name(normalizeLocatorValue(ref.getReferenceType(), value)));

                        case ByCssSelector -> criterias =
                                List.of(By.cssSelector(normalizeLocatorValue(ref.getReferenceType(), value)));

                        case ByClassName -> criterias = List.of(By.className(value));
                        case ByTagName -> criterias = List.of(By.tagName(value));
                        case ByLinkText -> criterias = List.of(By.linkText(value));
                        case ByPartialLinkText -> criterias = List.of(By.partialLinkText(value));

                        case attribute, attributeID, attributeName, searchAttribute -> criterias =
                                convertToCriteriaList(tagName, priority.getName(), value);

                        default -> {}
                    }

                    if (criterias == null) continue;

                    WebDriverWait wait = new WebDriverWait(getCurrentDriver(), Duration.ofSeconds(5));

                    for (By criteria : criterias) {

                        List<WebElement> foundElementList = new ArrayList<>();
                        try {
                            wait.until(ExpectedConditions.presenceOfElementLocated(criteria));
                            foundElementList = getCurrentDriver().findElements(criteria);

                            if (!foundElementList.isEmpty()) {
                                elementFound = foundElementList.get(0);
                                break;
                            }
                        } catch (TimeoutException ignored) {
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            attempts++;
            if (elementFound == null) {
                try {
                    if (isInterceptBotJob()) {
                        break;
                    }
                    onHoldInSeconds(1);

                    logOperations.warn(String.format(
                            "Re-try %d Locate Web Element TagName \"%s\"", attempts, currentInstruction.getName()));

                } catch (Exception e) {
                }
            }
        }

        return elementFound;
    }
```

---

## Short Analysis

* The locator flow is **priority-driven and deterministic**
* All matching references are evaluated per priority (no `findFirst()` limitation)
* Each locator attempt uses a **short, local 5-second wait**
* Retry delay reduced to **1 second**, improving execution speed
* Logic is flatter, easier to follow, and safer for dynamic DOMs

This implementation improves **reliability**, **maintainability**, and **runtime performance** without altering external behavior.

---

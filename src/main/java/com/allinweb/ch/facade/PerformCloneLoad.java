package com.allinweb.ch.facade;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.util.ARPriorities;
import com.allinweb.ch.util.ErrorMessage;
import java.util.Arrays;
import java.util.List;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * PerformActions.
 *
 * @author Osvaldo Martini
 * @version 1.0
 */
public class PerformCloneLoad {

    private ARPriorities arPriorities;
    private ARWebDriver arWebDriver;
    private static JavascriptExecutor jsExecutor;

    // Static final variable to hold the singleton instance
    protected static final SingletonSupplier<PerformCloneLoad> instance = () -> new PerformCloneLoad();

    // Private constructor to prevent instantiation
    private PerformCloneLoad() {
        // Initialize if necessary
    }

    public void initializePerformCloneLoad(ARPriorities arPriorities, ARWebDriver arWebDriver) {
        this.arPriorities = arPriorities;
        this.arWebDriver = arWebDriver;
    }

    // Public method to access the singleton instance
    public static PerformCloneLoad getInstance() {
        return instance.get();
    }

    public ErrorMessage dynamicPickOneCloneElementsDTO(
            WebDriver driver,
            String currentUrl,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            int homeBankingId) {

        List<String> dataList = Arrays.asList(dataArray);
        try {
            jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript(
                    jsPickOneCloneInject, currentUrl, currentUrl, dataList, searchHiddenFields, port, homeBankingId);
            return null;
        } catch (Exception error) {
            return new ErrorMessage("Error running Scanner", "Dynamic Load ElementsDTO error", error.getMessage());
        }
    }

    private String jsPickOneCloneInject =
            """
                    (function (
                      targetOriginURL,
                      trustedOriginURL,
                      searchTerms,
                      hiddenFields,
                      socketPort,
                      homeBankingId
                    ) {
                      var attempts = 0;
                      var wSocket = null;
                      // Temporary storage for original styles
                      const originalStyles = new Map();
                      var coordinatesElement = document.createElement("div");
                      coordinatesElement.id = "coordinates";
                      coordinatesElement.style.position = "fixed"; // Fixed so it stays above all elements
                      coordinatesElement.style.padding = "10px";
                      coordinatesElement.style.backgroundColor = "rgba(0, 0, 0, 0.5)";
                      coordinatesElement.style.color = "white";
                      coordinatesElement.style.borderRadius = "5px";
                      coordinatesElement.style.fontSize = "14px";
                      coordinatesElement.style.zIndex = Number.MAX_SAFE_INTEGER; // Set zIndex to the maximum allowed value
                      coordinatesElement.style.cursor = "pointer"; // Set cursor to hand (pointer)
                      coordinatesElement.textContent = "X: 0nbsp;&nbsp;&nbsp;&nbsp;Y: 0";

                      // Append the coordinates div to the body
                      document.body.appendChild(coordinatesElement);

                      window.elementInfoMap = new Map();
                      window.allElementInfo = [];

                      // Track the last hovered element to remove the border from it
                      let lastHoveredElement = null;

                      function connectWebSocket() {
                        try {
                          wSocket = new WebSocket(`ws://localhost:${socketPort}/websocket`);

                          wSocket.onopen = () => {
                            console.log("WebSocket connected");
                            attempts = 0; // Reset attempts on successful connection

                            try {
                              const subscriptionMessage = {
                                type: "echo",
                                body: "subscribe",
                              };
                              wSocket.send(JSON.stringify(subscriptionMessage));
                            } catch (sendError) {
                              console.error("Failed to send subscription message:", sendError);
                            }

                            // Call startCollectingElements AFTER WebSocket is open
                            sendingData();
                          };

                          wSocket.onmessage = (event) => {
                            let receivedMessage = event.data;

                            if (receivedMessage.endsWith("\\u0000")) {
                              receivedMessage = receivedMessage.slice(0, -1);
                            }

                            if (receivedMessage) {
                              try {
                                const parsedObject = JSON.parse(receivedMessage);
                                console.log("WebSocket message received:", parsedObject);

                                // Process parsedObject.body and parsedObject.footer here
                                if (parsedObject.body.includes("data_updated")) {
                                  //Handle data update
                                }

                                if (
                                  parsedObject.body.includes("cannot be processed") ||
                                  (parsedObject.footer &&
                                    parsedObject.footer.includes("cannot be processed"))
                                ) {
                                  //Handle cannot be processed
                                }
                              } catch (parseError) {
                                console.warn("Non-JSON message received:", receivedMessage);
                              }
                            }
                          };

                          wSocket.onerror = (error) => {
                            console.error("WebSocket error:", error);
                          };

                          wSocket.onclose = () => {
                            console.log("WebSocket connection closed");

                            if (attempts < 100) {
                              attempts++;
                              console.log(`Reconnecting attempt ${attempts}...`);
                              connectWebSocket();
                            } else {
                              console.log("100 Attempts to Reconnect with the WebSocket.");
                            }
                          };
                        } catch (initError) {
                          console.error("Failed to initialize WebSocket:", initError);
                        }
                      }

                      // Optionally, expose a cleanup function
                      window.cleanupWebSocket = () => {
                        try {
                          console.log("Cleaning up WebSocket...");
                          if (wSocket && wSocket.readyState === WebSocket.OPEN) {
                            wSocket.close();
                          }
                        } catch (cleanupError) {
                          console.error("Error during WebSocket cleanup:", cleanupError);
                        }
                      };

                      function sendingData() {
                        window.allElementInfo = [];
                        limitMapCharacters(window.elementInfoMap);
                        console.log("All element info stored in Map:", window.allElementInfo);

                        if (wSocket && wSocket.readyState) {
                          console.log("WebSocket readyState:", wSocket.readyState);
                        }

                        if (wSocket && wSocket.readyState === WebSocket.OPEN) {
                          if (window.allElementInfo.length > 0) {
                            const message = {
                              type: "SEARCH_TOOL",
                              sessionId: `scannerGrid-${homeBankingId}`,
                              operationId: "searchTerms",
                              homeBankingId: homeBankingId,
                              details: window.allElementInfo, // Send allElementInfo
                            };
                            wSocket.send(JSON.stringify(message));
                            console.log("Sent SEARCH_TOOL:", message);
                            window.elementInfoMap.clear();
                          }
                        } else {
                          console.warn("WebSocket is not open. Cannot send message.");
                        }
                      }

                      const getElementIdentity = function getElementIdentity(element) {
                        if (!hiddenFields) {
                          if (
                            (element.offsetWidth === 0 ||
                              element.offsetHeight === 0 ||
                              window.getComputedStyle(element).visibility === "hidden") &&
                            !(
                              element.tagName.toLowerCase() === "input" &&
                              element.type.toLowerCase() === "hidden"
                            )
                          ) {
                            return null; // Ignore all hidden elements except <input type="hidden">
                          }
                        }
                        const xPath = getMartiniXPath(element);
                        const tagName = element.tagName.toLowerCase();
                        const attributeData = Array.from(element.attributes).map((attr) => ({
                          name: attr.name,
                          value: attr.value,
                        }));
                        const attribId = element.id || "";
                        const attribName = element.name || "";
                        const coords = `${element.getBoundingClientRect().left.toFixed(2)},${element
                          .getBoundingClientRect()
                          .top.toFixed(2)}`;
                        const someText = getVisibleText(tagName, attributeData, element);

                        return {
                          xPath,
                          tagName,
                          attributeData,
                          customXPath: "",
                          attribId,
                          attribName,
                          coords,
                          someText,
                        };
                      };
                      function getVisibleText(tagName, attributeData, element) {
                        let textSet = new Set();
                        let textResult = "";

                        if (element) {
                          // Check computed styles to filter out hidden elements
                          const isHidden = (el) => {
                            const style = window.getComputedStyle(el);
                            return (
                              style.display === "none" ||
                              style.visibility === "hidden" ||
                              el.hasAttribute("aria-hidden")
                            );
                          };

                          if (!isHidden(element)) {
                            const extractedText = extractVisibleTextFromHTML(element);
                            textResult = [
                              ...extractedText.titles,
                              ...extractedText.text,
                              ...extractedText.labels,
                            ]
                              .map((text) => text.trim())
                              .filter(Boolean)
                              .join("; ");
                          }
                        }

                        // Extract attribute-based text (only meaningful ones)
                        attributeData.forEach(({ name, value }) => {
                          const trimmedValue = value.trim();
                          if (!trimmedValue) return;

                          if (["placeholder", "label", "name", "title", "id"].includes(name)) {
                            textSet.add(trimmedValue);
                          }

                          // Extract text from `srcdoc` if available
                          if (name === "srcdoc") {
                            try {
                              const doc = new DOMParser().parseFromString(value, "text/html");
                              const extractedText = extractVisibleTextFromHTML(doc.body);
                              [
                                ...extractedText.titles,
                                ...extractedText.text,
                                ...extractedText.labels,
                              ].forEach((text) => textSet.add(text.trim()));
                            } catch (e) {
                              console.warn("Error parsing srcdoc:", e);
                            }
                          }
                        });

                        // Add extracted visible text to the set (removing duplicates)
                        textResult
                          .split(";")
                          .map((text) => text.trim())
                          .filter(Boolean)
                          .forEach((text) => textSet.add(text));

                        // Return unique, clean, and visible text
                        return Array.from(textSet).join("; ");
                      }

                      const getMartiniXPath = function getMartiniXPath(element) {
                        if (element === document.body) return "/html/body";
                        let ix = 0;
                        const siblings = element.parentNode ? element.parentNode.childNodes : [];
                        for (let i = 0; i < siblings.length; i++) {
                          let sibling = siblings[i];
                          if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {
                            if (sibling === element) {
                              return (
                                getMartiniXPath(element.parentNode) +
                                "/" +
                                element.tagName.toLowerCase() +
                                "[" +
                                (ix + 1) +
                                "]"
                              );
                            }
                            ix++;
                          }
                        }
                        return "";
                      };

                      const elementDTO = function elementDTO(typeElement, identity) {
                        return {
                          typeElement: typeElement,
                          tagName: identity.tagName ?? "No Tag Name Detected",
                          xPath: identity.xPath ?? "",
                          someText: identity.someText ?? "",
                          attribId: identity.attribId ?? "",
                          attribName: identity.attribName ?? "",
                          coords: identity.coords ?? "",
                          attributeData: identity.attributeData ?? "",
                          customXPath: identity.customXPath ?? "",
                          iFrameXPath: identity.iFrameXPath ?? "",
                          shadowHost: identity.shadowHost ?? "",
                          shadowRoot: identity.shadowRoot ?? "",
                          nestedShadow: identity.nestedShadow ?? "",
                          cssSelector: identity.cssSelector ?? "",
                          attributeValue: identity.attributeValue ?? "",
                          attributeType: identity.attributeType ?? "",
                          searchAttributeValue: identity.searchAttributeValue ?? "",
                        };
                      };

                      function limitMapCharacters(elementInfoMap) {
                        elementInfoMap.forEach((value, key) => {
                          let modifiedValue = value;
                          window.allElementInfo.push(modifiedValue);
                        });
                      }

                      function extractVisibleTextFromHTML(element) {
                        if (!element) {
                          return { text: [], labels: [], titles: [] };
                        }

                        const result = {
                          text: new Set(),
                          labels: new Set(),
                          titles: new Set(),
                        };

                        // Utility function to check if an element is visible
                        const isVisible = (el) => {
                          const style = window.getComputedStyle(el);
                          return !(
                            style.display === "none" ||
                            style.visibility === "hidden" ||
                            el.hasAttribute("aria-hidden")
                          );
                        };

                        // Extract visible text content from an element
                        if (element.textContent?.trim() && isVisible(element)) {
                          result.text.add(element.textContent.trim());
                        }

                        // Extract text from labels (including associated input fields)
                        element.querySelectorAll("label").forEach((label) => {
                          if (isVisible(label) && label.textContent?.trim()) {
                            result.labels.add(label.textContent.trim());
                          }

                          // Handle labels associated with form elements
                          const forAttr = label.getAttribute("for");
                          if (forAttr) {
                            const inputElement = document.getElementById(forAttr);
                            if (inputElement && isVisible(inputElement)) {
                              const value = inputElement.value?.trim();
                              const placeholder = inputElement.placeholder?.trim();
                              if (value) result.text.add(value);
                              else if (placeholder) result.text.add(placeholder);
                            }
                          }
                        });

                        // Extract text from common inline and block elements
                        const visibleTextElements = [
                          "p",
                          "h1",
                          "h2",
                          "h3",
                          "h4",
                          "h5",
                          "h6",
                          "li",
                          "span",
                          "div",
                          "strong",
                          "em",
                          "b",
                          "i",
                          "blockquote",
                        ];
                        visibleTextElements.forEach((tag) => {
                          element.querySelectorAll(tag).forEach((child) => {
                            if (isVisible(child) && child.textContent?.trim()) {
                              result.text.add(child.textContent.trim());
                            }
                          });
                        });

                        // Extract visible link text
                        element.querySelectorAll("a").forEach((link) => {
                          if (isVisible(link) && link.textContent?.trim()) {
                            result.text.add(link.textContent.trim());
                          }
                        });

                        // Extract titles from iframes if accessible
                        element.querySelectorAll("iframe").forEach((iframe) => {
                          if (iframe.hasAttribute("title")) {
                            const title = iframe.getAttribute("title")?.trim();
                            if (title) result.titles.add(title);
                          }

                          try {
                            const iframeDoc =
                              iframe.contentDocument ||
                              new DOMParser().parseFromString(iframe.srcdoc || "", "text/html");
                            if (iframeDoc.body) {
                              const extractedText = extractVisibleTextFromHTML(iframeDoc.body);
                              extractedText.titles.forEach((title) => result.titles.add(title));
                              extractedText.text.forEach((text) => result.text.add(text));
                              extractedText.labels.forEach((label) => result.labels.add(label));
                            }
                          } catch (e) {
                            console.warn("Could not access iframe content", e);
                          }
                        });

                        // Return arrays instead of Sets
                        return {
                          text: Array.from(result.text),
                          labels: Array.from(result.labels),
                          titles: Array.from(result.titles),
                        };
                      }

                      // Add event listener for mouse movement to update coordinates
                      function showMartiniTooltip(event) {
                        const x = event.clientX; // X position
                        const y = event.clientY; // Y position

                        // Get the dimensions of the coordinatesElement
                        const elementWidth = coordinatesElement.offsetWidth;
                        const elementHeight = coordinatesElement.offsetHeight;

                        // Update the coordinates display
                        coordinatesElement.innerHTML = `X: ${x}&nbsp;&nbsp;&nbsp;&nbsp;Y: ${y}`;

                        // Update the position of coordinatesElement to follow the cursor
                        // Position the element such that the cursor is at the center of the element
                        coordinatesElement.style.left = `${x - elementWidth / 2}px`; // Center the element on the X axis
                        coordinatesElement.style.top = `${y - elementHeight / 2}px`; // Center the element on the Y axis

                        // Find the element directly under the mouse cursor
                        const elementBelowTooltip = document.elementFromPoint(x, y);

                        // Highlight the hovered element
                        if (lastHoveredElement !== elementBelowTooltip) {
                          // Remove highlight from the previous element if any
                          if (lastHoveredElement) {
                            lastHoveredElement.style.outline = ""; // Remove the previous highlight
                          }

                          // Add a border to highlight the current element
                          if (elementBelowTooltip && elementBelowTooltip !== coordinatesElement) {
                            elementBelowTooltip.style.outline = "3px solid red"; // Highlight the element
                          }

                          lastHoveredElement = elementBelowTooltip; // Update the last hovered element
                        }
                      }

                      function handleMartiniClick(event) {
                        event.preventDefault(); // Prevent the default click action
                        event.stopPropagation(); // Prevent the event from propagating upwards
                        coordinatesElement.style.display = "none";

                        // Get the coordinates of the click
                        const clickX = event.clientX;
                        const clickY = event.clientY;

                        // Get the element at the clicked position
                        const clickedElement = document.elementFromPoint(clickX, clickY);

                        coordinatesElement.style.display = "block";

                        // Highlight the clicked element (optional)
                        if (clickedElement) {
                          clickedElement.style.outline = "3px solid blue"; // Optionally highlight the element with a blue border
                        }

                        // If the element below the tooltip is an iframe
                        if (clickedElement && clickedElement.tagName.toLowerCase() === "iframe") {
                          // Get the document inside the iframe
                          var iframeDocument =
                            clickedElement.contentDocument || clickedElement.contentWindow.document;

                          // If the iframe document is valid
                          if (iframeDocument) {
                            // Initialize an array to store the iframe element information
                            window.allElementInfo = [];

                            const elementIdentity = getElementIdentity(clickedElement);

                            xPathIFrame = elementIdentity.xPath;

                            if (elementIdentity) {
                              window.elementInfoMap.set(
                                elementIdentity.xPath,
                                elementDTO("clicked-iFrame", elementIdentity)
                              );
                            }

                            // Get all elements inside the iframe and log their details
                            var iframeElements = iframeDocument.querySelectorAll("*");
                            iframeElements.forEach(function (elementInsideIframe) {
                              const elementIdentity = getElementIdentity(elementInsideIframe);
                              // console.log(
                              //   "elementIdentity.xPath",
                              //   `${xPathIFrame}${elementIdentity?.xPath}`
                              // );
                              if (elementIdentity) {
                                elementIdentity.iFrameXPath = xPathIFrame;
                                window.elementInfoMap.set(
                                  elementIdentity.xPath,
                                  elementDTO("iFrame-Child", elementIdentity)
                                );
                              }
                            });
                          }
                        } else {
                          var tagName = clickedElement.tagName.toLowerCase();

                          if (["html", "body", "main"].includes(tagName)) {
                            return; // Don't proceed if it's one of these elements
                          }

                          window.elementInfoMap.clear();

                          console.log("Clicked element:", clickedElement);

                          // Check if the clicked element has a shadow root
                          let shadowHost = clickedElement;

                          // Locate the shadow host element if it has a shadow root
                          while (shadowHost && !shadowHost.shadowRoot) {
                            shadowHost = shadowHost.parentElement; // Traverse upwards in the DOM
                          }

                          if (shadowHost && shadowHost.shadowRoot) {
                            // Access the Shadow DOM
                            const shadowRoot = shadowHost.shadowRoot;

                            // Find all clickable elements inside the Shadow DOM
                            let clickableElements = findClickableElements(shadowRoot);

                            // If clickable elements are found, perform your action (e.g., highlight them)
                            clickableElements.forEach((element) => {
                              pushElement(element, shadowHost, shadowRoot);
                            });
                          } else {
                            // Commom Elementes
                          }
                        }

                        sendingData();

                        // window.revertCloneInjections();

                        // Remove the tooltip from the page and delete the reference after 5 seconds
                        setTimeout(() => {
                          window.allElementInfo = [];
                          window.elementInfoMap.clear();
                        }, 1000);
                      }

                      function pushElement(element, shadowHost, shadowRoot) {
                        const elementIdentityTemp = getElementIdentity(element);

                        let shadowHostSelector = "";
                        let elementCssSelector = "";
                        let shadowPath = [];

                        function buildCssSelector(el) {
                          if (!el) return "";
                          let selector = el.tagName.toLowerCase();
                          if (el.id) selector += `#${el.id}`;
                          if (el.className) selector += `.${el.className.replace(/\\s+/g, ".")}`;
                          return selector;
                        }

                        // Traverse shadow hosts if nested shadow DOM exists
                        let currentHost = shadowHost;
                        while (currentHost) {
                          shadowPath.unshift(buildCssSelector(currentHost));
                          currentHost =
                            currentHost.parentNode instanceof ShadowRoot
                              ? currentHost.parentNode.host
                              : null;
                        }

                        if (shadowHost) {
                          shadowHostSelector = buildCssSelector(shadowHost);
                        }

                        if (element) {
                          elementCssSelector = buildCssSelector(element);
                        }

                        // Construct the natural CSS selector for nested Shadow DOM
                        let cssSelector = elementCssSelector;

                        // Build nested CSS selector, if shadowPath is not empty.
                        if (shadowPath.length > 0) {
                          cssSelector = shadowPath.reduceRight((acc, hostSelector) => {
                            return `${hostSelector} ${acc}`;
                          }, elementCssSelector);
                        }

                        const elementIdentity = {
                          ...elementIdentityTemp,
                          shadowHost: shadowHostSelector,
                          shadowRoot: shadowRoot ? true : false,
                          nestedShadow: shadowPath.length > 1, // Detects if multiple shadow roots are involved
                          cssSelector: elementCssSelector, // cssSelector shadowRoot
                        };

                        // Store tagName and other details in the Map
                        if (elementIdentity) {
                          if (!originalStyles.has(element)) {
                            // Store the original outline before changing it
                            originalStyles.set(element, element.style.outline);
                          }
                          element.style.outline = "3px solid red";

                          window.elementInfoMap.set(
                            elementIdentity.xPath,
                            elementDTO("clicked", elementIdentity)
                          );
                        }
                      }

                      // Function to find clickable elements (buttons, links, etc.)
                      function findClickableElements(root) {
                        const clickableSelectors = ["button", "a"]; // Add other clickable elements if needed
                        const clickableElements = [];
                        clickableSelectors.forEach((selector) => {
                          clickableElements.push(...root.querySelectorAll(selector));
                        });
                        return clickableElements;
                      }

                      connectWebSocket();

                      window.revertHoverPickInjections = function () {
                        document.removeEventListener("mousemove", showMartiniTooltip);
                        document.removeEventListener("click", handleMartiniClick);
                        console.log("revertHoverPickInjections");

                        // Remove the tooltip from the page and delete the reference after 5 seconds
                        setTimeout(() => {
                          removeElements();
                          restoreOriginalStyles();
                          window.allElementInfo = [];
                        }, 1000);
                      };

                      // Function to restore the original outline
                      function restoreOriginalStyles() {
                        originalStyles.forEach((originalStyle, element) => {
                          element.style.outline = originalStyle; // Restore original outline
                        });
                        originalStyles.clear(); // Clear the stored styles
                      }

                      function removeElements() {
                        // Remove highlight from the previous element if any
                        if (lastHoveredElement) {
                          lastHoveredElement.style.outline = ""; // Remove the previous highlight
                        }

                        if (coordinatesElement) {
                          coordinatesElement.remove(); // Completely remove the tooltip from the DOM
                          coordinatesElement = null; // Clear the reference to free memory
                          console.log("coordinatesElement completely removed.");
                        }
                      }

                      document.addEventListener("mousemove", showMartiniTooltip);
                      document.addEventListener("click", handleMartiniClick);

                      window.postMessage({ type: "myMessage", data: "some data" }, targetOriginURL);

                      window.addEventListener("message", function (event) {
                        if (event.origin !== trustedOriginURL) return; // check the origin
                        console.log(event.data);
                      });

                      // window.cloneTerms = null; // Invalidating the function
                    })(
                      arguments[0],
                      arguments[1],
                      arguments[2],
                      arguments[3],
                      arguments[4],
                      arguments[5]
                    );
                    // })("https://www.vpbank.com/", "https://www.vpbank.com/", ["*"], false, 8181, 3);

            """;
}

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
public class PerformPreLoad {

    private ARPriorities arPriorities;
    private ARWebDriver arWebDriver;
    private static JavascriptExecutor jsExecutor;

    // Static final variable to hold the singleton instance
    protected static final SingletonSupplier<PerformPreLoad> instance = () -> new PerformPreLoad();

    // Private constructor to prevent instantiation
    private PerformPreLoad() {
        // Initialize if necessary
    }

    public void initializePerformPreLoad(ARPriorities arPriorities, ARWebDriver arWebDriver) {
        this.arPriorities = arPriorities;
        this.arWebDriver = arWebDriver;
    }

    // Public method to access the singleton instance
    public static PerformPreLoad getInstance() {
        return instance.get();
    }

    public ErrorMessage dynamicLoadElementsDTO(
            WebDriver driver, String currentUrl, String[] dataArray, boolean searchHiddenFields, int port) {

        List<String> dataList = Arrays.asList(dataArray);
        try {
            jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript(jsCodeInject, dataList, searchHiddenFields, port);
            return null;
        } catch (Exception error) {
            return new ErrorMessage("Error running Scanner", "Dynamic Load ElementsDTO error", error.getMessage());
        }
    }

    public void dynamicLoadAlerts(
            WebDriver driver, String url, String[] dataArray, boolean searchHiddenFields, int port) {
        List<String> dataList = Arrays.asList(dataArray);

        try {
            jsExecutor = (JavascriptExecutor) driver;

            // Inject the JavaScript BEFORE navigating
            jsExecutor.executeScript(jsCodeMutationObserverAlerts, dataList, searchHiddenFields, port);

            //            // Navigate to the URL AFTER injection
            //            driver.get(url);
            //
            //            //Example of using the download state.
            //            jsExecutor.executeScript("window.startDownload();");
            //            // Example of updating the progress.
            //            for(int i = 0; i<=100; i+=10){
            //                jsExecutor.executeScript("window.updateDownloadProgress("+i+");");
            //                Thread.sleep(250); //simulate download time.
            //            }
            //            jsExecutor.executeScript("window.finishDownload();");

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    // JavaScript code to inject
    String jsCodeAlerts =
            """
                (function (searchTerms, hiddenFields, socketPort) {
                    // Create and style the alert
                    const alertDiv = document.createElement('div');
                    alertDiv.style.position = 'fixed';
                    alertDiv.style.top = '10px';
                    alertDiv.style.left = '10px';
                    alertDiv.style.backgroundColor = 'yellow';
                    alertDiv.style.padding = '10px';
                    alertDiv.style.border = '1px solid black';
                    alertDiv.style.zIndex = '9999'; // Ensure it's on top

                    // Add text to the alert
                    const alertText = document.createTextNode('Page loading... JavaScript injected.');
                    alertDiv.appendChild(alertText);

                    // Append the alert to the body
                    document.body.appendChild(alertDiv);

                    // Store a download state in window
                    window.downloadState = {
                        isDownloading: false,
                        progress: 0
                    };

                    // Function to update the alert with download progress
                    window.updateDownloadProgress = function(progress) {
                        window.downloadState.progress = progress;
                        alertText.textContent = 'Page loading... JavaScript injected. Download progress: ' + progress + '%';
                        console.log("Alert Text Content: " + alertText.textContent); // Console log
                    };

                    // Function to set the download state to downloading
                    window.startDownload = function() {
                        window.downloadState.isDownloading = true;
                        alertText.textContent = 'Page loading... JavaScript injected. Download started';
                        console.log("Alert Text Content: " + alertText.textContent); // Console log
                    };

                    // Function to set the download state to finished
                    window.finishDownload = function() {
                        window.downloadState.isDownloading = false;
                        alertText.textContent = 'Page loading... JavaScript injected. Download finished';
                        console.log("Alert Text Content: " + alertText.textContent); // Console log
                    };

                })(arguments, arguments, arguments);
                """;

    private String jsStorageCode =
            """
            (function (searchTerms, hiddenFields, socketPort) {
                function injectScript() {
                    // Create and style the alert
                    const alertDiv = document.createElement('div');
                    alertDiv.style.position = 'fixed';
                    alertDiv.style.top = '10px';
                    alertDiv.style.left = '10px';
                    alertDiv.style.backgroundColor = 'yellow';
                    alertDiv.style.padding = '10px';
                    alertDiv.style.border = '1px solid black';
                    alertDiv.style.zIndex = '9999';

                    const alertText = document.createTextNode('Page loading... JavaScript injected.');
                    alertDiv.appendChild(alertText);

                    document.body.appendChild(alertDiv);

                    window.downloadState = {
                        isDownloading: false,
                        progress: 0
                    };

                    window.updateDownloadProgress = function (progress) {
                        window.downloadState.progress = progress;
                        alertText.textContent = 'Page loading... JavaScript injected. Download progress: ' + progress + '%';
                        console.log("Alert Text Content: " + alertText.textContent);
                    };

                    window.startDownload = function () {
                        window.downloadState.isDownloading = true;
                        alertText.textContent = 'Page loading... JavaScript injected. Download started';
                        console.log("Alert Text Content: " + alertText.textContent);
                    };

                    window.finishDownload = function () {
                        window.downloadState.isDownloading = false;
                        alertText.textContent = 'Page loading... JavaScript injected. Download finished';
                        console.log("Alert Text Content: " + alertText.textContent);
                    };
                }

                // Check if the script has been stored in localStorage
                if (localStorage.getItem('injectedScript')) {
                    injectScript(); // Re-inject from localStorage
                } else {
                    // Initial injection and store in localStorage
                    injectScript();
                    localStorage.setItem('injectedScript', 'true'); // Store a flag
                }

                // Clear localStorage on unload to prevent persistent re-injection if needed.
                window.addEventListener('beforeunload', function() {
                    localStorage.removeItem('injectedScript');
                });

            })(arguments[0], arguments[1], arguments[2]);""";

    private String jsCodeMutationObserverAlerts =
            """
                    (function (searchTerms, hiddenFields, socketPort) {
                        // Your JavaScript code here (including alert and download state) ...

                        function injectScript() {
                            // Create and style the alert
                            const alertDiv = document.createElement('div');
                            alertDiv.style.position = 'fixed';
                            alertDiv.style.top = '10px';
                            alertDiv.style.left = '10px';
                            alertDiv.style.backgroundColor = 'yellow';
                            alertDiv.style.padding = '10px';
                            alertDiv.style.border = '1px solid black';
                            alertDiv.style.zIndex = '9999';

                            const alertText = document.createTextNode('Page loading... JavaScript injected.');
                            alertDiv.appendChild(alertText);

                            document.body.appendChild(alertDiv);

                            window.downloadState = {
                                isDownloading: false,
                                progress: 0
                            };

                            window.updateDownloadProgress = function (progress) {
                                window.downloadState.progress = progress;
                                alertText.textContent = 'Page loading... . Download progress: ' + progress + '%';
                                console.log("Alert Text Content: " + alertText.textContent);
                            };

                            window.startDownload = function () {
                                window.downloadState.isDownloading = true;
                                alertText.textContent = 'Page loading... . Download started';
                                console.log("Alert Text Content: " + alertText.textContent);
                            };

                            window.finishDownload = function () {
                                window.downloadState.isDownloading = false;
                                alertText.textContent = 'Page loading... . Download finished';
                                console.log("Alert Text Content: " + alertText.textContent);
                            };
                        }

                        injectScript(); // Initial injection

                        const observer = new MutationObserver(function (mutations) {
                            mutations.forEach(function (mutation) {
                                if (mutation.type === 'childList' && mutation.removedNodes.length > 0) {
                                    mutation.removedNodes.forEach(function (node) {
                                        if (node.nodeName === 'HTML') {
                                            injectScript(); // Re-inject on full page load
                                        }
                                    });
                                }
                            });
                        });

                        observer.observe(document.documentElement, { childList: true });

                    })(arguments[0], arguments[1], arguments[2]);
            """;

    private String jsCodeInit =
            """
            var elementInfoMap = new Map();
            var pageFullyLoaded = false;
            var hiddenFields = false;
            window.searchTerms = ["button", "input", "a", "div"];
            var allElementInfo = [];
            function init(eventName) {
              if (pageFullyLoaded) {
                console.log("Event Name", eventName);
                if (
                  [
                    "DOMContentLoaded",
                    "onreadystatechange",
                    "load",
                    "onload",
                    "Direct Execution",
                  ].includes(eventName) ||
                  ["complete", "interactive"].includes(document.readyState)
                ) {
                  startCollectingElements(window.searchTerms);
                }
              }
              pageFullyLoaded = true;
            }

            // Function to collect general elements based on search terms
            const collectElements = function collectElements(
              doc,
              searchTerms,
              collectionFound,
              elementInfoMap
            ) {
              // Collect elements from the current document using the provided search terms
              searchTerms.forEach((selector) => {
                collectionFound.push(...Array.from(doc.querySelectorAll(selector)));
              });

              // After collecting, process element identities for the parent document
              collectionFound.forEach((node) => {
                if (
                  ["html", "body", "main", "script", "meta", "head", "style"].includes(
                    node.tagName.toLowerCase()
                  )
                ) {
                  return;
                }

                const elementIdentity = getElementIdentity(node);
                if (elementIdentity) {
                  elementInfoMap.set(
                    elementIdentity.xpath,
                    `tagName-Found;${elementInfoString(node, elementIdentity)}`
                  );
                }
              });
            };

            function fetchAndParseIframeContent(iframe) {
              if (!iframe.src) return null;

              const xhr = new XMLHttpRequest();
              xhr.open("GET", iframe.src, false); // 'false' makes the request synchronous

              try {
                xhr.send();

                if (xhr.status !== 200) {
                  console.error("Error fetching the iframe content:", xhr.status);
                  return null;
                }

                const htmlContent = xhr.responseText;

                // Parse the HTML content
                const parser = new DOMParser();
                const parsedDocument = parser.parseFromString(htmlContent, "text/html");

                // Get all elements inside the parsed document
                const srcElements = parsedDocument.querySelectorAll("*");
                console.log(`srcElements Total: <${srcElements.length}>`);

                // srcElements.forEach((element) => {
                //   console.log(`Element: <${element.tagName}>`);
                //   console.log("Text Content:", element.textContent.trim());
                // });

                return srcElements; // Return the NodeList
              } catch (error) {
                console.error("Error fetching the iframe content:", error);
                return null;
              }
            }

            const iFrameDetails = function iFrameDetails(iframe, xPathIFrame, childSize) {
              const iframeDetails = `Elements inside iframe: ${childSize}`;

              console.log(
                `iFrame Found: ${
                  iframe.src || iframe.title || iframe.id || iframe.name || "No description"
                }; ${iframeDetails}`
              );
              // Store the iframe details in the elementInfoMap
              elementInfoMap.set(
                xPathIFrame,
                `xpath:${xPathIFrame};text:${
                  iframe.src || iframe.title || iframe.id || iframe.name || "No description"
                };${iframeDetails}`
              );
            };

            // Function to collect iframe elements recursively
            const collectIframeElements = function collectIframeElements(
              doc,
              collectionFound,
              elementInfoMap,
              isIframeChild = false
            ) {
              doc.querySelectorAll("iframe").forEach((iframe) => {
                try {
                  let iframeDocument =
                    iframe.contentDocument || iframe.contentWindow.document;

                  try {
                    console.log(
                      "Iframe origin:",
                      new URL(iframe.src, window.location.origin).origin
                    );
                    console.log("Parent origin:", window.location.origin);
                  } catch (e) {
                    console.warn("Cross-origin access denied for iframe:", iframe.src);
                  }

                  if (iframe) {
                    let iframeParsed = null;
                    let srcDocElements = null;

                    const xPathIFrame = getMartiniXPath(iframe); // Get the XPath of the iframe

                    const elementIdentity = getElementIdentity(iframe);
                    if (elementIdentity) {
                      elementInfoMap.set(
                        elementIdentity.xpath,
                        `iFrame-Found;${elementInfoString(iframe, elementIdentity)}`
                      );
                    }

                    const parser = new DOMParser();

                    if (iframe.srcdoc) {
                      iframeParsed = parser.parseFromString(iframe.srcdoc, "text/html");

                      // Select all elements inside the parsed document
                      srcDocElements = iframeParsed.querySelectorAll("*");
                    }

                    if (iframe.src) {
                      const srcElements = fetchAndParseIframeContent(iframe);
                      if (srcElements) {
                        console.log("Fetched Elements:", srcElements);

                        iFrameDetails(iframe, xPathIFrame, srcElements.length);

                        srcElements.forEach(function (element) {
                          const elementIdentity = getElementIdentity(element);
                          // console.log(
                          //   "elementIdentity.xpath",
                          //   `${xPathIFrame}${elementIdentity?.xpath}`
                          // );
                          if (elementIdentity) {
                            elementInfoMap.set(
                              `${xPathIFrame}${elementIdentity?.xpath}`,
                              `iFrame-Child;${elementInfoString(element, elementIdentity)}`
                            );
                          }
                        });
                      }
                    }

                    // Collect all elements inside the iframe
                    if (!iframe.src) {
                      iFrameDetails(
                        iframe,
                        xPathIFrame,
                        srcDocElements
                          ? srcDocElements.length
                          : iframeDocument
                          ? iframeDocument.querySelectorAll("*").length
                          : 0
                      );
                    }

                    iframeDocument
                      .querySelectorAll("*")
                      .forEach(function (elementInsideIframe) {
                        const elementIdentity = getElementIdentity(elementInsideIframe);

                        // console.log(
                        //   "elementIdentity.xpath",
                        //   `${xPathIFrame}${elementIdentity?.xpath}`
                        // );
                        if (elementIdentity) {
                          elementInfoMap.set(
                            `${xPathIFrame}${elementIdentity?.xpath}`,
                            `iFrame-Child;${elementInfoString(
                              elementInsideIframe,
                              elementIdentity
                            )}`
                          );
                        }
                      });

                    // Loop through all the elements and extract their properties
                    srcDocElements?.forEach(function (element) {
                      const elementType = element.tagName; // Get the tag name of the element
                      const elementContent = element.textContent.trim(); // Get the text content of the element

                      const elementIdentity = getElementIdentity(element);
                      // console.log(
                      //   "elementIdentity.xpath",
                      //   `${xPathIFrame}${elementIdentity?.xpath}`
                      // );
                      if (elementIdentity) {
                        elementInfoMap.set(
                          `${xPathIFrame}${elementIdentity?.xpath}`,
                          `iFrame-Child;${elementInfoString(element, elementIdentity)}`
                        );
                      }
                    });

                    // Process iframe content depending on the presence of srcdoc
                    if (iframeParsed) {
                      processIframeElements(iframeParsed, xPathIFrame);
                    }

                    // If the iframe contains nested iframes, recursively collect them
                    collectIframeElements(
                      iframeDocument,
                      collectionFound,
                      elementInfoMap,
                      true
                    );
                  } else {
                    console.warn(`Skipping cross-origin iframe: ${iframe.src}`);
                  }
                } catch (e) {
                  console.error(
                    `Error accessing iframe: ${iframe.src || "Unknown iframe"}`,
                    e
                  );
                }
              });
            };

            const processIframeElements = function (iframeDocument, xPathIFrame) {
              // Collect all elements inside the iframe
              iframeDocument.querySelectorAll("*").forEach(function (elementInsideIframe) {
                const elementIdentity = getElementIdentity(elementInsideIframe);

                // console.log(
                //   "elementIdentity.xpath",
                //   `${xPathIFrame}${elementIdentity?.xpath}`
                // );
                if (elementIdentity) {
                  elementInfoMap.set(
                    `${xPathIFrame}${elementIdentity?.xpath}`,
                    `iFrame-Child;${elementInfoString(
                      elementInsideIframe,
                      elementIdentity
                    )}`
                  );
                }
              });
            };

            // Function to initialize the collection process
            const startCollectingElements = function startCollectingElements(searchTerms) {
              // const searchTerms = ["button", "input", "a", "div"]; // Define elements to search for
              let elementInfoMap = new Map(); // Initialize the map to store element information
              let collectionFound = [];

              console.log("searchTerms", window.searchTerms);
              // First, collect iframe elements
              collectIframeElements(document, collectionFound, elementInfoMap);

              // Then, collect general elements based on search terms
              collectElements(document, searchTerms, collectionFound, elementInfoMap);

              limitMapCharacters(elementInfoMap);
              console.log("All element info stored in Map:", allElementInfo);

              return elementInfoMap;
            };

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
              const xpath = getMartiniXPath(element);
              const allAttributes = Array.from(element.attributes)
                .map((attr) => `${attr.name}="${attr.value}"`)
                .join(";");
              const attribId = element.id || "";
              const attribName = element.name || "";
              const coords = `${element.getBoundingClientRect().left.toFixed(2)},${element
                .getBoundingClientRect()
                .top.toFixed(2)}`;
              const someText =
                element.textContent.trim() ||
                (element.tagName.toLowerCase() === "input" ? element.value || "" : "");

              return {
                xpath,
                allAttributes,
                customXPath: "",
                attribId,
                attribName,
                coords,
                someText,
              };
            };

            // Helper function to generate a unique XPath for an element
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

            // Helper function to generate element information string
            const elementInfoString = function elementInfoString(element, identity) {
              return `${element.tagName.toLowerCase()};xpath:${identity.xpath};text:${
                identity.someText
              };attribId:${identity.attribId};attribName:${identity.attribName};coords:${
                identity.coords
              };allAttributes:${identity.allAttributes};customXPath:${
                identity.customXPath
              };`;
            };

            function limitMapCharacters(elementInfoMap, coordText) {
              elementInfoMap.forEach((value, key) => {
                let modifiedValue = value;
                allElementInfo.push(modifiedValue);
              });
            }

            // Event listener to handle incoming messages from iframes
            window.addEventListener("message", function (event) {
              if (event.origin !== window.trustedOriginURL) {
                return; // Ignore messages from untrusted origins
              }

              console.log("Received message data:", event.data);

              if (event.data.type === "elementsData") {
                const elementData = event.data.data; // Process received element data
                console.log("Element data from parent:", elementData);
              }
            });

            function checkEdgeTrackingPrevention() {
              if (navigator.userAgent.includes("Edg")) {
                console.log(
                  "Edge Tracking Prevention may be blocking iframes. Go to Edge Settings → Privacy, Search, and Services → Set Tracking Prevention to 'Basic' and refresh the page."
                );
              }
            }

            checkEdgeTrackingPrevention();

            // MOVE EVENT LISTENERS OUTSIDE
            if (
              document.readyState === "complete" ||
              document.readyState === "interactive"
            ) {
              setTimeout(() => init("Direct Execution"), 0);
            } else {
              document.addEventListener("DOMContentLoaded", () =>
                setTimeout(() => init("DOMContentLoaded"), 0)
              );
              window.addEventListener("load", () => init("load"));
              document.attachEvent?.("onreadystatechange", function () {
                if (document.readyState === "complete")
                  setTimeout(() => init("onreadystatechange"), 0);
              });
              window.attachEvent?.("onload", () => init("onload"));
            }

            startCollectingElements(window.searchTerms);
            // init("Initiate");
            // })(arguments[0], arguments[1]);
            // })(["div"], false);
            """;

    private String jsCodeSocket =
            """
        (function (searchTerms, hiddenFields, socketPort) {
          let attempts = 0;
          wSocket = null;
          window.searchTerms = [];
          var pageFullyLoaded = false;
          var elementInfoMap = new Map();
          // var elementInfoSubmit = new Map();
          window.allElementInfo = [];

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
                startCollectingElements(searchTerms);
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
                  connectWebSocket(); // Retry connection
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

          function init(eventName) {
            if (pageFullyLoaded) {
              console.log("Event Name", eventName);
              if (
                [
                  "DOMContentLoaded",
                  "onreadystatechange",
                  "load",
                  "onload",
                  "Direct Execution",
                ].includes(eventName) ||
                ["complete", "interactive"].includes(document.readyState)
              ) {
                console.log("searchTerms", window.searchTerms);
                connectWebSocket();
                // startCollectingElements(window.searchTerms);
              }
            }
            pageFullyLoaded = true;
          }

          // Function to collect general elements based on search terms
          const collectElements = function collectElements(
            doc,
            searchTerms,
            collectionFound,
            elementInfoMap
          ) {
            // Collect elements from the current document using the provided search terms
            searchTerms.forEach((selector) => {
              collectionFound.push(...Array.from(doc.querySelectorAll(selector)));
            });

            // After collecting, process element identities for the parent document
            collectionFound.forEach((element) => {
              if (
                ["html", "body", "main", "script", "meta", "head", "style"].includes(
                  element.tagName.toLowerCase()
                )
              ) {
                return;
              }

              const elementIdentity = getElementIdentity(element);
              if (elementIdentity) {
                elementInfoMap.set(
                  elementIdentity.xpath,
                  elementDTO("tagName-Found", element, elementIdentity)
                );
              }
            });
          };

          function fetchAndParseIframeContent(iframe) {
            if (!iframe.src) return null;

            const xhr = new XMLHttpRequest();
            xhr.open("GET", iframe.src, false); // 'false' makes the request synchronous

            try {
              xhr.send();

              if (xhr.status !== 200) {
                console.error("Error fetching the iframe content:", xhr.status);
                return null;
              }

              const htmlContent = xhr.responseText;

              // Parse the HTML content
              const parser = new DOMParser();
              const parsedDocument = parser.parseFromString(htmlContent, "text/html");

              // Get all elements inside the parsed document
              const srcElements = parsedDocument.querySelectorAll("*");
              console.log(`srcElements Total: <${srcElements.length}>`);

              // srcElements.forEach((element) => {
              //   console.log(`Element: <${element.tagName}>`);
              //   console.log("Text Content:", element.textContent.trim());
              // });

              return srcElements; // Return the NodeList
            } catch (error) {
              console.error("Error fetching the iframe content:", error);
              return null;
            }
          }

          const iFrameDetails = function iFrameDetails(iframe, xPathIFrame, childSize) {
            const iframeDetails = `Elements inside iframe: ${childSize}`;

            console.log(
              `iFrame Found: ${
                iframe.src ||
                iframe.title ||
                iframe.id ||
                iframe.name ||
                "No description"
              }; ${iframeDetails}`
            );
            // // Store the iframe details in the elementInfoMap
            // elementInfoMap.set(
            //   xPathIFrame,
            //   `xpath:${xPathIFrame};text:${
            //     iframe.src ||
            //     iframe.title ||
            //     iframe.id ||
            //     iframe.name ||
            //     "No description"
            //   };${iframeDetails}`
            // );
          };

          // Function to collect iframe elements recursively
          const collectIframeElements = function collectIframeElements(
            doc,
            collectionFound,
            elementInfoMap,
            isIframeChild = false
          ) {
            doc.querySelectorAll("iframe").forEach((iframe) => {
              try {
                let iframeDocument =
                  iframe.contentDocument || iframe.contentWindow.document;

                try {
                  console.log(
                    "Iframe origin:",
                    new URL(iframe.src, window.location.origin).origin
                  );
                  console.log("Parent origin:", window.location.origin);
                } catch (e) {
                  console.warn("Cross-origin access denied for iframe:", iframe.src);
                }

                if (iframe) {
                  let iframeParsed = null;
                  let srcDocElements = null;

                  const xPathIFrame = getMartiniXPath(iframe); // Get the XPath of the iframe

                  const elementIdentity = getElementIdentity(iframe);
                  if (elementIdentity) {
                    elementInfoMap.set(
                      elementIdentity.xpath,
                      elementDTO("iFrame-Found", iframe, elementIdentity)
                    );
                  }

                  const parser = new DOMParser();

                  if (iframe.srcdoc) {
                    iframeParsed = parser.parseFromString(iframe.srcdoc, "text/html");

                    // Select all elements inside the parsed document
                    srcDocElements = iframeParsed.querySelectorAll("*");
                  }

                  if (iframe.src) {
                    const srcElements = fetchAndParseIframeContent(iframe);
                    if (srcElements) {
                      // console.log("Fetched Elements:", srcElements);

                      iFrameDetails(iframe, xPathIFrame, srcElements.length);

                      srcElements.forEach(function (element) {
                        const elementIdentity = getElementIdentity(element);
                        // console.log(
                        //   "elementIdentity.xpath",
                        //   `${xPathIFrame}${elementIdentity?.xpath}`
                        // );
                        if (elementIdentity) {
                          elementInfoMap.set(
                            `${xPathIFrame}${elementIdentity?.xpath}`,
                            elementDTO("iFrame-Child", element, elementIdentity)
                          );
                        }
                      });
                    }
                  }

                  // Collect all elements inside the iframe
                  if (!iframe.src) {
                    iFrameDetails(
                      iframe,
                      xPathIFrame,
                      srcDocElements
                        ? srcDocElements.length
                        : iframeDocument
                        ? iframeDocument.querySelectorAll("*").length
                        : 0
                    );
                  }

                  iframeDocument
                    .querySelectorAll("*")
                    .forEach(function (elementInsideIframe) {
                      const elementIdentity = getElementIdentity(elementInsideIframe);

                      // console.log(
                      //   "elementIdentity.xpath",
                      //   `${xPathIFrame}${elementIdentity?.xpath}`
                      // );
                      if (elementIdentity) {
                        elementInfoMap.set(
                          `${xPathIFrame}${elementIdentity?.xpath}`,
                          elementDTO(
                            "iFrame-Child",
                            elementInsideIframe,
                            elementIdentity
                          )
                        );
                      }
                    });

                  // Loop through all the elements and extract their properties
                  srcDocElements?.forEach(function (element) {
                    const elementIdentity = getElementIdentity(element);
                    // console.log(
                    //   "elementIdentity.xpath",
                    //   `${xPathIFrame}${elementIdentity?.xpath}`
                    // );
                    if (elementIdentity) {
                      elementInfoMap.set(
                        `${xPathIFrame}${elementIdentity?.xpath}`,
                        elementDTO("iFrame-Child", element, elementIdentity)
                      );
                    }
                  });

                  // Process iframe content depending on the presence of srcdoc
                  if (iframeParsed) {
                    processIframeElements(iframeParsed, xPathIFrame);
                  }

                  // If the iframe contains nested iframes, recursively collect them
                  collectIframeElements(
                    iframeDocument,
                    collectionFound,
                    elementInfoMap,
                    true
                  );
                } else {
                  console.warn(`Skipping cross-origin iframe: ${iframe.src}`);
                }
              } catch (e) {
                console.error(
                  `Error accessing iframe: ${iframe.src || "Unknown iframe"}`,
                  e
                );
              }
            });
          };

          const processIframeElements = function (iframeDocument, xPathIFrame) {
            // Collect all elements inside the iframe
            iframeDocument
              .querySelectorAll("*")
              .forEach(function (elementInsideIframe) {
                const elementIdentity = getElementIdentity(elementInsideIframe);

                // console.log(
                //   "elementIdentity.xpath",
                //   `${xPathIFrame}${elementIdentity?.xpath}`
                // );
                if (elementIdentity) {
                  elementInfoMap.set(
                    `${xPathIFrame}${elementIdentity?.xpath}`,
                    elementDTO("iFrame-Child", elementInsideIframe, elementIdentity)
                  );
                }
              });
          };

          // Function to initialize the collection process
          const startCollectingElements = function startCollectingElements(
            searchTerms
          ) {
            // const searchTerms = ["button", "input", "a", "div"]; // Define elements to search for
            let elementInfoMap = new Map(); // Initialize the map to store element information
            let collectionFound = [];

            // First, collect iframe elements
            collectIframeElements(document, collectionFound, elementInfoMap);

            // Then, collect general elements based on search terms
            collectElements(document, searchTerms, collectionFound, elementInfoMap);

            window.allElementInfo = [];
            limitMapCharacters(elementInfoMap);
            console.log("All element info stored in Map:", allElementInfo);
            elementInfoMap.clear();

            console.log("WebSocket readyState:", wSocket.readyState);

            if (wSocket && wSocket.readyState === WebSocket.OPEN) {
              const message = {
                type: "SEARCH_TOOL",
                details: allElementInfo, // Send allElementInfo
              };
              wSocket.send(JSON.stringify(message));
              console.log("Sent SEARCH_TOOL:", message);
            } else {
              console.warn("WebSocket is not open. Cannot send message.");
            }
          };

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
            const xpath = getMartiniXPath(element);
            const attributeData = Array.from(element.attributes).map((attr) => ({
              name: attr.name,
              value: attr.value,
            }));
            const attribId = element.id || "";
            const attribName = element.name || "";
            const coords = `${element.getBoundingClientRect().left.toFixed(2)},${element
              .getBoundingClientRect()
              .top.toFixed(2)}`;
            const someText =
              element.textContent.trim() ||
              (element.tagName.toLowerCase() === "input" ? element.value || "" : "");

            return {
              xpath,
              attributeData,
              customXPath: "",
              attribId,
              attribName,
              coords,
              someText,
            };
          };

          // Helper function to generate a unique XPath for an element
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

          // Helper function to generate element information string
          const elementInfoString = function elementInfoString(element, identity) {
            return `${element.tagName.toLowerCase()};xpath:${identity.xpath};text:${
              identity.someText
            };attribId:${identity.attribId};attribName:${identity.attribName};coords:${
              identity.coords
            };attributeData:${identity.attributeData};customXPath:${
              identity.customXPath
            };`;
          };

          const elementDTO = function elementDTO(typeElement, element, identity) {
            return {
              typeElement: typeElement,
              tagName: element.tagName.toLowerCase(),
              xPath: identity.xPath ?? "",
              text: identity.text ?? "",
              attribId: identity.attribId ?? "",
              attribName: identity.attribName ?? "",
              coords: identity.coords ?? "",
              attributeData: identity.attributeData ?? "",
              customXPath: identity.customXPath ?? "",
              iFrameXPath: identity.iFrameXPath ?? "",
              attributeValue: identity.attributeValue ?? "",
              attributeType: identity.attributeType ?? "",
              searchAttributeValue: identity.searchAttributeValue ?? "",
            };
          };

          function limitMapCharacters(elementInfoMap, coordText) {
            elementInfoMap.forEach((value, key) => {
              let modifiedValue = value;
              window.allElementInfo.push(modifiedValue);
            });
          }

          // Event listener to handle incoming messages from iframes
          window.addEventListener("message", function (event) {
            if (event.origin !== window.trustedOriginURL) {
              return; // Ignore messages from untrusted origins
            }

            console.log("Received message data:", event.data);

            if (event.data.type === "elementsData") {
              const elementData = event.data.data; // Process received element data
              console.log("Element data from parent:", elementData);
            }
          });

          function checkEdgeTrackingPrevention() {
            if (navigator.userAgent.includes("Edg")) {
              console.log(
                "Edge Tracking Prevention may be blocking iframes. Go to Edge Settings → Privacy, Search, and Services → Set Tracking Prevention to 'Basic' and refresh the page."
              );
            }
          }

          checkEdgeTrackingPrevention();

          // MOVE EVENT LISTENERS OUTSIDE
          if (
            document.readyState === "complete" ||
            document.readyState === "interactive"
          ) {
            setTimeout(() => init("Direct Execution"), 0);
          } else {
            document.addEventListener("DOMContentLoaded", () =>
              setTimeout(() => init("DOMContentLoaded"), 0)
            );
            window.addEventListener("load", () => init("load"));
            document.attachEvent?.("onreadystatechange", function () {
              if (document.readyState === "complete")
                setTimeout(() => init("onreadystatechange"), 0);
            });
            window.attachEvent?.("onload", () => init("onload"));
          }

          // connectWebSocket();
          // startCollectingElements(searchTerms);
          // init("Initiate");
        })(arguments[0], arguments[1], arguments[2]);
        // })(["div"], true, 8181);

        """;

    private String jsCodeInject =
            """
            (function (searchTerms, hiddenFields, socketPort) {
              var wSocket = null;
              var socketPort = socketPort;
              var elementInfoMap = new Map();
              var pageFullyLoaded = false;
              var hiddenFields = false;
              window.searchTerms = ["button", "input", "a", "div"];
              var allElementInfo = [];

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
                    startCollectingElements(searchTerms);
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
                      connectWebSocket(); // Retry connection
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

              function init(eventName) {
                if (pageFullyLoaded) {
                  console.log("Event Name", eventName);
                  if (
                    [
                      "DOMContentLoaded",
                      "onreadystatechange",
                      "load",
                      "onload",
                      "Direct Execution",
                    ].includes(eventName) ||
                    ["complete", "interactive"].includes(document.readyState)
                  ) {
                    startCollectingElements(window.searchTerms);
                  }
                }
                pageFullyLoaded = true;
              }

              // Function to collect general elements based on search terms
              const collectElements = function collectElements(
                doc,
                searchTerms,
                collectionFound,
                elementInfoMap
              ) {
                // Collect elements from the current document using the provided search terms
                searchTerms.forEach((selector) => {
                  collectionFound.push(...Array.from(doc.querySelectorAll(selector)));
                });

                // After collecting, process element identities for the parent document
                collectionFound.forEach((element) => {
                  if (
                    ["html", "body", "main", "script", "meta", "head", "style"].includes(
                      element.tagName.toLowerCase()
                    )
                  ) {
                    return;
                  }

                  const elementIdentity = getElementIdentity(element);
                  if (elementIdentity) {
                    elementInfoMap.set(
                      elementIdentity.xpath,
                      elementDTO("tagName-Found", element, elementIdentity)
                    );
                  }
                });
              };

              function fetchAndParseIframeContent(iframe) {
                if (!iframe.src) return null;

                const xhr = new XMLHttpRequest();
                xhr.open("GET", iframe.src, false); // 'false' makes the request synchronous

                try {
                  xhr.send();

                  if (xhr.status !== 200) {
                    console.error("Error fetching the iframe content:", xhr.status);
                    return null;
                  }

                  const htmlContent = xhr.responseText;

                  // Parse the HTML content
                  const parser = new DOMParser();
                  const parsedDocument = parser.parseFromString(htmlContent, "text/html");

                  // Get all elements inside the parsed document
                  const srcElements = parsedDocument.querySelectorAll("*");
                  console.log(`srcElements Total: <${srcElements.length}>`);

                  // srcElements.forEach((element) => {
                  //   console.log(`Element: <${element.tagName}>`);
                  //   console.log("Text Content:", element.textContent.trim());
                  // });

                  return srcElements; // Return the NodeList
                } catch (error) {
                  console.error("Error fetching the iframe content:", error);
                  return null;
                }
              }

              const iFrameDetails = function iFrameDetails(iframe, xPathIFrame, childSize) {
                const iframeDetails = `Elements inside iframe: ${childSize}`;

                console.log(
                  `iFrame Found: ${
                    iframe.src ||
                    iframe.title ||
                    iframe.id ||
                    iframe.name ||
                    "No description"
                  }; ${iframeDetails}`
                );
                // // Store the iframe details in the elementInfoMap
                // elementInfoMap.set(
                //   xPathIFrame,
                //   `xpath:${xPathIFrame};text:${
                //     iframe.src ||
                //     iframe.title ||
                //     iframe.id ||
                //     iframe.name ||
                //     "No description"
                //   };${iframeDetails}`
                // );
              };

              // Function to collect iframe elements recursively
              const collectIframeElements = function collectIframeElements(
                doc,
                collectionFound,
                elementInfoMap,
                isIframeChild = false
              ) {
                doc.querySelectorAll("iframe").forEach((iframe) => {
                  try {
                    let iframeDocument =
                      iframe.contentDocument || iframe.contentWindow.document;

                    try {
                      console.log(
                        "Iframe origin:",
                        new URL(iframe.src, window.location.origin).origin
                      );
                      console.log("Parent origin:", window.location.origin);
                    } catch (e) {
                      console.warn("Cross-origin access denied for iframe:", iframe.src);
                    }

                    if (iframe) {
                      let iframeParsed = null;
                      let srcDocElements = null;

                      const xPathIFrame = getMartiniXPath(iframe); // Get the XPath of the iframe

                      const elementIdentity = getElementIdentity(iframe);
                      if (elementIdentity) {
                        elementInfoMap.set(
                          elementIdentity.xpath,
                          elementDTO("iFrame-Found", iframe, elementIdentity)
                        );
                      }

                      const parser = new DOMParser();

                      if (iframe.srcdoc) {
                        iframeParsed = parser.parseFromString(iframe.srcdoc, "text/html");

                        // Select all elements inside the parsed document
                        srcDocElements = iframeParsed.querySelectorAll("*");
                      }

                      if (iframe.src) {
                        const srcElements = fetchAndParseIframeContent(iframe);
                        if (srcElements) {
                          // console.log("Fetched Elements:", srcElements);

                          iFrameDetails(iframe, xPathIFrame, srcElements.length);

                          srcElements.forEach(function (element) {
                            const elementIdentity = getElementIdentity(element);
                            // console.log(
                            //   "elementIdentity.xpath",
                            //   `${xPathIFrame}${elementIdentity?.xpath}`
                            // );
                            if (elementIdentity) {
                              elementInfoMap.set(
                                `${xPathIFrame}${elementIdentity?.xpath}`,
                                elementDTO("iFrame-Child", element, elementIdentity)
                              );
                            }
                          });
                        }
                      }

                      // Collect all elements inside the iframe
                      if (!iframe.src) {
                        iFrameDetails(
                          iframe,
                          xPathIFrame,
                          srcDocElements
                            ? srcDocElements.length
                            : iframeDocument
                            ? iframeDocument.querySelectorAll("*").length
                            : 0
                        );
                      }

                      iframeDocument
                        .querySelectorAll("*")
                        .forEach(function (elementInsideIframe) {
                          const elementIdentity = getElementIdentity(elementInsideIframe);

                          // console.log(
                          //   "elementIdentity.xpath",
                          //   `${xPathIFrame}${elementIdentity?.xpath}`
                          // );
                          if (elementIdentity) {
                            elementInfoMap.set(
                              `${xPathIFrame}${elementIdentity?.xpath}`,
                              elementDTO(
                                "iFrame-Child",
                                elementInsideIframe,
                                elementIdentity
                              )
                            );
                          }
                        });

                      // Loop through all the elements and extract their properties
                      srcDocElements?.forEach(function (element) {
                        const elementIdentity = getElementIdentity(element);
                        // console.log(
                        //   "elementIdentity.xpath",
                        //   `${xPathIFrame}${elementIdentity?.xpath}`
                        // );
                        if (elementIdentity) {
                          elementInfoMap.set(
                            `${xPathIFrame}${elementIdentity?.xpath}`,
                            elementDTO("iFrame-Child", element, elementIdentity)
                          );
                        }
                      });

                      // Process iframe content depending on the presence of srcdoc
                      if (iframeParsed) {
                        processIframeElements(iframeParsed, xPathIFrame);
                      }

                      // If the iframe contains nested iframes, recursively collect them
                      collectIframeElements(
                        iframeDocument,
                        collectionFound,
                        elementInfoMap,
                        true
                      );
                    } else {
                      console.warn(`Skipping cross-origin iframe: ${iframe.src}`);
                    }
                  } catch (e) {
                    console.error(
                      `Error accessing iframe: ${iframe.src || "Unknown iframe"}`,
                      e
                    );
                  }
                });
              };

              const processIframeElements = function (iframeDocument, xPathIFrame) {
                // Collect all elements inside the iframe
                iframeDocument
                  .querySelectorAll("*")
                  .forEach(function (elementInsideIframe) {
                    const elementIdentity = getElementIdentity(elementInsideIframe);

                    // console.log(
                    //   "elementIdentity.xpath",
                    //   `${xPathIFrame}${elementIdentity?.xpath}`
                    // );
                    if (elementIdentity) {
                      elementInfoMap.set(
                        `${xPathIFrame}${elementIdentity?.xpath}`,
                        elementDTO("iFrame-Child", elementInsideIframe, elementIdentity)
                      );
                    }
                  });
              };

              // Function to initialize the collection process
              const startCollectingElements = function startCollectingElements(
                searchTerms
              ) {
                // const searchTerms = ["button", "input", "a", "div"]; // Define elements to search for
                let elementInfoMap = new Map(); // Initialize the map to store element information
                let collectionFound = [];

                // First, collect iframe elements
                collectIframeElements(document, collectionFound, elementInfoMap);

                // Then, collect general elements based on search terms
                collectElements(document, searchTerms, collectionFound, elementInfoMap);

                window.allElementInfo = [];
                limitMapCharacters(elementInfoMap);
                console.log("All element info stored in Map:", window.allElementInfo);
                elementInfoMap.clear();

                if (wSocket && wSocket.readyState) {
                  console.log("WebSocket readyState:", wSocket.readyState);
                }

                if (
                  wSocket &&
                  wSocket.readyState === WebSocket.OPEN &&
                  window.allElementInfo.length > 0
                ) {
                  const message = {
                    type: "SEARCH_TOOL",
                    details: window.allElementInfo, // Send allElementInfo
                  };
                  wSocket.send(JSON.stringify(message));
                  console.log("Sent SEARCH_TOOL:", message);
                } else {
                  console.warn("WebSocket is not open. Cannot send message.");
                }
              };

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
                const xpath = getMartiniXPath(element);
                const attributeData = Array.from(element.attributes).map((attr) => ({
                  name: attr.name,
                  value: attr.value,
                }));
                const attribId = element.id || "";
                const attribName = element.name || "";
                const coords = `${element.getBoundingClientRect().left.toFixed(2)},${element
                  .getBoundingClientRect()
                  .top.toFixed(2)}`;
                const someText =
                  element.textContent.trim() ||
                  (element.tagName.toLowerCase() === "input" ? element.value || "" : "");

                return {
                  xpath,
                  attributeData,
                  customXPath: "",
                  attribId,
                  attribName,
                  coords,
                  someText,
                };
              };

              // Helper function to generate a unique XPath for an element
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

              // Helper function to generate element information string
              const elementInfoString = function elementInfoString(element, identity) {
                return `${element.tagName.toLowerCase()};xpath:${identity.xpath};text:${
                  identity.someText
                };attribId:${identity.attribId};attribName:${identity.attribName};coords:${
                  identity.coords
                };attributeData:${identity.attributeData};customXPath:${
                  identity.customXPath
                };`;
              };

              const elementDTO = function elementDTO(typeElement, element, identity) {
                return {
                  typeElement: typeElement,
                  tagName: element.tagName.toLowerCase(),
                  xPath: identity.xPath ?? "",
                  text: identity.text ?? "",
                  attribId: identity.attribId ?? "",
                  attribName: identity.attribName ?? "",
                  coords: identity.coords ?? "",
                  attributeData: identity.attributeData ?? "",
                  customXPath: identity.customXPath ?? "",
                  iFrameXPath: identity.iFrameXPath ?? "",
                  attributeValue: identity.attributeValue ?? "",
                  attributeType: identity.attributeType ?? "",
                  searchAttributeValue: identity.searchAttributeValue ?? "",
                };
              };

              function limitMapCharacters(elementInfoMap, coordText) {
                elementInfoMap.forEach((value, key) => {
                  let modifiedValue = value;
                  window.allElementInfo.push(modifiedValue);
                });
              }

              // Event listener to handle incoming messages from iframes
              window.addEventListener("message", function (event) {
                if (event.origin !== window.trustedOriginURL) {
                  return; // Ignore messages from untrusted origins
                }

                console.log("Received message data:", event.data);

                if (event.data.type === "elementsData") {
                  const elementData = event.data.data; // Process received element data
                  console.log("Element data from parent:", elementData);
                }
              });

              function checkEdgeTrackingPrevention() {
                if (navigator.userAgent.includes("Edg")) {
                  console.log(
                    "Edge Tracking Prevention may be blocking iframes. Go to Edge Settings → Privacy, Search, and Services → Set Tracking Prevention to 'Basic' and refresh the page."
                  );
                }
              }

              checkEdgeTrackingPrevention();

              // MOVE EVENT LISTENERS OUTSIDE
              if (
                document.readyState === "complete" ||
                document.readyState === "interactive"
              ) {
                setTimeout(() => init("Direct Execution"), 0);
              } else {
                document.addEventListener("DOMContentLoaded", () =>
                  setTimeout(() => init("DOMContentLoaded"), 0)
                );
                window.addEventListener("load", () => init("load"));
                document.attachEvent?.("onreadystatechange", function () {
                  if (document.readyState === "complete")
                    setTimeout(() => init("onreadystatechange"), 0);
                });
                window.attachEvent?.("onload", () => init("onload"));
              }

              connectWebSocket();
              // startCollectingElements(window.searchTerms);
              // init("Initiate");
            })(arguments[0], arguments[1], arguments[2]);
            // })(["div"], false, 8181);
                        """;
}

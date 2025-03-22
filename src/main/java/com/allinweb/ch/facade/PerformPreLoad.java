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

    // "scannerTool", "scannerGrid", "searchTerms"
    public ErrorMessage dynamicLoadElementsDTO(
            WebDriver driver,
            String currentUrl,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId) {

        List<String> dataList = Arrays.asList(dataArray);
        try {
            jsExecutor = (JavascriptExecutor) driver;
            // "scannerTool", "scannerGrid", "searchTerms"
            jsExecutor.executeScript(
                    jsSearchInUse,
                    dataList,
                    searchHiddenFields,
                    port,
                    sessionId,
                    destination,
                    operationId,
                    homeBankingId);
            return null;
        } catch (Exception error) {
            return new ErrorMessage("Error running Scanner", "Dynamic Load ElementsDTO error", error.getMessage());
        }
    }

    public ErrorMessage dynamicInitElementsDTO(
            WebDriver driver, String currentUrl, String[] dataArray, boolean searchHiddenFields, int port) {

        List<String> dataList = Arrays.asList(dataArray);
        try {
            jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript(jsCodeInit, dataList, searchHiddenFields, port);
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
                    const alertText = document.createTextNode('Page loading... .');
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
                        alertText.textContent = 'Page loading... . Download progress: ' + progress + '%';
                        console.log("Alert Text Content: " + alertText.textContent); // Console log
                    };

                    // Function to set the download state to downloading
                    window.startDownload = function() {
                        window.downloadState.isDownloading = true;
                        alertText.textContent = 'Page loading... . Download started';
                        console.log("Alert Text Content: " + alertText.textContent); // Console log
                    };

                    // Function to set the download state to finished
                    window.finishDownload = function() {
                        window.downloadState.isDownloading = false;
                        alertText.textContent = 'Page loading... . Download finished';
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

                    const alertText = document.createTextNode('Page loading... .');
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

                            const alertText = document.createTextNode('Page loading... .');
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
      if (selector.includes("with id")) {
        foundElements = foundElements.filter((el) => el.hasAttribute("id"));
      } // If search term includes "with name", filter only elements that have a "name" attribute
      else if (selector.includes("with name")) {
        foundElements = foundElements.filter((el) => el.hasAttribute("name"));
      } else {
        collectionFound.push(...Array.from(doc.querySelectorAll(selector)));
      }
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
                    elementIdentity.xPath,
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
                        elementIdentity.xPath,
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
                          //   "elementIdentity.xPath",
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
                        //   "elementIdentity.xPath",
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
                      //   "elementIdentity.xPath",
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
                //   "elementIdentity.xPath",
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
              const attributeData = Array.from(element.attributes)
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

    private String jsSearchInUse =
            """
                    // SEARCH IN USE (SENDER: scannerTool) -> scannerGrid
                    (function (
                      searchTerms,
                      hiddenFields,
                      socketPort,
                      sessionId,
                      destination,
                      operationId,
                      homeBankingId
                    ) {
                      let attempts = 0;
                      let maxAttempts = 100;
                      let wSocket = null;
                      const originalStyles = new Map();
                      let pageFullyLoaded = false;
                      window.elementInfoMap = new Map();
                      // window.searchTerms = ["button", "input", "a", "select"];
                      window.searchTerms = searchTerms;
                      window.allElementInfo = [];
                      window.sessionId = sessionId;
                      window.destination = destination;
                      window.operationId = operationId;
                      window.homeBankingId = homeBankingId;
                      // var elementInfoSubmit = new Map();

                      function connectWebSocket() {
                        if (attempts >= maxAttempts) {
                          console.error("Reached maximum reconnection attempts. Stopping.");
                          return;
                        }

                        try {
                          console.log(`Attempt ${attempts + 1} to connect to WebSocket...`);
                          wSocket = new WebSocket(
                            `ws://localhost:${socketPort}/websocket?sessionId=${window.sessionId}`
                          );

                          wSocket.onopen = () => {
                            console.log(`WebSocket connected for session: ${window.sessionId}`);
                            attempts = 0; // Reset attempts on successful connection

                            try {
                              const subscriptionMessage = {
                                type: "echo",
                                sessionId: window.sessionId,
                                operationId: "test echo",
                                body: "subscribe",
                              };
                              wSocket.send(JSON.stringify(subscriptionMessage));
                            } catch (sendError) {
                              console.error("Failed to send subscription message:", sendError);
                            }

                            // Call startCollectingElements AFTER WebSocket is open
                            startCollectingElements(window.searchTerms);
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
                            // connectWebSocket(); // Retry connection
                          };

                          wSocket.onclose = () => {
                            console.log("WebSocket connection closed");

                            if (attempts < maxAttempts) {
                              attempts++;
                              console.log(`Reconnecting attempt ${attempts}...`);
                              connectWebSocket(); // Retry connection
                            } else {
                              console.log(
                                `${maxAttempts} Attempts to Reconnect with the WebSocket.`
                              );
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
                        collectionFound
                      ) {
                        // Collect elements from the current document using the provided search terms
                        if (searchTerms.length > 0) {
                          searchTerms.forEach((selector) => {
                            // If search term includes "with id", filter only elements that have an "id" attribute
                            if (selector.includes("with id")) {
                              collectionFound.push(...Array.from(doc.querySelectorAll("[id]")));
                            } // If search term includes "with id", filter only elements that have an "id" attribute
                            else if (selector.includes("with name")) {
                              foundElements = Array.from(doc.querySelectorAll("[name]"));
                              collectionFound.push(...Array.from(doc.querySelectorAll("[name]")));
                            } else {
                              collectionFound.push(...Array.from(doc.querySelectorAll(selector)));
                            }
                          });
                        } else {
                          // Collect all elements except iframes
                          collectionFound.push(
                            ...Array.from(doc.querySelectorAll("*")).filter(
                              (el) => el.tagName.toLowerCase() !== "iframe"
                            )
                          );
                        }

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
                            filterSearchTerms(
                              element,
                              "tagName-Found",
                              elementIdentity.xPath,
                              elementIdentity,
                              searchTerms
                            );
                          }
                        });
                      };

                      function filterSearchTerms(
                        element,
                        typeDTO,
                        referXPath,
                        elementIdentity,
                        searchTerms
                      ) {
                        if (
                          searchTerms.length === 0 ||
                          (!searchTerms.includes("with id") &&
                            !searchTerms.includes("with name") &&
                            !searchTerms.includes("with text"))
                        ) {
                          // Check if the clicked element has a shadow root
                          let shadowHost = element;

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
                              pushElement(
                                element,
                                elementIdentity,
                                referXPath,
                                typeDTO,
                                shadowHost,
                                shadowRoot
                              );
                            });
                          } else {
                            // If no search terms, directly add the element
                            pushElement(element, elementIdentity, referXPath, typeDTO, null, null);
                          }
                          // window.elementInfoMap.set(
                          //   referXPath,
                          //   elementDTO(typeDTO, elementIdentity)
                          // );
                          return;
                        }
                        // Iterate through search terms and apply corresponding checks
                        searchTerms.forEach((term) => {
                          let matches = false;

                          if (
                            term.includes("with id") &&
                            elementIdentity.attributeData.some((attr) => attr.name === "id")
                          ) {
                            matches = true;
                          } else if (
                            term.includes("with name") &&
                            elementIdentity.attributeData.some((attr) => attr.name === "name")
                          ) {
                            matches = true;
                          } else if (
                            term.includes("with text") &&
                            elementIdentity.someText.length > 0
                          ) {
                            matches = true;
                          }

                          // If a match is found, set the element in the map
                          if (matches) {
                            // Check if the clicked element has a shadow root
                            let shadowHost = element;

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
                                pushElement(
                                  element,
                                  elementIdentity,
                                  referXPath,
                                  typeDTO,
                                  shadowHost,
                                  shadowRoot
                                );
                              });
                            } else {
                              // If no search terms, directly add the element
                              pushElement(
                                element,
                                elementIdentity,
                                referXPath,
                                typeDTO,
                                null,
                                null
                              );
                            }
                            // window.elementInfoMap.set(
                            //   referXPath,
                            //   elementDTO(typeDTO, elementIdentity)
                            // );
                          }
                        });
                      }

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
                                filterSearchTerms(
                                  iframe,
                                  "iFrame-Found",
                                  elementIdentity.xPath,
                                  elementIdentity,
                                  searchTerms
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
                                    //   "elementIdentity.xPath",
                                    //   `${xPathIFrame}${elementIdentity?.xPath}`
                                    // );
                                    if (elementIdentity) {
                                      elementIdentity.iFrameXPath = xPathIFrame;
                                      filterSearchTerms(
                                        element,
                                        "iFrame-Child",
                                        `${xPathIFrame}${elementIdentity?.xPath}`,
                                        elementIdentity,
                                        searchTerms
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
                                  //   "elementIdentity.xPath",
                                  //   `${xPathIFrame}${elementIdentity?.xPath}`
                                  // );
                                  if (elementIdentity) {
                                    elementIdentity.iFrameXPath = xPathIFrame;
                                    filterSearchTerms(
                                      elementInsideIframe,
                                      "iFrame-Child",
                                      `${xPathIFrame}${elementIdentity?.xPath}`,
                                      elementIdentity,
                                      searchTerms
                                    );
                                  }
                                });

                              // Loop through all the elements and extract their properties
                              srcDocElements?.forEach(function (element) {
                                const elementIdentity = getElementIdentity(element);
                                // console.log(
                                //   "elementIdentity.xPath",
                                //   `${xPathIFrame}${elementIdentity?.xPath}`
                                // );
                                if (elementIdentity) {
                                  elementIdentity.iFrameXPath = xPathIFrame;
                                  filterSearchTerms(
                                    element,
                                    "iFrame-Child",
                                    `${xPathIFrame}${elementIdentity?.xPath}`,
                                    elementIdentity,
                                    searchTerms
                                  );
                                }
                              });

                              // Process iframe content depending on the presence of srcdoc
                              if (iframeParsed) {
                                processIframeElements(iframeParsed, xPathIFrame);
                              }

                              // If the iframe contains nested iframes, recursively collect them
                              collectIframeElements(iframeDocument, collectionFound, true);
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
                            //   "elementIdentity.xPath",
                            //   `${xPathIFrame}${elementIdentity?.xPath}`
                            // );
                            if (elementIdentity) {
                              elementIdentity.iFrameXPath = xPathIFrame;
                              filterSearchTerms(
                                elementInsideIframe,
                                "iFrame-Child",
                                `${xPathIFrame}${elementIdentity?.xPath}`,
                                elementIdentity,
                                searchTerms
                              );
                            }
                          });
                      };

                      // Function to initialize the collection process
                      const startCollectingElements = function startCollectingElements(
                        searchTerms
                      ) {
                        // const searchTerms = ["button", "input", "a", "div"]; // Define elements to search for
                        window.elementInfoMap = new Map(); // Initialize the map to store element information
                        let collectionFound = [];

                        // First, collect iframe elements
                        collectIframeElements(document, collectionFound, elementInfoMap);

                        // Then, collect general elements based on search terms
                        collectElements(document, searchTerms, collectionFound, elementInfoMap);

                        window.allElementInfo = [];
                        limitMapCharacters(window.elementInfoMap);
                        console.log("All element info stored in Map:", window.allElementInfo);
                        window.elementInfoMap.clear();

                        if (wSocket && wSocket.readyState) {
                          console.log("WebSocket readyState:", wSocket.readyState);
                        }

                        if (wSocket && wSocket.readyState === WebSocket.OPEN) {
                          const message = {
                            type: "SEARCH_TOOL",
                            sessionId: window.destination,
                            operationId: window.operationId,
                            homeBankingId: window.homeBankingId,
                            details: window.allElementInfo, // Send allElementInfo
                          };
                          wSocket.send(JSON.stringify(message));
                          console.log("Sent SEARCH_TOOL:", message);
                        }
                      };

                      function pushElement(
                        element,
                        elementIdentityTemp,
                        referXPath,
                        typeDTO,
                        shadowHost,
                        shadowRoot
                      ) {
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
                            referXPath, // Keep Distinction iFrameXPath / child / etc...
                            elementDTO(typeDTO, elementIdentity)
                          );
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

                        let tagName = element.tagName.toLowerCase();
                        const tagNameTemp = identifyElementTypeFromXPath(tagName, xPath);
                        if (tagNameTemp !== tagName) {
                          tagName = tagNameTemp;
                        }

                        const attributeData = Array.from(element.attributes).map((attr) => ({
                          name: attr.name,
                          value: attr.value,
                        }));
                        const attribId = element.id || "";
                        const attribName = element.name || "";
                        const coordinates = `${element
                          .getBoundingClientRect()
                          .left.toFixed(2)},${element.getBoundingClientRect().top.toFixed(2)}`;
                        const someText = getVisibleText(tagName, attributeData, element);

                        return {
                          xPath,
                          tagName,
                          attributeData,
                          customXPath: "",
                          attribId,
                          attribName,
                          coordinates,
                          someText,
                        };
                      };

                      // Function to check if an element is hidden (using computed styles and attributes)
                      const isHidden = (el) => {
                        const style = window.getComputedStyle(el);
                        return (
                          style.display === "none" ||
                          style.visibility === "hidden" ||
                          el.hasAttribute("aria-hidden")
                        );
                      };

                      function getVisibleText(tagName, attributeData, element) {
                        let textResult = "";

                        if (element && !isHidden(element)) {
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

                        // Define priority order for attributes
                        const attributePriority = [
                          "aria-label",
                          "aria-labelledby",
                          "aria-describedby",
                          "placeholder",
                          "label",
                          "name",
                          "title",
                          "alt",
                          "for",
                          "data-label",
                          "data-name",
                          "data-title",
                          "id",
                          "data-testid",
                        ];

                        let firstMeaningfulText = "";

                        // Function to get attribute text with priority
                        const getAttributeText = (name, value) => {
                          if (name === "aria-labelledby" || name === "aria-describedby") {
                            const referencedElement = document.getElementById(value);
                            if (referencedElement && !isHidden(referencedElement)) {
                              return referencedElement.textContent.trim();
                            }
                          }
                          return value.trim();
                        };

                        // Check element's text first
                        if (textResult && !/^\\..*\\{.*\\}$/.test(textResult)) {
                          firstMeaningfulText = textResult;
                        } else {
                          // Directly prioritize title before checking others
                          const titleAttr = attributeData.find(({ name }) => name === "title");
                          if (titleAttr) {
                            firstMeaningfulText = getAttributeText(titleAttr.name, titleAttr.value);
                          }

                          if (!firstMeaningfulText) {
                            for (const attr of attributePriority) {
                              const foundAttr = attributeData.find(({ name }) => name === attr);
                              if (foundAttr) {
                                firstMeaningfulText = getAttributeText(
                                  foundAttr.name,
                                  foundAttr.value
                                );
                                if (firstMeaningfulText) break; // Stop at first meaningful attribute
                              }
                            }
                          }
                        }

                        return firstMeaningfulText; // Return the most meaningful text
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

                        // Function to filter out technical patterns
                        const isTechnicalPattern = (word) => {
                          return word.includes("_") || word.includes("--") || word.includes("-");
                        };

                        // Extract visible text content from an element
                        if (element.textContent?.trim() && isVisible(element)) {
                          // Ignore text content that looks like CSS rules and words with technical patterns
                          const textContent = element.textContent.trim();
                          const words = textContent.split(/\\s+/);
                          const filteredWords = words.filter((word) => !isTechnicalPattern(word));
                          const filteredText = filteredWords.join(" ").trim();
                          if (filteredText) {
                            result.text.add(filteredText);
                          }
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
                              if (value) {
                                const words = value.split(/\\s+/);
                                const filteredWords = words.filter(
                                  (word) => !isTechnicalPattern(word)
                                );
                                const filteredText = filteredWords.join(" ").trim();
                                if (filteredText) result.text.add(filteredText);
                              } else if (placeholder) {
                                const words = placeholder.split(/\\s+/);
                                const filteredWords = words.filter(
                                  (word) => !isTechnicalPattern(word)
                                );
                                const filteredText = filteredWords.join(" ").trim();
                                if (filteredText) result.text.add(filteredText);
                              }
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
                              const textContent = child.textContent.trim();
                              const words = textContent.split(/\\s+/);
                              const filteredWords = words.filter(
                                (word) => !isTechnicalPattern(word)
                              );
                              const filteredText = filteredWords.join(" ").trim();
                              if (filteredText) {
                                result.text.add(filteredText);
                              }
                            }
                          });
                        });

                        // Extract visible link text
                        element.querySelectorAll("a").forEach((link) => {
                          if (isVisible(link) && link.textContent?.trim()) {
                            const textContent = link.textContent.trim();
                            const words = textContent.split(/\\s+/);
                            const filteredWords = words.filter((word) => !isTechnicalPattern(word));
                            const filteredText = filteredWords.join(" ").trim();
                            if (filteredText) {
                              result.text.add(filteredText);
                            }
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

                      function identifyElementTypeFromXPath(tagName, xpath) {
                        if (typeof xpath !== "string" || xpath.trim() === "") {
                          return "unknown";
                        }

                        const parts = xpath.split("/").filter((part) => part.trim() !== "");

                        for (let i = parts.length - 1; i >= 0; i--) {
                          const part = parts[i];

                          const tagMatch = part.match(/^([a-zA-Z-]+)(?:\\[\\d+\\])?/);
                          if (!tagMatch) continue;

                          const tag = tagMatch[1].toLowerCase();

                          if (tag === "a") {
                            return "a"; // Link
                          }

                          if (tag === "input") {
                            const typeMatch = part.match(/@type=["']?([^"'\\]]+)["']?/);
                            const type = typeMatch ? typeMatch[1].toLowerCase() : "";

                            if (["button", "submit", "reset"].includes(type)) {
                              return "button";
                            }
                            return "input";
                          }

                          if (tag === "button") {
                            return "button";
                          }

                          // Detect if it's an Angular Material expansion panel (likely a button)
                          if (
                            tag.includes("expansion-panel-header") ||
                            tag.includes("sidenav") ||
                            tag.includes("nav")
                          ) {
                            return "button";
                          }
                        }

                        return tagName; // Default to the given tagName if no match
                      }

                      const elementDTO = function elementDTO(typeElement, identity) {
                        return {
                          typeElement: typeElement,
                          tagName: identity.tagName ?? "No Tag Name Detected",
                          xPath: identity.xPath ?? "",
                          someText: identity.someText ?? "",
                          attribId: identity.attribId ?? "",
                          attribName: identity.attribName ?? "",
                          coordinates: identity.coordinates ?? "",
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

                      window.revertSearchInjections = function () {
                        // Remove the tooltip from the page and delete the reference after 5 seconds
                        setTimeout(() => {
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

                      // startCollectingElements(window.searchTerms);
                      // init("Initiate");
                      // window.initSearchTerms = null; // Invalidating the function
                    })(
                      arguments[0],
                      arguments[1],
                      arguments[2],
                      arguments[3],
                      arguments[4],
                      arguments[5],
                      arguments[6]
                    );

                    // })([], false, 8181, "scannerTool", "scannerGrid", "searchTerms", 3);
                    // })(["with name"], false, 8181, "scannerTool", "scannerGrid", "searchTerms", 3);
                    // })(
                    //   ["input", "button", "a", "select"],
                    //   false,
                    //   8181,
                    //   "scannerTool",
                    //   "scannerGrid-3",
                    //   "searchTerms",
                    //   3
                    // );
                    // })(["*"], false, 8181, "scannerTool", "scannerGrid", "searchTerms", 3);
                    // })(["button"], false, 8181, "scannerTool", "scannerGrid", "searchTerms", 3);
                    """;
}

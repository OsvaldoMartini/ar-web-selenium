(function initSearchTerms(searchTerms, hiddenFields, socketPort) {
  let attempts = 0;
  let maxAttempts = 100;
  let wSocket = null;
  let pageFullyLoaded = false;
  window.elementInfoMap = new Map();
  window.searchTerms = ["button", "input", "a", "select"];
  window.allElementInfo = [];
  // var elementInfoSubmit = new Map();

  function connectWebSocket() {
    if (attempts >= maxAttempts) {
      console.error("Reached maximum reconnection attempts. Stopping.");
      return;
    }

    try {
      console.log(`Attempt ${attempts + 1} to connect to WebSocket...`);
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

        if (receivedMessage.endsWith("\u0000")) {
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
          "tagName-Found",
          elementIdentity.xPath,
          elementIdentity,
          searchTerms
        );
      }
    });
  };

  function filterSearchTerms(
    typeDTO,
    referXPath,
    elementIdentity,
    searchTerms
  ) {
    if (
      searchTerms.length === 0 ||
      (!searchTerms.includes("with id") &&
        !searchTerms.includes("with name") &&
        !searchTerms.includes("allWithText"))
    ) {
      // If no search terms, directly add the element
      window.elementInfoMap.set(
        referXPath,
        elementDTO(typeDTO, elementIdentity)
      );
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
        term.includes("allWithText") &&
        elementIdentity.someText.length > 0
      ) {
        matches = true;
      }

      // If a match is found, set the element in the map
      if (matches) {
        window.elementInfoMap.set(
          referXPath,
          elementDTO(typeDTO, elementIdentity)
        );
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
        details: window.allElementInfo, // Send allElementInfo
      };
      wSocket.send(JSON.stringify(message));
      console.log("Sent SEARCH_TOOL:", message);
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
    const someText = getSomeText(tagName, attributeData, element);

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

  function getSomeText(tagName, attributeData, element) {
    let textSet = new Set();
    let textResult = "";

    if (["input", "textarea", "select", "button"].includes(tagName)) {
      const extractedText = extractTextFromHTML(element || "");
      textResult = [
        ...extractedText.titles,
        ...extractedText.text,
        ...extractedText.labels,
      ]
        .join("; ")
        .trim();
    } else if (["option", "label", "a"].includes(tagName)) {
      const extractedText = extractTextFromHTML(element || "");
      textResult = [
        ...extractedText.titles,
        ...extractedText.text,
        ...extractedText.labels,
      ]
        .join("; ")
        .trim();
    } else if (!["html", "body", "script"].includes(tagName)) {
      const extractedText = extractTextFromHTML(element || "");
      textResult = [
        ...extractedText.titles,
        ...extractedText.text,
        ...extractedText.labels,
      ]
        .join("; ")
        .trim();
    }

    // Now, extract text from attributes AFTER processing the element
    attributeData.forEach((attr) => {
      const trimmedValue = attr.value.trim();

      if (trimmedValue) {
        // Process only if value is not empty
        if (
          attr.name === "placeholder" ||
          attr.name === "label" ||
          attr.name === "name" ||
          attr.name === "title" ||
          attr.name === "id"
        ) {
          textSet.add(trimmedValue);
        }
      }
    });

    // Continue processing srcdoc separately
    attributeData.forEach((attr) => {
      if (attr.name === "srcdoc") {
        try {
          const doc = new DOMParser().parseFromString(attr.value, "text/html");
          const extractedText = extractTextFromHTML(doc.body);
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

    // Add the extracted text from the element to the set to avoid duplicates
    textResult
      .split(";")
      .map((text) => text.trim())
      .filter(Boolean)
      .forEach((text) => textSet.add(text));

    // Return a clean, unique, and deduplicated string
    return Array.from(textSet).join("; ");
  }

  function extractTextFromHTML(element) {
    // If element is invalid or empty, return an empty result
    if (!element || element === " ") {
      return {
        text: [],
        labels: [],
        titles: [],
      };
    }
    const result = {
      text: new Set(), // Using Set to avoid duplicate text
      labels: new Set(), // Using Set to avoid duplicate labels
      titles: new Set(), // Using Set to avoid duplicate titles
    };

    // Extract text content directly from the element (in case it has no children)
    if (element.textContent) {
      let elementText = element.textContent.trim();
      if (elementText) {
        result.text.add(elementText); // Using .add() instead of .push() for Set
      }
    }

    // Extract label text from input placeholders and other form-related data
    element.querySelectorAll("label").forEach((label) => {
      if (label.textContent) {
        let labelText = label.textContent.trim();
        if (labelText) {
          result.labels.add(labelText); // Using .add() for Set to ensure uniqueness
        }
      }

      // Handle associated input fields (if the label has a 'for' attribute)
      let forAttribute = label.getAttribute("for");
      if (forAttribute) {
        let associatedInput = element.querySelector(`#${forAttribute}`);
        if (associatedInput) {
          // Check if it's an input field or textarea and extract value or placeholder
          let inputValue = associatedInput.value?.trim();
          let inputPlaceholder = associatedInput.placeholder?.trim();
          if (inputValue) {
            result.text.add(inputValue); // Using .add() for Set to ensure uniqueness
          } else if (inputPlaceholder) {
            result.text.add(inputPlaceholder); // Fallback to placeholder
          }
        }
      }
    });

    // Extract text from common block and inline elements
    const textExtractors = [
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

    textExtractors.forEach((tagName) => {
      element.querySelectorAll(tagName).forEach((childElement) => {
        if (childElement.textContent) {
          let elemText = childElement.textContent.trim();
          if (elemText) {
            result.text.add(elemText); // Using .add() for Set to ensure uniqueness
          }
        }
      });
    });

    // Extract text from <a> tags (links)
    element.querySelectorAll("a").forEach((link) => {
      if (link.textContent) {
        let linkText = link.textContent.trim();
        if (linkText) {
          result.text.add(linkText); // Using .add() for Set to ensure uniqueness
        }
      }
    });

    // Extract iframe titles and nested content
    element.querySelectorAll("iframe").forEach((iframe) => {
      if (iframe.getAttribute("title")) {
        let title = iframe.getAttribute("title")?.trim();
        if (title) {
          result.titles.add(title); // Using .add() for Set to ensure uniqueness
        }
      }

      try {
        let iframeDoc =
          iframe.contentDocument ||
          new DOMParser().parseFromString(iframe.srcdoc || "", "text/html");
        let iframeContent = extractTextFromHTML(iframeDoc); // Here we assume iframeDoc is an element.
        iframeContent.titles.forEach((title) => result.titles.add(title));
        iframeContent.text.forEach((text) => result.text.add(text));
        iframeContent.labels.forEach((label) => result.labels.add(label));
      } catch (e) {
        console.warn("Could not access iframe content", e);
      }
    });

    // Convert Sets to arrays before returning to maintain previous structure
    return {
      text: Array.from(result.text),
      labels: Array.from(result.labels),
      titles: Array.from(result.titles),
    };
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
  // window.initSearchTerms = null; // Invalidating the function
})(arguments[0], arguments[1], arguments[2]);
// })([], false, 8181);
// })(["with name"], false, 8181);
// })(["input", "button", "a"], false, 8181);
// })(["*"], false, 8181);
// })(["button"], false, 8181);

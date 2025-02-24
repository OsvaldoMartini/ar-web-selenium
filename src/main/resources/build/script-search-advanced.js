window.onload = function () {
  console.log("Page fully loaded. Collecting elements...");
  startCollectingElements();
};

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

// Function to collect iframe elements recursively
const collectIframeElements = function collectIframeElements(
  doc,
  collectionFound,
  elementInfoMap,
  isIframeChild = false
) {
  // Iterate over iframes and search inside them recursively
  doc.querySelectorAll("iframe").forEach((iframe) => {
    try {
      const iframeDocument =
        iframe.contentDocument || iframe.contentWindow.document;

      if (iframeDocument) {
        const elementXPath = getMartiniXPath(iframe); // Get the XPath of the iframe
        const iframeDetails = `Elements inside iframe: ${
          iframeDocument.body
            ? iframeDocument.body.querySelectorAll("*").length
            : 0
        }`;

        console.log(
          `iFrame Found: ${
            iframe.src ||
            iframe.title ||
            iframe.id ||
            iframe.name ||
            "No description"
          }; ${iframeDetails}`
        );

        // Store the iframe details in the elementInfoMap
        elementInfoMap.set(
          elementXPath,
          `xpath:${elementXPath};text:${
            iframe.src ||
            iframe.title ||
            iframe.id ||
            iframe.name ||
            "No description"
          };${iframeDetails}`
        );

        const elementIdentity = getElementIdentity(iframe);
        if (elementIdentity) {
          elementInfoMap.set(
            elementIdentity.xpath,
            `iFrame-Found;${elementInfoString(iframe, elementIdentity)}`
          );
        }

        // Collect all elements inside the iframe
        iframeDocument
          .querySelectorAll("*")
          .forEach(function (elementInsideIframe) {
            const elementIdentity = getElementIdentity(elementInsideIframe);
            if (elementIdentity) {
              elementInfoMap.set(
                elementIdentity.xpath,
                `iFrame-Child;${elementInfoString(
                  elementInsideIframe,
                  elementIdentity
                )}`
              );
            }
          });

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

// Function to initialize the collection process
const startCollectingElements = function startCollectingElements() {
  const searchTerms = ["button", "input", "a", "div"]; // Define elements to search for
  let elementInfoMap = new Map(); // Initialize the map to store element information
  let collectionFound = [];

  // First, collect iframe elements
  collectIframeElements(document, collectionFound, elementInfoMap);

  // Then, collect general elements based on search terms
  collectElements(document, searchTerms, collectionFound, elementInfoMap);

  console.log("All element info stored in Map:", elementInfoMap);
  return elementInfoMap;
};

const martiniSearchTerm = function martiniSearchTerm(
  searchTerms,
  elementInfoMap
) {
  let collectionFound = [];

  // Collect elements from the current document using the provided search terms
  searchTerms.forEach((selector) => {
    collectionFound.push(...Array.from(document.querySelectorAll(selector)));
  });

  // Iterate over iframes and search inside them recursively
  collectIframeElements(document, searchTerms, collectionFound, elementInfoMap);

  console.log("All element info stored in Map:", elementInfoMap);
  return elementInfoMap;
};

const sendDataToIframe = function sendDataToIframe(
  iframe,
  collectionFound,
  elementInfoMap,
  isIframeChild
) {
  try {
    const iframeWindow = iframe.contentWindow; // Get iframe's window object

    // Create serializable data (exclude DOM elements)
    const serializableData = collectionFound.map((node) => {
      const { xpath, attribId, attribName, coords, someText, allAttributes } =
        getElementIdentity(node) || {}; // Fallback to empty object
      return { xpath, attribId, attribName, coords, someText, allAttributes };
    });

    const messageType = isIframeChild ? "iFrame-Child" : "iFrame-Found";

    iframeWindow.postMessage(
      {
        type: messageType, // Message type for iFrame parent or child
        data: serializableData, // Send serializable data
        elementInfoMap: Array.from(elementInfoMap.entries()), // Send map as array
      },
      window.trustedOriginURL
    ); // Send message to iframe with trusted origin
  } catch (error) {
    console.error("Error sending data to iframe:", error);
  }
};

// Helper function to extract element identity
const getElementIdentity = function getElementIdentity(element) {
  if (
    element.offsetWidth === 0 ||
    element.offsetHeight === 0 ||
    window.getComputedStyle(element).visibility === "hidden"
  ) {
    return null; // Skip hidden or non-visible elements
  }

  const xpath = getMartiniXPath(element);
  const allAttributes = Array.from(element.attributes)
    .map((attr) => `${attr.name}="${attr.value}"`)
    .join(";");
  const attribId = element.id || "";
  const attribName = element.name || "";
  const coords = `${element.getBoundingClientRect().left},${
    element.getBoundingClientRect().top
  }`;
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

// For testing, set trustedOriginURL locally
window.trustedOriginURL = "http://localhost:3000/";

(function (targetOriginURL, trustedOriginURL, searchTerms, hiddenFields) {
  var elementInfoMap = new Map();
  // var elementInfoSubmit = new Map();
  let elementsTagName = [];
  let elementsSelector = [];
  let allElementsPage = [];

  function handleSearchTermsMartini(searchTerms) {
    // Create a Map to store element info with XPath as the key
    var elementInfoMap = new Map();

    const foundTerm = searchTerms.find((term) => term.includes("allWithText"));

    if (foundTerm) {
      // Search All with Output texts

      // Collect elements based on search terms
      searchTerms.forEach((attribute) => {
        allElementsPage.push(...Array.from(document.querySelectorAll("*")));
      });

      allElementsPage.forEach((node) => {
        // Avoid processing main, body, and html tags
        if (
          ["html", "body", "main", "script", "meta", "head", "style"].includes(
            node.tagName.toLowerCase()
          )
        ) {
          return;
        }

        // Check if the element is an iframe
        if (node.tagName.toLowerCase() === "iframe") {
          try {
            // Access the iframe's contentDocument
            const iframeDocument =
              node.contentDocument || node.contentWindow.document;

            // If iframe's contentDocument is accessible, process its elements
            if (iframeDocument) {
              console.log(`Processing iframe: ${node.src}`);
              handleSearchTermsMartiniInIframe(
                iframeDocument,
                searchTerms,
                elementInfoMap
              );
            }
          } catch (e) {
            console.error("Error accessing iframe content:", e);
          }
          return;
        }

        const elementIdentity = getElementIdentity(node);
        if (elementIdentity) {
          // Only add if not null
          const {
            xpath,
            allAttributes,
            customXPath,
            attribId,
            attribName,
            coords,
            someText,
          } = elementIdentity;

          if (someText && someText.length > 0) {
            // Construct the element info string
            var elementInfoString = `${node.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;

            // highlightElementsSequentially(elementsToProcess);
            // Store the element information in the Map with XPath as the key
            if (!elementInfoMap.has(xpath)) {
              elementInfoMap.set(xpath, elementInfoString);
            }
          }
        }
      });
    } else {
      // Regular Search

      // Collect elements based on search terms
      searchTerms.forEach((attribute) => {
        elementsTagName.push(
          ...Array.from(document.getElementsByTagName(attribute))
        );
      });

      searchTerms.forEach((attribute) => {
        elementsSelector.push(
          ...Array.from(document.querySelectorAll("[" + attribute + "]"))
        );
      });

      elementsTagName.forEach((node) => {
        // Avoid processing main, body, and html tags
        if (
          ["html", "body", "main", "script", "meta", "head", "style"].includes(
            node.tagName.toLowerCase()
          )
        ) {
          return;
        }

        // Check if the element is an iframe
        if (node.tagName.toLowerCase() === "iframe") {
          try {
            // Access the iframe's contentDocument
            const iframeDocument =
              node.contentDocument || node.contentWindow.document;

            // If iframe's contentDocument is accessible, process its elements
            if (iframeDocument) {
              console.log(`Processing iframe: ${node.src}`);
              handleSearchTermsMartiniInIframe(
                iframeDocument,
                searchTerms,
                elementInfoMap
              );
            }
          } catch (e) {
            console.error("Error accessing iframe content:", e);
          }
          return;
        }

        const elementIdentity = getElementIdentity(node);
        if (elementIdentity) {
          // Only add if not null
          const {
            xpath,
            allAttributes,
            customXPath,
            attribId,
            attribName,
            coords,
            someText,
          } = elementIdentity;

          // Construct the element info string
          var elementInfoString = `${node.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;

          // highlightElementsSequentially(elementsToProcess);
          // Store the element information in the Map with XPath as the key
          if (!elementInfoMap.has(xpath)) {
            elementInfoMap.set(xpath, elementInfoString);
          }
        }
      });

      // Process each element in the main document
      elementsSelector.forEach((node) => {
        // Avoid processing main, body, and html tags
        if (
          ["html", "body", "main", "script", "meta", "head", "style"].includes(
            node.tagName.toLowerCase()
          )
        ) {
          return;
        }

        // Check if the element is an iframe
        if (node.tagName.toLowerCase() === "iframe") {
          try {
            // Access the iframe's contentDocument
            const iframeDocument =
              node.contentDocument || node.contentWindow.document;

            // If iframe's contentDocument is accessible, process its elements
            if (iframeDocument) {
              console.log(`Processing iframe: ${node.src}`);
              handleSearchTermsMartiniInIframe(
                iframeDocument,
                searchTerms,
                elementInfoMap
              );
            }
          } catch (e) {
            console.error("Error accessing iframe content:", e);
          }
          return;
        }

        const elementIdentity = getElementIdentity(node);
        if (elementIdentity) {
          // Only add if not null
          const {
            xpath,
            allAttributes,
            customXPath,
            attribId,
            attribName,
            coords,
            someText,
          } = elementIdentity;

          // Construct the element info string
          var elementInfoString = `${node.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;

          // highlightElementsSequentially(elementsToProcess);
          // Store the element information in the Map with XPath as the key
          if (!elementInfoMap.has(xpath)) {
            elementInfoMap.set(xpath, elementInfoString);
          }
        }
      });
    }

    limitMapCharacters(elementInfoMap, "tagName-found");

    // if (elementInfoSubmit && elementInfoSubmit.length > 0) {
    //   limitMapCharacters(elementInfoSubmit, "submit-found");
    // }
    // window.allElementInfo = elementInfoMap; // Save to global for further use
    // Optionally, log the entire Map of element information
    console.log("All element info stored in Map:", window.allElementInfo);
    return window.allElementInfo;
  }

  // Helper function to handle elements inside an iframe
  function handleSearchTermsMartiniInIframe(
    iframeDocument,
    searchTerms,
    elementInfoMap
  ) {
    let iframeElementsToProcess = [];

    // Collect elements inside the iframe based on search terms
    searchTerms.forEach((attribute) => {
      iframeElementsToProcess.push(
        ...Array.from(iframeDocument.querySelectorAll("[" + attribute + "]"))
      );
    });

    // Process each element inside the iframe
    iframeElementsToProcess.forEach((element) => {
      // Avoid processing main, body, and html tags
      if (["html", "body", "main"].includes(element.tagName.toLowerCase())) {
        return;
      }

      const elementIdentity = getElementIdentity(node);
      if (elementIdentity) {
        // Only add if not null
        const {
          xpath,
          allAttributes,
          customXPath,
          attribId,
          attribName,
          coords,
          someText,
        } = elementIdentity;

        let elementInfoString = `found:${element.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;

        if (!elementInfoMap.has(xpath)) {
          elementInfoMap.set(xpath, elementInfoString);
        }
      }
    });
  }

  function highlightElementsSequentially(elements) {
    let previousElement = null; // Variable to store the previously highlighted element
    let index = 0;

    // Function to change background color to red
    function changeBackgroundColor() {
      if (index >= elements.length) {
        // Stop if we've reached the end of the elements
        return;
      }

      // Get the current element
      const currentElement = elements[index];

      // Log current element
      console.log("Highlighting element:", currentElement);

      // If there is a previously highlighted element, reset its background color
      if (previousElement) {
        previousElement.style.backgroundColor = ""; // Reset background color
      }

      // Change the background color of the current element
      currentElement.style.backgroundColor = "red";

      // Update the previousElement to the current element
      previousElement = currentElement;

      // Increment the index to move to the next element
      index++;

      // Call the function again after a short delay (1000ms for 1 second)
      setTimeout(changeBackgroundColor, 1000); // Adjust delay as needed
    }

    // Start the background color change
    changeBackgroundColor();
  }

  // Call the function to highlight elements sequentially
  // highlightElementsSequentially();

  function getElementLocators(element) {
    const locators = [];

    if (element === document.body) {
      locators.push("/html/" + element.tagName.toLowerCase());
      return locators;
    }

    const tagName = element.tagName.toLowerCase();
    const id = element.id ? `#${element.id}` : "";
    const className = (
      typeof element.className === "string" ? element.className : ""
    )
      .split(" ")
      .filter((cls) => !/\d/.test(cls))
      .join(".");

    if (id) {
      locators.push(id);
    }

    if (className) {
      locators.push(`//${tagName}[contains(@class, '${className}')]`);
    }

    // Check for other attributes (e.g., 'data-*' attributes)
    const attributes = Array.from(element.attributes);
    attributes.forEach((attr) => {
      if (attr.name !== "class" && attr.name !== "id") {
        // Exclude class and id
        locators.push(`${tagName}[@${attr.name}="${attr.value}"]`);
      }
    });

    // Handle iframe elements
    if (element.ownerDocument !== document) {
      try {
        const iframe = element.ownerDocument.defaultView.frameElement;
        const iframeLocators = getElementLocators(iframe);
        iframeLocators.forEach((iframePath) => {
          locators.push(`${iframePath}//${tagName}`);
        });
      } catch (error) {
        console.error("Error getting locators for iframe element:", error);
      }
    } else {
      // Handle regular elements
      let ix = 0;
      const siblings = element.parentNode.childNodes;

      for (let i = 0; i < siblings.length; i++) {
        const sibling = siblings[i];

        if (sibling === element) {
          const parentLocators = getElementLocators(element.parentNode);
          parentLocators.forEach((parentPath) => {
            locators.push(`${parentPath}/${tagName}[${ix + 1}]`);
          });
          break;
        }

        if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {
          ix++;
        }
      }
    }

    return locators;
  }
  function getMartiniXPath(element) {
    if (element === document.body) {
      return "/html/body";
    }
    var ix = 0;
    var siblings = element.parentNode ? element.parentNode.childNodes : [];
    for (var i = 0; i < siblings.length; i++) {
      var sibling = siblings[i];
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
  }
  function getElementAttributes(element) {
    const attributes = [];

    try {
      for (const attr of element.attributes) {
        attributes.push(`${attr.name}="${attr.value}"`);
      }
    } catch (error) {
      // If accessing attributes directly fails (likely due to cross-origin restrictions)
      // Attempt to get attributes using JavaScript execution within the iframe's context
      const iframe = element.ownerDocument.defaultView.frameElement;
      if (iframe) {
        const iframeWindow = iframe.contentWindow;
        iframeWindow.document.addEventListener("DOMContentLoaded", () => {
          const iframeElement = iframeWindow.document.querySelector(
            `#${element.id}`
          ); // Adjust selector as needed
          if (iframeElement) {
            for (const attr of iframeElement.attributes) {
              attributes.push(`${attr.name}="${attr.value}"`);
            }
          }
        });
      }
    }

    return attributes;
  }

  function getElementIdentity(element) {
    // Allow <input type="hidden"> but exclude all other hidden elements
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

    var xPath = getMartiniXPath(element);
    var allAttributes = "";
    try {
      // console.log("element", element);
      allAttributes = getElementAttributes(element);
    } catch (error) {}
    var customXPath = "";
    try {
      customXPath = getElementLocators(element);
    } catch (error) {}

    var attribId = element.id || "";
    var attribName = element.name || "";
    var coords = element.getBoundingClientRect();
    coords = `${coords.left},${coords.top}`;

    var someText = element.textContent.trim() || "";
    if (
      element.tagName.toLowerCase() === "input" ||
      element.tagName.toLowerCase() === "textarea"
    ) {
      someText = element.value || "";
    }

    var someText = getSomeText(element.tagName.toLowerCase(), element);

    // // If element is an input with type submit OR a button with type submit
    // if (
    //   (element.tagName.toLowerCase() === "input" &&
    //     element.type === "submit") ||
    //   (element.tagName.toLowerCase() === "button" &&
    //     (element.type === "submit" || !element.type)) // Default button type is "submit" if not set
    // ) {
    //   var elementInfoString = `${element.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;

    //   // Add to global Map without repetition
    //   if (!elementInfoSubmit.has(xpath)) {
    //     elementInfoSubmit.set(xpath, elementInfoString);
    //   }
    // }

    return {
      xpath,
      allAttributes,
      customXPath,
      attribId,
      attribName,
      coords,
      someText,
    };
  }

  function getSomeText(tagName, element) {
    let someText = "";

    if (["input", "textarea", "select", "button"].includes(tagName)) {
      const extractedText = extractTextFromHTML(element || "");
      someText = [
        ...extractedText.titles,
        ...extractedText.text,
        ...extractedText.labels,
      ]
        .join("; ")
        .trim();
    } else if (["option", "label", "a"].includes(tagName)) {
      const extractedText = extractTextFromHTML(element || "");
      someText = [
        ...extractedText.titles,
        ...extractedText.text,
        ...extractedText.labels,
      ]
        .join("; ")
        .trim();
    } else if (!["html", "body", "script"].includes(tagName)) {
      const extractedText = extractTextFromHTML(element || "");
      someText = [
        ...extractedText.titles,
        ...extractedText.text,
        ...extractedText.labels,
      ]
        .join("; ")
        .trim();
    }

    someText = someText
      .split(";")
      .map((text) => text.trim())
      .filter(Boolean)
      .join(";"); // Clean up sequential text

    return someText;
  }

  function extractTextFromHTML(element) {
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

  function limitMapCharacters(elementInfoMap, coordText) {
    elementInfoMap.forEach((value, key) => {
      let modifiedValue = value;
      // Push the formatted value and key to the array
      window.allElementInfo.push(`${coordText}:${modifiedValue}`);
    });
  }

  function cleanOldValues() {
    window.allElementInfo = [];
  }

  cleanOldValues();

  window.revertSearchjections = function () {
    // alert("revertPickInjections");
    console.log("revertSearchjections");
    elementInfoMap.clear();
    allElementInfo = [];
    elementsTagName = [];
    elementsSelector = [];
    allElementsPage = [];

    setTimeout(() => {
      window.allElementInfo = [];
    }, 1000);
  };

  // window.postMessage({ type: "myMessage", data: "some data" }, targetOriginURL);
  window.addEventListener("message", function (event) {
    if (event.origin !== trustedOriginURL) return; // check the origin
    console.log(event.data);
  });

  function highlightElementsSequentially() {
    // Get all elements on the page
    const elements = document.querySelectorAll("*"); // This selects all elements
    let previousElement = null; // Variable to store the previously highlighted element

    let index = 0;

    // Function to change background color to red
    function changeBackgroundColor() {
      if (index >= elements.length) {
        // Stop if we've reached the end of the elements
        return;
      }

      // Get the current element
      const currentElement = elements[index];

      // If there is a previously highlighted element, reset its background color
      if (previousElement) {
        previousElement.style.backgroundColor = ""; // Reset background color
      }

      // Change the background color of the current element
      currentElement.style.backgroundColor = "#B0E0E6";
      // #E0FFFF → Light Cyan
      // #AFEEEE → Pale Turquoise
      // #B0E0E6 → Powder Blue

      // Update the previousElement to the current element
      previousElement = currentElement;

      // Increment the index to move to the next element
      index++;

      // Call the function again after a short delay (1000ms for 1 second)
      setTimeout(changeBackgroundColor, 1000); // Adjust delay as needed
    }

    // Start the background color change
    changeBackgroundColor();
  }

  // Call the function to highlight elements sequentially
  // highlightElementsSequentially();

  // Example usage:
  // handleSearchTermsMartini(["data-test"]);

  // document.addEventListener("DOMContentLoaded", () => {
  //   searchTerms.forEach((attribute) => {
  //     console.log("attribute", attribute);
  //     elementsTagName.push(
  //       ...Array.from(document.getElementsByTagName(attribute))
  //     );
  //   });
  //   console.log(elementsTagName); // Check if inputs are found
  // });

  handleSearchTermsMartini(searchTerms);
  // handleSearchTermsMartini(["allWithText"]);
})(arguments[0], arguments[1], arguments[2], arguments[3]);
// })("http://localhost:3000/", "http://localhost:3000/", [
//   "allWithText",
//   "div",
//   "id",
//   "name",
//   "input",
// ],
// true);

(function (targetOriginURL, trustedOriginURL) {
  var tooltip = document.createElement("div");
  tooltip.id = "Martini-Is-Awesome";
  tooltip.style.position = "absolute";
  // tooltip.style.backgroundColor = "rgba(255, 165, 0, 0.5)"; // Slightly opaque light orange
  tooltip.style.backgroundColor = "rgba(0, 0, 0, 0)"; // Full transparency
  tooltip.style.border = "1px solid #ccc";
  tooltip.style.padding = "10px";
  tooltip.style.borderRadius = "5px";
  tooltip.style.boxShadow = "0 2px 4px rgba(0, 0, 0, 0.2)";
  tooltip.style.fontFamily = "Arial, sans-serif";
  tooltip.style.fontSize = "14px";
  tooltip.style.color = "#333";
  tooltip.style.zIndex = "10000"; // Higher z-index
  tooltip.style.display = "none";
  document.body.appendChild(tooltip);

  var elementInfoMap = new Map();
  var allElementInfo = [];

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
  function getMartiniCustomXPath(element) {
    if (element === document.body) {
      return "/html/" + element.tagName.toLowerCase();
    }

    // Ensure className is a string; otherwise, set it as an empty string
    var className = (
      typeof element.className === "string" ? element.className : ""
    )
      .split(" ")
      .filter(function (cls) {
        return !/\d/.test(cls);
      })
      .join(".");

    var tagName = element.tagName.toLowerCase();
    var ix = 0;
    var siblings = element.parentNode.childNodes;

    for (var i = 0; i < siblings.length; i++) {
      var sibling = siblings[i];

      if (sibling === element) {
        var path = getMartiniCustomXPath(element.parentNode) + "/" + tagName;

        if (className) {
          path += '[contains(@class, "' + className + '")]';
        } else {
          path += "[" + (ix + 1) + "]";
        }
        return path;
      }

      if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {
        ix++;
      }
    }

    return "";
  }

  var lastHoveredIsIframe = null; // Keep track of the last hovered element type

  let lastHoveredElement = null; // Keep track of the previously hovered element
  // Declare global variables to store iframe details
  var iframeDocument = null;
  var iframeElementsCount = 0;

  function showMartiniTooltip(event) {
    var elementBelowTooltip = document.elementFromPoint(
      event.clientX,
      event.clientY
    );

    // Do nothing if the hovered element is the tooltip itself or an excluded tag (html, body, main)
    if (
      !elementBelowTooltip ||
      elementBelowTooltip === tooltip ||
      ["html", "body", "main"].includes(
        elementBelowTooltip.tagName.toLowerCase()
      )
    ) {
      return;
    }

    var isIframe = elementBelowTooltip.tagName.toLowerCase() === "iframe";

    // Reset only if switching between iframe and non-iframe elements
    if (lastHoveredIsIframe !== isIframe) {
      console.clear();
      elementInfoMap.clear();
      allElementInfo = [];
    }

    lastHoveredIsIframe = isIframe; // Update last hovered element type

    // Get the tag name of the element
    var tagNameTemp = elementBelowTooltip.tagName.toLowerCase();

    // // Get the text content of the element (if it has text)
    // var someText = elementBelowTooltip.textContent.trim();
    // if (someText === "") {
    //   someText = "No text content";
    // }

    var someText = getSomeText(
      elementBelowTooltip.tagName.toLowerCase(),
      elementBelowTooltip
    );

    // If it's an iframe, get the number of elements inside the iframe
    var iframeDetails = "";
    if (isIframe) {
      iframeDocument =
        elementBelowTooltip.contentDocument ||
        elementBelowTooltip.contentWindow.document;
      iframeElementsCount = iframeDocument
        ? iframeDocument.body.getElementsByTagName("*").length
        : 0;
      iframeDetails = `Elements inside iframe: ${iframeElementsCount}`;
    }

    var elementXPath = getMartiniXPath(elementBelowTooltip);

    // Store tagName and other details in the Map
    if (iframeDetails && iframeDetails.length > 0) {
      elementInfoMap.set(
        elementXPath,
        `xpath:${elementXPath};text:${someText};${iframeDetails};`
      );
    } else {
      const {
        xpath,
        allAttributes,
        customXPath,
        attribId,
        attribName,
        coords,
        someText,
      } = getElementIdentity(elementBelowTooltip);

      var elementInfoString = `${elementBelowTooltip.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;

      elementInfoMap.set(xpath, elementInfoString);
    }

    // Parse the someText using the semicolon delimiter
    var parsedText = someText.split(";");

    // Format the tooltip content to make it more readable
    var tooltipContent = "";
    tooltipContent += isIframe ? "[Iframe] <br>" : "";
    tooltipContent += `Tag Name: ${tagNameTemp}<br>`;
    tooltipContent += isIframe ? `- ${iframeDetails}<br>` : "";

    // Replace new lines with <br> before adding each item from parsedText
    tooltipContent += someText
      ? parsedText.map((item) => `- ${item}<br>`).join("")
      : "No Text<br>";

    // Set the tooltip content with line breaks
    tooltip.innerHTML = tagNameTemp;

    // Position the tooltip near the mouse cursor
    var tooltipWidth = tooltip.offsetWidth;
    var tooltipHeight = tooltip.offsetHeight;
    var left = event.pageX - tooltipWidth / 2;
    var top = event.pageY - tooltipHeight / 2;

    tooltip.style.left = left + "px";
    tooltip.style.top = top + "px";
    tooltip.style.display = "block";

    // Highlight the hovered element
    if (lastHoveredElement !== elementBelowTooltip) {
      // Remove highlight from the previous element if any
      if (lastHoveredElement) {
        lastHoveredElement.style.outline = ""; // Remove the previous highlight
      }
      // Add a border to highlight the current element
      elementBelowTooltip.style.outline = "3px solid red"; // Highlight the element

      lastHoveredElement = elementBelowTooltip; // Update the last hovered element
    }

    // console.log("Element Info:", elementInfoMap);
  }

  function hideMartiniTooltip() {
    tooltip.style.display = "none";
  }

  function handleMartiniClick(event) {
    event.preventDefault();
    event.stopPropagation();
    tooltip.style.display = "none";

    // Determine the element below the tooltip (mouse position)
    var elementBelowTooltip = document.elementFromPoint(
      event.clientX,
      event.clientY
    );

    // Hide the tooltip
    tooltip.style.display = "none";

    // If the element below the tooltip is an iframe
    if (
      elementBelowTooltip &&
      elementBelowTooltip.tagName.toLowerCase() === "iframe"
    ) {
      // Get the document inside the iframe
      var iframeDocument =
        elementBelowTooltip.contentDocument ||
        elementBelowTooltip.contentWindow.document;

      // If the iframe document is valid
      if (iframeDocument) {
        // Format the iframe details
        var iframeDetails = `Elements inside iframe: ${
          iframeDocument.body.getElementsByTagName("*").length
        }`;

        // Display the tooltip with iframe details
        tooltip.innerHTML = `[Iframe] <br> ${iframeDetails}`;

        // Position the tooltip near the mouse cursor
        var tooltipWidth = tooltip.offsetWidth;
        var tooltipHeight = tooltip.offsetHeight;
        var left = event.pageX - tooltipWidth / 2;
        var top = event.pageY - tooltipHeight / 2;

        tooltip.style.left = left + "px";
        tooltip.style.top = top + "px";
        tooltip.style.display = "block";

        // Initialize an array to store the iframe element information
        allElementInfo = [];

        const {
          xpath,
          allAttributes,
          customXPath,
          attribId,
          attribName,
          coords,
          someText,
        } = getElementIdentity(elementBelowTooltip);

        var elementInfoString = `${elementBelowTooltip.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;

        allElementInfo.push(`clicked-iFrame:${elementInfoString};`);

        // limitMapCharacters(elementInfoMap, "clicked-tagName");

        // Get all elements inside the iframe and log their details
        var iframeElements = iframeDocument.querySelectorAll("*");
        iframeElements.forEach(function (elementInsideIframe) {
          const {
            xpath,
            allAttributes,
            customXPath,
            attribId,
            attribName,
            coords,
            someText,
          } = getElementIdentity(elementInsideIframe);

          var elementInfoString = `iFrame-Child:${elementInsideIframe.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;

          allElementInfo.push(elementInfoString);
        });

        // Log the list of iframe elements
        console.log("List of iframe elements:", allElementInfo);
        window.allElementInfo = allElementInfo;
      } else {
        tooltip.innerHTML = "No iframe document found.";

        // Position the tooltip near the mouse cursor
        var tooltipWidth = tooltip.offsetWidth;
        var tooltipHeight = tooltip.offsetHeight;
        var left = event.pageX - tooltipWidth / 2;
        var top = event.pageY - tooltipHeight / 2;

        tooltip.style.left = left + "px";
        tooltip.style.top = top + "px";
        tooltip.style.display = "block";
      }
    } else {
      // If the clicked element is not an iframe, gather its regular information
      var tagName = elementBelowTooltip.tagName.toLowerCase();

      // Avoid main, body, and html tags
      if (["html", "body", "main"].includes(tagName)) {
        return; // Don't proceed if it's one of these elements
      }

      // Format and push regular element information to the array
      limitMapCharacters(elementInfoMap, "tagName-found");

      const {
        xpath,
        allAttributes,
        customXPath,
        attribId,
        attribName,
        coords,
        someText,
      } = getElementIdentity(elementBelowTooltip);

      var elementInfoString = `clicked:${elementBelowTooltip.tagName.toLowerCase()};xpath:${xpath};text:${someText};attribId:${attribId};attribName:${attribName};coords:${coords};allAttributes:${allAttributes};customXPath:${customXPath};`;

      allElementInfo.push(elementInfoString);

      console.log("List of elements:", allElementInfo);
      window.allElementInfo = allElementInfo;

      // Show the tooltip with the element details
      // tooltip.innerHTML = `${tagName} <br> ${someText}`;
      tooltip.innerHTML = `${tagName} <br> ${someText}`;
      var tooltipWidth = tooltip.offsetWidth;
      var tooltipHeight = tooltip.offsetHeight;
      var left = event.pageX - tooltipWidth / 2;
      var top = event.pageY - tooltipHeight / 2;

      tooltip.style.left = left + "px";
      tooltip.style.top = top + "px";
      tooltip.style.display = "block";
    }

    window.revertPickInjections();

    // Remove the tooltip from the page and delete the reference after 5 seconds
    // Remove the tooltip from the page and delete the reference after 5 seconds
    setTimeout(() => {
      if (tooltip) {
        tooltip.remove(); // Completely remove the tooltip from the DOM
        tooltip = null; // Clear the reference to free memory
        console.log("Tooltip completely removed.");
      }

      if (lastHoveredElement || elementBelowTooltip) {
        // Remove highlight from the previous element if any
        if (lastHoveredElement) {
          lastHoveredElement.style.outline = ""; // Remove the previous highlight
        }

        // Remove highlight from the previous element if any
        if (elementBelowTooltip) {
          elementBelowTooltip.style.outline = ""; // Remove the previous highlight
        }
      }
    }, 3000);
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
    var xpath = getMartiniXPath(element);
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

  function limitMapCharacters(elementInfoMap, coordText) {
    elementInfoMap.forEach((value, key) => {
      let modifiedValue = value;

      // TO DO  REDUCE ONLY THE TEXT FIELD

      // // Check if the key is "html" or value length is greater than 400
      // if (key === "html" || value.length > 400) {
      //   // Truncate the value to 150 characters and add "..."
      //   if (value.length > 150) {
      //     modifiedValue = value.substring(0, 150) + "...";
      //   }

      //   // If the length exceeds 400 characters, break the value into multiple lines
      //   if (value.length > 400) {
      //     const firstPart = value.substring(0, 150);
      //     const secondPart = value.substring(150);
      //     modifiedValue = `${firstPart}<br>...${secondPart}`;
      //   }
      // }

      // Push the formatted value and key to the array
      allElementInfo.push(`${coordText}:${modifiedValue}`);
    });
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

  function cleanOldValues() {
    window.allElementInfo = [];
  }

  cleanOldValues();

  window.revertPickInjections = function () {
    // alert("revertPickInjections");

    document.removeEventListener("mouseover", showMartiniTooltip);
    document.removeEventListener("click", handleMartiniClick);
    console.log("revertPickInjections");

    // Remove the tooltip from the page and delete the reference after 5 seconds
    setTimeout(() => {
      removeElements();
      window.allElementInfo = [];
    }, 1000);
  };

  function removeElements() {
    // Remove highlight from the previous element if any
    if (lastHoveredElement) {
      lastHoveredElement.style.outline = ""; // Remove the previous highlight
    }

    if (tooltip) {
      tooltip.remove(); // Completely remove the tooltip from the DOM
      tooltip = null; // Clear the reference to free memory
      console.log("Tooltip completely removed.");
    }
  }

  document.addEventListener("mouseover", showMartiniTooltip);
  //                document.addEventListener('mouseout', hideMartiniTooltip);
  document.addEventListener("click", handleMartiniClick);

  // window.postMessage({ type: "myMessage", data: "some data" }, targetOriginURL);

  window.addEventListener("message", function (event) {
    if (event.origin !== trustedOriginURL) return; // check the origin
    console.log(event.data);
  });
  // })(arguments[0], arguments[1]);
})("http://localhost:3000/", "http://localhost:3000/");

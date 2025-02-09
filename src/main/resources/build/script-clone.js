(function (targetOriginURL, trustedOriginURL) {
  var tooltip = document.createElement("div");
  tooltip.id = "Martini-Is-Awesome";
  tooltip.style.position = "absolute";
  tooltip.style.backgroundColor = "rgba(255, 165, 0, 0.5)";
  tooltip.style.border = "1px solid #ccc";
  tooltip.style.padding = "10px";
  tooltip.style.borderRadius = "5px";
  tooltip.style.boxShadow = "0 2px 4px rgba(0, 0, 0, 0.2)";
  tooltip.style.fontFamily = "Arial, sans-serif";
  tooltip.style.fontSize = "14px";
  tooltip.style.color = "#333";
  tooltip.style.zIndex = "10000";
  tooltip.style.display = "none";
  document.body.appendChild(tooltip);
  function getMartiniAbsoluteXPath(element) {
    if (element === document.body) {
      return "/html/" + element.tagName.toLowerCase();
    }
    var ix = 0;
    var siblings = element.parentNode.childNodes;
    for (var i = 0; i < siblings.length; i++) {
      var sibling = siblings[i];
      if (sibling === element) {
        return (
          getMartiniAbsoluteXPath(element.parentNode) +
          "/" +
          element.tagName.toLowerCase() +
          "[" +
          (ix + 1) +
          "]"
        );
      }
      if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {
        ix++;
      }
    }
    return "";
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
    var className = element.className
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
  function showMartiniTooltip(event) {
    // Ensure the element exists and is valid
    var elementBelowTooltip = document.elementFromPoint(
      event.clientX,
      event.clientY
    );

    // Check if the element exists and is a valid DOM element
    if (elementBelowTooltip && elementBelowTooltip.tagName) {
      window.tagNameTemp = elementBelowTooltip.tagName.toLowerCase();

      // Ensure getBoundingClientRect() is called on a valid element
      try {
        window.coordsTemp = elementBelowTooltip.getBoundingClientRect();
        window.coordsTemp =
          window.coordsTemp.left + "," + window.coordsTemp.top;
      } catch (e) {
        console.error("Error getting bounding rectangle:", e);
        window.coordsTemp = "Invalid Coordinates";
      }

      tooltip.textContent =
        window.tagNameTemp + "-Coordinates:(" + window.coordsTemp + ")";

      // Calculate tooltip dimensions and position
      var tooltipWidth = tooltip.offsetWidth;
      var tooltipHeight = tooltip.offsetHeight;
      var left = event.pageX - tooltipWidth / 2;
      var top = event.pageY - tooltipHeight / 2;

      // Set tooltip position
      tooltip.style.left = left + "px";
      tooltip.style.top = top + "px";
      tooltip.style.display = "block";
    } else {
      tooltip.style.display = "none"; // Hide tooltip if no valid element is found
    }
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

  function hideMartiniTooltip() {
    tooltip.style.display = "none";
  }
  function handleMartiniClick(event) {
    event.preventDefault();
    event.stopPropagation();
    tooltip.style.display = "none";
    var elementBelowTooltip = document.elementFromPoint(
      event.clientX,
      event.clientY
    );
    tooltip.style.display = "block";
    console.log(elementBelowTooltip);
    var xpath = getMartiniXPath(elementBelowTooltip);
    var absoluteXPath = getMartiniAbsoluteXPath(elementBelowTooltip);
    var customXPath = getMartiniCustomXPath(elementBelowTooltip);

    var someText = getSomeText(
      elementBelowTooltip.tagName.toLowerCase(),
      elementBelowTooltip
    );

    window.currentXPath = xpath;
    window.currentAbsoluteXPath = absoluteXPath;
    window.customXPath = customXPath;
    window.attribId = elementBelowTooltip.id || "";
    window.attribName = elementBelowTooltip.name || "";
    window.tagName = elementBelowTooltip.tagName.toLowerCase();
    window.coords = elementBelowTooltip.getBoundingClientRect();
    window.coords = window.coords.left + "," + window.coords.top;
    window.someText = someText;

    // Remove the tooltip from the page and delete the reference after 5 seconds
    setTimeout(() => {
      elementBelowTooltip = null;
      window.currentXPath = "";
      window.currentAbsoluteXPath = "";
      window.customXPath = "";
      window.attribId = "";
      window.attribName = "";
      window.tagName = "";
      window.coords = "";
      window.coords = "";
      window.someText = "";
      console.log("elementBelowTooltip", elementBelowTooltip);
      // revertCloneInjections();
    }, 2000);
  }
  window.currentXPath = "";
  window.currentAbsoluteXPath = "";
  window.customXPath = "";
  window.attribId = "";
  window.attribName = "";
  window.tagName = "";
  window.coords = "";
  window.tagNameTemp = "";
  window.coordsTemp = "";
  window.someText = "";
  document.addEventListener("mouseover", showMartiniTooltip);
  document.addEventListener("click", handleMartiniClick);

  window.revertCloneInjections = function () {
    alert("revertCloneInjections");

    document.removeEventListener("mouseover", showMartiniTooltip);
    document.removeEventListener("click", handleMartiniClick);
    console.log("revertCloneInjections");

    // Remove the tooltip from the page and delete the reference after 5 seconds
    setTimeout(() => {
      removeElements();
    }, 1000);
  };

  function removeElements() {
    if (tooltip) {
      tooltip.remove(); // Completely remove the tooltip from the DOM
      tooltip = null; // Clear the reference to free memory
      console.log("Tooltip completely removed.");
    }
  }

  // window.postMessage({ type: "myMessage", data: "some data" }, targetOriginURL);

  window.addEventListener("message", function (event) {
    if (event.origin !== trustedOriginURL) return; // check the origin
    console.log(event.data);
  });
})(arguments[0], arguments[1]);
// })("http://localhost:3000/", "http://localhost:3000/");

(function () {
  var tooltip = document.createElement("div");
  tooltip.id = "Martini-Is-Awesome";
  tooltip.style.position = "absolute";
  tooltip.style.backgroundColor = "rgba(255, 165, 0, 0.5)"; // Slightly opaque light orange
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

  function showMartiniTooltip(event) {
    var elementBelowTooltip = document.elementFromPoint(
      event.clientX,
      event.clientY
    );
    window.tagNameTemp = elementBelowTooltip.tagName.toLowerCase();
    window.coordsTemp = elementBelowTooltip.getBoundingClientRect();
    window.coordsTemp = window.coordsTemp.left + "," + window.coordsTemp.top;
    tooltip.textContent =
      window.tagNameTemp + "-Coordinates:(" + window.coordsTemp + ")";
    var tooltipWidth = tooltip.offsetWidth;
    var tooltipHeight = tooltip.offsetHeight;
    var left = event.pageX - tooltipWidth / 2;
    var top = event.pageY - tooltipHeight / 2;

    tooltip.style.left = left + "px";
    tooltip.style.top = top + "px";
    tooltip.style.display = "block";
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
    cleanOldValues();

    if (elementBelowTooltip.tagName.toLowerCase() === "iframe") {
      // If the clicked element is an iframe, get the iframe's XPath
      var iframeXPath = getMartiniXPath(elementBelowTooltip);
      window.iFrameXPath = iframeXPath;

      // Get the document inside the iframe
      var iframeDocument =
        elementBelowTooltip.contentDocument ||
        elementBelowTooltip.contentWindow.document;

      // Get all elements inside the iframe
      var iframeElements = iframeDocument.querySelectorAll("*");

      // Initialize an array to store the iframe elements' information
      var iframeElementInfo = [];

      // Loop through each element inside the iframe and log its XPath, coordinates, and handle input values
      iframeElements.forEach(function (elementInsideIframe) {
        // Get the XPath of the current element
        var iframeElementXPath = getMartiniXPath(elementInsideIframe);

        // Get the coordinates of the element inside the iframe
        var elementCoordinates = elementInsideIframe.getBoundingClientRect();
        var elementCoords = {
          left: elementCoordinates.left,
          top: elementCoordinates.top,
          right: elementCoordinates.right,
          bottom: elementCoordinates.bottom,
          width: elementCoordinates.width,
          height: elementCoordinates.height,
        };

        // Extract text content (or input value if applicable)
        var someText = "";
        if (
          elementInsideIframe.tagName.toLowerCase() === "input" ||
          elementInsideIframe.tagName.toLowerCase() === "textarea"
        ) {
          someText = elementInsideIframe.value || "";
        } else {
          someText = elementInsideIframe.textContent.trim() || "";
        }

        // Create an object to store the information about the element
        var elementInfo = {
          tagName: elementInsideIframe.tagName.toLowerCase(),
          xpath: iframeElementXPath,
          coordinates: elementCoords,
          text: someText,
        };

        // Push the element's info to the iframeElementInfo array
        iframeElementInfo.push(elementInfo);
      });

      // Return the list of iframe elements with their tagName, XPath, and coordinates
      console.log("iFrameXPath", window.iFrameXPath);
      console.log("List of iframe elements:", iframeElementInfo);
      window.iframeElements = iframeElementInfo;
    } else {
      // If the clicked element is not an iframe, get the regular XPath
      // Store attributes of the clicked element
      window.attribId = elementBelowTooltip.id || "";
      window.attribName = elementBelowTooltip.name || "";
      window.tagName = elementBelowTooltip.tagName.toLowerCase();
      window.coords = elementBelowTooltip.getBoundingClientRect();
      window.coords = window.coords.left + "," + window.coords.top;

      // Extract text content (or input value if applicable)
      if (
        elementBelowTooltip.tagName.toLowerCase() === "input" ||
        elementBelowTooltip.tagName.toLowerCase() === "textarea"
      ) {
        window.text = elementBelowTooltip.value || "";
      } else {
        window.text = elementBelowTooltip.textContent.trim() || "";
      }

      var xpath = getMartiniXPath(elementBelowTooltip);
      var absoluteXPath = getMartiniAbsoluteXPath(elementBelowTooltip);
      var customXPath = getMartiniCustomXPath(elementBelowTooltip);

      window.currentXPath = xpath;
      window.currentAbsoluteXPath = absoluteXPath;
      window.customXPath = customXPath;

      console.log("tagName", window.tagName);
      console.log("Current XPath:", window.currentXPath);
      console.log("Absolute XPath:", absoluteXPath);
      console.log("Custom XPath:", customXPath);
      console.log("Extracted Text:", window.text);
    }
  }

  function cleanOldValues() {
    window.iFrameXPath = "";
    window.iframeElements = [];
    window.currentXPath = "";
    window.currentAbsoluteXPath = "";
    window.customXPath = "";
    window.attribId = "";
    window.attribName = "";
    window.tagName = "";
    window.coords = "";
    window.tagNameTemp = "";
    window.coordsTemp = "";
    window.text = "";
  }

  cleanOldValues();

  document.addEventListener("mouseover", showMartiniTooltip);
  //                document.addEventListener('mouseout', hideMartiniTooltip);
  document.addEventListener("click", handleMartiniClick);
  window.removeClickListener = function () {
    document.removeEventListener("mouseover", showMartiniTooltip);
    //                    document.removeEventListener('mouseout', hideMartiniTooltip);
    document.removeEventListener("click", handleMartiniClick);
  };
})();

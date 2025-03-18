const coordinatesElement = document.createElement("div");
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

// Track the last hovered element to remove the border from it
let lastHoveredElement = null;

// Add event listener for mouse movement to update coordinates
document.addEventListener("mousemove", function (event) {
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
});

// Add event listener for click event to intercept the click
document.addEventListener("click", function (event) {
  event.preventDefault(); // Prevent the default click action
  event.stopPropagation(); // Prevent the event from propagating upwards
  coordinatesElement.style.display = "none";

  // Get the coordinates of the click
  const clickX = event.clientX;
  const clickY = event.clientY;

  // Get the element at the clicked position
  const clickedElement = document.elementFromPoint(clickX, clickY);

  // Highlight the clicked element (optional)
  if (clickedElement) {
    clickedElement.style.outline = "3px solid blue"; // Optionally highlight the element with a blue border
  }
  coordinatesElement.style.display = "block";

  // Log or process the clicked element
  console.log("Clicked element:", clickedElement);

  // Function to find clickable elements (buttons, links, etc.)
  function findClickableElements(root) {
    const clickableSelectors = ["button", "a"]; // Add other clickable elements if needed
    const clickableElements = [];
    clickableSelectors.forEach((selector) => {
      clickableElements.push(...root.querySelectorAll(selector));
    });
    return clickableElements;
  }

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
      // Optionally, highlight the clickable elements (with a red border in this example)
      element.style.outline = "3px solid red"; // Example: Highlighting clickable elements inside the shadow root
    });
  } else {
    // console.log(
    //   "No shadowRoot found for the clicked element or its ancestors."
    // );
  }

  // Optionally alert the tag name of the clicked element and coordinates
  // alert(
  //   `You clicked on a ${clickedElement.tagName} element at X: ${clickX}, Y: ${clickY}`
  // );
});

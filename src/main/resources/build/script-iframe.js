(function () {
  function getXPath(element) {
    if (element.id !== "") {
      return `//*[@id='${element.id}']`;
    }
    if (element === document.body) {
      return "/html/body";
    }
    let ix = 0;
    let siblings = element.parentNode.childNodes;
    for (let i = 0; i < siblings.length; i++) {
      let sibling = siblings[i];
      if (sibling === element) {
        return (
          getXPath(element.parentNode) +
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
  }

  function extractElementsInfo(iframeDocument) {
    let elements = iframeDocument.querySelectorAll("*");
    let extractedData = [];
    elements.forEach((element) => {
      let tagName = element.tagName.toLowerCase();
      let xpath = getXPath(element);
      let textContent = element.textContent.trim() || "";
      if (
        tagName === "input" ||
        tagName === "textarea" ||
        tagName === "select" ||
        tagName === "button"
      ) {
        textContent = element.value || element.placeholder || "";
      }
      extractedData.push(
        `tagName:${tagName};xpath:${xpath};text:${textContent}`
      );
    });
    console.log("Extracted Elements:", extractedData);
    return extractedData;
  }

  function processFirstIframe() {
    let iframes = document.querySelectorAll("iframe");
    if (iframes.length === 0) {
      console.error("No iframes found in the document");
      return;
    }
    let firstIframe = iframes[0];
    let iframeDocument =
      firstIframe.contentDocument || firstIframe.contentWindow.document;
    if (!iframeDocument) {
      console.error("Could not access iframe content");
      return;
    }
    console.log("Processing first iframe at:", getXPath(firstIframe));
    let extractedData = extractElementsInfo(iframeDocument);
    console.log("Extracted Elements Inside First iFrame:", extractedData);
  }

  processFirstIframe();
})();

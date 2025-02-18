(function (targetOriginURL, trustedOriginURL) {
  let elementsWithText = [];

  function getXPath(element) {
    if (element.id) {
      return `//*[@id='${element.id}']`;
    }
    if (element === document.body) {
      return "/html/body";
    }
    let index = 1;
    let siblings = element.parentNode ? element.parentNode.children : [];
    for (let i = 0; i < siblings.length; i++) {
      if (siblings[i] === element) {
        return (
          getXPath(element.parentNode) +
          "/" +
          element.tagName.toLowerCase() +
          `[${index}]`
        );
      }
      if (siblings[i].tagName === element.tagName) {
        index++;
      }
    }
    return "";
  }

  function collectElementsWithText() {
    let elements = document.querySelectorAll("*");

    elements.forEach((element) => {
      let text = element.textContent.trim();
      if (
        text.length > 0 &&
        element.offsetWidth > 0 &&
        element.offsetHeight > 0
      ) {
        let xpath = getXPath(element);
        if (xpath) {
          elementsWithText.push(xpath);
        }
      }
    });
    window.allWithText = elementsWithText;
    console.log(window.allWithText);
  }

  window.allWithText = [];
  collectElementsWithText();
  // })(arguments[0], arguments[1]);
})("http://localhost:3000/", "http://localhost:3000/");

(function (searchTerms, hiddenFields, socketPort) {
  function injectScript() {
    // Create and style the alert
    const alertDiv = document.createElement("div");
    alertDiv.style.position = "fixed";
    alertDiv.style.top = "10px";
    alertDiv.style.left = "10px";
    alertDiv.style.backgroundColor = "yellow";
    alertDiv.style.padding = "10px";
    alertDiv.style.border = "1px solid black";
    alertDiv.style.zIndex = "9999";

    const alertText = document.createTextNode(
      "Page loading... JavaScript injected."
    );
    alertDiv.appendChild(alertText);

    document.body.appendChild(alertDiv);

    window.downloadState = {
      isDownloading: false,
      progress: 0,
    };

    window.updateDownloadProgress = function (progress) {
      window.downloadState.progress = progress;
      alertText.textContent =
        "Page loading... JavaScript injected. Download progress: " +
        progress +
        "%";
      console.log("Alert Text Content: " + alertText.textContent);
    };

    window.startDownload = function () {
      window.downloadState.isDownloading = true;
      alertText.textContent =
        "Page loading... JavaScript injected. Download started";
      console.log("Alert Text Content: " + alertText.textContent);
    };

    window.finishDownload = function () {
      window.downloadState.isDownloading = false;
      alertText.textContent =
        "Page loading... JavaScript injected. Download finished";
      console.log("Alert Text Content: " + alertText.textContent);
    };
  }

  // Check if the script has been stored in localStorage
  if (localStorage.getItem("injectedScript")) {
    injectScript(); // Re-inject from localStorage
  } else {
    // Initial injection and store in localStorage
    injectScript();
    localStorage.setItem("injectedScript", "true"); // Store a flag
  }

  // Clear localStorage on unload to prevent persistent re-injection if needed.
  window.addEventListener("beforeunload", function () {
    localStorage.removeItem("injectedScript");
  });
  // })(arguments[0], arguments[1], arguments[2]);
})(["div"], true, 8181);

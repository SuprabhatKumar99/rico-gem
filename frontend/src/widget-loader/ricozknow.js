(function () {
  "use strict";

  var currentScript = document.currentScript;

  if (!currentScript) {
    console.error("RicozKnow: unable to determine the widget script element.");
    return;
  }

  var tenant = currentScript.getAttribute("data-tenant");

  if (!tenant) {
    console.error(
      "RicozKnow: missing data-tenant attribute."
    );
    return;
  }

  var scriptUrl = new URL(currentScript.src);

  var widgetUrl =
    scriptUrl.origin +
    "/widget?tenant=" +
    encodeURIComponent(tenant);

  /*
   * Create launcher button
   */
  var button = document.createElement("button");

  button.type = "button";
  button.setAttribute("aria-label", "Open support chat");

  button.innerHTML = "💬";

  button.style.position = "fixed";
  button.style.right = "24px";
  button.style.bottom = "24px";
  button.style.width = "56px";
  button.style.height = "56px";
  button.style.border = "0";
  button.style.borderRadius = "50%";
  button.style.background = "#0f172a";
  button.style.color = "#ffffff";
  button.style.fontSize = "24px";
  button.style.cursor = "pointer";
  button.style.zIndex = "2147483646";
  button.style.boxShadow = "0 8px 30px rgba(0,0,0,.25)";

  /*
   * Create iframe
   */
  var iframe = document.createElement("iframe");

  iframe.src = widgetUrl;
  iframe.title = "RicozKnow Support";
  iframe.setAttribute("allow", "clipboard-write");

  iframe.style.position = "fixed";
  iframe.style.right = "24px";
  iframe.style.bottom = "92px";
  iframe.style.width = "380px";
  iframe.style.height = "640px";
  iframe.style.maxWidth = "calc(100vw - 32px)";
  iframe.style.maxHeight = "calc(100vh - 120px)";
  iframe.style.border = "0";
  iframe.style.borderRadius = "18px";
  iframe.style.background = "transparent";
  iframe.style.zIndex = "2147483645";
  iframe.style.boxShadow = "0 12px 40px rgba(0,0,0,.25)";
  iframe.style.display = "none";

  /*
   * Toggle widget
   */
  button.addEventListener("click", function () {
    var isHidden = iframe.style.display === "none";

    iframe.style.display = isHidden ? "block" : "none";

    button.innerHTML = isHidden ? "×" : "💬";
  });

  document.body.appendChild(iframe);
  document.body.appendChild(button);
})();
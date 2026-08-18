THE OPEN QUESTION — which direction next?

Option 1 — Build self-healing now (highest value, larger/riskier)
Start the WebElement → selector/DTO refactor targeted at self-healing re-resolution: when a step's xPath fails during a TEST RUN, re-scan the page and match the stored element by name + position + OCR (the OCR foundation is already in place). Changes the core locate contract — do it incrementally with live testing.

Option 2 — Finish remaining leaf ports (lower risk)
Keep porting the smaller Selenium→Playwright pieces (CoordinateActions, SupportCapture, remaining executeScript/DOM reads) before touching the WebElement contract.

Option 3 — Migrate interactive PICK
Port hoverPick interactive picking (currently Selenium JS-injection over WebSocket) to Playwright evaluate, so element picking works fully in single-browser mode.

Option 4 — Pause & review
Walk through NEGATIVE IMPACTS MIGRATION.md together before proceeding.

The strong points to check step-by-step (the real blockers — §2 of the doc)

1. Every method returning/accepting WebElement — Playwright has no reusable handle, only lazy Locators. Callers must be reworked to selectors/DTOs. This is the bulk of the work AND the prerequisite for self-healing. (PerformActions.searchElement/locateElement/findWebElement/findElementByCssSelector, ElementLocator, ElementInteraction, ElementRecoveryService, TargetElement.element, ElementDtoMapper.)
2. Stateful frame/window switching (switchTo().frame()/window()/defaultContent(), getWindowHandles) — Playwright is stateless (frameLocator, context.pages()).
3. getIframeElementsMap() + entire IframeInputLocator — a map of live handles.
4. Interactive PICK / hover-pick highlight — 9-arg JS injection over WebSocket (portable in principle; needs single-arg-object refactor).
5. ARWebDriver driver-binary lifecycle — msedgedriver --version, EdgeOptions/ChromeOptions, RemoteWebDriver capabilities. (This version check was the source of the misleading "Invalid URL" dialog.)
6. Appium mobile "Next" IME and cross-origin contentDocument injection — permanently unsupported in web-Playwright.
7. Verify OCR actually completes in Playwright mode (native ar_ocr.dll) — I saw rects dumped but need to confirm OCR pipeline done (not a caught DLL warning) on the next scan.

---
I've paused all coding and made no changes since — everything is committed and pushed (latest 3beac1dd). Tell me which option (1–4) when you're ready, and I'll pick it up step by step.
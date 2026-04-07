/**
 * actionExecutor -- DOM action executor
 *
 * Receives commands from Java via WebSocket and executes them
 * directly in the browser DOM context.  No Selenium visibility
 * checks -- works on hidden radio buttons, pointer-events:none, etc.
 *
 * Supported actions:
 *   click       - click the element
 *   type        - clear + type text into the element
 *   clear       - clear the element value
 *   select      - select an option by visible text
 *   focus       - focus the element
 *   scroll      - scroll element into view
 *   getValue    - read element value/text and return it
 *   check       - set checkbox/radio to checked
 *   uncheck     - set checkbox/radio to unchecked
 */

// ── XPath resolver ──────────────────────────────────────────────────────────

function evaluateXPath(xpath) {
  try {
    const result = document.evaluate(
      xpath, document, null,
      XPathResult.FIRST_ORDERED_NODE_TYPE, null,
    );
    return result.singleNodeValue;
  } catch (_) { return null; }
}

// ── Element finder (tries XPath → CSS → coordinates) ────────────────────────

function findElement(cmd) {
  let el = null;

  if (cmd.xPath) {
    el = evaluateXPath(cmd.xPath);
  }

  if (!el && cmd.cssSelector) {
    el = document.querySelector(cmd.cssSelector);
  }

  if (!el && cmd.coordinates) {
    const parts = cmd.coordinates.split(',');
    if (parts.length === 2) {
      const x = parseFloat(parts[0]);
      const y = parseFloat(parts[1]);
      el = document.elementFromPoint(x, y);
    }
  }

  if (!el && cmd.attribId) {
    el = document.getElementById(cmd.attribId);
  }

  return el;
}

// ── Dispatch native input event so Angular/React picks up the change ────────

function dispatchInput(el) {
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.dispatchEvent(new Event('change', { bubbles: true }));
}

// ── Angular Material parent resolver ────────────────────────────────────────
// Hidden inputs inside mat-radio-button, mat-checkbox, mat-slide-toggle, etc.
// must be clicked on the Material wrapper, not the hidden <input>.

const MAT_WRAPPERS = [
  'mat-radio-button',
  'mat-checkbox',
  'mat-slide-toggle',
  'mat-button-toggle',
  'mat-list-option',
];

function resolveMatParent(el) {
  if (!el || !el.parentElement) return null;

  // Only resolve for hidden/non-interactive inputs
  const tag = el.tagName.toLowerCase();
  if (tag !== 'input') return null;

  let node = el.parentElement;
  // Walk up max 5 levels to find a Material wrapper
  for (let i = 0; i < 5 && node; i++) {
    const parentTag = node.tagName.toLowerCase();
    if (MAT_WRAPPERS.includes(parentTag)) return node;
    node = node.parentElement;
  }
  return null;
}

// ── Action handlers ─────────────────────────────────────────────────────────

const actions = {

  click(el) {
    // Angular Material: hidden inputs inside mat-radio-button, mat-checkbox, mat-slide-toggle
    // Clicking the hidden input does nothing — click the Material wrapper instead
    const matParent = resolveMatParent(el);
    const target = matParent || el;

    target.scrollIntoView({ block: 'center', behavior: 'instant' });
    target.click();

    // Also fire native MouseEvents for frameworks that don't use .click()
    target.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
    target.dispatchEvent(new MouseEvent('mouseup',   { bubbles: true, cancelable: true }));
    target.dispatchEvent(new MouseEvent('click',     { bubbles: true, cancelable: true }));

    const desc = matParent
      ? matParent.tagName.toLowerCase() + ' (parent of ' + el.tagName.toLowerCase() + ')'
      : el.tagName.toLowerCase();
    return { success: true, message: 'click() executed on ' + desc };
  },

  type(el, cmd) {
    const value = cmd.value ?? '';
    // Focus + clear + set value + dispatch events
    el.focus();
    if ('value' in el) {
      el.value = '';
      el.value = value;
    } else if (el.isContentEditable) {
      el.textContent = value;
    }
    dispatchInput(el);

    // Also try sendKeys-style for frameworks that listen to keydown/keyup
    for (const char of value) {
      el.dispatchEvent(new KeyboardEvent('keydown',  { key: char, bubbles: true }));
      el.dispatchEvent(new KeyboardEvent('keypress', { key: char, bubbles: true }));
      el.dispatchEvent(new KeyboardEvent('keyup',    { key: char, bubbles: true }));
    }

    return { success: true, message: `type("${value}") executed` };
  },

  clear(el) {
    el.focus();
    if ('value' in el) {
      el.value = '';
    } else if (el.isContentEditable) {
      el.textContent = '';
    }
    dispatchInput(el);
    return { success: true, message: 'clear() executed' };
  },

  select(el, cmd) {
    const text = cmd.value ?? '';
    if (el.tagName.toLowerCase() === 'select') {
      const option = Array.from(el.options).find(
        (o) => o.text.trim() === text.trim(),
      );
      if (option) {
        el.value = option.value;
        dispatchInput(el);
        return { success: true, message: `select("${text}") executed` };
      }
      return { success: false, message: `Option "${text}" not found` };
    }
    // For mat-select or custom dropdowns: click to open, then find option
    el.click();
    setTimeout(() => {
      const opt = document.querySelector(`mat-option[ng-reflect-value="${text}"], mat-option`)
        && Array.from(document.querySelectorAll('mat-option'))
          .find((o) => o.textContent.trim() === text.trim());
      if (opt) opt.click();
    }, 300);
    return { success: true, message: `select("${text}") attempted via click` };
  },

  focus(el) {
    el.focus();
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    return { success: true, message: 'focus() executed' };
  },

  scroll(el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    return { success: true, message: 'scrollIntoView() executed' };
  },

  getValue(el) {
    let value = '';
    if ('value' in el && el.value) {
      value = el.value;
    } else {
      value = (el.innerText || el.textContent || '').trim();
    }
    return { success: true, message: value };
  },

  check(el) {
    const target = resolveMatParent(el) || el;
    if (!el.checked) target.click();
    return { success: true, message: 'check() executed, checked=' + el.checked };
  },

  uncheck(el) {
    const target = resolveMatParent(el) || el;
    if (el.checked) target.click();
    return { success: true, message: 'uncheck() executed, checked=' + el.checked };
  },
};

// ── Main command handler ────────────────────────────────────────────────────

/**
 * Execute a command received from the Java backend.
 *
 * Expected command shape:
 * {
 *   operationId: 'actionExecutor',
 *   action:      'click' | 'type' | 'clear' | 'select' | 'focus' | 'scroll' | 'getValue' | 'check' | 'uncheck',
 *   xPath:       '/html/body/...',         // primary locator
 *   cssSelector: 'input#mat-radio-0-input', // fallback
 *   coordinates: '833.66,368.00',           // fallback
 *   attribId:    'mat-radio-0-input',       // fallback
 *   value:       'some text',               // for type/select
 *   commandId:   'uuid-123',                // to correlate response
 * }
 *
 * @param {object}   cmd       parsed command
 * @param {function} sendReply function(resultObj) to send back to Java
 */
export function executeCommand(cmd, sendReply) {
  const actionName = (cmd.action || '').toLowerCase();
  const handler = actions[actionName];

  console.log(`[actionExecutor] Received: action=${actionName}, xPath=${cmd.xPath || 'N/A'}`);

  if (!handler) {
    const result = {
      success: false,
      commandId: cmd.commandId,
      message: `Unknown action: "${actionName}"`,
    };
    console.warn('[actionExecutor]', result.message);
    sendReply(result);
    return;
  }

  const el = findElement(cmd);
  if (!el) {
    const result = {
      success: false,
      commandId: cmd.commandId,
      message: `Element not found: xPath=${cmd.xPath}, css=${cmd.cssSelector}, coords=${cmd.coordinates}, id=${cmd.attribId}`,
    };
    console.warn('[actionExecutor]', result.message);
    sendReply(result);
    return;
  }

  try {
    const result = handler(el, cmd);
    result.commandId = cmd.commandId;
    console.log(`[actionExecutor] ${actionName} -> ${result.message}`);
    sendReply(result);
  } catch (err) {
    const result = {
      success: false,
      commandId: cmd.commandId,
      message: `${actionName} failed: ${err.message}`,
    };
    console.error('[actionExecutor]', result.message);
    sendReply(result);
  }
}

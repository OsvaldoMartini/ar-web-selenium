import { ElementHandle, Locator, Page } from 'playwright-core';
import {
  ActionElementInspection,
  ActionElementPort,
  ActionPagePort,
  liveSelectorFor,
} from './physicalActionExecutor';
import { PhysicalAction } from './actionContracts';
import { pageKeyFromUrl } from './pageIdentity';

const QUERY_LIMIT = 101;
type DomHandle = ElementHandle<HTMLElement | SVGElement>;

export class PlaywrightActionPage implements ActionPagePort {
  constructor(private readonly page: Page, private readonly actionTimeoutMs = 10_000) {}

  async pageKey(): Promise<string> {
    return pageKeyFromUrl(this.page.url());
  }

  async query(selector: string, iframeXPath: string): Promise<readonly ActionElementPort[]> {
    return this.wrap(await this.locator(selector, iframeXPath));
  }

  async liveCandidates(
    action: PhysicalAction,
    iframeXPath: string,
  ): Promise<readonly ActionElementPort[]> {
    return this.wrap(await this.locator(liveSelectorFor(action), iframeXPath));
  }

  async waitForRender(delayMs: number): Promise<void> {
    await this.page.waitForTimeout(delayMs);
  }

  private locator(selector: string, iframeXPath: string): Locator {
    return iframeXPath
      ? this.page.frameLocator(`xpath=${iframeXPath}`).locator(selector)
      : this.page.locator(selector);
  }

  private async wrap(locator: Locator): Promise<readonly ActionElementPort[]> {
    const count = await locator.count();
    const handles: DomHandle[] = [];
    for (let index = 0; index < Math.min(count, QUERY_LIMIT); index += 1) {
      const handle = await locator.nth(index).elementHandle();
      if (handle) handles.push(handle as DomHandle);
    }
    return handles.map(handle => new PlaywrightActionElement(
      this.page, handle, this.actionTimeoutMs,
    ));
  }
}

class PlaywrightActionElement implements ActionElementPort {
  constructor(
    private readonly page: Page,
    private readonly handle: DomHandle,
    private readonly timeoutMs: number,
  ) {}

  async sameElement(other: ActionElementPort): Promise<boolean> {
    if (!(other instanceof PlaywrightActionElement)) return false;
    return this.handle.evaluate((element, candidate) => element === candidate, other.handle);
  }

  async inspect(
    action: PhysicalAction,
    expectedTag: string,
    requireSameOriginFrame: boolean,
    allowExplicitClickOverride: boolean,
  ): Promise<ActionElementInspection> {
    return this.handle.evaluate((element, args) => {
      const [requestedAction, expected, requireFrame, allowClickOverride] = args;
      const html = element as HTMLElement;
      const tagName = (element.tagName || '').toLowerCase();
      const type = String((html as HTMLInputElement).type || element.getAttribute('type') || '').toLowerCase();
      const role = (element.getAttribute('role') || '').toLowerCase();
      const style = window.getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      const visible = style.visibility !== 'hidden' && style.display !== 'none'
        && Number(style.opacity || 1) !== 0 && rect.width > 0 && rect.height > 0;
      const disabled = Boolean((html as HTMLButtonElement).disabled)
        || element.getAttribute('aria-disabled') === 'true';
      const readonly = Boolean((html as HTMLInputElement).readOnly)
        || element.getAttribute('aria-readonly') === 'true';
      const tagValidated = !expected || tagName === expected;
      let actionValidated = false;
      if (requestedAction === 'OUTPUT') actionValidated = visible;
      if (requestedAction === 'INPUT') {
        const input = tagName === 'textarea' || tagName === 'select' || html.isContentEditable
          || role === 'textbox'
          || (tagName === 'input'
            && !['button', 'submit', 'reset', 'file', 'checkbox', 'radio', 'hidden', 'image'].includes(type));
        actionValidated = visible && !disabled && !readonly && input;
      }
      if (requestedAction === 'CLICK') {
        const clickTag = ['a', 'button', 'label', 'summary', 'select', 'option'].includes(tagName)
          || (tagName === 'input' && type !== 'hidden');
        const clickRole = ['button', 'link', 'menuitem', 'tab', 'checkbox', 'radio', 'option', 'switch']
          .includes(role);
        actionValidated = visible && !disabled && (allowClickOverride || clickTag || clickRole
          || element.hasAttribute('onclick') || html.tabIndex >= 0);
      }
      let frameValidated = true;
      if (requireFrame) {
        try {
          frameValidated = window.top?.location.origin === window.location.origin;
        } catch {
          frameValidated = false;
        }
      }
      const root = element.getRootNode?.();
      const shadowValidated = !(typeof ShadowRoot !== 'undefined' && root instanceof ShadowRoot);
      const names = [
        html.id,
        element.getAttribute('name'),
        element.getAttribute('aria-label'),
        element.getAttribute('data-testid'),
        element.getAttribute('data-test-id'),
        element.getAttribute('test-id'),
        element.getAttribute('data-cy'),
        element.getAttribute('data-qa'),
        element.textContent,
      ].filter((value): value is string => typeof value === 'string');
      return {
        visible,
        frameValidated,
        shadowValidated,
        tagValidated,
        actionValidated,
        tagName,
        names,
      };
    }, [action, expectedTag, requireSameOriginFrame, allowExplicitClickOverride] as const);
  }

  async click(): Promise<void> {
    await this.handle.click({ timeout: this.timeoutMs });
  }

  async fill(value: string, pressEnter: boolean, pressTab: boolean): Promise<void> {
    const tagName = await this.handle.evaluate(element => element.tagName.toLowerCase());
    if (tagName === 'select') {
      const matches = await this.handle.evaluate((element, expected) =>
        Array.from((element as HTMLSelectElement).options)
          .filter(option => String(option.value) === expected).length, value);
      if (matches !== 1) throw new Error('SELECT_OPTION_AMBIGUOUS');
      await this.handle.selectOption({ value }, { timeout: this.timeoutMs });
    } else {
      await this.handle.fill(value, { timeout: this.timeoutMs });
    }
    if (pressEnter) await this.page.keyboard.press('Enter');
    if (pressTab) await this.page.keyboard.press('Tab');
  }

  async read(): Promise<string> {
    return this.handle.evaluate(element => {
      const valued = element as HTMLInputElement;
      if ('value' in valued && valued.value !== undefined && valued.value !== null) {
        return String(valued.value);
      }
      const html = element as HTMLElement;
      return String(html.innerText ?? element.textContent ?? '');
    });
  }
}

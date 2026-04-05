/**
 * searchListAsync — Element identity extraction
 */

import { generateXPath } from './xpathResolver.js';
import { classifyTag }    from '../classifier/tagClassifier.js';

export function isHidden(el) {
  const style = window.getComputedStyle(el);
  return (
    style.display === 'none' ||
    style.visibility === 'hidden' ||
    el.hasAttribute('aria-hidden')
  );
}

export function extractVisibleTextFromHTML(element) {
  if (!element) return { text: [], labels: [], titles: [] };

  const result = { text: new Set(), labels: new Set(), titles: new Set() };

  const isVisible = (el) => {
    const s = window.getComputedStyle(el);
    return !(s.display === 'none' || s.visibility === 'hidden' || el.hasAttribute('aria-hidden'));
  };

  const isTech = (w) => w.includes('_') || w.includes('--') || w.includes('-');

  if (element.textContent?.trim() && isVisible(element)) {
    const f = element.textContent.trim().split(/\s+/).filter((w) => !isTech(w)).join(' ').trim();
    if (f) result.text.add(f);
  }

  element.querySelectorAll('label').forEach((label) => {
    if (isVisible(label) && label.textContent?.trim()) result.labels.add(label.textContent.trim());
    const forAttr = label.getAttribute('for');
    if (forAttr) {
      const inp = document.getElementById(forAttr);
      if (inp && isVisible(inp)) {
        const raw = inp.value?.trim() || inp.placeholder?.trim() || '';
        if (raw) {
          const f = raw.split(/\s+/).filter((w) => !isTech(w)).join(' ').trim();
          if (f) result.text.add(f);
        }
      }
    }
  });

  const tags = ['p','h1','h2','h3','h4','h5','h6','li','span','div','strong','em','b','i','blockquote'];
  tags.forEach((tag) => {
    element.querySelectorAll(tag).forEach((child) => {
      if (isVisible(child) && child.textContent?.trim()) {
        const f = child.textContent.trim().split(/\s+/).filter((w) => !isTech(w)).join(' ').trim();
        if (f) result.text.add(f);
      }
    });
  });

  element.querySelectorAll('a').forEach((link) => {
    if (isVisible(link) && link.textContent?.trim()) {
      const f = link.textContent.trim().split(/\s+/).filter((w) => !isTech(w)).join(' ').trim();
      if (f) result.text.add(f);
    }
  });

  element.querySelectorAll('iframe').forEach((iframe) => {
    if (iframe.hasAttribute('title')) {
      const t = iframe.getAttribute('title')?.trim();
      if (t) result.titles.add(t);
    }
    try {
      const doc = iframe.contentDocument || new DOMParser().parseFromString(iframe.srcdoc || '', 'text/html');
      if (doc.body) {
        const sub = extractVisibleTextFromHTML(doc.body);
        sub.titles.forEach((t) => result.titles.add(t));
        sub.text.forEach((t) => result.text.add(t));
        sub.labels.forEach((t) => result.labels.add(t));
      }
    } catch (e) {
      console.log('Could not access iframe content', e);
    }
  });

  return {
    text: Array.from(result.text),
    labels: Array.from(result.labels),
    titles: Array.from(result.titles),
  };
}

function getVisibleText(tagName, attributeData, element) {
  let textResult = '';

  if (element && !isHidden(element)) {
    const extracted = extractVisibleTextFromHTML(element);
    textResult = [...extracted.titles, ...extracted.text, ...extracted.labels]
      .map((t) => t.trim()).filter(Boolean).join('; ');
  }

  const attributePriority = [
    'aria-label','aria-labelledby','aria-describedby','textarea','input','select',
    'placeholder','label','name','title','alt','for','data-label','data-name',
    'data-title','id','data-testid',
  ];

  let firstMeaningfulText = '';

  const getAttrText = (name, value) => {
    if (name === 'aria-labelledby' || name === 'aria-describedby') {
      const ref = document.getElementById(value);
      if (ref && !isHidden(ref)) return ref.textContent.trim();
    }
    return value.trim();
  };

  if (textResult && !/^\..*\{.*\}$/.test(textResult)) {
    firstMeaningfulText = textResult;
  } else {
    const titleAttr = attributeData.find(({ name }) => name === 'title');
    if (titleAttr) firstMeaningfulText = getAttrText(titleAttr.name, titleAttr.value);

    if (!firstMeaningfulText) {
      for (const attr of attributePriority) {
        const found = attributeData.find(({ name }) => name === attr);
        if (found) {
          firstMeaningfulText = getAttrText(found.name, found.value);
          if (firstMeaningfulText) break;
        }
      }
    }
  }

  return firstMeaningfulText;
}

/**
 * Build a full identity descriptor for a DOM element.
 */
export function getElementIdentity(hiddenFields, element) {
  if (!hiddenFields) {
    if (
      (element.offsetWidth === 0 || element.offsetHeight === 0 ||
        window.getComputedStyle(element).visibility === 'hidden') &&
      !(element.tagName.toLowerCase() === 'input' && element.type.toLowerCase() === 'hidden')
    ) {
      return null;
    }
  }

  const attributeData = Array.from(element.attributes).map((a) => ({ name: a.name, value: a.value }));
  const attribId   = element.id || '';
  const attribName = element.name || '';
  const coordinates = `${element.getBoundingClientRect().left.toFixed(2)},${element.getBoundingClientRect().top.toFixed(2)}`;

  let tagName  = element.tagName.toLowerCase();
  const someText = getVisibleText(tagName, attributeData, element);
  const xPath  = generateXPath(element);
  const classified = classifyTag(tagName, xPath);
  if (classified !== tagName) tagName = classified;

  return { xPath, tagName, attributeData, customXPath: '', attribId, attribName, coordinates, someText };
}

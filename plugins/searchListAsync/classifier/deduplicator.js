/**
 * searchListAsync — Deduplication logic
 *
 * Two-pass dedup: XPath ancestor grouping, then text/coordinate dedup.
 */

export function getResultMap(elementInfoMap) {
  const result = [];
  elementInfoMap.forEach((value) => result.push(value));
  return result;
}

/**
 * Pass 1 — group elements by shared <a> ancestor XPath, keep lowest-coordinate descendant.
 */
export function processElementsWithXPath(elementsList) {
  const grouped = new Map();

  const parseXPath = (xPath) =>
    xPath.split('/').filter((p) => p).map((p) => {
      const m = p.match(/([a-zA-Z]+)(?:\[(\d+)\])?/);
      return m ? { tagName: m[1], index: m[2] ? parseInt(m[2]) : null } : null;
    }).filter((i) => i !== null);

  const areSameComponent = (x1, x2) => {
    const p1 = parseXPath(x1);
    const p2 = parseXPath(x2);
    if (p1.length === 0 || p2.length === 0) return false;

    let commonLen = 0;
    for (let i = 0; i < Math.min(p1.length, p2.length); i++) {
      if (p1[i].tagName === p2[i].tagName && p1[i].index === p2[i].index) {
        if (p1[i].tagName === 'a') { commonLen = i + 1; break; }
      } else break;
    }
    if (commonLen === 0) return false;

    return p1.slice(0, commonLen).every((item, idx) =>
      item.tagName === p2[idx].tagName && item.index === p2[idx].index);
  };

  elementsList.forEach((el) => {
    if (el.xPath && el.coordinates) {
      let found = false;
      for (const [key, group] of grouped) {
        if (areSameComponent(el.xPath, key) && el.coordinates === group[0].coordinates) {
          group.push(el); found = true; break;
        }
      }
      if (!found) grouped.set(el.xPath, [el]);
    }
  });

  const result = [];
  grouped.forEach((group) => {
    if (group.length > 1) {
      let best = group[0];
      group.forEach((el) => {
        const [x, y] = el.coordinates.split(',').map(parseFloat);
        const [bx, by] = best.coordinates.split(',').map(parseFloat);
        if (y > by || (y === by && x > bx)) best = el;
      });
      result.push(best);
    } else {
      result.push(group[0]);
    }
  });

  return result;
}

/**
 * Pass 2 — text/coordinate dedup with aria-label priority.
 */
export function findUniqueAndOneRepeated(elementsList) {
  const wordFreq   = new Map();
  const wordToItems = new Map();
  const coordsMap   = new Map();

  const hasAttr = (el, name) => el.attributeData?.some((a) => a.name === name);

  elementsList.forEach((el) => {
    const tag = el.tagName.toLowerCase();
    if (tag !== 'span' && tag !== 'div' && tag !== 'button') return;

    const someText = el.someText?.trim();
    if (someText) {
      someText.split(/[\s,;]+/).forEach((w) => {
        const t = w.trim();
        if (t) {
          wordFreq.set(t, (wordFreq.get(t) || 0) + 1);
          if (!wordToItems.has(t)) wordToItems.set(t, new Set());
          wordToItems.get(t).add(el);
        }
      });
    }

    if (el.coordinates) {
      if (!coordsMap.has(el.coordinates)) coordsMap.set(el.coordinates, []);
      coordsMap.get(el.coordinates).push(el);
    }
  });

  // Resolve same-coordinates with aria-label priority
  coordsMap.forEach((elements) => {
    const priority = elements.find((el) => hasAttr(el, 'aria-label'));
    if (priority) {
      const ariaVal = priority.attributeData.find((a) => a.name === 'aria-label');
      if (ariaVal) elements.forEach((el) => { if (el.someText !== ariaVal.value) el.someText = ariaVal.value; });
    }
  });

  const repeatedWords = Array.from(wordFreq.entries())
    .filter(([, count]) => count > 1).map(([word]) => word);

  const result = [];
  const added  = new Set();

  repeatedWords.forEach((word) => {
    if (wordToItems.has(word)) {
      const items = Array.from(wordToItems.get(word));
      items.sort((a, b) =>
        hasAttr(b, 'aria-label') - hasAttr(a, 'aria-label') ||
        hasAttr(b, 'test-id') - hasAttr(a, 'test-id'));
      if (!added.has(items[0])) { result.push(items[0]); added.add(items[0]); }
    }
  });

  elementsList.forEach((el) => {
    if (!added.has(el)) {
      const someText = el.someText?.trim();
      if (someText) {
        const isRepeated = someText.split(/[\s,;]+/).map((w) => w.trim()).some((w) => repeatedWords.includes(w));
        if (!isRepeated) { result.push(el); added.add(el); }
      }
    }
  });

  // Coordinate dedup
  const uniqueCoords  = new Map();
  const filteredResult = [];

  result.forEach((el) => {
    if (el.coordinates) {
      if (!uniqueCoords.has(el.coordinates)) {
        uniqueCoords.set(el.coordinates, el); filteredResult.push(el);
      } else {
        const existing = uniqueCoords.get(el.coordinates);
        if ((!hasAttr(existing, 'aria-label') && hasAttr(el, 'aria-label')) ||
            (el.attributeData && existing.attributeData && el.attributeData.length > existing.attributeData.length)) {
          uniqueCoords.set(el.coordinates, el);
          filteredResult[filteredResult.indexOf(existing)] = el;
        }
      }
    } else {
      filteredResult.push(el);
    }
  });

  // XPath dedup
  const uniqueXPaths = new Set();
  const finalResult  = [];

  filteredResult.forEach((el) => {
    if (el.xPath && !uniqueXPaths.has(el.xPath)) { uniqueXPaths.add(el.xPath); finalResult.push(el); }
  });

  // Re-add non-span/div/button elements
  elementsList.forEach((el) => {
    const tag = el.tagName.toLowerCase();
    if (tag !== 'span' && tag !== 'div' && tag !== 'button') {
      if (el.xPath && !uniqueXPaths.has(el.xPath)) finalResult.push(el);
    }
  });

  return finalResult;
}

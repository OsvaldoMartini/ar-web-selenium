/**
 * classifier/deduplicator.js
 *
 * Two-pass deduplication of classified element descriptors:
 *   Pass 1 (R): XPath ancestor deduplication — keeps the lowest-coordinate
 *               descendant when multiple elements share an ancestor <a> path.
 *   Pass 2 (L): Text/coordinate deduplication — resolves conflicts when
 *               span/div/button elements share the same someText or coordinates,
 *               preferring the element with aria-label > test-id > attribute count.
 *
 * Extracted from the R() and L() functions in the original monolithic payload.
 * Pure computation — no DOM API calls. WASM migration candidate (M-8).
 */

/**
 * @param {object[]} elements — classified descriptors from the scanner+classifier pipeline
 * @returns {object[]}        — deduplicated, ordered list ready for chunk sending
 */
export function deduplicate(elements) {
  const afterXPath = deduplicateByXPathAncestor(elements);
  return deduplicateByTextAndCoordinates(afterXPath);
}

// ── Pass 1: XPath ancestor dedup (R) ─────────────────────────────────────────

function deduplicateByXPathAncestor(elements) {
  const groups = new Map(); // xPath → element[]

  const sharedAnchorPrefix = (xpathA, xpathB) => {
    const segsA = parseXPathSegments(xpathA);
    const segsB = parseXPathSegments(xpathB);
    let anchorDepth = 0;
    const minLen = Math.min(segsA.length, segsB.length);
    for (let i = 0; i < minLen; i++) {
      if (segsA[i].tagName === segsB[i].tagName && segsA[i].index === segsB[i].index) {
        if (segsA[i].tagName === 'a') { anchorDepth = i + 1; break; }
      } else break;
    }
    return anchorDepth;
  };

  elements.forEach(el => {
    if (!el.xPath || !el.coordinates) return;
    let grouped = false;
    for (const [key, group] of groups) {
      if (sharedAnchorPrefix(el.xPath, key) > 0 && el.coordinates === group[0].coordinates) {
        group.push(el);
        grouped = true;
        break;
      }
    }
    if (!grouped) groups.set(el.xPath, [el]);
  });

  const result = [];
  groups.forEach(group => {
    if (group.length > 1) {
      // Keep the element with the highest Y coordinate (bottom-most)
      let best = group[0];
      group.forEach(el => {
        const [, ay] = el.coordinates.split(',').map(parseFloat);
        const [, by] = best.coordinates.split(',').map(parseFloat);
        if (ay > by || (ay === by && parseFloat(el.coordinates) > parseFloat(best.coordinates))) {
          best = el;
        }
      });
      result.push(best);
    } else {
      result.push(group[0]);
    }
  });

  return result;
}

function parseXPathSegments(xpath) {
  return xpath.split('/').filter(Boolean).map(seg => {
    const m = seg.match(/([a-zA-Z]+)(?:\[(\d+)\])?/);
    return m ? { tagName: m[1], index: m[2] ? parseInt(m[2]) : null } : null;
  }).filter(Boolean);
}

// ── Pass 2: Text + coordinate dedup (L) ──────────────────────────────────────

function deduplicateByTextAndCoordinates(elements) {
  const TEXT_TAGS = new Set(['span', 'div', 'button']);

  const wordFreq    = new Map(); // word → count
  const wordToEls   = new Map(); // word → Set<element>
  const coordGroups = new Map(); // coordinates → element[]

  elements.forEach(el => {
    if (!TEXT_TAGS.has(el.tagName?.toLowerCase())) return;
    const text = (el.someText || '').trim();
    if (!text) return;

    text.split(/[\s,;]+/).forEach(word => {
      const w = word.trim();
      if (!w) return;
      wordFreq.set(w, (wordFreq.get(w) || 0) + 1);
      if (!wordToEls.has(w)) wordToEls.set(w, new Set());
      wordToEls.get(w).add(el);
    });

    if (el.coordinates) {
      if (!coordGroups.has(el.coordinates)) coordGroups.set(el.coordinates, []);
      coordGroups.get(el.coordinates).push(el);
    }
  });

  // Resolve aria-label conflicts within same-coordinate groups
  coordGroups.forEach(group => {
    const ariaEl = group.find(el => el.attributeData?.some(a => a.name === 'aria-label'));
    if (ariaEl) {
      const ariaVal = ariaEl.attributeData.find(a => a.name === 'aria-label')?.value?.trim();
      if (ariaVal) {
        group.forEach(el => { if (el.someText !== ariaVal) el.someText = ariaVal; });
      }
    }
  });

  const duplicateWords = Array.from(wordFreq.entries())
    .filter(([, count]) => count > 1)
    .map(([word]) => word);

  const seen    = new Set();
  const winners = [];

  duplicateWords.forEach(word => {
    if (!wordToEls.has(word)) return;
    let candidates = Array.from(wordToEls.get(word));
    candidates.sort((a, b) =>
      hasAttr(b, 'aria-label') - hasAttr(a, 'aria-label') ||
      hasAttr(b, 'test-id')    - hasAttr(a, 'test-id')
    );
    if (!seen.has(candidates[0])) {
      winners.push(candidates[0]);
      seen.add(candidates[0]);
    }
  });

  // Add non-duplicate span/div/button elements
  elements.forEach(el => {
    if (!TEXT_TAGS.has(el.tagName?.toLowerCase())) return;
    if (seen.has(el)) return;
    const text = (el.someText || '').trim();
    if (text && text.split(/[\s,;]+/).map(w => w.trim()).some(w => duplicateWords.includes(w))) return;
    winners.push(el);
    seen.add(el);
  });

  // Coordinate-level dedup among winners
  const coordBest = new Map();
  const final     = [];
  winners.forEach(el => {
    if (!el.coordinates) { final.push(el); return; }
    if (coordBest.has(el.coordinates)) {
      const existing = coordBest.get(el.coordinates);
      if (!hasAttr(existing, 'aria-label') && hasAttr(el, 'aria-label') ||
          (el.attributeData?.length || 0) > (existing.attributeData?.length || 0)) {
        coordBest.set(el.coordinates, el);
        final[final.indexOf(existing)] = el;
      }
    } else {
      coordBest.set(el.coordinates, el);
      final.push(el);
    }
  });

  // XPath-level dedup among final winners
  const xpathSeen = new Set();
  const deduped   = [];
  final.forEach(el => {
    if (el.xPath && xpathSeen.has(el.xPath)) return;
    if (el.xPath) xpathSeen.add(el.xPath);
    deduped.push(el);
  });

  // Append non-text-tag elements that were not processed above
  elements.forEach(el => {
    if (TEXT_TAGS.has(el.tagName?.toLowerCase())) return;
    if (el.xPath && xpathSeen.has(el.xPath)) return;
    if (el.xPath) xpathSeen.add(el.xPath);
    deduped.push(el);
  });

  return deduped;
}

function hasAttr(el, attrName) {
  return el.attributeData?.some(a => a.name === attrName) ? 1 : 0;
}

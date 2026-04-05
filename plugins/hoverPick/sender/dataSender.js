/**
 * hoverPick — Data sender
 *
 * Collects element info from the Map, applies post-processing
 * (mat-label, div→label, table normalisation), and sends the
 * payload over WebSocket.
 */

import { findMatLabel, changeDivToLabelWithSomeText, normalizeSomeTextForTables } from '../classifier/nameLabelResolver.js';

/**
 * Flatten the elementInfoMap into allElementInfo, post-process, and send.
 *
 * @param {object} wsClient  WebSocket client from buildWsClient
 */
export function sendData(wsClient) {
  window.allElementInfo = [];

  // Flatten map → array
  window.elementInfoMap.forEach((value) => {
    window.allElementInfo.push({ ...value, id: 1 });
  });

  // Post-processing
  findMatLabel(window.allElementInfo);
  changeDivToLabelWithSomeText(window.allElementInfo);
  normalizeSomeTextForTables(window.allElementInfo);

  console.log('All element info stored in Map:', window.allElementInfo);

  if (wsClient.isOpen()) {
    if (window.allElementInfo.length > 0) {
      const message = {
        type:           'SEARCH_TOOL',
        sessionId:      window.destination,
        operationId:    window.operationId,
        homeBankingId:  window.homeBankingId,
        botJobId:       window.botJobId,
        elementDetails: window.allElementInfo,
      };

      const base64 = btoa(unescape(encodeURIComponent(JSON.stringify(message))));
      wsClient.send(base64);
      window.elementInfoMap.clear();
    }
  } else {
    console.log('WebSocket is not open. Cannot send message.');
  }
}

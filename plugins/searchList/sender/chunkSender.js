/**
 * searchList — Chunk sender
 *
 * Splits element list into batches of 25 and sends them over WebSocket
 * with 300ms delay between batches. Message type: UPDATE_LIST_ELEMENTS.
 */

const CHUNK_SIZE = 25;
const CHUNK_DELAY_MS = 300;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Send all elements in chunks via WebSocket.
 *
 * @param {function} sendFn   wsClient.send(base64)
 * @param {object}   opts     session/routing info + elements array
 */
export async function sendChunks(sendFn, { sessionId, destination, operationId, homeBankingId, botJobId, elements }) {
  const all = Array.isArray(elements) ? elements : [];

  for (let i = 0; i < all.length; i += CHUNK_SIZE) {
    const chunk = all.slice(i, i + CHUNK_SIZE);

    const message = {
      type:           'UPDATE_LIST_ELEMENTS',
      sessionId:      destination,
      operationId,
      homeBankingId,
      botJobId,
      elementDetails: chunk,
      chunkIndex:     Math.floor(i / CHUNK_SIZE),
      totalChunks:    Math.ceil(all.length / CHUNK_SIZE),
      chunkSize:      CHUNK_SIZE,
      totalElements:  all.length,
    };

    const base64 = btoa(unescape(encodeURIComponent(JSON.stringify(message))));
    sendFn(base64);
    console.log(`Sent chunk #${Math.floor(i / CHUNK_SIZE)}`, chunk);

    await sleep(CHUNK_DELAY_MS);
  }
}

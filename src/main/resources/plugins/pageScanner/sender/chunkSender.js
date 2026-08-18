/**
 * sender/chunkSender.js
 *
 * Splits the element list into batches and sends each over the WebSocket
 * with a 300ms delay between chunks to avoid flooding the server.
 *
 * Extracted from the async chunk loop in the original monolithic payload.
 */

const CHUNK_SIZE  = 25;
const CHUNK_DELAY = 300; // ms

/**
 * @param {function} send     — wsClient._send(payload)
 * @param {object}   opts
 * @param {string}   opts.sessionId
 * @param {string}   opts.destination
 * @param {string}   opts.operationId
 * @param {number}   opts.homeBankingId
 * @param {number}   opts.botJobId
 * @param {object[]} opts.elements
 */
export async function sendChunks(send, {
  sessionId, destination, operationId, homeBankingId, botJobId, elements
}) {
  if (!Array.isArray(elements) || elements.length === 0) return;

  const totalChunks = Math.ceil(elements.length / CHUNK_SIZE);

  for (let i = 0; i < elements.length; i += CHUNK_SIZE) {
    const chunk      = elements.slice(i, i + CHUNK_SIZE);
    const chunkIndex = Math.floor(i / CHUNK_SIZE);

    send({
      type:          'SEARCH_TOOL',
      sessionId:     destination,   // routed to the destination session
      operationId,
      homeBankingId,
      botJobId,
      elementDetails: chunk,
      chunkIndex,
      totalChunks,
      chunkSize:     CHUNK_SIZE,
      totalElements: elements.length,
    });

    console.log(`Sent chunk #${chunkIndex}`, chunk);

    if (i + CHUNK_SIZE < elements.length) {
      await delay(CHUNK_DELAY);
    }
  }
}

const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

import { createHash } from 'node:crypto';

export const pageKeyFromUrl = (raw: string): string => {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    throw new Error('PAGE_CONTEXT_UNAVAILABLE');
  }
  if ((url.protocol !== 'http:' && url.protocol !== 'https:') || !url.hostname) {
    throw new Error('PAGE_CONTEXT_UNAVAILABLE');
  }
  let path = url.pathname || '/';
  if (path.length > 1 && path.endsWith('/')) path = path.slice(0, -1);
  const userInfo = url.username || url.password
    ? `${url.username}${url.password ? `:${url.password}` : ''}@`
    : '';
  const normalized = `${url.protocol}//${userInfo}${url.host}${path}${url.search}${url.hash}`;
  return `url-v1:${createHash('sha256').update(normalized, 'utf8').digest('hex')}`;
};

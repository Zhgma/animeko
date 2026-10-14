// SPDX-License-Identifier: AGPL-3.0-only
// Buffer static Compose resources before Kotlin receives the Response, so cancelling
// a completed fetch cannot invalidate a response body still needed by a resource reader.
export function tolerateResourceCacheWriteFailure(cacheStorage, host = window) {
  const fetch = host.fetch.bind(host);
  host.fetch = async (input, options) => {
    const response = await fetch(input, options);
    const url = new URL(typeof input === 'string' ? input : input.url, host.location.href);
    if (url.origin !== host.location.origin || !url.pathname.includes('/composeResources/') || !response.ok) return response;
    const bytes = await response.arrayBuffer();
    return new Response(bytes, {status:response.status, headers:{'Content-Type':response.headers.get('Content-Type') || 'application/octet-stream'}});
  };
  if (!cacheStorage) return;
  const open = cacheStorage.open.bind(cacheStorage);
  cacheStorage.open = async name => {
    const cache = await open(name);
    if (name === 'compose_web_resources_cache') {
      const put = cache.put.bind(cache);
      cache.put = async (...args) => { try { await put(...args); } catch { /* Optional cache write. */ } };
    }
    return cache;
  };
}

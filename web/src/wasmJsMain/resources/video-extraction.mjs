// SPDX-License-Identifier: AGPL-3.0-only
// Static resource discovery. Page scripts are data and are never evaluated.
export function publicVideoUrl(value, base) {
  try {
    const url = new URL(value.replaceAll('&amp;', '&').replaceAll('\\/', '/'), base);
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : '';
  } catch { return ''; }
}
export function videoCandidates(text, base) {
  const videos = [], pages = [], scripts = [];
  let scriptText = text;
  const add = (list, value) => { const url = publicVideoUrl(value, base); if (url && !list.includes(url)) list.push(url); };
  const media = value => /\.(?:m3u8|mp4|webm|mkv|flv)(?:$|[?#])/i.test(value);
  const candidate = value => {
    const url = publicVideoUrl(value, base); if (!url) return;
    // Parser URLs can contain a complete encoded video URL in their query.
    for (const key of ['url', 'v', 'vid']) {
      const nested = new URL(url).searchParams.get(key);
      if (nested && /^https:\/\//.test(nested) && media(nested)) add(videos, nested);
    }
    if (media(new URL(url).pathname)) add(videos, url);
  };
  for (const match of text.matchAll(/(?:var|let|const)\s+player_\w+\s*=\s*/g)) {
    const start = match.index + match[0].length;
    let end = start, depth = 0, quote = false, escaped = false;
    for (; end < text.length; end++) {
      const char = text[end];
      if (quote) { if (escaped) escaped = false; else if (char === '\\') escaped = true; else if (char === '"') quote = false; }
      else if (char === '"') quote = true;
      else if (char === '{') depth++;
      else if (char === '}' && --depth === 0) { end++; break; }
    }
    scriptText = scriptText.replace(text.slice(match.index, end), '');
    try {
      const player = JSON.parse(text.slice(start, end)); let value = player.url;
      if (typeof value !== 'string') continue;
      if (Number(player.encrypt) === 2) value = decodeURIComponent(atob(value));
      else if (Number(player.encrypt) === 1) value = decodeURIComponent(value);
      candidate(value);
      if (!media(value)) add(pages, value);
    } catch { /* A malformed player configuration is not executable JavaScript. */ }
  }
  for (const match of text.matchAll(/<(video|source|iframe|script)\b[^>]*?\bsrc\s*=\s*(["'])(.*?)\2/gis)) {
    const kind = match[1].toLowerCase();
    if (kind === 'iframe') { candidate(match[3]); add(pages, match[3]); }
    else if (kind === 'script') add(scripts, match[3]);
    else add(videos, match[3]);
  }
  // Literal URLs used by DPlayer, ArtPlayer and similar JS players.
  for (const match of scriptText.matchAll(/\b(?:url|file|src|videoUrl|playUrl)["']?\s*[:=]\s*(["'])(.*?)\1/gi)) candidate(match[2]);
  return {videos, pages, scripts};
}
export async function extractOnlineVideo(url, read, {signal, maxPages = 8} = {}) {
  const queue = [{url, depth:0}], visited = new Set();
  while (queue.length && visited.size < maxPages) {
    signal?.throwIfAborted();
    const current = queue.shift(); if (visited.has(current.url)) continue;
    visited.add(current.url);
    let page;
    try { page = await read(current.url, {signal}); }
    catch (error) { if (visited.size === 1 || signal?.aborted) throw error; continue; }
    const found = videoCandidates(page.text, page.url);
    if (found.videos.length) return {url:found.videos[0], pageUrl:page.url};
    if (current.depth < 2) {
      queue.unshift(...found.pages.slice(0,3).map(url=>({url,depth:current.depth+1})));
      queue.push(...found.scripts.filter(url=>/player|play|config|parse/i.test(url)).slice(0,3).map(url=>({url,depth:current.depth+1})));
    }
  }
  throw new Error('未提取到视频地址。此线路可能需要动态脚本或站点验证，请切换线路或数据源。');
}

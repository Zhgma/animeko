// SPDX-License-Identifier: AGPL-3.0-only
// Static deployments use the public winserver gateway; no local runtime is required.
(() => {
  const base = 'https://119.28.149.19:8443/api/web';
  globalThis.aniApi = Object.freeze({
    base, animeko:base + '/animeko', bangumi:base + '/bangumi', auth:base + '/auth/animeko',
    page:url=>base + '/online/page?url=' + encodeURIComponent(url),
    torrent:url=>base + '/torrent?url=' + encodeURIComponent(url),
    feed:url=>base + '/feeds?url=' + encodeURIComponent(url),
    image:url=>/^https:\/\/(?:lain\.bgm\.tv|static\.myani\.org)\//.test(url) ? base + '/bangumi/image?url=' + encodeURIComponent(url) : url,
  });
  globalThis.aniAnimekoBaseUrl = () => globalThis.aniApi.animeko;
})();

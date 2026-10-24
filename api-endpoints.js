// SPDX-License-Identifier: AGPL-3.0-only
// Static deployments use the public API gateway; no local runtime is required.
(() => {
  const base = 'https://43.131.255.74:8443/api/web';
  // Migrated services and cached collections can still return signed image links on the old host.
  const image = url => {
    const migrated = url.replace(/^https:\/\/119\.28\.149\.19:8443\/api\/(books|komiic)\/images\//, 'https://43.131.255.74:8443/api/$1/images/');
    return /^https:\/\/(?:lain\.bgm\.tv|static\.myani\.org)\//.test(migrated)
      ? base + '/bangumi/image?url=' + encodeURIComponent(migrated) : migrated;
  };
  globalThis.aniApi = Object.freeze({
    manga: base + '/manga',
    base, animeko:base + '/animeko', bangumi:base + '/bangumi', auth:base + '/auth/animeko',
    page:url=>base + '/online/page?url=' + encodeURIComponent(url),
    torrent:url=>base + '/torrent?url=' + encodeURIComponent(url),
    feed:url=>base + '/feeds?url=' + encodeURIComponent(url),
    image,
  });
  globalThis.aniAnimekoBaseUrl = () => globalThis.aniApi.animeko;
})();

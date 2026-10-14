// SPDX-License-Identifier: AGPL-3.0-only
// Conservative RSS title metadata; unknown values stay empty and remain available in excluded rows.
export function btAttributes(title, date = '') {
  const resolution = title.match(/\b(2160|1440|1080|720|576|480)[pi]\b/i)?.[0].toUpperCase() || '';
  const subtitles = [];
  if (/简|簡|\b(?:CHS|GB)\b/i.test(title)) subtitles.push('简中');
  if (/繁|\b(?:CHT|BIG5)\b/i.test(title)) subtitles.push('繁中');
  if (/\b(?:ENG|English)\b/i.test(title)) subtitles.push('英语');
  const subtitleKind = /内封|內封/.test(title) ? '内封' : /内嵌|內嵌/.test(title) ? '内嵌' : /外挂|外掛/.test(title) ? '外挂' : '';
  const first = title.match(/^[\[【]([^\]】]+)[\]】]/)?.[1] || '';
  const alliance = first && !/^(?:\d|BD|DVD|WEB|TV|1080|720|合集|整理|搬运|搬運)/i.test(first) ? first : '';
  const range = title.match(/[\[【]\s*(\d{1,3}(?:\.5)?)\s*[-~～]\s*(\d{1,3}(?:\.5)?)(?:[^\]】]*)[\]】]/);
  const episode = title.match(/(?:\bS\d{1,2}E|\bEP?\s*|\s-\s*)(\d{1,3}(?:\.5)?)(?=v\d|[^\d.]|$)/i)
    || title.match(/[\[【]\s*(\d{1,3}(?:\.5)?)(?:v\d+)?\s*[\]】]/i)
    || title.match(/第\s*(\d{1,3}(?:\.5)?)\s*[话話集]/);
  const episodeStart = range ? Number(range[1]) : episode ? Number(episode[1]) : -1;
  const episodeEnd = range ? Number(range[2]) : episodeStart;
  const timestamp = Date.parse(date);
  return { resolution, subtitles, subtitleKind, alliance, episodeStart, episodeEnd,
    published: Number.isFinite(timestamp) ? new Date(timestamp).toISOString().slice(5, 10) : '' };
}

import { createServer } from 'node:http';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { resolve, extname, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const base = fileURLToPath(new URL('.', import.meta.url));
const flavor = process.argv.includes('--development') ? 'developmentExecutable' : 'productionExecutable';
const root = resolve(base, 'build/dist/wasmJs', flavor);
if (!existsSync(resolve(root, 'index.html'))) throw new Error('Run ./build.ps1 first.');
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.mjs': 'text/javascript', '.json': 'application/json', '.wasm': 'application/wasm', '.css': 'text/css', '.ttf': 'font/ttf', '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml' };
createServer(async (request, response) => {
  const url = new URL(request.url, 'http://localhost');
  if (url.pathname === '/api/health') {
    response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    response.end(JSON.stringify({ service: 'animecomic-web', pid: process.pid })); return;
  }
  if (url.pathname === '/__preview') {
    const width = Math.min(1920, Math.max(320, Number(url.searchParams.get('width')) || 390));
    response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    response.end(`<!doctype html><html lang="zh-CN"><meta name="viewport" content="width=device-width"><title>AnimeComic 手机布局预览</title><body style="margin:0;background:#ddd;display:grid;justify-content:center"><iframe title="AnimeComic ${width}px" src="/" style="width:${width}px;height:844px;border:0"></iframe></body></html>`); return;
  }
  let path;
  try { path = resolve(root, '.' + decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname)); } catch { response.writeHead(400).end(); return; }
  if (!path.startsWith(root + sep) || !existsSync(path) || !statSync(path).isFile()) { response.writeHead(404).end('Not found'); return; }
  response.writeHead(200, { 'Content-Type': types[extname(path)] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
  createReadStream(path).pipe(response);
}).listen(4173, '127.0.0.1', () => console.log('AnimeComic Web: http://127.0.0.1:4173'));

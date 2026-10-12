// SPDX-License-Identifier: AGPL-3.0-only
import { appendFileSync, existsSync, renameSync, statSync, rmSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createBooksServer } from './server.mjs';

const log = resolve(process.env.ProgramData ?? 'C:/ProgramData', 'AnimeComicBooksApi/logs/service.log');
if (existsSync(log) && statSync(log).size > 5 * 1024 * 1024) {
  if (existsSync(log + '.1')) rmSync(log + '.1');
  renameSync(log, log + '.1');
}
function record(event, details = {}) {
  appendFileSync(log, JSON.stringify({ time: new Date().toISOString(), event, ...details }) + '\n');
}
process.on('uncaughtException', error => { record('fatal', { message: error.message }); process.exit(1); });
process.on('unhandledRejection', error => { record('fatal', { message: String(error) }); process.exit(1); });
const imageSecret = readFileSync(resolve(process.env.ProgramData ?? 'C:/ProgramData', 'AnimeComicBooksApi/config/image-signing.key'));
const server = createBooksServer({ trustLoopbackProxy: true, imageSecret });
server.listen(4180, '127.0.0.1', () => record('started', { port: 4180, pid: process.pid }));
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close(() => process.exit(0)));

// SPDX-License-Identifier: AGPL-3.0-only
import { appendFileSync, existsSync, renameSync, statSync, rmSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createKomiicServer } from './server.mjs';
const root = resolve(process.env.ProgramData ?? 'C:/ProgramData', 'AnimeComicKomiicApi');
const log = resolve(root, 'logs/service.log');
if (existsSync(log) && statSync(log).size > 5 * 1024 * 1024) {
  if (existsSync(log + '.1')) rmSync(log + '.1');
  renameSync(log, log + '.1');
}
const record = (event, details = {}) => appendFileSync(log, JSON.stringify({ time: new Date().toISOString(), event, ...details }) + '\n');
process.on('uncaughtException', () => { record('fatal'); process.exit(1); });
process.on('unhandledRejection', () => { record('fatal'); process.exit(1); });
const server = createKomiicServer({ trustLoopbackProxy: true, secret: readFileSync(resolve(root, 'config/image-signing.key')) });
server.listen(4181, '127.0.0.1', () => record('started', { port: 4181, pid: process.pid }));
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close(() => process.exit(0)));

// SPDX-License-Identifier: AGPL-3.0-only
import {appendFileSync, existsSync, renameSync, statSync, rmSync} from 'node:fs';
import {resolve} from 'node:path';
import {createWebServer} from './server.mjs';
const log=resolve(process.env.ProgramData ?? 'C:/ProgramData','AnimeComicWebApi/logs/service.log');
if (existsSync(log) && statSync(log).size > 5*1024*1024) {
  if (existsSync(log+'.1')) rmSync(log+'.1');
  renameSync(log,log+'.1');
}
const record=event=>appendFileSync(log,JSON.stringify({time:new Date().toISOString(),event,pid:process.pid})+'\n');
// Request URLs, headers, bodies and exception messages can contain credentials and are never logged.
process.on('uncaughtException',()=>{record('fatal');process.exit(1);});
process.on('unhandledRejection',()=>{record('fatal');process.exit(1);});
const server=createWebServer({trustLoopbackProxy:true});
server.listen(4182,'127.0.0.1',()=>record('started'));
for(const signal of ['SIGINT','SIGTERM']) process.on(signal,()=>server.close(()=>process.exit(0)));

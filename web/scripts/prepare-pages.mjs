// SPDX-License-Identifier: AGPL-3.0-only
// Creates a Pages artifact and a corresponding-source archive without changing the main Git checkout.
import {readFileSync, readdirSync, mkdirSync, cpSync, writeFileSync, existsSync} from 'node:fs';
import {resolve, join, relative, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
const web=resolve(dirname(fileURLToPath(import.meta.url)),'..'), root=resolve(web,'..');
const output=resolve(process.argv[2] || resolve(root,'../artifacts/pages-release-20261012'));
const flavor=process.argv.includes('--development')?'developmentExecutable':'productionExecutable';
const dist=resolve(web,'build/dist/wasmJs',flavor);
if(!existsSync(join(dist,'index.html')))throw new Error('Build the Web distribution first');
mkdirSync(output,{recursive:true});
cpSync(dist,output,{recursive:true,filter:path=>!path.endsWith('.map')});
writeFileSync(join(output,'.nojekyll'),'');
// Pages caches stable filenames. Version entry points and relative JS imports together.
const scripts = readdirSync(dist).filter(name=>/\.(?:m?js|css|html)$/.test(name)).sort();
const version = createHash('sha256').update(readFileSync(fileURLToPath(import.meta.url)));
for(const name of scripts)version.update(name).update(readFileSync(join(dist,name)));
const assetVersion=version.digest('hex').slice(0,16);
for(const name of scripts.filter(name=>name !== 'animecomic.js' && /\.m?js$/.test(name))) {
  const text=readFileSync(join(dist,name),'utf8').replace(/(['"])(\.\/[a-zA-Z0-9_./-]+\.m?js)\1/g, (_,quote,url)=>quote+url+'?v='+assetVersion+quote);
  writeFileSync(join(output,name),text);
}
let html=readFileSync(join(dist,'index.html'),'utf8');
for(const name of ['animecomic.js','api-endpoints.js','oauth.js','bridge.js','browser.css','resource-cache.mjs'])html=html.replaceAll(name,name+'?v='+assetVersion);
writeFileSync(join(output,'index.html'),html);
cpSync(join(root,'LICENSE.txt'),join(output,'LICENSE.txt'));

execFileSync(process.execPath,[join(web,'audit-shared-sources.mjs')],{cwd:root});
const audit=JSON.parse(readFileSync(join(web,'build/shared-source-audit.json')));
const files=new Set(audit.shared);
function add(path){const absolute=join(root,path);for(const e of readdirSync(absolute,{withFileTypes:true})){
 const p=join(path,e.name);if(e.isDirectory())add(p);else files.add(p.replaceAll('\\','/'));
}}
for(const dir of ['services/web-api','web/src','web/tests','web/scripts','web/kotlin-js-store','app/shared/app-lang/src/commonMain/kotlin','app/shared/app-lang/src/androidMain/res'])add(dir);
for(const path of ['gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties','gradle/libs.versions.toml','LICENSE.txt','app/shared/thirdparty/reorderable/LICENSE','subs/manga.json','app/desktop/src/main/composeResources/drawable/a_round.png','web/app-language/build.gradle.kts'])files.add(path);
for(const e of readdirSync(web,{withFileTypes:true}))if(e.isFile() && !e.name.endsWith('.log'))files.add('web/'+e.name);
const manifest=join(output,'source-files.json');writeFileSync(manifest,JSON.stringify([...files].sort()));
const pack = String.raw`import sys,json,zipfile,pathlib
root=pathlib.Path(sys.argv[1]); out=pathlib.Path(sys.argv[2]); files=json.loads((out/'source-files.json').read_text())
with zipfile.ZipFile(out/'source.zip','w',zipfile.ZIP_DEFLATED) as z:
 for name in files: z.write(root/name,name)
 z.writestr('BUILD-WEB.txt','Corresponding source for AnimeComic Web, AGPL-3.0. Original shared Kotlin paths are preserved. This archive contains the Web build and its shared App source dependencies, not the complete native App checkout. Requires JDK 21. From the archive root run: ./gradlew -p web :wasmJsBrowserDistribution. Node tests: node --test web/tests/*.test.mjs. Runtime API endpoints: web/src/wasmJsMain/resources/api-endpoints.js. Native App/upstream: https://github.com/Zhgma/animeko and https://github.com/open-ani/animeko.')
`;
execFileSync('python',['-X','utf8','-c',pack,root,output]);
writeFileSync(join(output,'release.json'),JSON.stringify({builtAt:new Date().toISOString(),assetVersion,flavor,sharedFiles:audit.shared.length,source:'source.zip',upstream:execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim()},null,2));
console.log(output);


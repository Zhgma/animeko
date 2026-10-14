// SPDX-License-Identifier: AGPL-3.0-only
// Inventory the actual Gradle source allowlist; classify changed App files against it.
import { readFileSync, readdirSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const web = dirname(fileURLToPath(import.meta.url)), root = resolve(web, '..');
const gradle = readFileSync(resolve(web, 'build.gradle.kts'), 'utf8');
const roots = [...gradle.matchAll(/kotlin\.srcDir\("([^"]+)"\)/g)].map(m => resolve(web, m[1]));
const includes = [...gradle.matchAll(/kotlin\.include\(([\s\S]*?)\)/g)].flatMap(m => [...m[1].matchAll(/"([^"]+)"/g)].map(v => v[1]));
const slash = value => value.replaceAll('\\', '/');
const matches = includes.map(pattern => new RegExp('^' + pattern.split('**').map(part => part.split('*').map(v => v.replace(/[.+?^${}()|[\]\\]/g, '\\$&')).join('[^/]*')).join('.*') + '$'));
function* walk(directory) {
  for (const entry of readdirSync(directory, {withFileTypes:true})) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) yield* walk(path);
    else if (entry.name.endsWith('.kt')) yield path;
  }
}
const shared = [...new Set(roots.flatMap(directory => [...walk(directory)].filter(path => matches.some(m => m.test(slash(relative(directory, path)))))))].map(path => slash(relative(root, path))).sort();
const changed = [...new Set(execFileSync('git', ['ls-files', '-m', '-o', '--exclude-standard', '-z'], {cwd:root, encoding:'utf8'}).split('\0').filter(path => path.startsWith('app/') && path.endsWith('.kt')))];
const report = {
  generatedAt: new Date().toISOString(),
  note: 'File inclusion proves shared source compilation, not full page or platform behavior parity.',
  shared,
  changedShared: changed.filter(path => shared.includes(path)).sort(),
  changedNotShared: changed.filter(path => !shared.includes(path)).sort(),
};
mkdirSync(resolve(web, 'build'), {recursive:true});
writeFileSync(resolve(web, 'build/shared-source-audit.json'), JSON.stringify(report, null, 2) + '\n');
console.log(`Shared App Kotlin files: ${shared.length}; changed included: ${report.changedShared.length}; changed outside Web: ${report.changedNotShared.length}`);
console.log('Full paths: web/build/shared-source-audit.json');
if (process.argv.includes('--list')) console.log(shared.join('\n'));

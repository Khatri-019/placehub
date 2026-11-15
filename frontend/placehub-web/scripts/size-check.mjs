// Bundle budget check (BUILD_SPEC 20.5). Run after `vite build`:
//   initial load (index.html + every asset it references) <= 120 KB gzip
//   each lazily loaded role chunk (student, recruiter, admin; JS + its CSS) <= 80 KB gzip
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';

const DIST = new URL('../dist/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const INITIAL_BUDGET = 120 * 1024;
const ROLE_BUDGET = 80 * 1024;
const ROLE_CHUNKS = ['student', 'recruiter', 'admin'];

const gz = (path) => gzipSync(readFileSync(path), { level: 9 }).length;
const kb = (n) => `${(n / 1024).toFixed(1)} KB`;

const html = readFileSync(join(DIST, 'index.html'), 'utf8');
const referenced = [...html.matchAll(/(?:src|href)="\/(assets\/[^"]+)"/g)].map((m) => m[1]);
let initial = gz(join(DIST, 'index.html'));
for (const asset of referenced) initial += gz(join(DIST, asset));

const assets = readdirSync(join(DIST, 'assets'));
const initialSet = new Set(referenced.map((a) => a.replace('assets/', '')));

/** A chunk plus every non-initial chunk it statically imports (what navigating into the area downloads). */
function closure(file, seen = new Set()) {
  if (seen.has(file) || initialSet.has(file)) return seen;
  seen.add(file);
  if (file.endsWith('.js')) {
    const src = readFileSync(join(DIST, 'assets', file), 'utf8');
    for (const m of src.matchAll(/["'`]\.\/([\w.-]+\.(?:js|css))["'`]/g)) closure(m[1], seen);
    const css = file.replace(/\.js$/, '.css');
    if (assets.includes(css)) closure(css, seen);
  }
  return seen;
}
let failed = false;
const rows = [['initial (landing/login)', initial, INITIAL_BUDGET]];
for (const role of ROLE_CHUNKS) {
  const files = assets.filter((f) => f.startsWith(`${role}-`) && (f.endsWith('.js') || f.endsWith('.css')));
  if (!files.some((f) => f.endsWith('.js'))) {
    console.error(`Missing lazy chunk for ${role}; is route-level code splitting still in place?`);
    failed = true;
    continue;
  }
  const all = new Set();
  files.forEach((f) => closure(f, all));
  rows.push([`${role} chunk (+shared)`, [...all].reduce((sum, f) => sum + gz(join(DIST, 'assets', f)), 0), ROLE_BUDGET]);
}

for (const [name, size, budget] of rows) {
  const ok = size <= budget;
  failed ||= !ok;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name.padEnd(26)} ${kb(size).padStart(9)} / ${kb(budget)}`);
}
if (referenced.some((a) => ROLE_CHUNKS.some((r) => a.startsWith(`assets/${r}-`)))) {
  console.error('FAIL  a role chunk is referenced from index.html (it would load for every visitor)');
  failed = true;
}
const fonts = [...html.matchAll(/href="\/(fonts\/[^"]+)"/g)].map((m) => m[1]);
for (const f of fonts) console.log(`INFO  preloaded font ${f}: ${kb(readFileSync(join(DIST, f)).length)} (woff2, already compressed; not in the JS/CSS budget)`);
process.exit(failed ? 1 : 0);

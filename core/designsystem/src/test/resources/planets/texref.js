// Reference texels for PlanetTexturesTest, computed by the design's own JavaScript.
// Run: node core/designsystem/src/test/resources/planets/texref.js design/shubhvela-screens.html
// Evaluates the "utilities" and "procedural surface maps" blocks of the HTML unchanged, then prints, per type,
// the sum of all channels rounded to 0..255 integers and a few sample texels (index i = j * 512 + x).
const fs = require('fs');
const html = fs.readFileSync(process.argv[2], 'utf8');
const a = html.indexOf('/* ---------- utilities ---------- */');
const b = html.indexOf('/* ---------- sphere renderer');
const code = html.slice(a, b) + '\n;globalThis.makeTex = makeTex;';
(0, eval)(code);
const toByte = v => Math.max(0, Math.min(255, Math.floor(v + 0.5)));
const samples = [0, 777, 32768 + 100, 65536 + 256, 70000, 100001, 131071];
for (const type of ['moon', 'mercury', 'venus', 'mars', 'jupiter', 'saturn', 'sun']) {
  const t0 = Date.now();
  const d = makeTex(type);
  const ms = Date.now() - t0;
  let sum = 0;
  for (let k = 0; k < d.length; k++) sum += toByte(d[k]);
  const s = samples.map(i => [toByte(d[i * 3]), toByte(d[i * 3 + 1]), toByte(d[i * 3 + 2])]);
  console.log(type, sum, JSON.stringify(s), ms + 'ms');
}

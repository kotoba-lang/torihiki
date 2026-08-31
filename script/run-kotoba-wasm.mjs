import fs from 'node:fs';
const { instantiateKotoba } = await import(process.argv[3]);
const k = await instantiateKotoba(fs.readFileSync(process.argv[2]), {});
const e = k.instance.exports;
const cases = JSON.parse(fs.readFileSync(process.argv[4], 'utf8'));
const out = cases.map(([fn, ...args]) => {
  const f = e[fn];
  if (!f) return [fn, args, 'NO-EXPORT'];
  try { return [fn, args, String(f(...args.map(BigInt)))]; }
  catch (err) { return [fn, args, 'TRAP:' + err.message]; }
});
console.log(JSON.stringify(out));

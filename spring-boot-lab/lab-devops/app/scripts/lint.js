// "Lint" mínimo e multiplataforma: verifica a sintaxe de todos os ficheiros JS
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const dirs = ['src', 'scripts', 'test'].map((d) => path.join(__dirname, '..', d));
let failed = 0;
for (const dir of dirs) {
  for (const f of fs.readdirSync(dir).filter((f) => f.endsWith('.js'))) {
    try {
      execFileSync(process.execPath, ['--check', path.join(dir, f)], { stdio: 'pipe' });
    } catch (err) {
      failed++;
      console.error(`✗ ${f}\n${err.stderr}`);
    }
  }
}
if (failed) process.exit(1);
console.log('lint ok');

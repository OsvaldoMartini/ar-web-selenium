#!/usr/bin/env node
/**
 * Build all plugins in one shot.
 * Step 1: esbuild bundles + minifies
 * Step 2: javascript-obfuscator protects the output
 *
 * Usage:
 *   cd plugins
 *   node build-plugins.js              # build + obfuscate
 *   node build-plugins.js --no-obfuscate  # build only (faster, for dev)
 */

const { buildSync } = require('esbuild');
const JavaScriptObfuscator = require('javascript-obfuscator');
const path = require('path');
const fs = require('fs');

const obfuscate = !process.argv.includes('--no-obfuscate');

const plugins = [
  { name: 'hoverPick',       entry: 'hoverPick/index.js',       outfile: 'hoverPick/build/hoverPick.min.js' },
  { name: 'pageScanner',     entry: 'pageScanner/index.js',     outfile: 'pageScanner/build/scanner.min.js' },
  { name: 'searchList',      entry: 'searchList/index.js',      outfile: 'searchList/build/searchList.min.js' },
  { name: 'searchListAsync', entry: 'searchListAsync/index.js', outfile: 'searchListAsync/build/searchListAsync.min.js' },
  { name: 'pluginTest',      entry: 'pluginTest/index.js',      outfile: 'pluginTest/build/pluginTest.min.js' },
  { name: 'actionExecutor',  entry: 'actionExecutor/index.js',  outfile: 'actionExecutor/build/actionExecutor.min.js' },
];

// Obfuscation settings — strong protection, keeps runtime performance
const obfuscatorOptions = {
  compact: true,
  controlFlowFlattening: true,
  controlFlowFlatteningThreshold: 0.5,
  deadCodeInjection: true,
  deadCodeInjectionThreshold: 0.2,
  identifierNamesGenerator: 'hexadecimal',
  renameGlobals: false,          // keep window.* globals intact
  selfDefending: false,          // disabled — breaks Selenium executeScript
  stringArray: true,
  stringArrayEncoding: ['base64'],
  stringArrayThreshold: 0.75,
  transformObjectKeys: true,
  unicodeEscapeSequence: false,
};

const pluginsDir = __dirname;
let built = 0;
let skipped = 0;

console.log(`=== Building All Plugins ${obfuscate ? '(+ obfuscation)' : '(no obfuscation)'} ===\n`);

for (const plugin of plugins) {
  const entryPath = path.join(pluginsDir, plugin.entry);
  const outPath = path.join(pluginsDir, plugin.outfile);

  if (!fs.existsSync(entryPath)) {
    console.log(`  [skip] ${plugin.name} — ${plugin.entry} not found`);
    skipped++;
    continue;
  }

  try {
    // Step 1: esbuild bundle + minify
    buildSync({
      entryPoints: [entryPath],
      bundle: true,
      minify: true,
      outfile: outPath,
    });

    let size = (fs.statSync(outPath).size / 1024).toFixed(1);

    // Step 2: obfuscate
    if (obfuscate) {
      const code = fs.readFileSync(outPath, 'utf8');
      const result = JavaScriptObfuscator.obfuscate(code, obfuscatorOptions);
      fs.writeFileSync(outPath, result.getObfuscatedCode(), 'utf8');
      size = (fs.statSync(outPath).size / 1024).toFixed(1);
      console.log(`  [done] ${plugin.name} — ${size} KB (obfuscated)`);
    } else {
      console.log(`  [done] ${plugin.name} — ${size} KB`);
    }

    built++;
  } catch (err) {
    console.error(`  [FAIL] ${plugin.name}: ${err.message}`);
  }
}

console.log(`\nDone: ${built} built, ${skipped} skipped.`);

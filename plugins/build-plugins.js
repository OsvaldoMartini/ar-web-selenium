#!/usr/bin/env node
/**
 * Build all plugins in one shot.
 *
 * Usage:
 *   cd plugins
 *   node build-plugins.js
 */

const { buildSync } = require('esbuild');
const path = require('path');
const fs = require('fs');

const plugins = [
  { name: 'hoverPick',       entry: 'hoverPick/index.js',       outfile: 'hoverPick/build/hoverPick.min.js' },
  { name: 'pageScanner',     entry: 'pageScanner/index.js',     outfile: 'pageScanner/build/scanner.min.js' },
  { name: 'searchList',      entry: 'searchList/index.js',      outfile: 'searchList/build/searchList.min.js' },
  { name: 'searchListAsync', entry: 'searchListAsync/index.js', outfile: 'searchListAsync/build/searchListAsync.min.js' },
  { name: 'pluginTest',      entry: 'pluginTest/index.js',      outfile: 'pluginTest/build/pluginTest.min.js' },
  { name: 'actionExecutor',  entry: 'actionExecutor/index.js',  outfile: 'actionExecutor/build/actionExecutor.min.js' },
];

const pluginsDir = __dirname;
let built = 0;
let skipped = 0;

console.log('=== Building All Plugins ===\n');

for (const plugin of plugins) {
  const entryPath = path.join(pluginsDir, plugin.entry);
  const outPath = path.join(pluginsDir, plugin.outfile);

  if (!fs.existsSync(entryPath)) {
    console.log(`  [skip] ${plugin.name} — ${plugin.entry} not found`);
    skipped++;
    continue;
  }

  try {
    const result = buildSync({
      entryPoints: [entryPath],
      bundle: true,
      minify: true,
      outfile: outPath,
    });

    const size = (fs.statSync(outPath).size / 1024).toFixed(1);
    console.log(`  [done] ${plugin.name} — ${size} KB`);
    built++;
  } catch (err) {
    console.error(`  [FAIL] ${plugin.name}: ${err.message}`);
  }
}

console.log(`\nDone: ${built} built, ${skipped} skipped.`);

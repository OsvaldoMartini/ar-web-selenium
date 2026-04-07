#!/usr/bin/env node
/**
 * Plugin hot-reload dev script.
 *
 * Watches all plugin source files and auto-rebuilds the .min.js bundles
 * when changes are detected. Works with the Java PluginFileWatcher that
 * monitors build/ directories and clears plugin caches automatically.
 *
 * Usage:
 *   cd plugins
 *   node watch-plugins.js
 *
 * Or via npm (if you add it to package.json scripts):
 *   npm run watch
 *
 * Requires: esbuild (npm install -g esbuild  OR  npx will fetch it)
 */

const { context } = require('esbuild');
const path = require('path');

// ── Plugin definitions ─────────────────────────────────────────────────────
// Each entry: { name, entry, outfile }
const plugins = [
  {
    name: 'hoverPick',
    entry: 'hoverPick/index.js',
    outfile: 'hoverPick/build/hoverPick.min.js',
  },
  {
    name: 'pageScanner',
    entry: 'pageScanner/index.js',
    outfile: 'pageScanner/build/scanner.min.js',
  },
  {
    name: 'searchList',
    entry: 'searchList/index.js',
    outfile: 'searchList/build/searchList.min.js',
  },
  {
    name: 'searchListAsync',
    entry: 'searchListAsync/index.js',
    outfile: 'searchListAsync/build/searchListAsync.min.js',
  },
  {
    name: 'pluginTest',
    entry: 'pluginTest/index.js',
    outfile: 'pluginTest/build/pluginTest.min.js',
  },
  {
    name: 'actionExecutor',
    entry: 'actionExecutor/index.js',
    outfile: 'actionExecutor/build/actionExecutor.min.js',
  },
];

// ── Start watchers ─────────────────────────────────────────────────────────

const pluginsDir = __dirname;

async function startWatchers() {
  console.log('=== Plugin Hot Reload ===\n');

  const watchers = [];

  for (const plugin of plugins) {
    const entryPath = path.join(pluginsDir, plugin.entry);
    const outPath = path.join(pluginsDir, plugin.outfile);

    // Skip plugins that don't exist yet
    try {
      require('fs').accessSync(entryPath);
    } catch {
      console.log(`  [skip] ${plugin.name} — ${plugin.entry} not found`);
      continue;
    }

    try {
      const ctx = await context({
        entryPoints: [entryPath],
        bundle: true,
        minify: true,
        outfile: outPath,
        logLevel: 'info',
        plugins: [{
          name: 'rebuild-log',
          setup(build) {
            build.onEnd((result) => {
              const time = new Date().toLocaleTimeString();
              if (result.errors.length === 0) {
                console.log(`  [${time}] ${plugin.name} rebuilt OK`);
              } else {
                console.error(`  [${time}] ${plugin.name} FAILED — ${result.errors.length} error(s)`);
              }
            });
          },
        }],
      });

      await ctx.watch();
      watchers.push(ctx);
      console.log(`  [watch] ${plugin.name}`);
    } catch (err) {
      console.error(`  [error] ${plugin.name}: ${err.message}`);
    }
  }

  console.log(`\nWatching ${watchers.length} plugins. Press Ctrl+C to stop.\n`);

  // Graceful shutdown
  process.on('SIGINT', async () => {
    console.log('\nStopping watchers...');
    for (const ctx of watchers) {
      await ctx.dispose();
    }
    process.exit(0);
  });
}

startWatchers().catch((err) => {
  console.error('Failed to start watchers:', err);
  process.exit(1);
});

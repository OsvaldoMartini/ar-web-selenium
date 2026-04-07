#!/usr/bin/env node
/**
 * Encrypts all plugin .min.js files with AES-256-GCM.
 *
 * Output: {plugin}/build/{name}.min.enc  (encrypted bundle)
 *         plugins.key                     (encryption key — keep secret!)
 *
 * Usage:
 *   node encrypt-plugins.js                  # generate new key + encrypt
 *   node encrypt-plugins.js --key <hex>      # use existing key
 *
 * The Java class EncryptedPluginLoader decrypts these at runtime.
 */

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 12;   // GCM standard
const TAG_LENGTH = 16;  // GCM auth tag

const plugins = [
  { name: 'hoverPick',       file: 'hoverPick/build/hoverPick.min.js' },
  { name: 'pageScanner',     file: 'pageScanner/build/scanner.min.js' },
  { name: 'searchList',      file: 'searchList/build/searchList.min.js' },
  { name: 'searchListAsync', file: 'searchListAsync/build/searchListAsync.min.js' },
  { name: 'pluginTest',      file: 'pluginTest/build/pluginTest.min.js' },
  { name: 'actionExecutor',  file: 'actionExecutor/build/actionExecutor.min.js' },
];

const pluginsDir = __dirname;

// ── Key management ──────────────────────────────────────────────────────────

let keyHex;
const keyArgIdx = process.argv.indexOf('--key');
if (keyArgIdx !== -1 && process.argv[keyArgIdx + 1]) {
  keyHex = process.argv[keyArgIdx + 1];
  console.log('Using provided key.\n');
} else {
  const keyPath = path.join(pluginsDir, 'plugins.key');
  if (fs.existsSync(keyPath)) {
    keyHex = fs.readFileSync(keyPath, 'utf8').trim();
    console.log('Using existing key from plugins.key\n');
  } else {
    keyHex = crypto.randomBytes(32).toString('hex');
    fs.writeFileSync(keyPath, keyHex, 'utf8');
    console.log('Generated NEW key → plugins.key (keep this secret!)\n');
  }
}

const key = Buffer.from(keyHex, 'hex');

// ── Encrypt ─────────────────────────────────────────────────────────────────

console.log('=== Encrypting Plugins (AES-256-GCM) ===\n');

let encrypted = 0;
for (const plugin of plugins) {
  const inPath = path.join(pluginsDir, plugin.file);
  const outPath = inPath.replace('.min.js', '.min.enc');

  if (!fs.existsSync(inPath)) {
    console.log(`  [skip] ${plugin.name} — not found`);
    continue;
  }

  const plaintext = fs.readFileSync(inPath);
  const iv = crypto.randomBytes(IV_LENGTH);
  const cipher = crypto.createCipheriv(ALGORITHM, key, iv);

  const encryptedData = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  const tag = cipher.getAuthTag();

  // File format: [IV (12 bytes)] [Auth Tag (16 bytes)] [Encrypted Data]
  const output = Buffer.concat([iv, tag, encryptedData]);
  fs.writeFileSync(outPath, output);

  const sizeKB = (output.length / 1024).toFixed(1);
  console.log(`  [done] ${plugin.name} — ${sizeKB} KB → ${path.basename(outPath)}`);
  encrypted++;
}

console.log(`\nDone: ${encrypted} encrypted.`);
console.log(`\nKey (for Java EncryptedPluginLoader):\n  ${keyHex}`);
console.log(`\nIMPORTANT: Store the key securely. Without it, .enc files are useless.`);

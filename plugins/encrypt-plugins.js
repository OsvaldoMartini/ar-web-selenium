#!/usr/bin/env node
/**
 * Encrypts all plugin .min.js files with AES-256-GCM.
 *
 * Output: {plugin}/build/{name}.min.enc  (encrypted bundle)
 *         {plugin}.zip                   (zip containing the .enc)
 *         plugins.key                    (encryption key — keep secret!)
 *
 * Usage:
 *   node encrypt-plugins.js                                     # new key, plain hex
 *   node encrypt-plugins.js --password                          # new key, password protected (prompts)
 *   node encrypt-plugins.js --password "secret"                 # inline password
 *   node encrypt-plugins.js --key <hex>                         # reuse existing key
 *   node encrypt-plugins.js --owner "user@email.com"            # owner metadata
 *   node encrypt-plugins.js --license "D:\path\to\ARWeb.lic"   # custom license
 *
 * The Java class EncryptedPluginLoader decrypts these at runtime.
 */

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const readline = require('readline');
const { createWriteStream } = require('fs');

const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 12;   // GCM standard
const TAG_LENGTH = 16;  // GCM auth tag
const SALT_LENGTH = 16;
const PBKDF2_ITERATIONS = 100_000;
const KEY_LENGTH = 32;  // AES-256

const DEFAULT_LICENSE = 'D:/Projects/ARWeb-Martini/ARWeb-Scanner/ARWeb.lic';

const plugins = [
  { name: 'hoverPick',       file: 'hoverPick/build/hoverPick.min.js' },
  { name: 'pageScanner',     file: 'pageScanner/build/scanner.min.js' },
  { name: 'searchList',      file: 'searchList/build/searchList.min.js' },
  { name: 'searchListAsync', file: 'searchListAsync/build/searchListAsync.min.js' },
  { name: 'pluginTest',      file: 'pluginTest/build/pluginTest.min.js' },
  { name: 'actionExecutor',  file: 'actionExecutor/build/actionExecutor.min.js' },
];

const pluginsDir = __dirname;

// ── Crypto helpers ───────────────────────────────────────────────────────────

function wrapKeyProtected(pluginKey, password, fingerprint) {
  const salt = crypto.randomBytes(SALT_LENGTH);
  const iv = crypto.randomBytes(IV_LENGTH);
  const combined = password + '|' + fingerprint;
  const wrapperKey = crypto.pbkdf2Sync(combined, salt, PBKDF2_ITERATIONS, KEY_LENGTH, 'sha256');
  const cipher = crypto.createCipheriv(ALGORITHM, wrapperKey, iv);
  const encrypted = Buffer.concat([cipher.update(pluginKey), cipher.final()]);
  const tag = cipher.getAuthTag();
  return 'PROTECTED:' + Buffer.concat([salt, iv, encrypted, tag]).toString('base64');
}

// Simple CRC-32 for ZIP
const crcTable = (() => {
  const t = new Int32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let j = 0; j < 8; j++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    t[i] = c;
  }
  return t;
})();

function crc32(buf) {
  let crc = ~0;
  for (let i = 0; i < buf.length; i++) crc = crcTable[(crc ^ buf[i]) & 0xFF] ^ (crc >>> 8);
  return ~crc;
}

function createZip(files, outPath) {
  const out = createWriteStream(outPath);
  const entries = [];
  let offset = 0;

  // Write local file headers + data
  for (const { name, data } of files) {
    const nameBytes = Buffer.from(name, 'utf8');
    const crc = crc32(data);
    const lfh = Buffer.alloc(30 + nameBytes.length);
    lfh.writeUInt32LE(0x04034b50, 0);
    lfh.writeUInt16LE(20, 4);
    lfh.writeUInt16LE(0, 8);
    lfh.writeInt32LE(crc, 14);
    lfh.writeUInt32LE(data.length, 18);
    lfh.writeUInt32LE(data.length, 22);
    lfh.writeUInt16LE(nameBytes.length, 26);
    nameBytes.copy(lfh, 30);
    out.write(lfh);
    out.write(data);
    entries.push({ nameBytes, data, crc, offset });
    offset += lfh.length + data.length;
  }

  // Central directory
  const cdStart = offset;
  for (const entry of entries) {
    const cd = Buffer.alloc(46 + entry.nameBytes.length);
    cd.writeUInt32LE(0x02014b50, 0);
    cd.writeUInt16LE(20, 4);
    cd.writeUInt16LE(20, 6);
    cd.writeUInt16LE(0, 8);
    cd.writeInt32LE(entry.crc, 16);
    cd.writeUInt32LE(entry.data.length, 20);
    cd.writeUInt32LE(entry.data.length, 24);
    cd.writeUInt16LE(entry.nameBytes.length, 28);
    cd.writeUInt32LE(entry.offset, 42);
    entry.nameBytes.copy(cd, 46);
    out.write(cd);
    offset += cd.length;
  }

  // End of central directory
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(entries.length, 8);
  eocd.writeUInt16LE(entries.length, 10);
  eocd.writeUInt32LE(offset - cdStart, 12);
  eocd.writeUInt32LE(cdStart, 16);
  out.write(eocd);
  out.end();
}

function askPassword(prompt) {
  return new Promise(resolve => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    rl.question(prompt, answer => { rl.close(); resolve(answer.trim()); });
  });
}

// ── Main ─────────────────────────────────────────────────────────────────────

async function main() {
  const args = process.argv.slice(2);

  let keyHex = null;
  let usePassword = false;
  let password = null;
  let licensePath = DEFAULT_LICENSE;
  let owner = null;

  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--key'      && args[i + 1]) keyHex = args[++i];
    if (args[i] === '--license'  && args[i + 1]) licensePath = args[++i];
    if (args[i] === '--owner'    && args[i + 1]) owner = args[++i];
    if (args[i] === '--password') {
      usePassword = true;
      if (args[i + 1] && !args[i + 1].startsWith('--')) password = args[++i];
    }
  }

  console.log('=== Encrypt Plugins (AES-256-GCM) ===\n');

  // ── Resolve key ──
  const keyPath = path.join(pluginsDir, 'plugins.key');
  const rawKeyPath = path.join(pluginsDir, '.plugin-key-raw');

  if (keyHex) {
    console.log('Using provided key.\n');
  } else if (fs.existsSync(rawKeyPath)) {
    keyHex = fs.readFileSync(rawKeyPath, 'utf8').trim();
    // Only reuse if it's a plain hex key (not wrapped)
    if (/^[0-9a-fA-F]{64}$/.test(keyHex)) {
      console.log('Using existing raw key from .plugin-key-raw\n');
    } else {
      keyHex = null;
    }
  }

  if (!keyHex) {
    keyHex = crypto.randomBytes(KEY_LENGTH).toString('hex');
    console.log('Generated NEW key.\n');
  }

  const key = Buffer.from(keyHex, 'hex');

  // ── Password protection ──
  if (usePassword) {
    if (!fs.existsSync(licensePath)) {
      console.error('License not found: ' + licensePath);
      process.exit(1);
    }
    const licContent = fs.readFileSync(licensePath);
    const fingerprint = crypto.createHash('sha256').update(licContent).digest('hex');
    console.log('License:     ' + licensePath);
    console.log('Fingerprint: ' + fingerprint.substring(0, 16) + '...');
    if (owner) console.log('Owner:       ' + owner);

    if (!password) {
      password = await askPassword('\nEnter plugin password: ');
      if (!password || password.length < 4) {
        console.error('Password too short (min 4 chars)');
        process.exit(1);
      }
      const confirm = await askPassword('Confirm password:      ');
      if (password !== confirm) {
        console.error('Passwords do not match!');
        process.exit(1);
      }
    }

    const protectedKey = wrapKeyProtected(key, password, fingerprint);
    fs.writeFileSync(keyPath, protectedKey, 'utf8');
    console.log('\nKey saved as PROTECTED (password + license bound)\n');
  } else {
    fs.writeFileSync(keyPath, keyHex, 'utf8');
    console.log('Key saved as plain hex (dev mode)\n');
  }

  // Save raw key for future use
  fs.writeFileSync(rawKeyPath, keyHex, 'utf8');

  // ── Encrypt plugins ──
  console.log('=== Encrypting ===\n');

  let encrypted = 0;
  for (const plugin of plugins) {
    const inPath = path.join(pluginsDir, plugin.file);
    const outPath = inPath.replace('.min.js', '.min.enc');

    if (!fs.existsSync(inPath)) {
      console.log('  [skip] ' + plugin.name + ' — .min.js not found');
      continue;
    }

    const plaintext = fs.readFileSync(inPath);
    const iv = crypto.randomBytes(IV_LENGTH);
    const cipher = crypto.createCipheriv(ALGORITHM, key, iv);
    const encryptedData = Buffer.concat([cipher.update(plaintext), cipher.final()]);
    const tag = cipher.getAuthTag();

    // File format: [IV (12)] [Auth Tag (16)] [Encrypted Data]
    const output = Buffer.concat([iv, tag, encryptedData]);
    fs.writeFileSync(outPath, output);

    // Create ZIP containing the .enc file
    const encFileName = path.basename(outPath);
    const zipPath = path.join(pluginsDir, plugin.name + '.zip');
    createZip([{ name: encFileName, data: output }], zipPath);

    const sizeKB = (output.length / 1024).toFixed(1);
    const zipKB = (fs.statSync(zipPath).size / 1024).toFixed(1);
    console.log('  [done] ' + plugin.name + ' — ' + sizeKB + ' KB enc, ' + zipKB + ' KB zip');
    encrypted++;
  }

  // Save metadata
  if (owner || usePassword) {
    const meta = {
      owner: owner || 'unknown',
      created: new Date().toISOString(),
      keyFormat: usePassword ? 'PROTECTED' : 'plain',
      plugins: encrypted,
    };
    fs.writeFileSync(path.join(pluginsDir, '.encrypt-meta.json'), JSON.stringify(meta, null, 2), 'utf8');
  }

  console.log('\n=== Done: ' + encrypted + ' encrypted ===\n');
  console.log('Key: ' + keyHex.substring(0, 8) + '...' + (usePassword ? ' (PROTECTED)' : ' (plain hex)'));
  if (usePassword) {
    console.log('\nAt runtime the Java app will prompt for the password.');
    console.log('Delete .plugin-key-raw from production machines!');
  }
  console.log('');
}

main().catch(err => { console.error(err); process.exit(1); });

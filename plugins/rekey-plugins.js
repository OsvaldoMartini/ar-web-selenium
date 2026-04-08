#!/usr/bin/env node
/**
 * Re-key & re-encrypt all plugins with a new password-protected key.
 *
 * Flow:
 *   1. Decrypt existing .enc files with the OLD raw key (.plugin-key-raw)
 *   2. Generate a NEW random AES-256 key
 *   3. Re-encrypt all plugins with the new key
 *   4. Wrap the new key with password + license fingerprint → PROTECTED:...
 *   5. Save plugins.key + .plugin-key-raw
 *   6. Re-zip each plugin
 *
 * Usage:
 *   node rekey-plugins.js                                          (prompts for password)
 *   node rekey-plugins.js --password "secret"                      (inline password)
 *   node rekey-plugins.js --license "D:\path\to\ARWeb.lic"        (custom license)
 *   node rekey-plugins.js --owner "osvaldo.martini@gmail.com"      (owner metadata)
 */

const crypto = require('crypto')
const fs = require('fs')
const path = require('path')
const readline = require('readline')
const { createWriteStream } = require('fs')

// ── Constants ────────────────────────────────────────────────────────────────

const ALGORITHM = 'aes-256-gcm'
const IV_LENGTH = 12
const TAG_LENGTH = 16
const SALT_LENGTH = 16
const PBKDF2_ITERATIONS = 100_000
const KEY_LENGTH = 32

const DEFAULT_LICENSE = 'D:/Projects/ARWeb-Martini/ARWeb-Scanner/ARWeb.lic'

const plugins = [
  { name: 'hoverPick',       enc: 'hoverPick/hoverPick.min.enc' },
  { name: 'pageScanner',     enc: 'pageScanner/scanner.min.enc' },
  { name: 'searchList',      enc: 'searchList/searchList.min.enc' },
  { name: 'searchListAsync', enc: 'searchListAsync/searchListAsync.min.enc' },
  { name: 'pluginTest',      enc: 'pluginTest/pluginTest.min.enc' },
  { name: 'actionExecutor',  enc: 'actionExecutor/actionExecutor.min.enc' },
]

const pluginsDir = __dirname

// ── Crypto helpers ───────────────────────────────────────────────────────────

function decryptGCM(data, key) {
  if (data.length < IV_LENGTH + TAG_LENGTH) throw new Error('File too short')
  const iv  = data.subarray(0, IV_LENGTH)
  const tag = data.subarray(IV_LENGTH, IV_LENGTH + TAG_LENGTH)
  const enc = data.subarray(IV_LENGTH + TAG_LENGTH)
  const decipher = crypto.createDecipheriv(ALGORITHM, key, iv)
  decipher.setAuthTag(tag)
  return Buffer.concat([decipher.update(enc), decipher.final()])
}

function encryptGCM(data, key) {
  const iv = crypto.randomBytes(IV_LENGTH)
  const cipher = crypto.createCipheriv(ALGORITHM, key, iv)
  const encrypted = Buffer.concat([cipher.update(data), cipher.final()])
  const tag = cipher.getAuthTag()
  // Format: [IV 12] [Tag 16] [Encrypted...]
  return Buffer.concat([iv, tag, encrypted])
}

function wrapKeyProtected(pluginKey, password, fingerprint) {
  const salt = crypto.randomBytes(SALT_LENGTH)
  const iv = crypto.randomBytes(IV_LENGTH)
  const combined = password + '|' + fingerprint
  const wrapperKey = crypto.pbkdf2Sync(combined, salt, PBKDF2_ITERATIONS, KEY_LENGTH, 'sha256')
  const cipher = crypto.createCipheriv(ALGORITHM, wrapperKey, iv)
  const encrypted = Buffer.concat([cipher.update(pluginKey), cipher.final()])
  const tag = cipher.getAuthTag()
  const wrapped = Buffer.concat([salt, iv, encrypted, tag])
  return 'PROTECTED:' + wrapped.toString('base64')
}

function createZip(files, outPath) {
  // Minimal ZIP creation (store, no compression — plugins are already minified)
  const entries = []
  let offset = 0

  for (const { name, data } of files) {
    const nameBytes = Buffer.from(name, 'utf8')
    // Local file header (30 + name length)
    const lfh = Buffer.alloc(30 + nameBytes.length)
    lfh.writeUInt32LE(0x04034b50, 0)    // signature
    lfh.writeUInt16LE(20, 4)             // version needed
    lfh.writeUInt16LE(0, 6)              // flags
    lfh.writeUInt16LE(0, 8)              // compression (store)
    lfh.writeUInt16LE(0, 10)             // mod time
    lfh.writeUInt16LE(0, 12)             // mod date
    const crc = crc32(data)
    lfh.writeInt32LE(crc, 14)            // crc-32
    lfh.writeUInt32LE(data.length, 18)   // compressed size
    lfh.writeUInt32LE(data.length, 22)   // uncompressed size
    lfh.writeUInt16LE(nameBytes.length, 26) // file name length
    lfh.writeUInt16LE(0, 28)             // extra field length
    nameBytes.copy(lfh, 30)

    entries.push({ nameBytes, data, offset, crc })
    offset += lfh.length + data.length
  }

  // Write
  const out = createWriteStream(outPath)
  let cdOffset = 0

  for (const entry of entries) {
    const { nameBytes, data } = entry
    const lfh = Buffer.alloc(30 + nameBytes.length)
    lfh.writeUInt32LE(0x04034b50, 0)
    lfh.writeUInt16LE(20, 4)
    lfh.writeUInt16LE(0, 8)
    lfh.writeInt32LE(entry.crc, 14)
    lfh.writeUInt32LE(data.length, 18)
    lfh.writeUInt32LE(data.length, 22)
    lfh.writeUInt16LE(nameBytes.length, 26)
    nameBytes.copy(lfh, 30)
    out.write(lfh)
    out.write(data)
    cdOffset += lfh.length + data.length
  }

  // Central directory
  const cdStart = cdOffset
  for (const entry of entries) {
    const { nameBytes, data, offset: localOff, crc } = entry
    const cd = Buffer.alloc(46 + nameBytes.length)
    cd.writeUInt32LE(0x02014b50, 0)      // signature
    cd.writeUInt16LE(20, 4)              // version made by
    cd.writeUInt16LE(20, 6)              // version needed
    cd.writeUInt16LE(0, 8)               // flags
    cd.writeUInt16LE(0, 10)              // compression
    cd.writeUInt16LE(0, 12)              // mod time
    cd.writeUInt16LE(0, 14)              // mod date
    cd.writeInt32LE(crc, 16)             // crc-32
    cd.writeUInt32LE(data.length, 20)    // compressed size
    cd.writeUInt32LE(data.length, 24)    // uncompressed size
    cd.writeUInt16LE(nameBytes.length, 28) // file name length
    cd.writeUInt32LE(localOff, 42)       // offset of local header
    nameBytes.copy(cd, 46)
    out.write(cd)
    cdOffset += cd.length
  }

  // End of central directory
  const eocd = Buffer.alloc(22)
  eocd.writeUInt32LE(0x06054b50, 0)
  eocd.writeUInt16LE(entries.length, 8)
  eocd.writeUInt16LE(entries.length, 10)
  eocd.writeUInt32LE(cdOffset - cdStart, 12)
  eocd.writeUInt32LE(cdStart, 16)
  out.write(eocd)
  out.end()
}

// Simple CRC-32
const crcTable = (() => {
  const t = new Int32Array(256)
  for (let i = 0; i < 256; i++) {
    let c = i
    for (let j = 0; j < 8; j++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1)
    t[i] = c
  }
  return t
})()

function crc32(buf) {
  let crc = ~0
  for (let i = 0; i < buf.length; i++) crc = crcTable[(crc ^ buf[i]) & 0xFF] ^ (crc >>> 8)
  return ~crc
}

// ── Password prompt ──────────────────────────────────────────────────────────

function askPassword(prompt) {
  return new Promise(resolve => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout })
    rl.question(prompt, answer => { rl.close(); resolve(answer.trim()) })
  })
}

// ── Main ─────────────────────────────────────────────────────────────────────

async function main() {
  const args = process.argv.slice(2)

  let licensePath = DEFAULT_LICENSE
  let password = null
  let owner = null

  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--license'  && args[i + 1]) licensePath = args[++i]
    if (args[i] === '--password' && args[i + 1]) password = args[++i]
    if (args[i] === '--owner'    && args[i + 1]) owner = args[++i]
  }

  console.log('=== Re-Key & Re-Encrypt Plugins ===\n')

  // ── Validate license ──
  if (!fs.existsSync(licensePath)) {
    console.error('License not found: ' + licensePath)
    process.exit(1)
  }
  const licContent = fs.readFileSync(licensePath)
  const fingerprint = crypto.createHash('sha256').update(licContent).digest('hex')
  console.log('License:     ' + licensePath)
  console.log('Fingerprint: ' + fingerprint.substring(0, 16) + '...')
  if (owner) console.log('Owner:       ' + owner)

  // ── Load old key ──
  const rawKeyPath = path.join(pluginsDir, '.plugin-key-raw')
  if (!fs.existsSync(rawKeyPath)) {
    console.error('\n.plugin-key-raw not found — cannot decrypt existing plugins.')
    console.error('If you have the hex key, save it to .plugin-key-raw and retry.')
    process.exit(1)
  }
  const oldKeyHex = fs.readFileSync(rawKeyPath, 'utf8').trim()
  const oldKey = Buffer.from(oldKeyHex, 'hex')
  console.log('Old key:     ' + oldKeyHex.substring(0, 8) + '...\n')

  // ── Prompt for password ──
  if (!password) {
    password = await askPassword('Enter new password: ')
    if (!password || password.length < 4) {
      console.error('Password too short (min 4 chars)')
      process.exit(1)
    }
    const confirm = await askPassword('Confirm password:   ')
    if (password !== confirm) {
      console.error('Passwords do not match!')
      process.exit(1)
    }
  }

  // ── Generate new key ──
  const newKey = crypto.randomBytes(KEY_LENGTH)
  const newKeyHex = newKey.toString('hex')
  console.log('\nNew key:     ' + newKeyHex.substring(0, 8) + '...')

  // ── Decrypt → Re-encrypt each plugin ──
  console.log('\n=== Processing Plugins ===\n')

  let count = 0
  for (const plugin of plugins) {
    const encPath = path.join(pluginsDir, plugin.enc)

    if (!fs.existsSync(encPath)) {
      console.log('  [skip] ' + plugin.name + ' — .enc not found')
      continue
    }

    // Decrypt with old key
    const encData = fs.readFileSync(encPath)
    let plaintext
    try {
      plaintext = decryptGCM(encData, oldKey)
    } catch (e) {
      console.log('  [FAIL] ' + plugin.name + ' — decrypt failed: ' + e.message)
      continue
    }

    // Re-encrypt with new key
    const newEncData = encryptGCM(plaintext, newKey)
    fs.writeFileSync(encPath, newEncData)

    // Re-zip
    const encFileName = path.basename(plugin.enc)
    const zipPath = path.join(pluginsDir, plugin.name + '.zip')
    createZip([{ name: encFileName, data: newEncData }], zipPath)

    const sizeKB = (newEncData.length / 1024).toFixed(1)
    console.log('  [done] ' + plugin.name + ' — ' + sizeKB + ' KB')
    count++
  }

  // ── Save new PROTECTED key ──
  const protectedKey = wrapKeyProtected(newKey, password, fingerprint)
  const keyPath = path.join(pluginsDir, 'plugins.key')
  fs.writeFileSync(keyPath, protectedKey, 'utf8')

  // Save raw key for future re-keying
  fs.writeFileSync(rawKeyPath, newKeyHex, 'utf8')

  // Save metadata
  const meta = {
    owner: owner || 'unknown',
    created: new Date().toISOString(),
    keyFormat: 'PROTECTED',
    fingerprint: fingerprint.substring(0, 16),
    plugins: count,
  }
  fs.writeFileSync(path.join(pluginsDir, '.rekey-meta.json'), JSON.stringify(meta, null, 2), 'utf8')

  console.log('\n=== Done ===\n')
  console.log('  Plugins re-encrypted: ' + count)
  console.log('  Key format:           PROTECTED (password + license bound)')
  console.log('  plugins.key saved:    ' + keyPath)
  if (owner) console.log('  Owner:                ' + owner)
  console.log('\n  To decrypt at runtime, the Java app will prompt for the password.')
  console.log('  Delete .plugin-key-raw from production machines!\n')
}

main().catch(err => { console.error(err); process.exit(1) })

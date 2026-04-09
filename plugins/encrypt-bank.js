#!/usr/bin/env node
/**
 * Encrypt plugins with a MASTER KEY for bank/client distribution.
 *
 * The bank receives:  .zip files + plugins.key (plain hex key)
 * To decrypt:         same hex key is needed — no password, no license.
 *
 * Usage:
 *   node encrypt-bank.js                          # generate new key
 *   node encrypt-bank.js --key <64-char-hex>      # use existing master key
 *   node encrypt-bank.js --client "Bank Name"     # tag with client name
 *
 * Output:
 *   {plugin}/build/{name}.min.enc   — encrypted file
 *   {plugin}.zip                    — zip containing the .enc
 *   plugins.key                     — plain hex key (give to the bank)
 */

const crypto = require('crypto')
const fs = require('fs')
const path = require('path')
const { createWriteStream } = require('fs')

const ALGORITHM = 'aes-256-gcm'
const IV_LENGTH = 12
const KEY_LENGTH = 32

const plugins = [
  { name: 'hoverPick',       file: 'hoverPick/build/hoverPick.min.js' },
  { name: 'pageScanner',     file: 'pageScanner/build/scanner.min.js' },
  { name: 'searchList',      file: 'searchList/build/searchList.min.js' },
  { name: 'searchListAsync', file: 'searchListAsync/build/searchListAsync.min.js' },
  { name: 'pluginTest',      file: 'pluginTest/build/pluginTest.min.js' },
  { name: 'actionExecutor',  file: 'actionExecutor/build/actionExecutor.min.js' },
]

const pluginsDir = __dirname

// ── ZIP helper ───────────────────────────────────────────────────────────────

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

function createZip(files, outPath) {
  const out = createWriteStream(outPath)
  const entries = []
  let offset = 0

  for (const { name, data } of files) {
    const nameBytes = Buffer.from(name, 'utf8')
    const c = crc32(data)
    const lfh = Buffer.alloc(30 + nameBytes.length)
    lfh.writeUInt32LE(0x04034b50, 0)
    lfh.writeUInt16LE(20, 4)
    lfh.writeUInt16LE(0, 8)
    lfh.writeInt32LE(c, 14)
    lfh.writeUInt32LE(data.length, 18)
    lfh.writeUInt32LE(data.length, 22)
    lfh.writeUInt16LE(nameBytes.length, 26)
    nameBytes.copy(lfh, 30)
    out.write(lfh)
    out.write(data)
    entries.push({ nameBytes, data, crc: c, offset })
    offset += lfh.length + data.length
  }

  const cdStart = offset
  for (const e of entries) {
    const cd = Buffer.alloc(46 + e.nameBytes.length)
    cd.writeUInt32LE(0x02014b50, 0)
    cd.writeUInt16LE(20, 4)
    cd.writeUInt16LE(20, 6)
    cd.writeInt32LE(e.crc, 16)
    cd.writeUInt32LE(e.data.length, 20)
    cd.writeUInt32LE(e.data.length, 24)
    cd.writeUInt16LE(e.nameBytes.length, 28)
    cd.writeUInt32LE(e.offset, 42)
    e.nameBytes.copy(cd, 46)
    out.write(cd)
    offset += cd.length
  }

  const eocd = Buffer.alloc(22)
  eocd.writeUInt32LE(0x06054b50, 0)
  eocd.writeUInt16LE(entries.length, 8)
  eocd.writeUInt16LE(entries.length, 10)
  eocd.writeUInt32LE(offset - cdStart, 12)
  eocd.writeUInt32LE(cdStart, 16)
  out.write(eocd)
  out.end()
}

// ── Main ─────────────────────────────────────────────────────────────────────

const args = process.argv.slice(2)

let keyHex = null
let clientName = null

for (let i = 0; i < args.length; i++) {
  if (args[i] === '--key'    && args[i + 1]) keyHex = args[++i]
  if (args[i] === '--client' && args[i + 1]) clientName = args[++i]
}

console.log('=== Encrypt Plugins — Bank Master Key ===\n')

if (keyHex) {
  if (!/^[0-9a-fA-F]{64}$/.test(keyHex)) {
    console.error('Invalid key: must be 64 hex characters (256-bit)')
    process.exit(1)
  }
  console.log('Using provided master key: ' + keyHex.substring(0, 8) + '...')
} else {
  keyHex = crypto.randomBytes(KEY_LENGTH).toString('hex')
  console.log('Generated NEW master key:  ' + keyHex.substring(0, 8) + '...')
}

if (clientName) console.log('Client: ' + clientName)

const key = Buffer.from(keyHex, 'hex')

console.log('\n=== Encrypting ===\n')

let count = 0
for (const plugin of plugins) {
  const inPath = path.join(pluginsDir, plugin.file)
  const outPath = inPath.replace('.min.js', '.min.enc')

  if (!fs.existsSync(inPath)) {
    console.log('  [skip] ' + plugin.name + ' — .min.js not found')
    continue
  }

  const plaintext = fs.readFileSync(inPath)
  const iv = crypto.randomBytes(IV_LENGTH)
  const cipher = crypto.createCipheriv(ALGORITHM, key, iv)
  const encrypted = Buffer.concat([cipher.update(plaintext), cipher.final()])
  const tag = cipher.getAuthTag()

  const output = Buffer.concat([iv, tag, encrypted])
  fs.writeFileSync(outPath, output)

  const encName = path.basename(outPath)
  const zipPath = path.join(pluginsDir, plugin.name + '.zip')
  createZip([{ name: encName, data: output }], zipPath)

  const sizeKB = (output.length / 1024).toFixed(1)
  console.log('  [done] ' + plugin.name + ' — ' + sizeKB + ' KB')
  count++
}

// Save plain hex key
const keyPath = path.join(pluginsDir, 'plugins.key')
fs.writeFileSync(keyPath, keyHex, 'utf8')

const meta = {
  mode: 'master-key',
  client: clientName || 'unknown',
  created: new Date().toISOString(),
  plugins: count,
}
fs.writeFileSync(path.join(pluginsDir, '.encrypt-meta.json'), JSON.stringify(meta, null, 2), 'utf8')

console.log('\n=== Done: ' + count + ' encrypted ===\n')
console.log('  plugins.key:  ' + keyPath + '  (plain hex)')
console.log('  Master key:   ' + keyHex)
console.log('')
console.log('  Give the bank:')
console.log('    - All .zip files')
console.log('    - plugins.key')
console.log('')
console.log('  The Java app will use the key directly (no password, no license needed).')
console.log('')

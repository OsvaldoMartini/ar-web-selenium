#!/usr/bin/env node
/**
 * Encrypt plugins bound to an ARWeb.lic license file.
 *
 * The key is wrapped as ACTIVATED: (machine_id + license fingerprint).
 * At runtime, the Java app unwraps automatically — no password needed,
 * but only works on the machine where it was activated.
 *
 * Usage:
 *   node encrypt-license.js                                          # default license path
 *   node encrypt-license.js --license "D:\path\to\ARWeb.lic"        # custom license
 *   node encrypt-license.js --client "Bank Name"                     # tag with client name
 *
 * Output:
 *   {plugin}/build/{name}.min.enc   — encrypted file
 *   {plugin}.zip                    — zip containing the .enc
 *   plugins.key                     — ACTIVATED: wrapped key (license + machine bound)
 */

const crypto = require('crypto')
const fs = require('fs')
const path = require('path')
const os = require('os')
const { createWriteStream } = require('fs')
const { networkInterfaces } = require('os')

const ALGORITHM = 'aes-256-gcm'
const IV_LENGTH = 12
const SALT_LENGTH = 16
const PBKDF2_ITERATIONS = 100_000
const KEY_LENGTH = 32

const DEFAULT_LICENSE = 'D:/Projects/ARWeb-Martini/ARWeb-Scanner/ARWeb.lic'

const plugins = [
  { name: 'hoverPick',       file: 'hoverPick/build/hoverPick.min.js' },
  { name: 'pageScanner',     file: 'pageScanner/build/scanner.min.js' },
  { name: 'searchList',      file: 'searchList/build/searchList.min.js' },
  { name: 'searchListAsync', file: 'searchListAsync/build/searchListAsync.min.js' },
  { name: 'pluginTest',      file: 'pluginTest/build/pluginTest.min.js' },
  { name: 'actionExecutor',  file: 'actionExecutor/build/actionExecutor.min.js' },
]

const pluginsDir = __dirname

// ── Machine ID (matches Java EncryptedPluginLoaderTest.getMachineId) ──────

function getMachineId() {
  const parts = []
  parts.push(os.hostname())
  parts.push('|')

  const nets = networkInterfaces()
  for (const name of Object.keys(nets)) {
    for (const iface of nets[name]) {
      if (iface.mac && iface.mac !== '00:00:00:00:00:00') {
        parts.push(iface.mac.replace(/:/g, ''))
        parts.push(',')
      }
    }
  }

  parts.push(os.type() + ' ' + os.release())
  parts.push('|')
  parts.push(os.userInfo().username)

  return crypto.createHash('sha256').update(parts.join('')).digest('hex')
}

// ── Key wrapping (ACTIVATED: machine + license bound) ────────────────────

function wrapKeyActivated(pluginKey, machineId, fingerprint) {
  const salt = crypto.randomBytes(SALT_LENGTH)
  const iv = crypto.randomBytes(IV_LENGTH)
  const combined = machineId + '|' + fingerprint
  const wrapperKey = crypto.pbkdf2Sync(combined, salt, PBKDF2_ITERATIONS, KEY_LENGTH, 'sha256')
  const cipher = crypto.createCipheriv(ALGORITHM, wrapperKey, iv)
  const encrypted = Buffer.concat([cipher.update(pluginKey), cipher.final()])
  const tag = cipher.getAuthTag()
  return 'ACTIVATED:' + Buffer.concat([salt, iv, encrypted, tag]).toString('base64')
}

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

let licensePath = DEFAULT_LICENSE
let clientName = null

for (let i = 0; i < args.length; i++) {
  if (args[i] === '--license' && args[i + 1]) licensePath = args[++i]
  if (args[i] === '--client'  && args[i + 1]) clientName = args[++i]
}

console.log('=== Encrypt Plugins — License Bound (ACTIVATED) ===\n')

if (!fs.existsSync(licensePath)) {
  console.error('License not found: ' + licensePath)
  process.exit(1)
}

const licContent = fs.readFileSync(licensePath)
const fingerprint = crypto.createHash('sha256').update(licContent).digest('hex')
const machineId = getMachineId()

console.log('License:     ' + licensePath)
console.log('Fingerprint: ' + fingerprint.substring(0, 16) + '...')
console.log('Machine ID:  ' + machineId.substring(0, 16) + '...')
if (clientName) console.log('Client:      ' + clientName)

// Generate key
const key = crypto.randomBytes(KEY_LENGTH)
const keyHex = key.toString('hex')
console.log('Plugin key:  ' + keyHex.substring(0, 8) + '...\n')

console.log('=== Encrypting ===\n')

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

// Save ACTIVATED key
const activatedKey = wrapKeyActivated(key, machineId, fingerprint)
const keyPath = path.join(pluginsDir, 'plugins.key')
fs.writeFileSync(keyPath, activatedKey, 'utf8')

// Save raw key for owner backup
fs.writeFileSync(path.join(pluginsDir, '.plugin-key-raw'), keyHex, 'utf8')

const meta = {
  mode: 'activated',
  client: clientName || 'unknown',
  machineId: machineId.substring(0, 16),
  fingerprint: fingerprint.substring(0, 16),
  created: new Date().toISOString(),
  plugins: count,
}
fs.writeFileSync(path.join(pluginsDir, '.encrypt-meta.json'), JSON.stringify(meta, null, 2), 'utf8')

console.log('\n=== Done: ' + count + ' encrypted ===\n')
console.log('  plugins.key:  ACTIVATED (machine + license bound)')
console.log('  Bound to:     this machine + ' + path.basename(licensePath))
console.log('')
console.log('  Ship to production:')
console.log('    - All .zip files')
console.log('    - plugins.key')
console.log('    - ARWeb.lic (must match)')
console.log('')
console.log('  The Java app decrypts automatically — no password needed.')
console.log('  Only works on this machine with the matching license file.')
console.log('')

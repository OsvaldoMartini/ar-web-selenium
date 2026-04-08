#!/usr/bin/env node
/**
 * Initial key setup for the password-protected plugin system.
 *
 * Generates a random AES-256 key, wraps it with a password + license fingerprint,
 * and saves the protected plugins.key file.
 *
 * Usage:
 *   node setup-key.js --license "D:\path\to\ARWeb.lic" --password "mypassword"
 *   node setup-key.js --license "D:\path\to\ARWeb.lic"   (prompts for password)
 *
 * Also outputs the raw hex key for encrypt-plugins.js:
 *   node encrypt-plugins.js --key <hex>
 */

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const readline = require('readline');

const SALT_LENGTH = 16;
const IV_LENGTH = 12;
const PBKDF2_ITERATIONS = 100000;
const KEY_LENGTH = 32; // AES-256

async function main() {
  const args = process.argv.slice(2);

  // Parse args
  let licensePath = null;
  let password = null;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--license' && args[i + 1]) licensePath = args[++i];
    if (args[i] === '--password' && args[i + 1]) password = args[++i];
  }

  if (!licensePath) {
    // Default license path
    licensePath = 'D:/Projects/ARWeb-Martini/ARWeb-Scanner/ARWeb.lic';
  }

  if (!fs.existsSync(licensePath)) {
    console.error('License file not found: ' + licensePath);
    process.exit(1);
  }

  console.log('=== Plugin Key Setup ===\n');
  console.log('License: ' + licensePath);

  // Compute license fingerprint (SHA-256)
  const licContent = fs.readFileSync(licensePath);
  const fingerprint = crypto.createHash('sha256').update(licContent).digest('hex');
  console.log('License fingerprint: ' + fingerprint.substring(0, 16) + '...\n');

  // Get password
  if (!password) {
    password = await askPassword('Enter plugin password: ');
    const confirm = await askPassword('Confirm password: ');
    if (password !== confirm) {
      console.error('Passwords do not match!');
      process.exit(1);
    }
  }

  // Generate random plugin key
  const pluginKey = crypto.randomBytes(KEY_LENGTH);
  const pluginKeyHex = pluginKey.toString('hex');
  console.log('\nPlugin key (hex): ' + pluginKeyHex);

  // Wrap with password + license
  const salt = crypto.randomBytes(SALT_LENGTH);
  const iv = crypto.randomBytes(IV_LENGTH);
  const combined = password + '|' + fingerprint;
  const wrapperKey = crypto.pbkdf2Sync(combined, salt, PBKDF2_ITERATIONS, KEY_LENGTH, 'sha256');

  const cipher = crypto.createCipheriv('aes-256-gcm', wrapperKey, iv);
  const encrypted = Buffer.concat([cipher.update(pluginKey), cipher.final()]);
  const tag = cipher.getAuthTag();

  // Format: salt + iv + encrypted + tag (GCM appends tag)
  const wrapped = Buffer.concat([salt, iv, encrypted, tag]);
  const protectedKey = 'PROTECTED:' + wrapped.toString('base64');

  // Save
  const keyPath = path.join(__dirname, 'plugins.key');
  fs.writeFileSync(keyPath, protectedKey, 'utf8');
  console.log('\nSaved: ' + keyPath);
  console.log('Format: PROTECTED (password + license bound)\n');

  console.log('Next steps:');
  console.log('  1. Encrypt plugins: node encrypt-plugins.js --key ' + pluginKeyHex);
  console.log('  2. Copy plugins.key to production folder');
  console.log('  3. Delete the raw hex key from your terminal history!\n');

  // Also save raw key temporarily for encrypt-plugins.js
  const rawKeyPath = path.join(__dirname, '.plugin-key-raw');
  fs.writeFileSync(rawKeyPath, pluginKeyHex, 'utf8');
  console.log('Raw key also saved to .plugin-key-raw (delete after encrypting!)');
}

function askPassword(prompt) {
  return new Promise(resolve => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    rl.question(prompt, answer => { rl.close(); resolve(answer.trim()); });
  });
}

main().catch(err => { console.error(err); process.exit(1); });

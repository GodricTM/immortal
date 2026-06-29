#!/usr/bin/env node
// bspatch.js — bsdiff4 patch applier for Windows (uses Git's bunzip2.exe)
// Usage: node bspatch.js <oldfile> <newfile> <patchfile>
'use strict';
const { execFileSync } = require('child_process');
const fs   = require('fs');
const os   = require('os');
const path = require('path');

// bsdiff's "offtin": 8-byte LE, high bit of byte 7 = sign
function offtin(buf, off) {
  let y = buf[off + 7] & 0x7F;
  for (let i = 6; i >= 0; i--) y = y * 256 + buf[off + i];
  if (buf[off + 7] & 0x80) y = -y;
  return y;
}

// Find bunzip2 — prefer Git's copy since it's not normally on Windows PATH
function findBunzip2() {
  const candidates = [
    'C:\\Program Files\\Git\\mingw64\\bin\\bunzip2.exe',
    'C:\\Program Files (x86)\\Git\\mingw64\\bin\\bunzip2.exe',
  ];
  for (const c of candidates) if (fs.existsSync(c)) return c;
  // fallback: hope it's on PATH
  return 'bunzip2';
}

function bzDecompress(bunzip2, data) {
  const tmp = path.join(os.tmpdir(), `bsp_${Date.now()}_${Math.random().toString(36).slice(2)}`);
  fs.writeFileSync(tmp + '.bz2', data);
  execFileSync(bunzip2, ['-f', tmp + '.bz2'], { stdio: 'pipe' });
  const out = fs.readFileSync(tmp);
  try { fs.unlinkSync(tmp); } catch (_) {}
  return out;
}

const [,, oldPath, newPath, patchPath] = process.argv;
if (!oldPath || !newPath || !patchPath) {
  console.error('Usage: node bspatch.js <old> <new> <patch>'); process.exit(1);
}

const patch = fs.readFileSync(patchPath);
const old   = fs.readFileSync(oldPath);

if (patch.slice(0, 8).toString('ascii') !== 'BSDIFF40') {
  console.error('Not a BSDIFF40 patch'); process.exit(1);
}

const ctrlLen = offtin(patch, 8);
const diffLen = offtin(patch, 16);
const newSize = offtin(patch, 24);

const bz = findBunzip2();
process.stderr.write(`bspatch: decompressing ctrl (${ctrlLen} bytes)...\n`);
const ctrl  = bzDecompress(bz, patch.slice(32, 32 + ctrlLen));
process.stderr.write(`bspatch: decompressing diff (${diffLen} bytes)...\n`);
const diff  = bzDecompress(bz, patch.slice(32 + ctrlLen, 32 + ctrlLen + diffLen));
process.stderr.write(`bspatch: decompressing extra...\n`);
const extra = bzDecompress(bz, patch.slice(32 + ctrlLen + diffLen));

const out = Buffer.alloc(newSize);
let oldpos = 0, newpos = 0, ctrlpos = 0, diffpos = 0, extrapos = 0;

while (newpos < newSize) {
  const x = offtin(ctrl, ctrlpos); ctrlpos += 8;
  const y = offtin(ctrl, ctrlpos); ctrlpos += 8;
  const z = offtin(ctrl, ctrlpos); ctrlpos += 8;

  for (let i = 0; i < x; i++) {
    out[newpos + i] = ((oldpos + i < old.length ? old[oldpos + i] : 0) + diff[diffpos + i]) & 0xFF;
  }
  newpos += x; oldpos += x; diffpos += x;

  extra.copy(out, newpos, extrapos, extrapos + y);
  newpos += y; extrapos += y;

  oldpos += z;
}

fs.writeFileSync(newPath, out);
process.stderr.write(`bspatch: wrote ${out.length} bytes -> ${newPath}\n`);

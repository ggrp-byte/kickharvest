// Copies the canonical UI (../index.html) into the app folder.
// The root index.html is the single source of truth.
const fs = require('fs');
const path = require('path');
const src = path.join(__dirname, '..', 'index.html');
const dst = path.join(__dirname, 'index.html');
fs.copyFileSync(src, dst);
console.log('UI zsynchronizowana:', dst);

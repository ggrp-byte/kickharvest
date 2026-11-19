const { app, BrowserWindow, Tray, Menu, ipcMain, nativeImage, session, shell } = require('electron');
const path = require('path');

let win = null;
let playerWin = null;
let loginWin = null;
let tray = null;
let quitting = false;

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36';

async function kickCookies() {
  try {
    const s = session.fromPartition('persist:kick');
    return await s.cookies.get({ url: 'https://kick.com' });
  } catch (e) { return []; }
}

// Cloudflare blocks plain requests from the app process (403).
// Workaround (same as the desktop tool): fetch from INSIDE a real kick.com page,
// same-origin, with cookies — that always passes.
async function fetchFromKickPage(url) {
  let w = null, created = false;
  if (playerWin && !playerWin.isDestroyed()) w = playerWin;
  else if (loginWin && !loginWin.isDestroyed()) w = loginWin;
  if (!w) {
    w = new BrowserWindow({
      width: 300,
      height: 200,
      show: false,
      skipTaskbar: true,
      webPreferences: { partition: 'persist:kick' }
    });
    created = true;
  }
  try {
    const cur = w.webContents.getURL();
    if (created || !/^https:\/\/kick\.com/.test(cur)) {
      await w.loadURL('https://kick.com');
      await new Promise(r => setTimeout(r, 2500));
    }
    const text = await w.webContents.executeJavaScript(
      "fetch(" + JSON.stringify(url) + ",{headers:{'Accept':'application/json'},credentials:'include'}).then(function(r){return r.text()}).catch(function(e){return JSON.stringify({error:String(e)})})"
    );
    return JSON.parse(text);
  } finally {
    if (created) { try { w.destroy(); } catch (e) {} }
  }
}

function createUI() {
  win = new BrowserWindow({
    width: 920,
    height: 800,
    minWidth: 380,
    minHeight: 560,
    title: 'KickHarvest',
    autoHideMenuBar: true,
    backgroundColor: '#0d0f0c',
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  win.loadFile(path.join(__dirname, 'index.html'));
  win.on('close', (e) => {
    if (!quitting) { e.preventDefault(); win.hide(); }
  });
}

const MUTE_JS = `(function(){
  if (window.__kickminerTimer) return;
  window.__kickminerTimer = setInterval(function(){
    var v = document.querySelector('video');
    if (v) {
      try { v.muted = true; v.volume = 0; } catch (e) {}
      try {
        v.style.opacity = '0';
        v.style.width = '1px';
        v.style.height = '1px';
        v.style.position = 'fixed';
        v.style.bottom = '0';
        v.style.right = '0';
        v.style.pointerEvents = 'none';
      } catch (e) {}
      if (v.paused) { try { v.play().catch(function(){}); } catch (e) {} }
    }
  }, 2000);
})();`;

function openPlayer(url) {
  if (playerWin && !playerWin.isDestroyed()) {
    playerWin.loadURL(url);
    return;
  }
  playerWin = new BrowserWindow({
    width: 420,
    height: 240,
    show: false,
    skipTaskbar: true,
    webPreferences: {
      partition: 'persist:kick',
      backgroundThrottling: false
    }
  });
  playerWin.webContents.setAudioMuted(true);
  let first = true;
  playerWin.webContents.on('did-finish-load', () => {
    playerWin.webContents.executeJavaScript(MUTE_JS).catch(() => {});
    if (first) {
      first = false;
      playerWin.webContents.executeJavaScript(
        "try { sessionStorage.setItem('stream_quality','160'); } catch(e) {}"
      ).then(() => {
        playerWin.webContents.reloadIgnoringCache();
      }).catch(() => {});
    }
  });
  playerWin.on('closed', () => { playerWin = null; });
  playerWin.loadURL(url);
}

function createTray() {
  try {
    const img = nativeImage.createFromPath(path.join(__dirname, 'icon.png'));
    tray = new Tray(img.resize({ width: 16, height: 16 }));
    tray.setToolTip('KickHarvest — pracuje w tle');
    tray.setContextMenu(Menu.buildFromTemplate([
      { label: 'Pokaż okno', click: () => { if (win) win.show(); } },
      { type: 'separator' },
      { label: 'Zakończ', click: () => { quitting = true; app.quit(); } }
    ]));
    tray.on('click', () => {
      if (win) { win.isVisible() ? win.hide() : win.show(); }
    });
  } catch (e) {
    tray = null;
  }
}

ipcMain.on('open-player', (e, url) => { openPlayer(String(url || '')); });
ipcMain.on('close-player', () => {
  if (playerWin && !playerWin.isDestroyed()) playerWin.destroy();
  playerWin = null;
});
ipcMain.on('open-login', () => {
  if (loginWin && !loginWin.isDestroyed()) { loginWin.show(); return; }
  loginWin = new BrowserWindow({
    width: 880,
    height: 720,
    title: 'KickHarvest — logowanie',
    autoHideMenuBar: true,
    webPreferences: { partition: 'persist:kick' }
  });
  loginWin.loadURL('https://kick.com');
  loginWin.on('closed', () => { loginWin = null; });
});
ipcMain.on('open-drops', () => {
  shell.openExternal('https://kick.com/drops');
});
ipcMain.on('set-login-item', (e, val) => {
  try { app.setLoginItemSettings({ openAtLogin: !!val }); } catch (err) {}
});
ipcMain.handle('get-login-item', () => {
  try { return app.getLoginItemSettings().openAtLogin; } catch (err) { return false; }
});
ipcMain.on('tray-tooltip', (e, txt) => {
  if (tray) { try { tray.setToolTip('KickHarvest — ' + String(txt || '')); } catch (err) {} }
});
ipcMain.handle('get-drops', async () => {
  try {
    const campaigns = await fetchFromKickPage('https://web.kick.com/api/v1/drops/campaigns');
    let progress = null;
    try { progress = await fetchFromKickPage('https://web.kick.com/api/v1/drops/progress'); } catch (e) {}
    const cd = campaigns && Array.isArray(campaigns.data) ? campaigns.data : null;
    if (!cd) return { error: 'no_data' };
    const pd = progress && Array.isArray(progress.data) ? progress.data : [];
    return { campaigns: cd, progress: pd };
  } catch (e) {
    return { error: String(e) };
  }
});
ipcMain.handle('get-livestreams', async (e, categoryId) => {
  try {
    const j = await fetchFromKickPage('https://web.kick.com/api/v1/livestreams?limit=12&sort=viewer_count_desc&category_id=' + encodeURIComponent(String(categoryId || '')));
    const d = j && j.data;
    const arr = Array.isArray(d) ? d : (d && Array.isArray(d.livestreams) ? d.livestreams : []);
    return arr.map(s => {
      const ch = s.channel || {};
      return {
        slug: ch.slug || (ch.user && (ch.user.username || ch.user.slug)) || s.slug || '',
        title: s.session_title || '',
        viewer_count: s.viewer_count || 0
      };
    }).filter(s => s.slug);
  } catch (e) {
    return [];
  }
});

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  // Another copy is already running — exit and let it show its window instead.
  app.quit();
} else {
  app.on('second-instance', () => {
    if (win) { win.show(); win.focus(); }
  });

  app.whenReady().then(() => {
    createUI();
    createTray();
  });

  app.on('window-all-closed', () => { /* keep running in tray */ });
  app.on('before-quit', () => { quitting = true; });
}

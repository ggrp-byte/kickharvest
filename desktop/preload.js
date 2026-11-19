const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('kickminer', {
  isNative: true,
  openPlayer: (url) => ipcRenderer.send('open-player', url),
  closePlayer: () => ipcRenderer.send('close-player'),
  openLogin: () => ipcRenderer.send('open-login'),
  onLoginClosed: (cb) => ipcRenderer.on('login-closed', cb),
  openDrops: () => ipcRenderer.send('open-drops'),
  setAutoLaunch: (val) => ipcRenderer.send('set-login-item', val),
  getAutoLaunch: () => ipcRenderer.invoke('get-login-item'),
  setTooltip: (txt) => ipcRenderer.send('tray-tooltip', txt),
  getDrops: () => ipcRenderer.invoke('get-drops'),
  getLivestreams: (categoryId) => ipcRenderer.invoke('get-livestreams', categoryId)
});

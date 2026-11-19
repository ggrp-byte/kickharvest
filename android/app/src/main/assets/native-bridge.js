(function () {
  'use strict';
  if (!window.AndroidHost) return;
  var serial = 0, callbacks = Object.create(null);
  window.__nativeReply = function (id, body) {
    var req = callbacks[id];
    if (!req) return;
    delete callbacks[id];
    clearTimeout(req.timer);
    try { req.resolve(JSON.parse(body)); }
    catch (e) { req.resolve({ error: 'invalid_native_json' }); }
  };
  function request(method, arg) {
    return new Promise(function (resolve) {
      var id = ++serial;
      var timer = setTimeout(function () {
        if (callbacks[id]) { delete callbacks[id]; resolve({ error: 'native_response_timeout' }); }
      }, 23000);
      callbacks[id] = { resolve: resolve, timer: timer };
      try {
        if (arg === undefined) window.AndroidHost[method](id);
        else window.AndroidHost[method](id, String(arg));
      } catch (e) { window.__nativeReply(id, JSON.stringify({ error: String(e) })); }
    });
  }
  window.kickminer = Object.freeze({
    isNative: true, isAndroid: true,
    openPlayer: function (url) { window.AndroidHost.openPlayer(String(url)); },
    openPlayers: function (slugs, player, view, mode) {
      window.AndroidHost.openPlayers(JSON.stringify(slugs), String(player), String(view), String(mode));
    },
    closePlayer: function () { window.AndroidHost.closePlayer(); },
    openLogin: function () { window.AndroidHost.openLogin(); },
    openInventory: function () { window.AndroidHost.openInventory(); },
    openPip: function () { window.AndroidHost.openPip(); },
    getDrops: function () { return request('getDrops'); },
    getLivestreams: function (category) { return request('getLivestreams', category); },
    getCategories: function (query) { return request('getCategories', query); },
    getChannel: function (slug) { return request('getChannel', slug); },
    getAuthStatus: function () { return request('getAuthStatus'); }
  });
})();

'use strict';
/* No network requests or mock API data ever go into the application itself.
   This harness tests the actual packaged HTML/JS and the bridge contract. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const asset = path.join(__dirname, '..','android','app','src','main','assets');
const bridge = fs.readFileSync(path.join(asset,'native-bridge.js'),'utf8');
const html = fs.readFileSync(path.join(asset,'index.html'),'utf8');
const match = html.match(/<script>\s*('use strict';[\s\S]*?)<\/script>/);
assert(match,'main dashboard script exists');
const elementMap = Object.create(null);
const calls = [];
const storage = Object.create(null);
function elem(tag='div') {
  const node = {
    tag, style:{}, value:'', disabled:false, checked:false, children:[], dataset:{},
    className:'', innerHTML:'', textContent:'', listeners:{},
    classList:{toggle(){}},
    addEventListener(name, cb){this.listeners[name]=cb;},
    appendChild(child){this.children.push(child); return child;},
    append(...children){this.children.push(...children);},
    querySelectorAll(){return [];},
    querySelector(){return elem('option')},
    setAttribute(){},
    getAttribute(name){return this[name]||null},
    fire(name, data={}){assert(this.listeners[name],`${this.tag} ${name} listener`);this.listeners[name].call(this,{stopPropagation(){}, ...data});}
  };
  return node;
}
const document = {
  visibilityState:'visible',
  querySelector(sel){if(!elementMap[sel]) elementMap[sel]=elem(sel);return elementMap[sel]},
  querySelectorAll(){return []},
  createElement(tag){return elem(tag)},
  addEventListener(){},
};
const host = {
  openPlayer(url){calls.push(['openPlayer',url]);},closePlayer(){calls.push(['closePlayer']);},
  openLogin(){calls.push(['openLogin']);},openInventory(){calls.push(['openInventory']);},
  openPip(){calls.push(['openPip']);},
  getAuthStatus(id){calls.push(['getAuthStatus',id]);
    queueMicrotask(()=>ctx.__nativeReply(id, JSON.stringify({authenticated:true,username:'tester'})));
  },
  getDrops(id){calls.push(['getDrops',id]);
    queueMicrotask(()=>ctx.__nativeReply(id, JSON.stringify({campaigns:[{
      id:'a',name:'<script>alert(1)</script>',ends_at:'2090-01-01',
      category:{name:'GTA',id:'42'},rewards:[{name:'Badge',required_units:60}]
    }],progress:[{id:'a',rewards:[{progress:30,required_units:60}]}]})));
  },
  getLivestreams(id,cat){calls.push(['getLivestreams',id,cat]);
    queueMicrotask(()=>ctx.__nativeReply(id, JSON.stringify([{slug:'testlive'}])));
  },
  getCategories(id,query){calls.push(['getCategories',id,query]);
    queueMicrotask(()=>ctx.__nativeReply(id, JSON.stringify([{id:'42',name:'GTA',slug:'gta'}])));
  },
  openPlayers(json,player,view,mode){calls.push(['openPlayers',json,player,view,mode]);},
  getChannel(id,slug){calls.push(['getChannel',id,slug]);
    queueMicrotask(()=>ctx.__nativeReply(id, JSON.stringify({is_live:true,categories:[{name:'GTA'}]})));
  }
};
const ctx = vm.createContext({
  document, AndroidHost:host, localStorage:{getItem(k){return storage[k]},setItem(k,v){storage[k]=v}},
  performance:{now(){return 0}},navigator:{}, location:{search:'?android=1'},
  setTimeout,clearTimeout,setInterval(){return 0},
  Promise,JSON,Math,Number,Date,String,Object,Array,console,
});
ctx.window=ctx;ctx.window.addEventListener=()=>{};
vm.runInContext(bridge,ctx,{filename:'native-bridge.js'});
vm.runInContext(match[1],ctx,{filename:'index.html<script>'});
assert(ctx.kickminer.isAndroid && ctx.kickminer.isNative);
assert.equal(ctx.settings.mode,'popup');
assert.equal(document.querySelector('#modeSel').disabled,false);
assert.equal(document.querySelector('#playerSel').disabled,false);
assert.equal(document.querySelector('#viewSel').disabled,false);
assert.equal(document.querySelector('#categoryPanel').style.display,'');
assert.equal(document.querySelector('#androidPanel').style.display,'');
async function flush(){await Promise.resolve();await Promise.resolve();await Promise.resolve();}
(async()=>{
  document.querySelector('#androidAuth').fire('click');await flush();
  assert.match(document.querySelector('#androidAuthText').textContent,/tester/);
  document.querySelector('#androidLogin').fire('click');
  document.querySelector('#androidInventory').fire('click');
  assert(calls.some(a=>a[0]==='openLogin'));
  assert(calls.some(a=>a[0]==='openInventory'));
  document.querySelector('#btnDrops').fire('click');await flush();
  let box=document.querySelector('#dropsBox');
  assert.match(box.innerHTML,/50%/);
  assert(!box.innerHTML.includes('<script>alert(1)</script>'),'API text must be HTML-escaped');
  assert(box.innerHTML.includes('&lt;script&gt;'));
  document.querySelector('#chIn').value='xqc';
  document.querySelector('#btnAdd').fire('click');
  assert(ctx.channels.some(a=>a.slug==='xqc'));
  document.querySelector('#btnStart').fire('click');
  assert.equal(ctx.running,true);
  assert(calls.some(a=>a[0]==='openPlayers'&&a[1].includes('xqc')&&a[2]==='full'));
  // Native settings must cause real player reconfiguration.
  document.querySelector('#viewSel').value='mini';document.querySelector('#viewSel').fire('change');
  assert(calls.some(a=>a[0]==='openPlayers'&&a[3]==='mini'));
  document.querySelector('#modeSel').value='embed-all';document.querySelector('#modeSel').fire('change');
  assert(calls.some(a=>a[0]==='openPlayers'&&a[4]==='embed-all'));
  document.querySelector('#playerSel').value='light';document.querySelector('#playerSel').fire('change');
  assert(calls.some(a=>a[0]==='openPlayers'&&a[2]==='light'));
  ctx.__androidPlaybackStopped();
  assert.equal(ctx.running,false);
  assert(calls.some(a=>a[0]==='closePlayer'));
  assert(storage.kick_miner_drop_v2,'state persisted to localStorage');
  document.querySelector('#catIn').value='GTA';
  document.querySelector('#btnFindLive').fire('click');await flush();
  await flush();
  assert(calls.some(a=>a[0]==='getCategories'&&a[2]==='GTA'));
  await flush();
  assert(ctx.channels.some(c=>c.slug==='testlive'),'category auto-adds live streamers');
  const list=await ctx.kickminer.getLivestreams('42');
  assert.equal(list[0].slug,'testlive');
  console.log('PASS: Android touch settings enabled, native player modes, category search + auto LIVE, auth, escaping, stop, persistence');
})().catch(err=>{console.error(err);process.exitCode=1});

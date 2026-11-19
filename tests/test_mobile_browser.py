"""Actual Chromium DOM/touch-flow smoke test. All remote APIs are stubbed locally."""
from pathlib import Path
from playwright.sync_api import sync_playwright
assets = Path(__file__).resolve().parents[1]/'android/app/src/main/assets'
html= (assets/'index.html').read_text()
bridge=(assets/'native-bridge.js').read_text()
html=html.replace('<script src="native-bridge.js"></script>','<script>'+bridge+'</script>')
script = r'''
window.__calls=[];
window.AndroidHost={
 openLogin(){window.__calls.push(['login'])},openInventory(){window.__calls.push(['inventory'])},
 openPlayer(u){window.__calls.push(['single',u])},closePlayer(){window.__calls.push(['close'])},
 openPlayers(a,p,v,m){window.__calls.push(['multi',JSON.parse(a),p,v,m])},openPip(){},
 getAuthStatus(id){window.__nativeReply(id,JSON.stringify({authenticated:true,username:'test'}))},
 getDrops(id){window.__nativeReply(id,JSON.stringify({campaigns:[],progress:[]}))},
 getLivestreams(id,c){window.__calls.push(['streams',c]);window.__nativeReply(id,JSON.stringify([{slug:'streamer_live_1',viewer_count:431}]))},
 getCategories(id,query){window.__calls.push(['categories',query]);window.__nativeReply(id,JSON.stringify([{id:'1552',name:'Counter-Strike 2',slug:'counter-strike-2'},{id:'8',name:'Counter-Strike',slug:'counter-strike'}]))},
 getChannel(id,slug){window.__nativeReply(id,JSON.stringify({is_live:true,categories:[{name:'Counter-Strike 2'}]}))}
};
'''
with sync_playwright() as pw:
    browser=pw.chromium.launch(headless=True, executable_path='/usr/bin/chromium',args=['--no-sandbox'])
    context=browser.new_context(viewport={'width':393,'height':852},device_scale_factor=2,is_mobile=True,has_touch=True)
    page=context.new_page()
    errors=[]
    page.on('pageerror',lambda error:errors.append(str(error)))
    page.evaluate('''window.__persist={};Object.defineProperty(window,'localStorage',{configurable:true,value:{getItem:k=>window.__persist[k]||null,setItem:(k,v)=>window.__persist[k]=v}});''')
    page.evaluate(script)
    page.set_content(html,wait_until='load')
    for selector in ['#modeSel','#playerSel','#viewSel']:
        assert page.locator(selector).is_enabled(),f'{selector} disabled'
        assert page.locator(selector).is_visible(),f'{selector} invisible'
    page.locator('#modeSel').select_option('embed-all')
    page.locator('#playerSel').select_option('light')
    page.locator('#viewSel').select_option('mini')
    assert page.evaluate("[settings.mode, settings.player, settings.view]")==['embed-all','light','mini']
    page.locator('#catIn').fill('Counter-Strike 2')
    page.locator('#btnFindLive').click()
    page.wait_for_function("document.querySelector('#categoryStatus').textContent.includes('dodano 1')", timeout=5000)
    assert 'streamer_live_1' in page.locator('#list').inner_text()
    page.locator('#btnStart').click()
    page.wait_for_function("window.__calls.some(x=>x[0]==='multi')",timeout=5000)
    player=page.evaluate("window.__calls.filter(x=>x[0]==='multi').at(-1)")
    assert player[2:] == ['light','mini','embed-all'],player
    assert 'streamer_live_1' in player[1],player
    # Refreshing content simulates Android WebView reopening the panel while retaining localStorage.
    page.set_content(html,wait_until='load')
    assert page.locator('#modeSel').input_value()=='embed-all'
    assert page.locator('#playerSel').input_value()=='light'
    assert page.locator('#viewSel').input_value()=='mini'
    assert page.locator('#autoLive').is_checked()
    assert not errors,errors
    print('PASS: touch-width Chromium DOM, all 3 selects functional, category auto-add, persisted settings, native player payload, no JS errors')
    browser.close()

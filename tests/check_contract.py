"""Static contract and manifest checks (run with python tests/check_contract.py)."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re
ROOT=Path(__file__).resolve().parents[1]
assets=ROOT/'android/app/src/main/assets'
java=(ROOT/'android/app/src/main/java/com/kickminer/app/MainActivity.java').read_text()
bridge=(assets/'native-bridge.js').read_text()
html=(assets/'index.html').read_text()
manifest=ET.parse(ROOT/'android/app/src/main/AndroidManifest.xml').getroot()
need={'openLogin','openInventory','openPlayer','closePlayer','getDrops','getLivestreams','getChannel','getAuthStatus','openPip','getCategories','openPlayers'}
actual=set(re.findall(r'@JavascriptInterface\s+public void (\w+)\(',java))
assert actual==need, (need-actual,actual-need)
for n in need:assert re.search(r'\b'+n+r':\s*function\b',bridge),n
assert java.count('addJavascriptInterface(')==1
assert 'panel.addJavascriptInterface(' in java
assert 'kick.addJavascriptInterface(' not in java and 'api.addJavascriptInterface(' not in java
assert 'setAllowUniversalAccessFromFileURLs(false)' in java
assert 'setAllowFileAccessFromFileURLs(false)' in java
assert 'cookies.flush()' in java
assert 'native-bridge.js' in html
assert "$('#modeSel').disabled=true" not in html
assert "getCategories(" in html
assert "selectedCategory" in html
assert "openPlayers(" in html
assert 'window.__androidPlaybackStopped' in html
android='{http://schemas.android.com/apk/res/android}'
activity=manifest.find('application/activity')
assert activity is not None
assert activity.attrib[android+'supportsPictureInPicture']=='true'
assert manifest.find('uses-permission').attrib[android+'name']=='android.permission.INTERNET'
print('PASS: Android JNI/JS parity, category discovery, functional selects, isolated bridge, cookies, manifest')

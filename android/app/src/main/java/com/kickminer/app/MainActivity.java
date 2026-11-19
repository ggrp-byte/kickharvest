package com.kickminer.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.PictureInPictureParams;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Native implementation of the desktop IPC contract; no secrets leave the WebView cookie jar. */
public final class MainActivity extends Activity {
    private static final String KICK = "https://kick.com";
    private static final Pattern SLUG = Pattern.compile("[a-zA-Z0-9_-]{2,40}");
    private static final Pattern CATEGORY = Pattern.compile("[0-9]{1,12}");
    private static final long TIMEOUT = 20000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Request> inFlight = new HashMap<>();
    private final ArrayDeque<Request> queued = new ArrayDeque<>();
    private CookieManager cookies;
    private FrameLayout root;
    private WebView panel, kick, api;
    private LinearLayout playerColumn;
    private final ArrayList<WebView> extraPlayers = new ArrayList<>();
    private LinearLayout overlay, bar;
    private boolean apiReady, browserShowing, playing, muted = true, destroyed;
    private View fullScreen;
    private WebChromeClient.CustomViewCallback fullScreenCallback;

    private static final class Request {
        final int id;
        final String type, param;
        Request(int id, String type, String param) { this.id=id; this.type=type; this.param=param; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13,15,12));
        getWindow().setNavigationBarColor(Color.rgb(13,15,12));
        cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13,15,12));
        setContentView(root);

        panel = new WebView(this);
        setup(panel, false);
        // IMPORTANT: never add this bridge to kick or api! Only local app assets.
        panel.addJavascriptInterface(new Bridge(), "AndroidHost");
        panel.setWebChromeClient(new WebChromeClient());
        panel.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!r.isForMainFrame()) return false;
                return panelNavigation(r.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String s) {
                return panelNavigation(Uri.parse(s));
            }
        });
        root.addView(panel, new FrameLayout.LayoutParams(-1,-1));

        api = new WebView(this);
        setup(api, true);
        api.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
                apiReady=false;
            }
            @Override public void onPageFinished(WebView v, String url) {
                if (isKick(url)) { apiReady=true; drain(); }
            }
        });
        FrameLayout.LayoutParams tiny = new FrameLayout.LayoutParams(1,1);
        tiny.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        root.addView(api, tiny);
        api.setAlpha(0.01f);

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(Color.rgb(13,15,12));
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(-1,-1));
        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.rgb(24,29,21));
        bar.setPadding(dp(3),0,dp(3),0);
        overlay.addView(bar, new LinearLayout.LayoutParams(-1,dp(52)));
        Button back = button("Panel");
        back.setOnClickListener(v -> closeKick());
        bar.addView(back);
        TextView label = new TextView(this);
        label.setText("Kick · wspólne cookies");
        label.setTextColor(Color.WHITE);
        label.setSingleLine(true);
        label.setTextSize(11);
        bar.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Button mute = button("Włącz dźwięk");
        mute.setOnClickListener(v -> { muted=!muted; mute.setText(muted ? "Włącz dźwięk" : "Wycisz"); applyMute(); });
        bar.addView(mute);
        if (Build.VERSION.SDK_INT >= 26) {
            Button pip = button("PiP");
            pip.setOnClickListener(v -> startPip());
            bar.addView(pip);
        }
        kick = new WebView(this);
        setup(kick,true);
        kick.getSettings().setSupportMultipleWindows(true);
        kick.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return kickNavigation(r.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return kickNavigation(Uri.parse(url));
            }
            @Override public void onPageFinished(WebView v, String url) {
                cookies.flush();
                if (playing && isKick(url)) applyMute();
            }
        });
        kick.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView v, boolean isDialog, boolean userGesture, Message msg) {
                if (!userGesture) return false;
                showPopup(msg);
                return true;
            }
            @Override public void onShowCustomView(View v, CustomViewCallback cb) {
                if (fullScreen!=null) {cb.onCustomViewHidden();return;}
                fullScreen=v;fullScreenCallback=cb;
                root.addView(v,new FrameLayout.LayoutParams(-1,-1));
                bar.setVisibility(View.GONE);
            }
            @Override public void onHideCustomView() { hideFullScreen(); }
        });
        playerColumn=new LinearLayout(this);
        playerColumn.setOrientation(LinearLayout.VERTICAL);
        overlay.addView(playerColumn,new LinearLayout.LayoutParams(-1,0,1));
        playerColumn.addView(kick,new LinearLayout.LayoutParams(-1,0,1));
        panel.loadUrl("file:///android_asset/index.html?android=1");
        api.loadUrl(KICK+"/");
    }

    private int dp(float a) { return (int)(a*getResources().getDisplayMetrics().density+0.5f); }
    private Button button(String t) {
        Button b=new Button(this);
        b.setText(t);b.setTextSize(11);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setPadding(dp(4),0,dp(4),0);
        b.setMinWidth(0);b.setMinimumWidth(0);
        return b;
    }
    private void setup(WebView v, boolean remote) {
        WebSettings s=v.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false); // android_asset is an explicit platform exception
        s.setAllowContentAccess(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        cookies.setAcceptThirdPartyCookies(v,remote);
    }
    private static boolean isKick(String url) {
        if (url==null) return false;
        try {
            Uri u=Uri.parse(url);String host=u.getHost();
            return "https".equalsIgnoreCase(u.getScheme()) && host!=null
                    && (host.equalsIgnoreCase("kick.com")||host.toLowerCase(Locale.ROOT).endsWith(".kick.com"));
        } catch(Exception ignored) { return false; }
    }
    private boolean panelNavigation(Uri uri) {
        String url=uri.toString();
        if (url.startsWith("file:///android_asset/index.html")||"about:blank".equals(url))return false;
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            if(isKick(url)) showKick(url,false);
            else external(uri);
        }
        return true; // block untrusted navigation in a WebView with Java bridge
    }
    private boolean kickNavigation(Uri uri) {
        String scheme=uri.getScheme();
        if("https".equalsIgnoreCase(scheme)||"about".equalsIgnoreCase(scheme))return false;
        if("http".equalsIgnoreCase(scheme)) {
            if ("kick.com".equalsIgnoreCase(uri.getHost())) return true;
            external(uri);return true;
        }
        if("intent".equalsIgnoreCase(scheme)) {
            try {
                Intent i=Intent.parseUri(uri.toString(),Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);i.setComponent(null);i.setSelector(null);
                startActivity(i);
            }catch(Exception e){Toast.makeText(this,"Logowanie zewnętrzne niedostępne",Toast.LENGTH_SHORT).show();}
        } else if("mailto".equalsIgnoreCase(scheme)) external(uri);
        return true;
    }
    private void external(Uri uri) {
        try {Intent i=new Intent(Intent.ACTION_VIEW,uri);i.addCategory(Intent.CATEGORY_BROWSABLE);startActivity(i);}
        catch(ActivityNotFoundException e){Toast.makeText(this,"Brak aplikacji do otwarcia linku",Toast.LENGTH_SHORT).show();}
    }
    private void showPopup(Message message) {
        WebView popup = new WebView(this);
        setup(popup,true);
        Dialog dialog = new Dialog(this);
        dialog.setContentView(popup);
        popup.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r) {
                return kickNavigation(r.getUrl());
            }
        });
        popup.setWebChromeClient(new WebChromeClient(){
            @Override public void onCloseWindow(WebView v) { dialog.dismiss(); }
        });
        dialog.setOnDismissListener(d -> {
            cookies.flush();popup.stopLoading();popup.destroy();
        });
        WebView.WebViewTransport transport=(WebView.WebViewTransport)message.obj;
        transport.setWebView(popup);message.sendToTarget();
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
    }
    // Player WebViews are separate from the local dashboard and never receive AndroidHost.
    // The list is bounded to three channels on a phone to avoid runaway memory consumption.
    private void clearExtras() {
        for(WebView w:extraPlayers){
            playerColumn.removeView(w);
            w.stopLoading();w.loadUrl("about:blank");w.destroy();
        }
        extraPlayers.clear();
    }
    private WebView makeAdditionalPlayer() {
        WebView w=new WebView(this);
        setup(w,true);
        w.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                return kickNavigation(r.getUrl());
            }
            @Override public void onPageFinished(WebView v,String url){
                cookies.flush();if(playing)applyMute();
            }
        });
        w.setWebChromeClient(new WebChromeClient());
        return w;
    }
    private void showPlayers(String slugsJson,String player,String view,String mode) {
        if(destroyed)return;
        JSONArray data;
        try{data=new JSONArray(slugsJson);}catch(JSONException e){return;}
        boolean many="embed-all".equals(mode);
        if(!"popup".equals(mode)&&!"embed-seq".equals(mode)&&!many)return;
        if(!"normal".equals(view)&&!"mini".equals(view)&&!"hidden".equals(view))return;
        if(!"full".equals(player)&&!"light".equals(player))return;
        ArrayList<String> urls=new ArrayList<>();
        for(int i=0;i<data.length()&&urls.size()<(many?3:1);i++){
            String slug=data.optString(i,"").trim();
            if(!SLUG.matcher(slug).matches()||"..".equals(slug))continue;
            urls.add("light".equals(player)?"https://player.kick.com/"+slug+"?autoplay=true&muted=true":KICK+"/"+slug);
        }
        if(urls.isEmpty())return;
        if(fullScreen!=null)hideFullScreen();
        browserShowing=true;playing=true;
        overlay.setVisibility(View.VISIBLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        clearExtras();
        playerColumn.removeView(kick);
        playerColumn.addView(kick);
        int count=urls.size();
        // Hidden is intentionally tiny but cannot guarantee video playback or rewards.
        boolean normal="normal".equals(view);
        int size="hidden".equals(view)?dp(2):dp(220);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,normal?0:size,normal?1:0);
        kick.setLayoutParams(lp);
        kick.setVisibility(View.VISIBLE);
        if(!urls.get(0).equals(kick.getUrl()))kick.loadUrl(urls.get(0));
        for(int i=1;i<count;i++){
            WebView extra=makeAdditionalPlayer();
            extraPlayers.add(extra);
            playerColumn.addView(extra,new LinearLayout.LayoutParams(-1,normal?0:size,normal?1:0));
            extra.loadUrl(urls.get(i));
        }
        applyMute();
    }
    private void showKick(String url, boolean stream) {
        if (!isKick(url)) return;
        if (!stream && playing) notifyStopped();
        browserShowing=true;playing=stream;
        clearExtras();
        playerColumn.removeView(kick);
        playerColumn.addView(kick,new LinearLayout.LayoutParams(-1,0,1));
        overlay.setVisibility(View.VISIBLE);
        if (!url.equals(kick.getUrl()))kick.loadUrl(url);
        getWindow().setFlags(stream ? WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON : 0,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(stream)applyMute();
    }
    private void applyMute() {
        if (!playing || kick==null)return;
        String value=muted?"true":"false";
        String js="(function(){try{var v=document.querySelector('video');if(v){v.muted="+value+";"
                +(muted?"v.volume=0;":"v.volume=1;")+"}}catch(e){}})()";
        kick.evaluateJavascript(js,null);
        for(WebView w:extraPlayers)w.evaluateJavascript(js,null);
        handler.postDelayed(() -> {
            if(!destroyed&&playing&&browserShowing&&kick!=null){
                kick.evaluateJavascript(js,null);
                for(WebView w:extraPlayers)w.evaluateJavascript(js,null);
            }
        },2100);
    }
    private void notifyStopped() {
        if(panel!=null) panel.evaluateJavascript("window.__androidPlaybackStopped&&window.__androidPlaybackStopped()",null);
    }
    private void closeKick() {
        if(fullScreen!=null)hideFullScreen();
        boolean wasPlaying=playing;
        playing=false;browserShowing=false;
        overlay.setVisibility(View.GONE);
        clearExtras();
        kick.stopLoading();kick.loadUrl("about:blank");
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        cookies.flush();
        if(wasPlaying)notifyStopped();
    }
    private void hideFullScreen() {
        if(fullScreen==null)return;
        root.removeView(fullScreen);fullScreen=null;
        bar.setVisibility(View.VISIBLE);
        if(fullScreenCallback!=null){fullScreenCallback.onCustomViewHidden();fullScreenCallback=null;}
    }
    private void startPip() {
        if(!playing||!browserShowing)return;
        if(Build.VERSION.SDK_INT>=26) {
            try{enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());}
            catch(Exception e){Toast.makeText(this,"PiP niedostępny",Toast.LENGTH_SHORT).show();}
        }
    }
    @Override public void onPictureInPictureModeChanged(boolean pip,Configuration c) {
        super.onPictureInPictureModeChanged(pip,c);
        if(bar!=null)bar.setVisibility(pip?View.GONE:View.VISIBLE);
    }
    private final class Bridge {
        @JavascriptInterface public void openLogin(){runOnUiThread(()->showKick(KICK+"/",false));}
        @JavascriptInterface public void openInventory(){runOnUiThread(()->showKick(KICK+"/drops/inventory",false));}
        @JavascriptInterface public void openPlayer(String url){
            String slug="";
            try{Uri u=Uri.parse(url);String path=u.getPath();
                if("https".equalsIgnoreCase(u.getScheme())&&"kick.com".equalsIgnoreCase(u.getHost())
                        &&path!=null&&path.startsWith("/")&&path.indexOf('/',1)<0){
                    String candidate=path.substring(1);
                    if(SLUG.matcher(candidate).matches())slug=candidate;
                }
            }catch(Exception ignored){}
            final String selected=slug;
            runOnUiThread(()->{if(!selected.isEmpty())showKick(KICK+"/"+selected,true);});
        }
        @JavascriptInterface public void openPlayers(String slugsJson,String player,String view,String mode){
            if(slugsJson==null||slugsJson.length()>500)return;
            runOnUiThread(()->showPlayers(slugsJson,player,view,mode));
        }
        @JavascriptInterface public void closePlayer(){runOnUiThread(()->{if(browserShowing&&playing)closeKick();});}
        @JavascriptInterface public void getDrops(int id){runOnUiThread(()->enqueue(id,"drops",""));}
        @JavascriptInterface public void getLivestreams(int id,String category){runOnUiThread(()->enqueue(id,"livestreams",category));}
        @JavascriptInterface public void getCategories(int id,String query){runOnUiThread(()->enqueue(id,"categories",query));}
        @JavascriptInterface public void getChannel(int id,String slug){runOnUiThread(()->enqueue(id,"channel",slug));}
        @JavascriptInterface public void getAuthStatus(int id){runOnUiThread(()->enqueue(id,"auth",""));}
        @JavascriptInterface public void openPip(){runOnUiThread(()->startPip());}
    }
    private void enqueue(int id,String kind,String value) {
        if(destroyed||id<=0||id>10000000||inFlight.containsKey(id))return;
        if(inFlight.size()>=12){respond(id,"{\"error\":\"too_many_requests\"}");return;}
        if("channel".equals(kind)&&(value==null||!SLUG.matcher(value).matches())) {
            respond(id,"{\"error\":\"bad_channel\"}");return;
        }
        if("livestreams".equals(kind)&&(value==null||!CATEGORY.matcher(value).matches())) {
            respond(id,"{\"error\":\"bad_category\"}");return;
        }
        if("categories".equals(kind)&&(value==null||value.trim().length()<2||value.length()>80)){
            respond(id,"{\"error\":\"bad_search_query\"}");return;
        }
        Request r=new Request(id,kind,value);
        inFlight.put(id,r);
        handler.postDelayed(()->{
            if(inFlight.remove(id)!=null){queued.remove(r);respond(id,"{\"error\":\"request_timeout\"}");}
        },TIMEOUT);
        if(apiReady)execute(r); else queued.add(r);
    }
    private void drain(){
        while(!queued.isEmpty()){
            Request r=queued.poll();if(inFlight.containsKey(r.id))execute(r);
        }
    }
    private void execute(Request r) {
        if(destroyed||!apiReady)return;
        String url,extra;
        switch(r.type){
            case "drops":
                url="https://web.kick.com/api/v1/drops/campaigns";
                extra="var pr=null;try{pr=await get('https://web.kick.com/api/v1/drops/progress')}catch(e){}"
                    +"var c=(data&&data.data)||data,p=(pr&&pr.data)||pr;"
                    +"return {campaigns:Array.isArray(c)?c:(c&&c.campaigns)||[],"
                    +"progress:Array.isArray(p)?p:(p&&p.progress)||[]};";
                break;
            case "livestreams":
                url="https://web.kick.com/api/v1/livestreams?limit=24&sort=viewer_count_desc&category_id="+Uri.encode(r.param);
                extra="var d=(data&&data.data)||data,a=Array.isArray(d)?d:(d&&d.livestreams)||[];"
                    +"return a.filter(function(s){var cat=s.category||(s.categories&&s.categories[0]);"
                    +"return !cat||!cat.id||String(cat.id)==="+JSONObject.quote(r.param)+"})"
                    +".map(function(s){var c=s.channel||{};return {slug:c.slug||"
                    +"(c.user&&(c.user.username||c.user.slug))||s.slug||'',title:s.session_title||s.title||'',"
                    +"viewer_count:s.viewer_count||0}}).filter(function(s){return /^[a-z0-9_-]{2,40}$/i.test(s.slug)});";
                break;
            case "categories":
                url="https://kick.com/api/v1/categories?search="+Uri.encode(r.param);
                // Best-effort: Kick has changed category endpoints; do not fabricate results.
                // Probe real Kick category APIs and a live directory fallback.
                extra="var q="+JSONObject.quote(r.param.toLowerCase(Locale.ROOT))+";"
                    +"var urls=['https://web.kick.com/api/v1/categories?search='+encodeURIComponent(q),"
                    +"'https://kick.com/api/v1/subcategories?search='+encodeURIComponent(q),"
                    +"'https://kick.com/api/v1/subcategories/'+encodeURIComponent(q.replace(/[^a-z0-9]+/g,'-')),"
                    +"'https://web.kick.com/api/v1/livestreams?limit=100'];"
                    +"var docs=[data];for(var url of urls){try{docs.push(await get(url))}catch(e){}}"
                    +"var found=[],seen={};function take(n,depth){if(!n||depth>5)return;"
                    +"if(Array.isArray(n)){n.slice(0,300).forEach(function(v){take(v,depth+1)});return}"
                    +"if(typeof n!=='object')return;"
                    +"if(n.id!=null&&typeof n.name==='string'&&n.name.toLowerCase().indexOf(q)>=0){"
                    +"var id=String(n.id);if(/^\\d+$/.test(id)&&!seen[id]){seen[id]=true;found.push({id:id,name:n.name,slug:n.slug||''})}}"
                    +"['data','categories','subcategories','results','items','livestreams','category'].forEach(function(k){if(n[k])take(n[k],depth+1)})}"
                    +"docs.forEach(function(d){take(d,0)});"
                    +"found.sort(function(a,b){var ax=a.name.toLowerCase()===q?0:1,bx=b.name.toLowerCase()===q?0:1;return ax-bx});"
                    +"return found.slice(0,20);";
                break;
            case "channel":url=KICK+"/api/v2/channels/"+Uri.encode(r.param);extra="return data;";break;
            case "auth":url=KICK+"/api/v1/user";
                extra="var u=(data&&data.data)||data;return {authenticated:!!(u&&u.username),username:(u&&u.username)||''};";
                break;
            default:return;
        }
        // Only fixed API URLs are evaluated in our separate Kick-origin renderer.
        // No raw cookies or general-purpose HTTP bridge is ever exposed.
        String script="(function(){window.__kickMinerReq=window.__kickMinerReq||{};var id="+r.id+";"
                +"async function get(u){var x=await fetch(u,{credentials:'include',cache:'no-store',"
                +"headers:{'Accept':'application/json'}});if(!x.ok)throw new Error('HTTP '+x.status);return x.json()}"
                +"(async function(){var data=null;"
                +("categories".equals(r.type)?"try{data=await get("+JSONObject.quote(url)+")}catch(e){}":
                    "data=await get("+JSONObject.quote(url)+")")
                +";"+extra+"})()"
                +".then(function(v){var s=JSON.stringify(v);window.__kickMinerReq[id]=s.length>250000?"
                +"JSON.stringify({error:'response_too_large'}):s})"
                +".catch(function(e){window.__kickMinerReq[id]=JSON.stringify({error:String(e.message||e)})});})()";
        api.evaluateJavascript(script,null);
        handler.postDelayed(()->poll(r),450);
    }
    private void poll(Request r) {
        if(destroyed||!apiReady||!inFlight.containsKey(r.id))return;
        String query="(function(){var m=window.__kickMinerReq||{},v=m["+r.id
                +"];if(typeof v==='string'){delete m["+r.id+"];return v}return null})()";
        api.evaluateJavascript(query,encoded->{
            if(destroyed||!inFlight.containsKey(r.id))return;
            String result=null;
            try{
                Object o=new JSONTokener(encoded==null?"null":encoded).nextValue();
                if(o instanceof String)result=(String)o;
            }catch(JSONException ignored){}
            if(result!=null){inFlight.remove(r.id);respond(r.id,result);}
            else handler.postDelayed(()->poll(r),450);
        });
    }
    private void respond(int id,String json) {
        if(!destroyed&&panel!=null)panel.evaluateJavascript("window.__nativeReply&&window.__nativeReply("+id+","
                +JSONObject.quote(json)+")",null);
    }
    @Override public void onBackPressed() {
        if(fullScreen!=null)hideFullScreen();
        else if(browserShowing){if(!playing&&kick.canGoBack())kick.goBack();else closeKick();}
        else super.onBackPressed();
    }
    @Override protected void onPause(){super.onPause();if(cookies!=null)cookies.flush();}
    @Override protected void onDestroy(){
        destroyed=true;handler.removeCallbacksAndMessages(null);
        inFlight.clear();queued.clear();
        if(playerColumn!=null)clearExtras();
        if(root!=null)root.removeAllViews();
        release(panel);release(kick);release(api);
        panel=null;kick=null;api=null;
        if(cookies!=null)cookies.flush();
        super.onDestroy();
    }
    private static void release(WebView w){if(w!=null){w.stopLoading();w.loadUrl("about:blank");w.removeAllViews();w.destroy();}}
}

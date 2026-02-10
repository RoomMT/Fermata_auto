package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_EVENT;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_ENDED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_FOUND;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PAUSED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PLAYING;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_QUALITIES;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.FrameLayout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.web.FermataJsInterface;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

public class YoutubeWebView extends FermataWebView {

    public static YoutubeWebView activeInstance;
    private YoutubeJsInterface js;
    private YoutubeMediaEngine engine;
    private static final String PLAYER_ID = "movie_player";
    private final Handler fullScreenHandler = new Handler(Looper.getMainLooper());
	private boolean isProcessingFullScreen = false; // Biến chặn Loop
    private Runnable fullScreenRunnable;

    // --- CẤU HÌNH WIDGET ---
    private SignalOverlayView overlayView; // View riêng biệt
    private int linkLevel = 0;
    private int internetLevel = 0;

    // Kích thước & Vị trí Widget
    private float WIDGET_X = 3f;
    private float WIDGET_Y = 2f;
    private float WIDGET_WIDTH = 85f;
    private float WIDGET_HEIGHT = 45f;

    private final Handler signalHandler = new Handler(Looper.getMainLooper());
    private final Runnable signalUpdater = new Runnable() {
        @Override
        public void run() {
            updateLinkSpeed();
            checkRealInternet();
            // Vẽ lại OverlayView (Chứ không phải WebView)
            if (overlayView != null && overlayView.getVisibility() == View.VISIBLE) {
                overlayView.invalidate();
            }
            signalHandler.postDelayed(this, 5000);
        }
    };

    private static final long FULL_SCREEN_DELAY_MS = 6000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (activeInstance != null) {
                try {
                    resumeTimers();
                    activeInstance.loadUrl("javascript:(function(){ window.scrollBy(0, 1); window.scrollBy(0, -1); })()");
                } catch (Exception e) {}
                mainHandler.postDelayed(this, 2000);
            }
        }
    };

    public YoutubeWebView(Context context) { super(context); init(); }
    public YoutubeWebView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public YoutubeWebView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    @SuppressLint("SetJavaScriptEnabled")
    private void init() {
        if (activeInstance != null) {
            if (activeInstance != this) {
                try {
                    Log.w("FERMATA_YT", "Found stale instance. Cleaning up...");
                    activeInstance.cleanupEngine();
                    if (activeInstance.getParent() != null) {
                        ((ViewGroup)activeInstance.getParent()).removeView(activeInstance);
                    }
                    activeInstance.destroy();
                } catch (Exception e) {}
            }
            activeInstance = null;
        }

        activeInstance = this;
        // Dùng HARDWARE để Scale hoạt động mượt và Video đẹp
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setBackgroundColor(Color.BLACK);

        WebSettings s = getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        mainHandler.post(heartbeat);

        // Khởi tạo và chạy Overlay
        signalHandler.post(signalUpdater);
    }

    // =========================================================================
    // PHƯƠNG ÁN: ADD VIEW VÀO PARENT (Theo yêu cầu)
    // =========================================================================
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (activeInstance != null && activeInstance != this) { try { activeInstance.destroy(); } catch (Exception e) {} }
        activeInstance = this;
        if (getLayerType() != View.LAYER_TYPE_HARDWARE) setLayerType(View.LAYER_TYPE_HARDWARE, null);
        syncEngine();

        // Tạo Overlay
        if (overlayView == null) {
            overlayView = new SignalOverlayView(getContext());
        }

        try {
            // Lấy Parent của WebView (Cái khung chứa do Fermata tạo ra)
            ViewGroup parentView = (ViewGroup) this.getParent();
            
            if (parentView != null) {
                // Xóa nếu đã có để tránh trùng
                parentView.removeView(overlayView);

                // Cấu hình Layout cho Widget
                // Dùng FrameLayout.LayoutParams vì đa số container trong Android là FrameLayout hoặc Relative
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        (int) WIDGET_WIDTH, 
                        (int) WIDGET_HEIGHT
                );
                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.topMargin = (int) WIDGET_Y;
                params.leftMargin = (int) WIDGET_X;

                // Set cứng tọa độ X,Y phòng trường hợp Parent không hỗ trợ Gravity
                overlayView.setX(WIDGET_X);
                overlayView.setY(WIDGET_Y);

                // Elevation cao để nổi lên trên
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    overlayView.setElevation(1000f);
                }

                // Add vào Parent
                parentView.addView(overlayView, params);
                overlayView.bringToFront(); // Đưa lên trên cùng
                
                Log.d("FERMATA_YT", "Overlay added to Parent");
            }
        } catch (Exception e) {
            Log.e("FERMATA_YT", "Add Overlay Error: " + e.getMessage());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // Gỡ Overlay khi thoát
        if (overlayView != null) {
            try {
                ViewGroup parent = (ViewGroup) overlayView.getParent();
                if (parent != null) parent.removeView(overlayView);
            } catch (Exception e) {}
        }

        if (activeInstance == null) {
            super.onDetachedFromWindow();
        } else {
            Log.d("FERMATA_YT", "Background Mode");
        }
    }

    // =========================================================================
    // LỚP VIEW WIDGET (VẼ GIAO DIỆN)
    // =========================================================================
    private class SignalOverlayView extends View {
        private Paint pBg, pInactive, pRed, pOrange, pGreen, pX;
        private RectF bgRect = new RectF(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT);

        public SignalOverlayView(Context context) {
            super(context);
            initPaints();
        }

        private void initPaints() {
            pBg = new Paint(); pBg.setColor(Color.parseColor("#B3000000")); pBg.setStyle(Paint.Style.FILL); pBg.setAntiAlias(true);
            pInactive = new Paint(); pInactive.setColor(Color.parseColor("#555555")); pInactive.setStyle(Paint.Style.FILL); pInactive.setAntiAlias(true);
            pRed = new Paint(); pRed.setColor(Color.parseColor("#F44336")); pRed.setStyle(Paint.Style.FILL); pRed.setAntiAlias(true);
            pOrange = new Paint(); pOrange.setColor(Color.parseColor("#FF9800")); pOrange.setStyle(Paint.Style.FILL); pOrange.setAntiAlias(true);
            pGreen = new Paint(); pGreen.setColor(Color.parseColor("#4CAF50")); pGreen.setStyle(Paint.Style.FILL); pGreen.setAntiAlias(true);
            pX = new Paint(); pX.setColor(Color.RED); pX.setStrokeWidth(6f); pX.setAntiAlias(true); pX.setStrokeCap(Paint.Cap.ROUND);
        }

        private Paint getPaintForLevel(int level) {
            if (level == 0) return pRed;
            if (level <= 2) return pRed;
            if (level <= 4) return pOrange;
            return pGreen;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRoundRect(bgRect, 5, 5, pBg);

            if (internetLevel == 0) {
                float pad = 15f;
                canvas.drawLine(pad, pad, WIDGET_WIDTH - pad, WIDGET_HEIGHT - pad, pX);
                canvas.drawLine(WIDGET_WIDTH - pad, pad, pad, WIDGET_HEIGHT - pad, pX);
                drawDots(canvas);
                return;
            }

            float totalBars = 6f;
            float gap = 4f; 
            float paddingSide = 8f;
            float contentWidth = WIDGET_WIDTH - (paddingSide * 2); 
            float barWidth = (contentWidth - (gap * (totalBars - 1))) / totalBars;
            float barBottomY = WIDGET_HEIGHT - 12f; 

            Paint barPaint = getPaintForLevel(internetLevel);
            
            for (int i = 1; i <= 6; i++) {
                float maxBarH = barBottomY - 4f;
                float barHeight = maxBarH * (i / totalBars); 
                float left = paddingSide + (i - 1) * (barWidth + gap);
                float top = barBottomY - barHeight;
                float right = left + barWidth;
                Paint p = (i <= internetLevel) ? barPaint : pInactive;
                canvas.drawRect(left, top, right, barBottomY, p);
            }
            drawDots(canvas);
        }

        private void drawDots(Canvas canvas) {
            float totalBars = 6f;
            float gap = 4f; 
            float paddingSide = 8f;
            float contentWidth = WIDGET_WIDTH - (paddingSide * 2); 
            float barWidth = (contentWidth - (gap * (totalBars - 1))) / totalBars;
            float dotCenterY = WIDGET_HEIGHT - 5f; 
            float dotRadius = barWidth / 2.5f;
            Paint dotActivePaint = getPaintForLevel(linkLevel);

            for (int i = 1; i <= 6; i++) {
                float centerX = paddingSide + (i - 1) * (barWidth + gap) + (barWidth / 2);
                Paint p = (i <= linkLevel) ? dotActivePaint : pInactive;
                if (linkLevel == 0 && i == 1) p = pRed;
                canvas.drawCircle(centerX, dotCenterY, dotRadius, p);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                stop();
                cancelFullScreenRequest();
                loadUrl("https://m.youtube.com");
            }
            return true;
        }
    }

    // =========================================================================
    // LOGIC FULLSCREEN (HYBRID: Block Native API + Click Button)
    // =========================================================================
    
    private boolean isFullScreenRequested = false;
    private void cancelFullScreenRequest() {
        if (fullScreenRunnable != null) {
            fullScreenHandler.removeCallbacks(fullScreenRunnable);
            fullScreenRunnable = null;
        }
        isFullScreenRequested = false;
    }

	@Override
    protected boolean requestFullScreen() {
        // Nếu đang xử lý hoặc đang trong delay thì không chạy lại nữa
        if (isProcessingFullScreen || isFullScreenRequested) return true;

        cancelFullScreenRequest(); 
        isFullScreenRequested = true;

        fullScreenRunnable = new Runnable() {
            @Override
            public void run() {
                isFullScreenRequested = false;
                if (isShown() && hasWindowFocus()) {
                    isProcessingFullScreen = true; // Đánh dấu bắt đầu thực thi JS
                    executeRequestFullScreenJs();
                    
                    // Sau khi thực thi xong, cho phép nhận lệnh tiếp theo sau 3 giây
                    // (để tránh các sự kiện thừa từ YouTube gọi lại)
                    fullScreenHandler.postDelayed(() -> isProcessingFullScreen = false, 3000);
                }
                fullScreenRunnable = null;
            }
        };

        fullScreenHandler.postDelayed(fullScreenRunnable, FULL_SCREEN_DELAY_MS);
        return true;
    }
    
	private void executeRequestFullScreenJs() {
        final String JS_SMART_RESUME =
            "javascript:(function() {" +
            "  var path = window.location.pathname;" +
            "  if (!path.includes('/watch') && !path.includes('/shorts')) return;" +

            "  function tryFullscreen() {" +
            "    var v = document.querySelector('video');" +
            "    var isFull = !!(document.fullscreenElement || document.webkitFullscreenElement);" +
            "    if (!v || isFull) return false;" +

            // Kiểm tra video đã sẵn sàng chưa
            "    if (v.readyState >= 2) {" +
            "      if (v.webkitRequestFullscreen) v.webkitRequestFullscreen();" +
            "      else if (v.requestFullscreen) v.requestFullscreen();" +
            "      return true;" +
            "    }" +
            "    return false;" +
            "  }" +

            // 1. Thử ngay lập tức
            "  if (!tryFullscreen()) {" +
            // 2. Nếu không được (do resume chưa kịp load), thử lại sau mỗi 1 giây (tối đa 3 lần)
            "    var count = 0;" +
            "    var retry = setInterval(function() {" +
            "      count++;" +
            "      if (tryFullscreen() || count > 3) clearInterval(retry);" +
            "    }, 1000);" +
            "  }" +

            // 3. ÉP PLAY (Luôn thực hiện để kích hoạt âm thanh/hình ảnh)
            "  setTimeout(function() {" +
            "    var v = document.querySelector('video');" +
            "    if (v && v.paused) v.play();" +
            "  }, 800);" +
            "})();";

        exec(JS_SMART_RESUME);
    }
    // =========================================================================
    // LOGIC ĐO MẠNG
    // =========================================================================
    
    private void updateLinkSpeed() {
        try {
            Context ctx = getContext();
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) { linkLevel = 0; return; }
            NetworkCapabilities nc = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc != null) {
                int downSpeed = nc.getLinkDownstreamBandwidthKbps();
                if (downSpeed >= 100000) linkLevel = 6;
                else if (downSpeed >= 60000) linkLevel = 5;
                else if (downSpeed >= 30000) linkLevel = 4;
                else if (downSpeed >= 15000) linkLevel = 3;
                else if (downSpeed >= 5000) linkLevel = 2;
                else linkLevel = 1;
            } else { linkLevel = 0; }
        } catch (Exception e) { linkLevel = 1; }
    }

    private void checkRealInternet() {
        new Thread(() -> {
            int quality = 0;
            try {
                long startTime = System.currentTimeMillis();
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("8.8.8.8", 53), 2000);
                socket.close();
                long latency = System.currentTimeMillis() - startTime;
                if (latency < 25) quality = 6;
                else if (latency < 50) quality = 5;
                else if (latency < 150) quality = 4;
                else if (latency < 450) quality = 3;
                else if (latency < 1500) quality = 2;
                else quality = 1;
            } catch (IOException e) { quality = 0; }
            final int finalQuality = quality;
            mainHandler.post(() -> { 
                internetLevel = finalQuality; 
                // Cập nhật Widget
                if (overlayView != null) overlayView.invalidate();
            });
        }).start();
    }

    // =========================================================================
    // CÁC HÀM CŨ (GIỮ NGUYÊN)
    // =========================================================================
    
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(View.VISIBLE); resumeTimers(); }
    @Override public void onPause() { super.onResume(); resumeTimers(); cancelFullScreenRequest(); }
    @Override public void onResume() { super.onResume(); if (getLayerType() != View.LAYER_TYPE_HARDWARE) setLayerType(View.LAYER_TYPE_HARDWARE, null); resumeTimers(); exec("javascript:(function(){ if(window.__fermataAudioCtx) window.__fermataAudioCtx.resume(); })()"); requestFullScreen(); }
    @Override protected void pageLoaded(String uri) { attachListeners(); CookieManager.getInstance().flush(); resumeTimers(); if(engine!=null&&MainActivityDelegate.get(getContext())!=null){MediaSessionCallback cb=MainActivityDelegate.get(getContext()).getMediaSessionCallback();if(cb!=null)cb.setEngine(engine);} }
    private void exec(String js) { resumeTimers(); mainHandler.post(() -> loadUrl(js)); }

    void play(){ String js="javascript:(function(){ if(window.__fermataAudioCtx) window.__fermataAudioCtx.resume(); var p=document.getElementById('movie_player'); var v=document.querySelector('video'); if(p&&p.playVideo) p.playVideo(); else if(v) v.play(); })()"; exec(js); requestFullScreen(); }
    void pause(){ exec("javascript:(function(){ var p=document.getElementById('movie_player'); if(p&&p.pauseVideo) p.pauseVideo(); else { var v=document.querySelector('video'); if(v) v.pause(); } })()"); cancelFullScreenRequest(); }
    void stop(){ exec("javascript:var v=document.querySelector('video'); if(v) { v.currentTime=0; v.pause(); }"); cancelFullScreenRequest(); }
    void prev(){ prevNext(false); }
    void next(){ prevNext(true); }
    private void prevNext(boolean next){ String js="javascript:(function(){ var p=document.getElementById('movie_player'); if(p&&p.nextVideo&&p.previousVideo){ if("+next+") p.nextVideo(); else p.previousVideo(); }else{ var btn=document.querySelector('"+(next?".ytp-next-button":".ytp-prev-button")+"'); if(btn) btn.click(); } })()"; exec(js); requestFullScreen(); }
    public void setVolume(float volume) { String js="javascript:(function(){ var v=document.querySelector('video'); if(v) v.volume="+Math.max(0f,Math.min(1f,volume))+"; var p=document.getElementById('movie_player'); if(p&&p.setVolume) p.setVolume("+(volume*100)+"); })()"; exec(js); }
    public void syncEngine() { mainHandler.post(() -> { try { MainActivityDelegate a = MainActivityDelegate.get(getContext()); if (a != null) { MediaSessionCallback cb = a.getMediaSessionCallback(); if (cb != null) { if (engine == null || cb.getEngine() != engine) { if (engine == null) engine = new YoutubeMediaEngine(this, a); cb.setEngine(engine); } } } } catch (Exception e) {} }); }
    @Override protected FermataJsInterface createJsInterface() { MainActivityDelegate a = MainActivityDelegate.get(getContext()); if (engine == null) engine = new YoutubeMediaEngine(this, a); return js = new YoutubeJsInterface(this, engine); }
    @Override public YoutubeAddon getAddon() { return (YoutubeAddon) super.getAddon(); }
    private void attachListeners() { String debug=BuildConfig.D?JS_EVENT+"("+JS_VIDEO_FOUND+", null);\n":""; String scale=getAddon().getScale().prefName(); exec("javascript:\nfunction attachVideoListeners(v){ if(v.getAttribute('FermataAttached')==='true')return; v.setAttribute('FermataAttached','true'); v.setAttribute('playsinline','true'); v.setAttribute('style','object-fit:"+scale+"');\n"+debug+" if((v.currentTime>0)&&!v.paused&&!v.ended) "+JS_EVENT+"("+JS_VIDEO_PLAYING+",v.currentSrc);\n v.addEventListener('playing',function(e){"+JS_EVENT+"("+JS_VIDEO_PLAYING+",v.currentSrc);});\n v.addEventListener('pause',function(e){"+JS_EVENT+"("+JS_VIDEO_PAUSED+",v.currentSrc);});\n v.addEventListener('ended',function(e){"+JS_EVENT+"("+JS_VIDEO_ENDED+",null);});\n}\nfunction findVideo(){ var video=document.querySelectorAll('video'); video.forEach(attachVideoListeners);\n setTimeout(findVideo,1000);\n}\nfindVideo();"); }
    FutureSupplier<String> getVideoQualities() { Promise<String> p = js.getResultPromise();
		exec("javascript:(function(){\n" +
                "  try {\n" +
                "    var p = document.getElementById('movie_player');\n" +
                "    var levels = p.getAvailableQualityLevels();\n" +
                "    var res = '';\n" +
                "    var current = p.getPlaybackQuality();\n" +
                "    if (levels && levels.length > 0) {\n" +
                "      for (var i = 0; i < levels.length; i++) {\n" +
                "        if (i > 0) res += ';';\n" +
                "        if (levels[i] === current) res += '*';\n" +
                "        var name = levels[i];\n" +
                "        if (name === 'highres') name = 'High Res';\n" +
                "        else if (name === 'hd2160') name = '4K';\n" +
                "        else if (name === 'hd1440') name = '2K';\n" +
                "        else if (name === 'hd1080') name = '1080p';\n" +
                "        else if (name === 'hd720') name = '720p';\n" +
                "        else if (name === 'large') name = '480p';\n" +
                "        else if (name === 'medium') name = '360p';\n" +
                "        else if (name === 'small') name = '240p';\n" +
                "        else if (name === 'tiny') name = '144p';\n" +
                "        res += name;\n" +
                "      }\n" +
                "      " + JS_EVENT + "(" + JS_VIDEO_QUALITIES + ", res);\n" +
                "    } else { " + JS_EVENT + "(" + JS_VIDEO_QUALITIES + ", null); }\n" +
                "  } catch(e) { " + JS_EVENT + "(" + JS_VIDEO_QUALITIES + ", null); }\n" +
                "})()");
        return p;
    }
    void setVideoQuality(int idx) { 
		final String jsCode =
		"javascript:(function(){" +
		"  try {" +
		"    var p = document.getElementById('movie_player');" +
		"    if (!p) return;" +
		"    var currentPos = p.getCurrentTime();" +
		"    var levels = p.getAvailableQualityLevels();" +
		"    var qualityChanged = false;" +
		"    var playerState = p.getPlayerState();" +
		"    if (levels && levels[" + idx + "]) {" +
		"      var q = levels[" + idx + "];" +
		"      if (p.getPlaybackQuality() !== q) {" +
		"        p.setPlaybackQualityRange(q);" +
		"        p.setPlaybackQuality(q);" + 
		"        qualityChanged = true;" +
		"      }" +
		"    }" +
		"    if (qualityChanged) {" +
		"      if (playerState !== 1) {" +
		"        setTimeout(function() { p.playVideo(); }, 300);" + 
		"      }" +
		"    } else if (playerState === 2) {" + 
		"      p.playVideo();" +
		"    }" +
		"  } catch(e) {" +
		"    console.error('Fermata Quality Switch Error:', e);" +
		"  }" +
		"})()";
		loadUrl(jsCode);}
	
    FutureSupplier<Long> getDuration() { return getMilliseconds("duration"); }
    FutureSupplier<Long> getPosition() { return getMilliseconds("currentTime"); }
    private FutureSupplier<Long> getMilliseconds(String value) { Promise<Long> p = new Promise<>(); evaluateJavascript("(function(){var v = document.querySelector('video'); return (v != null) ? v." + value + " : 0})();", v -> { try { p.complete((long) (Double.parseDouble(v) * 1000)); } catch (Exception ex) { p.complete(0L); } }); return p; }
    void setPosition(long position) { exec("javascript:var v=document.querySelector('video'); if(v) v.currentTime="+ (position/1000f) +";"); }
    FutureSupplier<Float> getSpeed() { Promise<Float> p = new Promise<>(); evaluateJavascript("(function(){var v=document.querySelector('video'); return v?v.playbackRate:0})()", v -> { try { p.complete(Float.parseFloat(v)); } catch(Exception e){ p.complete(1f); } }); return p; }
    void setSpeed(float speed) { exec("javascript:var v=document.querySelector('video'); if(v) v.playbackRate="+speed+";"); }
    FutureSupplier<String> getVideoTitle() { Promise<String> p = new Promise<>(); evaluateJavascript("document.title", p::complete); return p; }
    void setScale(YoutubeAddon.VideoScale scale) { getAddon().setScale(scale); String p = scale.prefName(); exec("javascript:document.querySelectorAll('video').forEach(v=> v.setAttribute('style', 'object-fit:" + p + "'));"); }
    private void cleanupEngine() { if (engine != null) { try { MainActivityDelegate a = MainActivityDelegate.get(getContext()); if (a != null && a.getMediaSessionCallback() != null && a.getMediaSessionCallback().getEngine() == engine) a.getMediaSessionCallback().setEngine(null); } catch (Exception e) {} engine = null; } }
    @Override
    public void destroy() {
        Log.d("FERMATA_YT", "YoutubeWebView: FULL CLEANUP - Destroying instance");

        // 1. Cancel all Handler Loops (heartbeat, signalUpdater, fullScreen)
        if (mainHandler != null) mainHandler.removeCallbacksAndMessages(null);
        if (signalHandler != null) signalHandler.removeCallbacksAndMessages(null);
        if (fullScreenHandler != null) fullScreenHandler.removeCallbacksAndMessages(null);

        // 2. Clear Runnable references
        cancelFullScreenRequest();

        // 3. Cleanup Engine & Overlay View
        cleanupEngine();
        if (overlayView != null) {
            try {
                if (overlayView.getParent() != null) {
                    ((ViewGroup) overlayView.getParent()).removeView(overlayView);
                }
            } catch (Exception e) {}
            overlayView = null;
        }

        // 4. Release WebView resources
        setLayerType(View.LAYER_TYPE_NONE, null);
        try {
            stopLoading();
            loadUrl("about:blank");
        } catch (Exception e) {}

        if (activeInstance == this) {
            activeInstance = null;
        }

        super.destroy();
    }
}
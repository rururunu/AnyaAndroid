package ai.anya.companion.core.designsystem.component

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ai.anya.companion.core.designsystem.theme.LocalDarkTheme

/**
 * Renders a ```mermaid fence inside a transparent WebView, mirroring the desktop
 * MermaidCard: pan / pinch inside the viewport, zoom buttons, fit-to-view and a
 * "show source" toggle. Rendering needs network access for the mermaid bundle.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
public fun AnyaMermaidBlock(
    source: String,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    val html = remember(source, dark, scheme.onBackground, scheme.surface) {
        buildMermaidHtml(
            source = source,
            dark = dark,
            text = scheme.onBackground,
            muted = scheme.onSurfaceVariant,
            surface = scheme.surface,
            card = scheme.surfaceVariant,
            border = scheme.outline.copy(alpha = if (dark) 0.35f else 0.45f),
            accent = scheme.primary,
        )
    }
    var showSource by rememberSaveable(source) { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.onSurface.copy(alpha = 0.06f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "图表",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (!showSource) {
                MermaidAction(icon = Icons.Rounded.Remove, label = "缩小") {
                    webViewRef?.evaluateJavascript("window.__anyaZoomBy && window.__anyaZoomBy(1/1.2)", null)
                }
                MermaidAction(icon = Icons.Rounded.Add, label = "放大") {
                    webViewRef?.evaluateJavascript("window.__anyaZoomBy && window.__anyaZoomBy(1.2)", null)
                }
                MermaidAction(icon = Icons.Rounded.CropFree, label = "适应视图") {
                    webViewRef?.evaluateJavascript("window.__anyaFit && window.__anyaFit()", null)
                }
            }
            MermaidAction(
                icon = Icons.Rounded.Code,
                label = if (showSource) "隐藏源码" else "显示源码",
                active = showSource,
            ) { showSource = !showSource }
        }
        if (showSource) {
            AnyaCodeFenceBlock(language = "mermaid", code = source)
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        background = null
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER
                        // The diagram pans / pinches inside the WebView; keep the outer
                        // list from stealing the gesture once a touch starts here.
                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.setBackgroundColor(AndroidColor.TRANSPARENT)
                            }
                        }
                        tag = html
                        loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "UTF-8", null)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    webView.setBackgroundColor(AndroidColor.TRANSPARENT)
                    webViewRef = webView
                    if (webView.tag != html) {
                        webView.tag = html
                        webView.loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "UTF-8", null)
                    }
                },
                onRelease = { if (webViewRef === it) webViewRef = null },
            )
        }
    }
}

@Composable
private fun MermaidAction(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (active) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else Color.Transparent,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Color.css(): String {
    val argb = toArgb()
    val a = (argb ushr 24) and 0xff
    val r = (argb shr 16) and 0xff
    val g = (argb shr 8) and 0xff
    val b = argb and 0xff
    return if (a == 0xff) {
        String.format("#%02x%02x%02x", r, g, b)
    } else {
        "rgba($r,$g,$b,${"%.2f".format(a / 255f)})"
    }
}

private fun buildMermaidHtml(
    source: String,
    dark: Boolean,
    text: Color,
    muted: Color,
    surface: Color,
    card: Color,
    border: Color,
    accent: Color,
): String {
    val b64 = Base64.encodeToString(source.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    val textCss = text.css()
    val mutedCss = muted.css()
    val surfaceCss = surface.css()
    val cardCss = card.css()
    val borderCss = border.css()
    val accentCss = accent.css()
    val accentSoft = if (dark) "#4fd6a0" else "#34c98f"
    val pies = listOf("#4f8ef7", "#34c98f", "#f7a44f", "#e05c7a", "#9b6df2", "#3ec6d9", "#d9c14f")
        .mapIndexed { i, c -> "pie${i + 1}: '$c'" }
        .joinToString(",\n              ")
    return """
        <!DOCTYPE html>
        <html style="background:transparent!important">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
          <style>
            html, body { margin:0; padding:0; width:100%; height:100%; overflow:hidden;
              background: transparent !important; touch-action: none; }
            #viewport { position:relative; width:100%; height:100%; overflow:hidden; }
            #stage { position:absolute; left:0; top:0; transform-origin:0 0; will-change:transform; }
            #stage svg { display:block; max-width:none !important; }
            .status { position:absolute; left:0; right:0; top:45%; text-align:center;
              font: 12px/1.5 -apple-system, "Noto Sans SC", sans-serif; color: $mutedCss; padding: 0 12px; white-space: pre-wrap; }
            .status.error { color: ${if (dark) "#ff8a80" else "#c42b1c"}; }
          </style>
        </head>
        <body style="background:transparent!important">
          <div id="viewport">
            <div id="stage"></div>
            <div id="status" class="status">渲染中…</div>
          </div>
          <script>
            function b64ToUtf8(b64) {
              const binary = atob(b64);
              const bytes = new Uint8Array(binary.length);
              for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
              return new TextDecoder('utf-8').decode(bytes);
            }
            const source = b64ToUtf8('$b64');
            const viewport = document.getElementById('viewport');
            const stage = document.getElementById('stage');
            const status = document.getElementById('status');
            let scale = 1, tx = 0, ty = 0, natW = 0, natH = 0;
            const MIN = 0.2, MAX = 6;
            function apply() {
              stage.style.transform = 'translate(' + tx + 'px,' + ty + 'px) scale(' + scale + ')';
            }
            function clampScale(s) { return Math.min(MAX, Math.max(MIN, s)); }
            function fit() {
              if (!natW || !natH) return;
              const vw = viewport.clientWidth, vh = viewport.clientHeight;
              const pad = 12;
              scale = clampScale(Math.min((vw - pad * 2) / natW, (vh - pad * 2) / natH));
              tx = (vw - natW * scale) / 2;
              ty = (vh - natH * scale) / 2;
              apply();
            }
            function zoomBy(factor, cx, cy) {
              const vw = viewport.clientWidth, vh = viewport.clientHeight;
              const px = cx == null ? vw / 2 : cx;
              const py = cy == null ? vh / 2 : cy;
              const next = clampScale(scale * factor);
              const ratio = next / scale;
              tx = px - (px - tx) * ratio;
              ty = py - (py - ty) * ratio;
              scale = next;
              apply();
            }
            window.__anyaZoomBy = function(f) { zoomBy(f); };
            window.__anyaFit = fit;

            // Touch: one finger pans, two fingers pinch around the midpoint.
            let touches = new Map();
            let lastDist = 0, lastMid = null;
            viewport.addEventListener('touchstart', (e) => {
              for (const t of e.changedTouches) touches.set(t.identifier, { x: t.clientX, y: t.clientY });
              if (touches.size === 2) {
                const [a, b] = [...touches.values()];
                lastDist = Math.hypot(a.x - b.x, a.y - b.y);
                lastMid = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
              }
              e.preventDefault();
            }, { passive: false });
            viewport.addEventListener('touchmove', (e) => {
              if (touches.size === 1) {
                const t = e.changedTouches[0];
                const prev = touches.get(t.identifier);
                if (prev) { tx += t.clientX - prev.x; ty += t.clientY - prev.y; apply(); }
              }
              for (const t of e.changedTouches) touches.set(t.identifier, { x: t.clientX, y: t.clientY });
              if (touches.size === 2) {
                const [a, b] = [...touches.values()];
                const dist = Math.hypot(a.x - b.x, a.y - b.y);
                const mid = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
                if (lastDist > 0) zoomBy(dist / lastDist, mid.x, mid.y);
                if (lastMid) { tx += mid.x - lastMid.x; ty += mid.y - lastMid.y; apply(); }
                lastDist = dist; lastMid = mid;
              }
              e.preventDefault();
            }, { passive: false });
            const endTouch = (e) => {
              for (const t of e.changedTouches) touches.delete(t.identifier);
              if (touches.size < 2) { lastDist = 0; lastMid = null; }
            };
            viewport.addEventListener('touchend', endTouch);
            viewport.addEventListener('touchcancel', endTouch);
            let lastTap = 0;
            viewport.addEventListener('touchend', (e) => {
              if (touches.size !== 0 || e.changedTouches.length !== 1) return;
              const now = Date.now();
              if (now - lastTap < 280) fit();
              lastTap = now;
            });

            (async function() {
              try {
                if (typeof mermaid === 'undefined') throw new Error('无法加载 mermaid（需要网络）');
                mermaid.initialize({
                  startOnLoad: false,
                  securityLevel: 'loose',
                  theme: 'base',
                  themeVariables: {
                    darkMode: ${if (dark) "true" else "false"},
                    background: 'transparent',
                    fontFamily: '"Noto Sans SC", system-ui, sans-serif',
                    fontSize: '13px',
                    primaryColor: '$cardCss',
                    primaryTextColor: '$textCss',
                    primaryBorderColor: '$borderCss',
                    secondaryColor: '$surfaceCss',
                    secondaryTextColor: '$textCss',
                    secondaryBorderColor: '$borderCss',
                    tertiaryColor: '$surfaceCss',
                    tertiaryTextColor: '$mutedCss',
                    tertiaryBorderColor: '$borderCss',
                    lineColor: '$mutedCss',
                    textColor: '$textCss',
                    mainBkg: '$cardCss',
                    nodeBorder: '$borderCss',
                    nodeTextColor: '$textCss',
                    clusterBkg: '$surfaceCss',
                    clusterBorder: '$borderCss',
                    defaultLinkColor: '$mutedCss',
                    titleColor: '$textCss',
                    edgeLabelBackground: '$cardCss',
                    actorBorder: '$borderCss',
                    actorBkg: '$cardCss',
                    actorTextColor: '$textCss',
                    signalColor: '$textCss',
                    signalTextColor: '$textCss',
                    labelBoxBkgColor: '$surfaceCss',
                    labelBoxBorderColor: '$borderCss',
                    labelTextColor: '$textCss',
                    loopTextColor: '$textCss',
                    noteBorderColor: '$borderCss',
                    noteBkgColor: '$surfaceCss',
                    noteTextColor: '$textCss',
                    activationBorderColor: '$accentCss',
                    activationBkgColor: '${if (dark) "#2a2a2a" else "#e8e8e8"}',
                    sequenceNumberColor: '$mutedCss',
                    sectionBkgColor: '$surfaceCss',
                    altSectionBkgColor: '$cardCss',
                    gridColor: '$borderCss',
                    taskBkgColor: '$accentSoft',
                    taskTextColor: '$textCss',
                    taskTextLightColor: '$mutedCss',
                    taskTextOutsideColor: '$textCss',
                    taskTextClickableColor: '$accentCss',
                    activeTaskBkgColor: '$accentCss',
                    activeTaskBorderColor: '$accentCss',
                    doneTaskBkgColor: '$surfaceCss',
                    doneTaskBorderColor: '$borderCss',
                    critBkgColor: '#c42b1c',
                    critBorderColor: '#c42b1c',
                    todayLineColor: '$accentCss',
                    $pies
                  },
                });
                const { svg } = await mermaid.render('anya-mermaid', source);
                stage.innerHTML = svg;
                const el = stage.querySelector('svg');
                if (!el) throw new Error('empty diagram');
                el.style.maxWidth = 'none';
                el.style.background = 'transparent';
                let vb = el.viewBox && el.viewBox.baseVal;
                if (vb && vb.width && vb.height) {
                  natW = vb.width; natH = vb.height;
                } else {
                  const box = el.getBoundingClientRect();
                  natW = box.width || 300; natH = box.height || 200;
                }
                el.setAttribute('width', natW);
                el.setAttribute('height', natH);
                el.style.width = natW + 'px';
                el.style.height = natH + 'px';
                status.remove();
                fit();
                window.addEventListener('resize', fit);
              } catch (e) {
                status.className = 'status error';
                status.textContent = '图表渲染失败\n' + (e && e.message ? e.message : String(e));
              }
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}

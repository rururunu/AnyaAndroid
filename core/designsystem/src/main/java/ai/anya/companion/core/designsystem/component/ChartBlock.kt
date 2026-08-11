package ai.anya.companion.core.designsystem.component

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
public fun AnyaChartBlock(
    specJson: String,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val html = remember(specJson, dark) { buildChartHtml(specJson, dark) }
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
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
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.setBackgroundColor(AndroidColor.TRANSPARENT)
                        view?.evaluateJavascript(
                            """
                            (function(){
                              document.documentElement.style.background='transparent';
                              document.body.style.background='transparent';
                              var c=document.getElementById('chart');
                              if(c) c.style.background='transparent';
                              var svg=document.querySelector('#chart svg');
                              if(svg){
                                svg.style.background='transparent';
                                svg.removeAttribute('style');
                                var rects=svg.querySelectorAll('rect');
                                if(rects.length){
                                  var bg=rects[0];
                                  var w=bg.getAttribute('width');
                                  var h=bg.getAttribute('height');
                                  if(w && h && Number(w)>100 && Number(h)>100){
                                    bg.setAttribute('fill','none');
                                    bg.setAttribute('fill-opacity','0');
                                  }
                                }
                              }
                            })();
                            """.trimIndent(),
                            null,
                        )
                    }
                }
                loadDataWithBaseURL(
                    "https://cdn.jsdelivr.net/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            webView.setBackgroundColor(AndroidColor.TRANSPARENT)
            webView.loadDataWithBaseURL(
                "https://cdn.jsdelivr.net/",
                html,
                "text/html",
                "UTF-8",
                null,
            )
        },
    )
}

private fun buildChartHtml(specJson: String, dark: Boolean): String {
    val b64 = Base64.encodeToString(specJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    val axisColor = if (dark) "#b7bcc5" else "#667085"
    val splitLineColor = if (dark) "rgba(255,255,255,0.12)" else "rgba(0,0,0,0.08)"
    val labelColor = if (dark) "#e5e7eb" else "#344054"
    val errColor = if (dark) "#9aa0a8" else "#666666"
    // Keep page + echarts fully transparent so the chat surface shows through.
    return """
        <!DOCTYPE html>
        <html style="background:transparent!important">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
          <script src="https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js"></script>
          <style>
            html, body, #chart {
              margin: 0; padding: 0;
              background: transparent !important;
              background-color: transparent !important;
            }
            #chart { width: 100%; height: 280px; }
            #chart svg { background: transparent !important; }
            .err { padding: 12px; font: 12px/1.5 sans-serif; color: $errColor; white-space: pre-wrap; }
          </style>
        </head>
        <body style="background:transparent!important">
          <div id="chart" style="background:transparent!important"></div>
          <script>
            function b64ToUtf8(b64) {
              const binary = atob(b64);
              const bytes = new Uint8Array(binary.length);
              for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
              return new TextDecoder('utf-8').decode(bytes);
            }
            const spec = JSON.parse(b64ToUtf8('$b64'));
            const palette = ['#4f8ef7','#34c98f','#f7a44f','#e05c7a','#9b59f5','#2ec4b6'];
            function axisStyle() {
              return { color: '$axisColor', fontSize: 11 };
            }
            function buildOption(spec) {
              if (spec.type === 'custom' && spec.option) {
                return Object.assign({}, spec.option, { backgroundColor: 'transparent' });
              }
              const type = spec.type;
              const unit = spec.unit || '';
              const tooltipValue = (v) => (Array.isArray(v) ? v.join(', ') : v) + (unit ? ' ' + unit : '');
              let option;
              if (type === 'bar' || type === 'line') {
                const series = (spec.series || []).map((s) => ({
                  name: s.name,
                  type,
                  data: s.data,
                  smooth: type === 'line',
                  barMaxWidth: 28,
                  itemStyle: type === 'bar' ? { borderRadius: [3,3,0,0] } : undefined,
                }));
                const hasLegend = series.length > 1 || Boolean(series[0] && series[0].name);
                option = {
                  color: palette,
                  tooltip: { trigger: 'axis', valueFormatter: tooltipValue },
                  grid: { left: 8, right: 16, top: hasLegend ? 36 : 16, bottom: 6, containLabel: true },
                  xAxis: { type: 'category', data: spec.x || [], axisLabel: axisStyle(), axisLine: { lineStyle: { color: '$splitLineColor' } } },
                  yAxis: { type: 'value', splitLine: { lineStyle: { color: '$splitLineColor' } }, axisLabel: axisStyle() },
                  legend: hasLegend ? { top: 0, textStyle: axisStyle() } : undefined,
                  series,
                };
              } else if (type === 'pie' || type === 'funnel') {
                const isPie = type === 'pie';
                option = {
                  color: palette,
                  tooltip: { trigger: 'item', valueFormatter: tooltipValue },
                  legend: {
                    bottom: 0,
                    type: 'scroll',
                    icon: 'circle',
                    itemWidth: 10,
                    itemHeight: 10,
                    textStyle: axisStyle(),
                  },
                  series: [{
                    type,
                    radius: isPie ? ['36%','60%'] : undefined,
                    center: isPie ? ['50%','42%'] : ['50%','45%'],
                    data: spec.items || [],
                    label: { show: false },
                    labelLine: { show: false },
                    emphasis: {
                      label: { show: true, fontSize: 12, color: '$labelColor' },
                    },
                  }],
                };
              } else if (type === 'scatter') {
                const series = (spec.series || []).map((s) => ({
                  name: s.name, type: 'scatter', data: s.data, symbolSize: 10,
                }));
                option = {
                  color: palette,
                  tooltip: { trigger: 'item' },
                  xAxis: { type: 'value', axisLabel: axisStyle() },
                  yAxis: { type: 'value', splitLine: { lineStyle: { color: '$splitLineColor' } }, axisLabel: axisStyle() },
                  series,
                };
              } else if (type === 'gauge') {
                option = {
                  series: [{
                    type: 'gauge',
                    min: spec.min ?? 0,
                    max: spec.max ?? 100,
                    data: spec.items || [],
                    axisLine: { lineStyle: { color: [[1, '$splitLineColor']] } },
                    detail: { color: '$labelColor' },
                  }],
                };
              } else if (type === 'radar') {
                option = {
                  radar: {
                    indicator: spec.indicators || [],
                    axisName: { color: '$axisColor' },
                    splitLine: { lineStyle: { color: '$splitLineColor' } },
                    splitArea: { show: false },
                  },
                  series: [{ type: 'radar', data: (spec.series || []).map((s) => ({ name: s.name, value: s.data })) }],
                };
              } else {
                option = { title: { text: spec.title || type, left: 'center', textStyle: { fontSize: 13, color: '$labelColor' } } };
              }
              option.backgroundColor = 'transparent';
              return option;
            }
            try {
              const el = document.getElementById('chart');
              // SVG renderer + no built-in dark theme (dark theme paints an opaque bg).
              const chart = echarts.init(el, null, { renderer: 'svg' });
              const option = buildOption(spec);
              option.backgroundColor = 'transparent';
              chart.setOption(option, { notMerge: true });
              // Strip any full-size background rect ECharts may still insert.
              requestAnimationFrame(function() {
                var svg = el.querySelector('svg');
                if (!svg) return;
                svg.style.background = 'transparent';
                var kids = svg.children;
                for (var i = 0; i < kids.length; i++) {
                  var node = kids[i];
                  if (node.tagName === 'rect') {
                    var w = Number(node.getAttribute('width') || 0);
                    var h = Number(node.getAttribute('height') || 0);
                    if (w > 100 && h > 100) {
                      node.setAttribute('fill', 'none');
                      node.setAttribute('fill-opacity', '0');
                    }
                  }
                }
              });
              window.addEventListener('resize', () => chart.resize());
            } catch (e) {
              document.body.innerHTML = '<div class="err">图表渲染失败\n' + String(e) + '</div>';
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}

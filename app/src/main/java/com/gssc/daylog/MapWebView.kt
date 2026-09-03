package com.gssc.daylog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.WebView
import java.util.Locale

/**
 * A real map, drawn with OpenStreetMap tiles through Leaflet.
 * No API key and no billing account, unlike Google Maps.
 * With no internet the tiles stay blank but the route line still draws.
 */
@SuppressLint("SetJavaScriptEnabled")
class MapWebView(ctx: Context) : WebView(ctx) {

    private var ready = false
    private var pending: String? = null

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        setBackgroundColor(Color.TRANSPARENT)
        webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                ready = true
                pending?.let { evaluateJavascript(it, null) }
                pending = null
            }
        }
        loadDataWithBaseURL(
            "https://tile.openstreetmap.org/",
            HTML, "text/html", "utf-8", null
        )
    }

    fun update(fixes: List<Fix>, stops: List<Stop>, dark: Boolean) {
        val track = StringBuilder("[")
        for ((i, f) in fixes.withIndex()) {
            if (i > 0) track.append(",")
            track.append(String.format(Locale.US, "[%.6f,%.6f]", f.lat, f.lng))
        }
        track.append("]")

        val marks = StringBuilder("[")
        for ((i, s) in stops.withIndex()) {
            if (i > 0) marks.append(",")
            val label = (s.name.ifBlank { "Stop" })
                .replace("\\", "").replace("\"", "'")
            marks.append(
                String.format(
                    Locale.US, "{\"la\":%.6f,\"ln\":%.6f,\"t\":\"%s\",\"w\":\"%s\"}",
                    s.lat, s.lng, label, Fmt.hhmm(s.start) + " - " + Fmt.hhmm(s.end)
                )
            )
        }
        marks.append("]")

        val js = "draw($track,$marks,$dark);"
        if (ready) evaluateJavascript(js, null) else pending = js
    }

    companion object {
        private const val HTML = """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
 html,body,#m{margin:0;padding:0;height:100%;width:100%;background:transparent}
 .leaflet-container{background:#e8ece4}
 .dk .leaflet-tile{filter:invert(1) hue-rotate(180deg) brightness(.85) contrast(.9)}
 .dk .leaflet-container{background:#0D1614}
 .lbl{font:600 11px system-ui;color:#1a1a1a;background:#fff;border:1px solid #bbb;
      border-radius:5px;padding:2px 6px;white-space:nowrap}
</style></head><body><div id="m"></div><script>
var map = L.map('m',{zoomControl:false,attributionControl:false});
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19}).addTo(map);
map.setView([26.40,50.10],10);
var line=null, layer=L.layerGroup().addTo(map);

function draw(track, stops, dark){
  document.body.className = dark ? 'dk' : '';
  if(line){ map.removeLayer(line); line=null; }
  layer.clearLayers();

  if(track.length > 1){
    line = L.polyline(track,{color:'#2E7D0E',weight:5,opacity:.9}).addTo(map);
  }
  stops.forEach(function(s){
    L.circleMarker([s.la,s.ln],{radius:8,color:'#2E7D0E',weight:3,
      fillColor:'#fff',fillOpacity:1}).addTo(layer).bindTooltip(s.t+'<br>'+s.w);
  });
  if(track.length > 1){
    map.fitBounds(L.polyline(track).getBounds(),{padding:[28,28]});
  } else if(stops.length){
    map.setView([stops[0].la,stops[0].ln],15);
  }
}
</script></body></html>
"""
    }
}

package com.nifty.options

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * NIFTY Intraday Option Analyzer — single screen.
 * Tap REFRESH -> fetches the live NSE option chain, computes Black-Scholes
 * Greeks, and shows the most suitable CALL, PUT and a neutral BALANCED
 * (straddle) strike. Switch between expiries instantly (no re-fetch).
 *
 * Educational tool only. Not investment advice. Verify on your broker.
 */
class MainActivity : Activity() {

    private val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val R_RATE = 0.065 // risk-free rate (annual)

    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private lateinit var button: Button
    private lateinit var spinner: ProgressBar
    private lateinit var expiryLabel: TextView
    private lateinit var expiryRow: LinearLayout
    private lateinit var expiryScroll: HorizontalScrollView
    private var d = 1f

    private var lastJson: String? = null
    private var expiries: List<String> = emptyList()
    private var selectedExpiry: String? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        d = resources.displayMetrics.density
        CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E1726"))
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        root.addView(text("NIFTY", 26, Color.WHITE, bold = true))
        root.addView(text("Intraday Option Strike Analyzer", 13, Color.parseColor("#9FB3C8")))

        button = Button(this).apply {
            text = "REFRESH  (fetch live NSE data)"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            setOnClickListener { fetch() }
        }
        root.addView(button, lp(MATCH_PARENT, WRAP_CONTENT, top = 16))

        spinner = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(spinner, lp(WRAP_CONTENT, WRAP_CONTENT, top = 12).also { it.gravity = Gravity.CENTER })

        status = text("Tap REFRESH to load the latest option chain.", 13, Color.parseColor("#9FB3C8"))
        root.addView(status, lp(MATCH_PARENT, WRAP_CONTENT, top = 10))

        expiryLabel = text("", 12, Color.parseColor("#9FB3C8"), bold = true).apply { visibility = View.GONE }
        root.addView(expiryLabel, lp(MATCH_PARENT, WRAP_CONTENT, top = 14))

        expiryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        expiryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            addView(expiryRow)
        }
        root.addView(expiryScroll, lp(MATCH_PARENT, WRAP_CONTENT, top = 6))

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results, lp(MATCH_PARENT, WRAP_CONTENT, top = 6))

        root.addView(
            text(
                "Ranks how TRADEABLE each strike is (liquidity + responsiveness + " +
                        "time-decay efficiency). It does not predict direction — a CALL suits a " +
                        "bullish view, a PUT a bearish one, BALANCED a neutral straddle. " +
                        "Educational only; not investment advice.",
                11, Color.parseColor("#6B7C93")
            ), lp(MATCH_PARENT, WRAP_CONTENT, top = 20)
        )

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0E1726"))
            addView(root)
        }
        setContentView(scroll)
    }

    // ---------------- networking ----------------

    private fun fetch() {
        button.isEnabled = false
        spinner.visibility = View.VISIBLE
        status.text = "Fetching NIFTY option chain from NSE…"
        results.removeAllViews()
        Thread {
            try {
                val body = httpGetNSE()
                val exps = parseExpiries(body)
                runOnUiThread {
                    spinner.visibility = View.GONE
                    button.isEnabled = true
                    lastJson = body
                    expiries = exps
                    if (selectedExpiry == null || selectedExpiry !in expiries)
                        selectedExpiry = expiries.firstOrNull()
                    buildExpiryChips()
                    analyzeAndRender()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    spinner.visibility = View.GONE
                    button.isEnabled = true
                    status.text = "Could not fetch (${e.message}).\n\nNSE often rate-limits — wait a few " +
                            "seconds and tap REFRESH again. Use normal mobile data / Wi-Fi (some VPNs are blocked)."
                }
            }
        }.start()
    }

    private fun conn(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9,en-IN;q=0.8,en-GB;q=0.7")
            setRequestProperty("Cache-Control", "max-age=0")
            setRequestProperty("sec-ch-ua",
                "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
            setRequestProperty("sec-ch-ua-mobile", "?0")
            setRequestProperty("sec-ch-ua-platform", "\"Windows\"")
            setRequestProperty("Sec-Fetch-Dest", "document")
            setRequestProperty("Sec-Fetch-Mode", "navigate")
            setRequestProperty("Sec-Fetch-Site", "none")
            setRequestProperty("Sec-Fetch-User", "?1")
            setRequestProperty("Upgrade-Insecure-Requests", "1")
        }

    private fun prime(url: String) {
        try {
            val c = conn(url)
            c.responseCode
            c.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
        }
    }

    private fun httpGetNSE(): String {
        prime("https://www.nseindia.com")
        prime("https://www.nseindia.com/option-chain")
        try { Thread.sleep(700) } catch (_: Exception) {}
        var last = "no response"
        val api = "https://www.nseindia.com/api/option-chain-indices?symbol=NIFTY"
        for (attempt in 0 until 3) {
            val c = conn(api)
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code in 200..299 && body.contains("records")) return body
            last = "HTTP $code"
            Thread.sleep(1200)
            prime("https://www.nseindia.com")
        }
        throw RuntimeException(last)
    }

    private fun parseExpiries(json: String): List<String> {
        val arr = JSONObject(json).getJSONObject("records").getJSONArray("expiryDates")
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    // ---------------- expiry selector ----------------

    private fun buildExpiryChips() {
        expiryRow.removeAllViews()
        if (expiries.isEmpty()) {
            expiryScroll.visibility = View.GONE
            expiryLabel.visibility = View.GONE
            return
        }
        expiryLabel.text = "EXPIRY  (tap to switch)"
        expiryLabel.visibility = View.VISIBLE
        expiryScroll.visibility = View.VISIBLE
        val show = expiries.take(6)
        for ((idx, e) in show.withIndex()) {
            val selected = (e == selectedExpiry)
            val tag = when (idx) {
                0 -> "\n(nearest)"
                1 -> "\n(next)"
                else -> ""
            }
            val chip = Button(this).apply {
                text = e + tag
                isAllCaps = false
                textSize = 12f
                setTextColor(if (selected) Color.WHITE else Color.parseColor("#9FB3C8"))
                setBackgroundColor(Color.parseColor(if (selected) "#2563EB" else "#16213A"))
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener {
                    selectedExpiry = e
                    buildExpiryChips()
                    analyzeAndRender()
                }
            }
            val p = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            p.rightMargin = dp(8)
            chip.layoutParams = p
            expiryRow.addView(chip)
        }
    }

    private fun analyzeAndRender() {
        val json = lastJson ?: return
        val exp = selectedExpiry ?: return
        try {
            val res = analyze(json, exp)
            status.text = "Updated:  spot ${fmt(res.spot, 2)}   •   expiry ${res.expiry}"
            render(res)
        } catch (e: Exception) {
            status.text = "Could not analyze (${e.message}). Try REFRESH again."
        }
    }

    // ---------------- analysis ----------------

    private data class Pick(
        val strike: Double, val ltp: Double, val iv: Double, val delta: Double,
        val gamma: Double, val vega: Double, val theta: Double, val oi: Double,
        val vol: Double, val score: Double
    )

    private data class Balanced(val strike: Double, val ce: Pick, val pe: Pick, val combined: Double)

    private data class Result(
        val spot: Double, val expiry: String, val atm: Double, val pcr: Double, val maxPain: Double,
        val bestCall: Pick?, val bestPut: Pick?, val balanced: Balanced?,
        val topCalls: List<Pick>, val topPuts: List<Pick>
    )

    private fun normCdf(x: Double): Double {
        val t = 1.0 / (1.0 + 0.2316419 * abs(x))
        val dn = 0.3989422804014327 * exp(-x * x / 2.0)
        val p = dn * t * (0.319381530 + t * (-0.356563782 + t *
                (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
        return if (x >= 0) 1.0 - p else p
    }

    private fun pdf(x: Double) = 0.3989422804014327 * exp(-x * x / 2.0)

    private fun analyze(json: String, expiry: String): Result {
        val rec = JSONObject(json).getJSONObject("records")
        val spot = rec.optDouble("underlyingValue", 0.0)
        val data = rec.getJSONArray("data")

        // time to expiry (expiry day 15:30 IST), clamped to >= 1 minute
        val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        sdf.timeZone = ist
        val cal = Calendar.getInstance(ist)
        cal.time = sdf.parse(expiry)!!
        cal.set(Calendar.HOUR_OF_DAY, 15); cal.set(Calendar.MINUTE, 30); cal.set(Calendar.SECOND, 0)
        val yearMs = 365.0 * 24 * 3600 * 1000
        var t = (cal.timeInMillis - System.currentTimeMillis()) / yearMs
        if (t < 1.0 / (365 * 24 * 60)) t = 1.0 / (365 * 24 * 60)

        val K = ArrayList<Double>(); val ceOI = ArrayList<Double>(); val peOI = ArrayList<Double>()
        data class Leg(val k: Double, val ltp: Double, val iv: Double, val oi: Double, val vol: Double)
        val ces = ArrayList<Leg>(); val pes = ArrayList<Leg>()
        var maxVolCe = 1.0; var maxOiCe = 1.0; var maxVolPe = 1.0; var maxOiPe = 1.0
        var sumCeOI = 0.0; var sumPeOI = 0.0

        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            if (o.optString("expiryDate") != expiry) continue
            val k = o.optDouble("strikePrice", 0.0)
            val ce = o.optJSONObject("CE"); val pe = o.optJSONObject("PE")
            val coi = ce?.optDouble("openInterest", 0.0) ?: 0.0
            val poi = pe?.optDouble("openInterest", 0.0) ?: 0.0
            K.add(k); ceOI.add(coi); peOI.add(poi); sumCeOI += coi; sumPeOI += poi
            if (ce != null) {
                val l = Leg(k, ce.optDouble("lastPrice", 0.0), ce.optDouble("impliedVolatility", 0.0),
                    coi, ce.optDouble("totalTradedVolume", 0.0))
                ces.add(l); maxVolCe = max(maxVolCe, l.vol); maxOiCe = max(maxOiCe, l.oi)
            }
            if (pe != null) {
                val l = Leg(k, pe.optDouble("lastPrice", 0.0), pe.optDouble("impliedVolatility", 0.0),
                    poi, pe.optDouble("totalTradedVolume", 0.0))
                pes.add(l); maxVolPe = max(maxVolPe, l.vol); maxOiPe = max(maxOiPe, l.oi)
            }
        }

        var atm = spot; var bestDist = Double.MAX_VALUE
        for (k in K) if (abs(k - spot) < bestDist) { bestDist = abs(k - spot); atm = k }
        val pcr = if (sumCeOI > 0) sumPeOI / sumCeOI else 0.0
        var maxPain = atm; var minCash = Double.MAX_VALUE
        for (x in K) {
            var cash = 0.0
            for (j in K.indices) {
                if (x > K[j]) cash += ceOI[j] * (x - K[j])
                if (K[j] > x) cash += peOI[j] * (K[j] - x)
            }
            if (cash < minCash) { minCash = cash; maxPain = x }
        }

        fun pick(l: Leg, isCall: Boolean, maxVol: Double, maxOi: Double): Pick? {
            if (l.iv <= 0.0) return null
            val sig = l.iv / 100.0
            val d1 = (ln(spot / l.k) + (R_RATE + sig * sig / 2) * t) / (sig * sqrt(t))
            val d2 = d1 - sig * sqrt(t)
            val delta = if (isCall) normCdf(d1) else normCdf(d1) - 1.0
            val gamma = pdf(d1) / (spot * sig * sqrt(t))
            val vega = spot * pdf(d1) * sqrt(t) / 100.0
            val theta = if (isCall)
                (-(spot * pdf(d1) * sig) / (2 * sqrt(t)) - R_RATE * l.k * exp(-R_RATE * t) * normCdf(d2)) / 365.0
            else
                (-(spot * pdf(d1) * sig) / (2 * sqrt(t)) + R_RATE * l.k * exp(-R_RATE * t) * normCdf(-d2)) / 365.0
            val liq = 0.6 * (l.vol / maxVol) + 0.4 * (l.oi / maxOi)
            val dSuit = max(0.0, 1.0 - abs(abs(delta) - 0.5) / 0.5)
            val cost = 1.0 - min(1.0, (abs(theta) / max(l.ltp, 0.05)) / 0.5)
            val score = 100.0 * (0.45 * liq + 0.35 * dSuit + 0.20 * cost)
            return Pick(l.k, l.ltp, l.iv, delta, gamma, vega, theta, l.oi, l.vol, score)
        }

        val ceList = ces.mapNotNull { pick(it, true, maxVolCe, maxOiCe) }
        val peList = pes.mapNotNull { pick(it, false, maxVolPe, maxOiPe) }
        val ceByK = ceList.associateBy { it.strike }
        val peByK = peList.associateBy { it.strike }
        val callPicks = ceList.sortedByDescending { it.score }
        val putPicks = peList.sortedByDescending { it.score }

        // BALANCED = strike where BOTH legs are most tradeable (neutral / straddle)
        var balanced: Balanced? = null
        var bestComb = -1.0
        for ((k, c) in ceByK) {
            val p = peByK[k] ?: continue
            val comb = (c.score + p.score) / 2.0
            if (comb > bestComb) { bestComb = comb; balanced = Balanced(k, c, p, comb) }
        }

        return Result(
            spot, expiry, atm, pcr, maxPain,
            callPicks.firstOrNull(), putPicks.firstOrNull(), balanced,
            callPicks.take(3), putPicks.take(3)
        )
    }

    // ---------------- rendering ----------------

    private fun render(r: Result) {
        results.removeAllViews()

        val ctx = card("#16213A")
        ctx.addView(text("MARKET CONTEXT", 12, Color.parseColor("#9FB3C8"), bold = true))
        ctx.addView(kv("Spot", fmt(r.spot, 2)))
        ctx.addView(kv("ATM strike", fmt(r.atm, 0)))
        ctx.addView(kv("Put-Call Ratio (OI)", fmt(r.pcr, 2) + sentiment(r.pcr)))
        ctx.addView(kv("Max Pain", fmt(r.maxPain, 0)))
        results.addView(ctx)

        results.addView(text("BEST CALL  (for a bullish view)", 14, Color.parseColor("#34D399"), bold = true, top = 18))
        results.addView(pickCard(r.bestCall, "#10261C", "#34D399"))

        results.addView(text("BEST PUT  (for a bearish view)", 14, Color.parseColor("#F87171"), bold = true, top = 16))
        results.addView(pickCard(r.bestPut, "#2A1518", "#F87171"))

        results.addView(text("BALANCED  (neutral — straddle/strangle)", 14, Color.parseColor("#FBBF24"), bold = true, top = 16))
        results.addView(balancedCard(r.balanced))

        if (r.topCalls.size > 1) {
            results.addView(text("Other calls", 12, Color.parseColor("#9FB3C8"), bold = true, top = 16))
            for (p in r.topCalls.drop(1)) results.addView(miniRow(p))
        }
        if (r.topPuts.size > 1) {
            results.addView(text("Other puts", 12, Color.parseColor("#9FB3C8"), bold = true, top = 12))
            for (p in r.topPuts.drop(1)) results.addView(miniRow(p))
        }
    }

    private fun pickCard(p: Pick?, bg: String, accent: String): View {
        val c = card(bg)
        if (p == null) {
            c.addView(text("No liquid strike found.", 13, Color.parseColor("#9FB3C8")))
            return c
        }
        c.addView(text("Strike ${fmt(p.strike, 0)}", 22, Color.parseColor(accent), bold = true))
        c.addView(text("Score ${fmt(p.score, 1)} / 100", 13, Color.WHITE))
        c.addView(kv("LTP (premium)", fmt(p.ltp, 2)))
        c.addView(kv("Implied Vol", fmt(p.iv, 2) + " %"))
        c.addView(kv("Delta", fmt(p.delta, 3)))
        c.addView(kv("Gamma", fmt(p.gamma, 5)))
        c.addView(kv("Vega (per 1% IV)", fmt(p.vega, 3)))
        c.addView(kv("Theta (per day)", fmt(p.theta, 2)))
        c.addView(kv("Open Interest", fmt(p.oi, 0)))
        c.addView(kv("Volume", fmt(p.vol, 0)))
        return c
    }

    private fun balancedCard(b: Balanced?): View {
        val c = card("#241E10")
        if (b == null) {
            c.addView(text("No strike with both sides liquid.", 13, Color.parseColor("#9FB3C8")))
            return c
        }
        c.addView(text("Strike ${fmt(b.strike, 0)}", 22, Color.parseColor("#FBBF24"), bold = true))
        c.addView(text("Combined score ${fmt(b.combined, 1)} / 100", 13, Color.WHITE))
        c.addView(kv("Straddle cost (CE+PE)", fmt(b.ce.ltp + b.pe.ltp, 2)))
        c.addView(kv("Net delta (CE+PE)", fmt(b.ce.delta + b.pe.delta, 3)))
        c.addView(kv("Call leg  LTP / Δ", fmt(b.ce.ltp, 2) + "  /  " + fmt(b.ce.delta, 3)))
        c.addView(kv("Put leg  LTP / Δ", fmt(b.pe.ltp, 2) + "  /  " + fmt(b.pe.delta, 3)))
        c.addView(
            text(
                "Use when you expect a big move but not the direction (buy both legs), " +
                        "or to write premium if you expect price to stay near this strike.",
                11, Color.parseColor("#9FB3C8"), top = 8
            )
        )
        return c
    }

    private fun miniRow(p: Pick): View {
        val row = card("#16213A").apply { setPadding(dp(12), dp(8), dp(12), dp(8)) }
        row.addView(
            text(
                "Strike ${fmt(p.strike, 0)}   •   LTP ${fmt(p.ltp, 2)}   •   Δ ${fmt(p.delta, 2)}" +
                        "   •   score ${fmt(p.score, 1)}", 12, Color.WHITE
            )
        )
        return row
    }

    private fun sentiment(pcr: Double) = when {
        pcr <= 0 -> ""
        pcr > 1.2 -> "  (bullish bias)"
        pcr < 0.8 -> "  (bearish bias)"
        else -> "  (neutral / range)"
    }

    // ---------------- view helpers ----------------

    private fun dp(v: Int) = (v * d).toInt()

    private fun lp(w: Int, h: Int, top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }

    private fun text(s: String, size: Int, color: Int, bold: Boolean = false, top: Int = 0): TextView =
        TextView(this).apply {
            text = s; textSize = size.toFloat(); setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(top), 0, 0)
        }

    private fun card(bg: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor(bg))
        setPadding(dp(16), dp(14), dp(16), dp(14))
        val p = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        p.topMargin = dp(8)
        layoutParams = p
    }

    private fun kv(k: String, v: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val key = TextView(this).apply {
            text = k; textSize = 13f; setTextColor(Color.parseColor("#9FB3C8"))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val value = TextView(this).apply {
            text = v; textSize = 13f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        row.addView(key); row.addView(value)
        return row
    }

    private fun fmt(v: Double, dec: Int): String = String.format(Locale.US, "%,.${dec}f", v)
}

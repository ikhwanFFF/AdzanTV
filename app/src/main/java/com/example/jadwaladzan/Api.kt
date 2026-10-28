package com.example.jadwaladzan

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

const val BASE = "https://api-khgt.muhammadiyah.or.id"

val ORDER = listOf("imsak", "subuh", "terbit", "dzuhur", "ashar", "maghrib", "isya")
val ADZAN = listOf("subuh", "dzuhur", "ashar", "maghrib", "isya")
val LABELS = mapOf(
    "imsak" to "Imsak", "subuh" to "Subuh", "terbit" to "Terbit", "dzuhur" to "Dzuhur",
    "ashar" to "Ashar", "maghrib" to "Maghrib", "isya" to "Isya"
)

// Struktur "data" tidak dijelaskan rinci di dokumentasi, jadi nama field dicocokkan secara toleran.
private val KEYS = mapOf(
    "imsak" to listOf("imsak"),
    "subuh" to listOf("subuh", "shubuh", "fajr", "fajar"),
    "terbit" to listOf("terbit", "syuruq", "syuruk", "sunrise"),
    "dzuhur" to listOf("dzuhur", "zuhur", "duhur", "dhuhr", "zuhr"),
    "ashar" to listOf("ashar", "asar", "asr"),
    "maghrib" to listOf("maghrib", "magrib"),
    "isya" to listOf("isya", "isyak", "isha")
)

data class Place(val name: String, val lat: Double, val lng: Double)
data class Day(val date: String, val times: Map<String, String>)
data class Schedule(val tz: Double, val days: List<Day>)

private val HM = Regex("""(\d{1,2})[:.](\d{2})""")
private val YMD = Regex("""^\d{4}-\d{2}-\d{2}""")

private fun post(path: String, body: JSONObject): JSONObject {
    val c = URL(BASE + path).openConnection() as HttpURLConnection
    c.requestMethod = "POST"
    c.connectTimeout = 15000
    c.readTimeout = 20000
    c.doOutput = true
    c.setRequestProperty("Content-Type", "application/json")
    c.setRequestProperty("Accept", "application/json")
    c.outputStream.use { it.write(body.toString().toByteArray()) }
    val code = c.responseCode
    val stream = if (code in 200..299) c.inputStream else c.errorStream
    val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
    if (code !in 200..299) throw Exception("HTTP $code pada $path")
    return JSONObject(text)
}

private fun flat(o: Any?, out: MutableMap<String, String> = mutableMapOf()): MutableMap<String, String> {
    when (o) {
        is JSONObject -> o.keys().forEach { k ->
            val v = o.get(k)
            if (v is JSONObject || v is JSONArray) flat(v, out) else out[k.lowercase()] = v.toString()
        }
        is JSONArray -> for (i in 0 until o.length()) flat(o.get(i), out)
    }
    return out
}

private fun parseDays(data: Any?): List<Day> {
    val items = mutableListOf<Any>()
    when (data) {
        is JSONArray -> for (i in 0 until data.length()) items.add(data.get(i))
        is JSONObject -> {
            val inner = data.optJSONArray("data")
            if (inner != null) for (i in 0 until inner.length()) items.add(inner.get(i)) else items.add(data)
        }
    }
    return items.map { item ->
        val f = flat(item)
        val times = ORDER.associateWith { k ->
            val raw = KEYS[k]!!.firstNotNullOfOrNull { f[it] }
            val m = raw?.let { HM.find(it) }
            if (m != null) "%02d:%s".format(m.groupValues[1].toInt(), m.groupValues[2]) else ""
        }
        val candidates = listOf("date", "tanggal", "tgl", "masehi", "gregorian").mapNotNull { f[it] } + f.values
        val date = candidates.firstOrNull { YMD.containsMatchIn(it) }?.take(10) ?: ""
        Day(date, times)
    }
}

fun fetchSchedule(p: Place, date: LocalDate, mode: String): Schedule {
    val res = post(
        "/api/sholat/schedule",
        JSONObject().put("lat", p.lat).put("lng", p.lng).put("date", date.toString()).put("mode", mode)
    )
    if (res.has("status") && !res.optBoolean("status", true)) {
        throw Exception(res.optString("message", "API mengembalikan status gagal"))
    }
    val tz = res.optJSONObject("meta")?.optDouble("tz", 7.0) ?: 7.0
    return Schedule(tz, parseDays(res.opt("data")))
}

fun searchPlaces(q: String): List<Place> {
    val res = post("/api/sholat/search", JSONObject().put("q", q))
    val arr = res.optJSONArray("data") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.getJSONObject(i)
        val lat = o.optString("lat").toDoubleOrNull()
        val lng = o.optString("lng").toDoubleOrNull()
        if (lat == null || lng == null) null
        else Place(o.optString("display_name", "$lat,$lng").split(",").take(2).joinToString(",").trim(), lat, lng)
    }
}

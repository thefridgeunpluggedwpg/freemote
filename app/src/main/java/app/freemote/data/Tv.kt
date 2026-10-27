package app.freemote.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Tv(val name: String, val host: String, val paired: Boolean = false)

/** Remembers TVs the user has paired with (app-private SharedPreferences). */
class TvStore(context: Context) {
    private val prefs = context.getSharedPreferences("tvs", Context.MODE_PRIVATE)

    fun load(): List<Tv> {
        val arr = JSONArray(prefs.getString("list", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Tv(o.getString("name"), o.getString("host"), o.optBoolean("paired"))
        }
    }

    fun save(tv: Tv) {
        val list = load().filterNot { it.host == tv.host } + tv
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("host", it.host).put("paired", it.paired)) }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun forget(host: String) {
        val arr = JSONArray()
        load().filterNot { it.host == host }.forEach {
            arr.put(JSONObject().put("name", it.name).put("host", it.host).put("paired", it.paired))
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }
}

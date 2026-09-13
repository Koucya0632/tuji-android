package app.tuji.android.atlas

import android.content.Context
import app.tuji.android.core.catalog.RecentSearches
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * 最近搜尋, kept on this device.
 *
 * Local on iOS too (`UserDefaults`, the same key name): what somebody looked up
 * on one phone is not an account fact worth a round trip.
 */
class RecentSearchStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _queries = MutableStateFlow(read())
    val queries: StateFlow<List<String>> = _queries.asStateFlow()

    fun push(query: String) = write(RecentSearches.push(_queries.value, query))

    fun clear() = write(emptyList())

    private fun write(list: List<String>) {
        if (list == _queries.value) return
        _queries.value = list
        prefs.edit().putString(KEY, JSONArray(list).toString()).apply()
    }

    private fun read(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS = "tuji.cache"
        const val KEY = "tuji.cache.recentSearches"
    }
}

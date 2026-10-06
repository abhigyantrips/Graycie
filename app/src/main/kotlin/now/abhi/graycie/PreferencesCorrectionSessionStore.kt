package now.abhi.graycie

import android.content.SharedPreferences

/** Kept separate from manager preferences so policy saves cannot overwrite the journal. */
class PreferencesCorrectionSessionStore(private val preferences: SharedPreferences) : CorrectionSessionStore {
    override fun read(): CorrectionSession? {
        if (!preferences.getBoolean("saved", false)) return null
        fun values(prefix: String) = CorrectionValues(
            if (preferences.contains("${prefix}Mode")) preferences.getInt("${prefix}Mode", 0) else null,
            if (preferences.contains("${prefix}Enabled")) preferences.getInt("${prefix}Enabled", 0) else null,
        )
        return CorrectionSession(
            original = values("original"),
            expected = values("expected"),
            pending = if (preferences.getBoolean("pending", false)) values("pending") else null,
            completed = preferences.getBoolean("completed", false),
        )
    }

    override fun write(session: CorrectionSession): Boolean = preferences.edit().apply {
        fun value(key: String, value: Int?) {
            if (value == null) remove(key) else putInt(key, value)
        }
        fun values(prefix: String, values: CorrectionValues?) {
            value("${prefix}Mode", values?.mode)
            value("${prefix}Enabled", values?.enabled)
        }
        putBoolean("saved", true)
        putBoolean("completed", session.completed)
        putBoolean("pending", session.pending != null)
        values("original", session.original)
        values("expected", session.expected)
        values("pending", session.pending)
    }.commit()
}

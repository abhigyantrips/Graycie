package now.abhi.graycie

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import java.text.Collator

/** Current-user launchable apps, including launchers without an app-drawer entry. */
class AppCatalog(private val context: Context) {
    data class App(
        val packageName: String,
        val label: String,
        val isHome: Boolean,
        val icon: () -> Drawable,
    )

    @Suppress("DEPRECATION") // This overload supports API 26 as well as current Android.
    fun apps(): List<App> {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val drawer = launcherApps.getActivityList(null, Process.myUserHandle()).map { app ->
            App(app.componentName.packageName, appLabel(app.componentName.packageName, app.label), false) {
                app.getIcon(context.resources.displayMetrics.densityDpi)
            }
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val activeHome = context.packageManager.resolveActivity(
            homeIntent, PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
        val homes = context.packageManager.queryIntentActivities(
            homeIntent, 0
        ).map { home ->
            App(
                home.activityInfo.packageName,
                appLabel(home.activityInfo.packageName, home.loadLabel(context.packageManager)),
                home.activityInfo.packageName == activeHome,
            ) {
                home.loadIcon(context.packageManager)
            }
        }
        val collator = Collator.getInstance()
        return (homes + drawer).distinctBy { it.packageName }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun appLabel(packageName: String, label: CharSequence?): String {
        if (packageName == ANDROID_SETTINGS_PACKAGE) return "Settings"
        return label?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: packageName
    }

    private companion object {
        const val ANDROID_SETTINGS_PACKAGE = "com.android.settings"
    }
}

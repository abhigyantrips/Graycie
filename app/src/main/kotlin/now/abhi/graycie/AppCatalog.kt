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

    private val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    @Suppress("DEPRECATION") // This overload supports API 26 as well as current Android.
    fun apps(): List<App> {
        val drawer = drawerActivities().map { app ->
            App(app.componentName.packageName, appLabel(app.componentName.packageName, app.label), false) {
                app.getIcon(context.resources.displayMetrics.densityDpi)
            }
        }
        val activeHome = context.packageManager.resolveActivity(
            homeIntent, PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
        val homes = homeActivities().map { home ->
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

    /** The launchable set of [apps] without loading any label or icon. */
    fun packageNames(): Set<String> =
        (drawerActivities().map { it.componentName.packageName } +
            homeActivities().map { it.activityInfo.packageName }).toSet()

    @Suppress("DEPRECATION") // This overload supports API 26 as well as current Android.
    private fun drawerActivities() =
        context.getSystemService(LauncherApps::class.java).getActivityList(null, Process.myUserHandle())

    private fun homeActivities() =
        context.packageManager.queryIntentActivities(homeIntent, 0)

    private fun appLabel(packageName: String, label: CharSequence?): String {
        if (packageName == ANDROID_SETTINGS_PACKAGE) return "Settings"
        return label?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: packageName
    }

    private companion object {
        const val ANDROID_SETTINGS_PACKAGE = "com.android.settings"
    }
}

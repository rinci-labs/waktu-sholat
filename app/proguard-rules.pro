# Keep the generated city table and the release entry points.
-keep class dev.rafa.waktusholat.core.** { *; }
-keep class dev.rafa.waktusholat.data.** { *; }
-keep class dev.rafa.waktusholat.WaktuSholatApp { *; }

# Widget providers, activities and receivers are instantiated by name from the manifest.
-keep class * extends android.app.Activity
-keep class * extends android.app.Application
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.appwidget.AppWidgetProvider

# RemoteViews inflates layouts by name at runtime.
-keep class dev.rafa.waktusholat.widget.** { *; }

-dontwarn java.lang.invoke.**
-renamesourcefileattribute SourceFile

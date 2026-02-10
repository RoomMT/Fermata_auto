-keepattributes LineNumberTable,SourceFile
-keep class me.aap.fermata.** { *; }
-keep interface me.aap.fermata.** { *; }
-keep class me.aap.utils.ui.** { *; }
-keep class me.aap.utils.app.** { *; }
-keep class me.aap.utils.async.** { *; }
-keep class me.aap.utils.log.Log { *; }
-keep class me.aap.utils.vfs.** { *; }
-keep class org.videolan.libvlc.** { *; }

-keep class androidx.car.app.** { *; }
-keep class org.chromium.net.impl.NativeCronetEngineBuilderImpl { *; }

-dontwarn com.sun.jna.platform.win32.**
-dontwarn com.jcraft.jsch.PageantConnector

-keep class androidx.media3.** { *; }

# Keep ExoPlayer definitions to prevent stripping in Release builds
-keep class me.aap.fermata.engine.exoplayer.** { *; }
-keep class me.aap.fermata.engine.exoplayer.ExoPlayerEngineProvider { *; }
-keep class com.google.android.apps.auto.sdk.** { *; }

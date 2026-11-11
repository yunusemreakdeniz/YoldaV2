-keepattributes LineNumberTable,SourceFile
-keepnames class me.aap.** { *; }
-keep class me.aap.fermata.auto.** { *; }
-keep class org.videolan.libvlc.** { *; }
-keep class me.aap.fermata.vfs.sftp.** { *; }
-keep class me.aap.fermata.vfs.smb.** { *; }
-keep class me.aap.fermata.vfs.gdrive.** { *; }
-keep class androidx.car.app.** { *; }
-keep class org.chromium.net.impl.NativeCronetEngineBuilderImpl { *; }

-dontwarn com.sun.jna.platform.win32.**
-dontwarn com.jcraft.jsch.PageantConnector
-dontwarn okio.*

-keepnames class androidx.media3.exoplayer.ExoPlayerImpl { *; }
-keepnames class androidx.media3.exoplayer.ExoPlayerImplInternal { *; }

# Yolda: ana ekran YouTube eklentisini adıyla çağırır
-keepclassmembers class me.aap.fermata.addon.web.yt.YoutubeFragment {
    public void loadUrl(java.lang.String);
    public java.lang.String getUrl();
}
-keepclassmembers class me.aap.fermata.addon.web.WebBrowserFragment {
    public void loadUrl(java.lang.String);
    public java.lang.String getUrl();
}

# Yolda: Shizuku (gömülü araç modu kurulumu) ve yansımayla çağrılan newProcess
-keep class rikka.shizuku.** { *; }
-keep class rikka.sui.** { *; }
-dontwarn rikka.shizuku.**

# Yolda Müzik: NewPipeExtractor (JS çözücü Rhino yansıma kullanır)
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-dontwarn javax.lang.model.**
-dontwarn org.jsoup.**
-dontwarn com.google.protobuf.**

# Yolda Müzik: YouTube yanıtları protobuf-lite ile çözülür; alan adları yansımayla okunur,
# küçültülürse "ParsingException" verir
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }

# Yolda: "Hey Yolda" (Vosk + JNA yerel köprü, yansıma kullanır)
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**

# OmniTrack ProGuard / R8 Rules
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-keep class com.omnitrack.app.BuildConfig { *; }

# Preserve Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlinx Serialization
-dontnote kotlinx.serialization.SerializationKt
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class com.omnitrack.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.omnitrack.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.omnitrack.app.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class **
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Preserve Data Models, Room DB, Backup, Sync, Preferences, and Updater DTOs
-keep class com.omnitrack.app.data.backup.** { *; }
-keep class com.omnitrack.app.data.model.** { *; }
-keep class com.omnitrack.app.data.db.** { *; }
-keep class com.omnitrack.app.data.artwork.ArtworkCandidate** { *; }
-keep class com.omnitrack.app.data.preferences.DetailsPageLayoutSettings** { *; }
-keep class com.omnitrack.app.data.sync.** { *; }
-keep class com.omnitrack.app.updater.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# OkHttp3 / Okio / TLS / Coil 3 / Media3 optional transitive warnings
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn coil3.**
-dontwarn androidx.media3.**
-dontwarn org.jsoup.**
-keep class com.omnitrack.app.data.fanfic.** { *; }

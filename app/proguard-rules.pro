# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


# ───────────── AppMeter ─────────────
# Gson y Retrofit leen los modelos por reflexión (@SerializedName, genéricos): no se ofuscan ni se reducen.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep class de.kai_morich.simple_bluetooth_terminal.models.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
# Interfaz de Retrofit: los métodos se leen por sus anotaciones
-keep interface de.kai_morich.simple_bluetooth_terminal.api.ApiService { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Para que los reportes de fallos de Play sean legibles
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Redes WiFi validadas: se guardan como JSON en las preferencias; los nombres de campo deben seguir siendo los mismos
# que escribió la versión anterior de la app.
-keep class de.kai_morich.simple_bluetooth_terminal.ui.medidor.WiFiValidationModule$ValidatedNetwork { *; }

# OkHttp: proveedores TLS opcionales que no se incluyen en la app
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

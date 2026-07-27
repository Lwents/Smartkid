# Keep model field names used by reflective or JSON-based integrations.
-keepattributes Signature,*Annotation*

# Android entry points are referenced from the manifest and layouts.
-keep class com.example.smartkid.** extends android.app.Activity { *; }
-keep class com.example.smartkid.** extends android.app.Application { *; }
-keep class com.example.smartkid.common.ui.** extends android.view.View { *; }

# Optional OkHttp platform integrations are not packaged on Android devices.
-dontwarn javax.annotation.Nullable
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.OpenSSLProvider

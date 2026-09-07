# Add project specific ProGuard rules here.
# Room, WorkManager and the Google API client generate/reflect on some classes; keep them
# since minifyEnabled is currently false anyway (see app/build.gradle.kts) this file is a
# placeholder ready for when release shrinking is turned on.
-keep class com.borzini.pos.data.db.** { *; }
-keep class com.google.api.services.** { *; }
-keep class com.google.api.client.** { *; }

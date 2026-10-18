# Keep data models for JSON serialization and SQLite mapping
-keep class com.duorhythm.app.data.model.** { *; }

# Dimezis BlurView keep rules
-keep class eightbitlab.com.blurview.** { *; }

# WorkManager Worker keep rules
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

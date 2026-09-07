// Top-level build file. Deliberately empty of plugin declarations: each module (app, core)
// applies its own plugins with a version from the catalog. Keeping the root free of plugin
// resolution means a JVM-only task (e.g. `:core:test`) never needs to reach Google's Maven
// repository for the Android Gradle Plugin, only `:app`'s own build does.

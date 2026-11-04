# Keep CatVod Spider Plugin API classes for DexClassLoader compatibility
-keep class com.github.catvod.crawler.** { *; }
-keep class com.resbox.player.data.model.** { *; }
-keepattributes *Annotation*,InnerClasses,Signature
-dontwarn org.jsoup.**

# Activities, receivers, widget providers and the custom view are referenced from the manifest and
# layouts, and AAPT already emits keep rules for those. Nothing here needs reflection, so everything
# else is left to R8 to shrink, inline and obfuscate.

# Kotlin's runtime null checks are unreachable in this closed app; drop them from the dex.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkParameterIsNotNull(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
}

-allowaccessmodification
-repackageclasses
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

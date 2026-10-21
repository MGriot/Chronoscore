# R8 rules for the Android release build.
# Compose, SQLDelight, coroutines and Compose resources ship their own consumer rules;
# keep stack traces readable in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

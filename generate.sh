#!/bin/sh

javaDir='java/src/main/java/com/davipb/jamspell/jni'
cppDir='cpp'

mkdir -p "$javaDir" "$cppDir"

rm "$cppDir/jamspell-jni.cpp"
find "$javaDir" -name '*.java' -delete

swig -c++ -java -package com.davipb.jamspell.jni -cppext cpp -o "$cppDir/jamspell-jni.cpp" -outdir "$javaDir" jamspell-jni.i

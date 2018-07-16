#! /bin/sh

mkdir -p build/linux64
cd build/linux64

cmake -G "Unix Makefiles" ../..
cd ../..

cmake --build build/linux64 --config Release

outDir='../java/src/main/resources/com/davipb/jamspell/linux/x86/bit64'
mkdir -p "$outDir"

cp build/linux64/libjamspell-jni.so "$outDir"
echo "libjamspell-jni.so" > "$outDir/jamspell-jni-name"

$javaDir = 'java/src/main/java/com/davipb/jamspell/jni'
$cppDir = 'cpp'

if (-not (Test-Path $javaDir)) { New-Item -Type Directory $javaDir }
if (-not (Test-Path $cppDir)) { New-Item -Type Directory $cppDir }

Remove-Item $cppDir/jamspell-jni.cpp
Get-ChildItem -Filter "*.java" $javaDir | Remove-Item

swig -c++ -java -package com.davipb.jamspell.jni -cppext cpp -o $cppDir/jamspell-jni.cpp -outdir $javaDir jamspell-jni.i

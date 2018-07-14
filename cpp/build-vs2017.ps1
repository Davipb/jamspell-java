if (-not (Test-Path 'build/win32')) { New-Item -Type Directory build/win32 }
if (-not (Test-Path 'build/win64')) { New-Item -Type Directory build/win64 }

Set-Location build/win32
cmake -G 'Visual Studio 15 2017' ../..

Set-Location ../win64
cmake -G 'Visual Studio 15 2017 Win64' ../..

Set-Location ../..
cmake --build build/win32 --config Release
cmake --build build/win64 --config Release

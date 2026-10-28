$ErrorActionPreference = "Stop"
python encrypt_assets.py
.\gradlew.bat assembleDebug assembleRelease
Write-Host "APK build complete! Check app/build/outputs/apk/"

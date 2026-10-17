@echo off
cd /d C:\Users\Xyrus\Desktop\XyrusCode\opencode-android-wt
call gradlew.bat test --no-daemon
echo EXITCODE=%ERRORLEVEL%

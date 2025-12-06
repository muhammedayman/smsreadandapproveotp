@echo off
echo Connecting to device...
adb wait-for-device
echo Clearing old logs...
adb logcat -c
echo.
echo =======================================================
echo  Listening for logs...
echo  PLEASE RUN THE APP ON YOUR PHONE NOW TO CRASH IT.
echo  When the app crashes, close this window or press Ctrl+C
echo =======================================================
echo.
echo Logging to crash_log.txt...
adb logcat *:E > crash_log.txt
pause

#!/bin/bash

if [[ -d "$ANDROID_HOME" ]]; then
  echo 'Environment variable `ANDROID_HOME` not found'
  break
fi

export PATH=${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${ANDROID_HOME}/tools:$PATH

echo "Choose AVD to cold boot:"
select avd in $(emulator -list-avds); do
  if [ -n "$avd" ]; then
    echo "Cold booting AVD '$avd'"
    emulator @$avd -writable-system -no-snapshot-load > emulator.log &
    break
  else
    echo "Unknown option: '$REPLY'"
  fi
done
adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done; input keyevent 82'

echo "Remount system partition"
adb root
adb shell avbctl disable-verification
adb reboot
while true; do
  adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done; input keyevent 82'
  adb root
  if [[ "$(adb remount)" = "remount succeeded" ]]; then
    echo "Remount succeeded"
    break
  else
    adb reboot
  fi
done

echo "Install system CA certificate"
adb push src/main/resources/certs/b44475dc.0 /system/etc/security/cacerts
adb shell chmod 664 /system/etc/security/cacerts/b44475dc.0
adb reboot
adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done; input keyevent 82'

echo "Configure emulator HTTP proxy"
adb shell settings put global http_proxy 10.0.2.2:8888

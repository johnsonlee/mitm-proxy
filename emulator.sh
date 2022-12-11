#!/bin/bash

if [[ ! -d "$ANDROID_HOME" ]]; then
  echo 'Environment variable `ANDROID_HOME` not found'
  exit
fi

export PATH="${ANDROID_HOME}/tools/bin:$PATH"
export PATH="${ANDROID_HOME}/tools:$PATH"
export PATH="${ANDROID_HOME}/platform-tools:$PATH"
export PATH="${ANDROID_HOME}/emulator:$PATH"
export PATH="${ANDROID_HOME}/cmdline-tools:$PATH"
export PATH="${ANDROID_HOME}/build-tools:$PATH"

which emulator

if [[ "$(emulator -list-avds | wc -l)" -le 0 ]]; then
  echo 'No available AVD'
  exit
fi

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
sleep 5
adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done; input keyevent 82'

echo "Remount system partition"
adb root
sleep 1
adb shell avbctl disable-verification
sleep 1
adb reboot
sleep 5
while true; do
  adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done; input keyevent 82'
  adb root
  sleep 1
  if [[ "$(adb remount)" = "remount succeeded" ]]; then
    echo "Remount succeeded"
    break
  else
    adb reboot
    sleep 5
  fi
done

echo "Install system CA certificate"
adb push server/src/main/resources/certs/b44475dc.0 /system/etc/security/cacerts
sleep 1
adb shell chmod 664 /system/etc/security/cacerts/b44475dc.0
sleep 1
adb reboot
sleep 5
adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done; input keyevent 82'

echo "Configure emulator HTTP proxy"
adb shell settings put global http_proxy 10.0.2.2:8888

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../native/go"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}"
export CGO_ENABLED=1 GOOS=android
for entry in 'arm64-v8a arm64 aarch64-linux-android26' 'armeabi-v7a arm armv7a-linux-androideabi26' 'x86_64 amd64 x86_64-linux-android26'; do
 read -r abi arch triple <<< "$entry"
 export GOARCH="$arch" CC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/$triple-clang"
 if [ "$arch" = arm ]; then export GOARM=7; else unset GOARM; fi
 out="../../systemblock/build/go/$abi"
 mkdir -p "$out"
 go build -trimpath -buildmode=c-shared -ldflags='-s -w -extldflags=-Wl,-z,max-page-size=16384' -o "$out/libubgo.so" .
done

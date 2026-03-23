#!/usr/bin/env bash
set -e

if [ -n "$1" ]; then
  keystore=$1
else
  read -p "Key store: " keystore
fi

read -s -p "Key store password: " keystore_password
printf "\n"

if [ -n "$2" ]; then
    keyname=$2
else
  read -p "Key name: " keyname
fi

read -s -p "Key password: " key_password
printf "\n"

./gradlew clean assembleRelease \
  -Pandroid.injected.signing.store.file=$keystore \
  -Pandroid.injected.signing.store.password=$key_password \
  -Pandroid.injected.signing.key.alias=$keyname \
  -Pandroid.injected.signing.key.password=$key_password

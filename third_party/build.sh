#!/usr/bin/env bash

if [ -z "$ANDROID_SDK_ROOT" ]; then
        echo "ERROR: ANDROID_SDK_ROOT isn't set"
        exit 1
fi

ROOT_DIR=$(pwd)
BUILD_DIR=${ROOT_DIR}/build

NDK_VERSION="25.1.8937393"
NDK_DIR="${ANDROID_SDK_ROOT}/ndk/${NDK_VERSION}"
CMAKE_VERSION="3.22.1"
CMAKE_DIR="${ANDROID_SDK_ROOT}/cmake/${CMAKE_VERSION}/bin"

function build_for_android {
        ABI=$1
        ANDROID_SYSTEM_VERSION=$2
        BUILD_TYPE_NAME=$3
        if [[ "$BUILD_TYPE_NAME" == "debug" ]]
        then
                BUILD_TYPE="Debug"
        elif [[ "$BUILD_TYPE_NAME" == "release" ]]
        then
                BUILD_TYPE="Release"
        else
                echo "the BUILD_TYPE_NAME in second argument isn't managed : ${BUILD_TYPE_NAME}"
                exit 1
        fi

        ABI_BUILD_DIR=${ROOT_DIR}/build/${ABI}

        ${CMAKE_DIR}/cmake -B${ABI_BUILD_DIR} \
                -H. \
                -DCMAKE_BUILD_TYPE=${BUILD_TYPE} \
                -DANDROID_ABI=${ABI} \
                -DCMAKE_ARCHIVE_OUTPUT_DIRECTORY=${ROOT_DIR}/prebuilt/${BUILD_TYPE_NAME}/${ABI}/ \
                -DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${ROOT_DIR}/prebuilt/${BUILD_TYPE_NAME}/${ABI}/ \
                -DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${ROOT_DIR}/prebuilt/${BUILD_TYPE_NAME}/${ABI}/ \
                -DANDROID_PLATFORM=${ANDROID_SYSTEM_VERSION} \
                -DCMAKE_ANDROID_STL=c++_static \
                -DCMAKE_NDK_DIR=$NDK_DIR \
                -DCMAKE_TOOLCHAIN_FILE=$NDK_DIR/build/cmake/android.toolchain.cmake \
                -DANDROID_TOOLCHAIN=clang \
                -DCMAKE_INSTALL_PREFIX=. \
                -DCMAKE_MAKE_PROGRAM=${CMAKE_DIR}/ninja \
                -GNinja

        pushd ${ABI_BUILD_DIR}
                cmake --build . -- -j5
        popd

        rm -rf ${ABI_BUILD_DIR}
}


ANDROID_MINSDK_VERSION="21"
# build_for_android armeabi-v7a android-${ANDROID_MINSDK_VERSION} debug
# build_for_android arm64-v8a android-${ANDROID_MINSDK_VERSION} debug
# build_for_android x86 android-${ANDROID_MINSDK_VERSION} debug
# build_for_android x86_64 android-${ANDROID_MINSDK_VERSION} debug
build_for_android armeabi-v7a android-${ANDROID_MINSDK_VERSION} release
build_for_android arm64-v8a android-${ANDROID_MINSDK_VERSION} release
build_for_android x86 android-${ANDROID_MINSDK_VERSION} release
build_for_android x86_64 android-${ANDROID_MINSDK_VERSION} release

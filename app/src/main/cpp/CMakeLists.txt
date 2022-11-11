# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.22.1)

# Declares and names the project.

project("jxlreader")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

set(LIB_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party)
set(CMAKE_BUILD_TYPE Release)
set(LIB_DIR ${LIB_ROOT}/prebuilt/${CMAKE_BUILD_TYPE}/${CMAKE_ANDROID_ARCH_ABI}/lib)
include_directories(${LIB_DIR}/../include)

add_library(jxl_dec-static STATIC IMPORTED)
set_target_properties(
        jxl_dec-static
        PROPERTIES
        IMPORTED_LOCATION ${LIB_DIR}/libjxl_dec.a
)
add_library(jxl_threads-static STATIC IMPORTED)
set_target_properties(
        jxl_threads-static
        PROPERTIES
        IMPORTED_LOCATION ${LIB_DIR}/libjxl_threads.a
)
add_library(brotlidec-static STATIC IMPORTED)
set_target_properties(
        brotlidec-static
        PROPERTIES
        IMPORTED_LOCATION ${LIB_DIR}/libbrotlidec-static.a
)
add_library(brotlicommon-static STATIC IMPORTED)
set_target_properties(
        brotlicommon-static
        PROPERTIES
        IMPORTED_LOCATION ${LIB_DIR}/libbrotlicommon-static.a
)
add_library(hwy-static STATIC IMPORTED)
set_target_properties(
        hwy-static
        PROPERTIES
        IMPORTED_LOCATION ${LIB_DIR}/libhwy.a
)

add_library( # Sets the name of the library.
        jxlreader

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        native-lib.cpp)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        jxlreader

        # Links the target library to the log library
        # included in the NDK.
        android jnigraphics log z
        jxl_dec-static jxl_threads-static brotlicommon-static brotlidec-static hwy-static)

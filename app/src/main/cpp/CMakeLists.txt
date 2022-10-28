# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.18.1)

# Declares and names the project.

project("jxlreader")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.


include_directories(includes)
add_library(jxl
        SHARED
        IMPORTED)

add_library(brotlidec
        SHARED
        IMPORTED)

add_library(brotlienc
        SHARED
        IMPORTED)

add_library(brotlicommon
        SHARED
        IMPORTED)

add_library(jxl_threads
        SHARED
        IMPORTED)

set_target_properties( # Specifies the target library.
        jxl

        # Specifies the parameter you want to define.
        PROPERTIES IMPORTED_LOCATION

        # Provides the path to the library you want to import.
        ${CMAKE_SOURCE_DIR}/../jniLibs/libjxl.so)

set_target_properties( # Specifies the target library.
        brotlidec

        # Specifies the parameter you want to define.
        PROPERTIES IMPORTED_LOCATION

        # Provides the path to the library you want to import.
        ${CMAKE_SOURCE_DIR}/../jniLibs/libbrotlidec.so)

set_target_properties( # Specifies the target library.
        brotlienc

        # Specifies the parameter you want to define.
        PROPERTIES IMPORTED_LOCATION

        # Provides the path to the library you want to import.
        ${CMAKE_SOURCE_DIR}/../jniLibs/libbrotlienc.so)

set_target_properties( # Specifies the target library.
        brotlicommon

        # Specifies the parameter you want to define.
        PROPERTIES IMPORTED_LOCATION

        # Provides the path to the library you want to import.
        ${CMAKE_SOURCE_DIR}/../jniLibs/libbrotlicommon.so)

set_target_properties( # Specifies the target library.
        jxl_threads

        # Specifies the parameter you want to define.
        PROPERTIES IMPORTED_LOCATION

        # Provides the path to the library you want to import.
        ${CMAKE_SOURCE_DIR}/../jniLibs/libjxl_threads.so)

add_library( # Sets the name of the library.
        jxlreader

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        native-lib.cpp)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        jxlreader

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}
        jxl brotlidec brotlienc brotlicommon jxl_threads)
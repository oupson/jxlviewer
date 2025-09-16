cmake_minimum_required(VERSION 3.22.1)

set(CMAKE_ANDROID_NDK_VERSION 23)

project("jxlreader")

# load dependencies
add_subdirectory(third_party)

# add project headers
include_directories(includes)

# add jni library
add_library(
        jxlreader

        # Sets the library as a shared library.
        SHARED

        native-lib.cpp
        src/Exception.cpp
        src/Decoder.cpp)


# find android logger
find_library(log-lib log)


target_link_libraries(
        jxlreader

        android jnigraphics log z
        jxl_dec jxl_threads)

link_skcms(jxlreader)

add_link_options("WL,--build-id=none")
target_link_options(
        jxlreader
        PUBLIC
        "-Wl,--build-id=none"
        "-Wl,--version-script,${CMAKE_SOURCE_DIR}/libjxlreader.map.txt"
        "-Wl,--no-undefined-version"
)

set_target_properties(jxlreader
        PROPERTIES
        LINK_DEPENDS ${CMAKE_SOURCE_DIR}/libjxlreader.map.txt
)
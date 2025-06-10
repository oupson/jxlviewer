cmake_minimum_required(VERSION 3.16)

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
target_link_options(jxlreader PUBLIC "-Wl,--build-id=none")
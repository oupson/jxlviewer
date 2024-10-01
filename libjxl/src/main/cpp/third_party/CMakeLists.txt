# libjxl
set(JPEGXL_STATIC CACHE BOOL ON)
set(BUILD_SHARED_LIBS CACHE BOOL OFF)
set(JPEGXL_ENABLE_SJPEG CACHE BOOL OFF)

add_subdirectory(libjxl)

include(skcms.cmake)
configure_file("${CMAKE_CURRENT_SOURCE_DIR}/skcms/LICENSE"
        ${PROJECT_BINARY_DIR}/LICENSE.skcms COPYONLY)

function(link_skcms TARGET_NAME)
    target_sources(${TARGET_NAME} PRIVATE "${PROJECT_SOURCE_DIR}/third_party/skcms/skcms.cc")
    target_sources(${TARGET_NAME} PRIVATE "${PROJECT_SOURCE_DIR}/third_party/skcms/src/skcms_TransformBaseline.cc")
    target_sources(${TARGET_NAME} PRIVATE "${PROJECT_SOURCE_DIR}/third_party/skcms/src/skcms_TransformHsw.cc")
    target_sources(${TARGET_NAME} PRIVATE "${PROJECT_SOURCE_DIR}/third_party/skcms/src/skcms_TransformSkx.cc")

    target_include_directories(${TARGET_NAME} PRIVATE "${PROJECT_SOURCE_DIR}/third_party/skcms/")

    include(CheckCXXCompilerFlag)
    check_cxx_compiler_flag("-Wno-psabi" CXX_WPSABI_SUPPORTED)
    if (CXX_WPSABI_SUPPORTED)
        set_source_files_properties("${PROJECT_SOURCE_DIR}/third_party/skcms/skcms.cc"
                PROPERTIES COMPILE_OPTIONS "-Wno-psabi"
                TARGET_DIRECTORY ${TARGET_NAME})
    endif ()
endfunction()

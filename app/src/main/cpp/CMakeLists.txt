cmake_minimum_required(VERSION 3.22)
project("nexus_llama" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# When llama.cpp submodule is added (git submodule add https://github.com/ggerganov/llama.cpp nativelibs/llama.cpp):
#
# # Add llama.cpp as a static library
# set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../nativelibs/llama.cpp)
# if(EXISTS ${LLAMA_DIR}/CMakeLists.txt)
#     # Build llama.cpp with GGML backend
#     set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
#     set(LLAMA_METAL OFF CACHE BOOL "" FORCE)
#     set(LLAMA_CUBLAS OFF CACHE BOOL "" FORCE)
#     set(LLAMA_VULKAN OFF CACHE BOOL "" FORCE)
#     set(LLAMA_LLAMAFILE ON CACHE BOOL "" FORCE)
#     add_subdirectory(${LLAMA_DIR} ${CMAKE_BINARY_DIR}/llama)
#     
#     add_library(nexus_llama SHARED
#         llama_jni.cpp
#     )
#     
#     target_include_directories(nexus_llama PRIVATE
#         ${CMAKE_CURRENT_SOURCE_DIR}
#         ${LLAMA_DIR}
#         ${LLAMA_DIR}/ggml/include
#         ${LLAMA_DIR}/common
#     )
#     
#     target_link_libraries(nexus_llama
#         PRIVATE
#         llama
#         ggml
#         log
#         android
#         dl
#         m
#         z
#     )
# else()
    # Without llama.cpp submodule — build stub for API-only mode
    add_library(nexus_llama SHARED
        llama_jni.cpp
    )

    target_include_directories(nexus_llama PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
    )

    target_link_libraries(nexus_llama
        log
        android
        dl
        m
    )
# endif()

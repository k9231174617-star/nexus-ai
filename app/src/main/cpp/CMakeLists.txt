cmake_minimum_required(VERSION 3.22)
project("nexus_llama" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp sources (add as submodule in nativelibs/llama.cpp)
# Uncomment and adjust paths when llama.cpp submodule is added:
# add_subdirectory(../../nativelibs/llama.cpp ${CMAKE_BINARY_DIR}/llama)
# target_link_libraries(nexus_llama PRIVATE llama)
# target_include_directories(nexus_llama PRIVATE ../../nativelibs/llama.cpp)

add_library(nexus_llama SHARED
    llama_jni.cpp
    # Additional C/C++ sources can go here
)

target_include_directories(nexus_llama PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../../nativelibs
)

# Link required libraries
target_link_libraries(nexus_llama
    log
    android
    dl
    # m
    # pthread
)

# Optional: link prebuilt llama.cpp static lib
# add_library(llama STATIC IMPORTED)
# set_target_properties(llama PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../nativelibs/llama.cpp/build/${ANDROID_ABI}/libllama.a)
# target_link_libraries(nexus_llama PRIVATE llama)

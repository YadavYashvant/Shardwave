cmake_minimum_required(VERSION 3.22.1)

project("shardwave")

set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

add_subdirectory(llama_cpp)

add_library(
    shardwave
    SHARED
    llama_bridge.cpp
)

find_library(
    log-lib
    log
)

target_include_directories(
    shardwave
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_cpp/ggml/include
)

target_link_libraries(
    shardwave
    llama
    ${log-lib}
)

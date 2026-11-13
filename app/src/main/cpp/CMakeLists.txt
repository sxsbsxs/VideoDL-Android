cmake_minimum_required(VERSION 3.22.1)
project(videodl_native LANGUAGES CXX)

add_library(
    videodl_native
    SHARED
    ffmpeg_bridge.cpp
)

target_compile_features(videodl_native PRIVATE cxx_std_17)

find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(
    videodl_native
    PRIVATE
    ${log-lib}
    ${dl-lib}
)

cmake_minimum_required(VERSION 3.5.0)
project(entry)

set(NATIVERENDER_ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR})

# Kuikly SDK
add_library(kuikly_render ALIAS render::kuikly)

# 业务产物
add_library(kuikly_shared SHARED IMPORTED)
set_target_properties(kuikly_shared PROPERTIES
    IMPORTED_LOCATION ${NATIVERENDER_ROOT_PATH}/../../../libs/${OHOS_ARCH}/libshared.so
)

add_library(entry SHARED napi_init.cpp)

target_link_libraries(entry PUBLIC
    libace_napi.z.so
    kuikly_shared
    kuikly_render
)

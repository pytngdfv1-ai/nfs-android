cmake_minimum_required(VERSION 3.22)
project(nfs_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# --- SDL3 (compartida) ---
set(SDL_SHARED ON CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_TEST_LIBRARY OFF CACHE BOOL "" FORCE)
add_subdirectory(${SDL_DIR} SDL3-build)

# --- Código del juego ---
# Ya NO se enlaza gl4es: solo se usan sus cabeceras para compilar.
# El juego habla directo con OpenGL ES (GLESv3) del teléfono.
set(U ${UPSTREAM})
file(GLOB LIB_SRC
    ${U}/src/lib/*.cpp
    ${U}/src/lib/sdl-backend/*.cpp
    ${U}/src/lib/winapi/*.cpp
    ${U}/src/lib/winapi/ddraw/*.cpp
    ${U}/src/lib/winapi/dinput/*.cpp
    ${U}/src/lib/winapi/dsound/*.cpp)
file(GLOB GAME_SRC
    ${U}/src/${GAME}/*.cpp
    ${U}/src/${GAME}/disassembly/*.cpp)

# libmain.so: SDLActivity la carga junto a libSDL3.so
add_library(main SHARED
    ${LIB_SRC}
    ${GAME_SRC}
    ${CMAKE_CURRENT_SOURCE_DIR}/android_init.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/android_mouse.cpp)

target_include_directories(main PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/shim
    ${U}/include
    ${U}/src/${GAME}
    ${U}/src/${GAME}/disassembly
    ${GL4ES_DIR}/include)

target_compile_definitions(main PRIVATE WITH_MMX)
target_compile_options(main PRIVATE -w -O1 -g0 -fno-strict-aliasing)

target_link_libraries(main PRIVATE SDL3::SDL3 GLESv3 dl log android)

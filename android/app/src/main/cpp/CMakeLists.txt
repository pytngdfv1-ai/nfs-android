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

# --- gl4es (OpenGL de escritorio sobre GLES), enlazado ESTATICO ---
set(NOX11 ON CACHE BOOL "" FORCE)
set(GLX_STUBS ON CACHE BOOL "" FORCE)
set(STATICLIB ON CACHE BOOL "" FORCE)
add_subdirectory(${GL4ES_DIR} gl4es-build)

# --- Código del juego ---
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
add_library(main SHARED ${LIB_SRC} ${GAME_SRC})

target_include_directories(main PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/shim
    ${U}/include
    ${U}/src/${GAME}
    ${U}/src/${GAME}/disassembly
    ${GL4ES_DIR}/include)

target_compile_definitions(main PRIVATE WITH_MMX)
target_compile_options(main PRIVATE -w -O1 -g0 -fno-strict-aliasing)

target_link_libraries(main PRIVATE SDL3::SDL3 GL dl log android)

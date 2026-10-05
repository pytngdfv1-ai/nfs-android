// Shim para ARM64: reemplaza el "int3" (solo x86) por __builtin_trap()
#pragma once

// Carga el x86.h original del proyecto upstream
#include_next <x86.h>

#undef NFS2_ASSERT
#define NFS2_ASSERT(x) \
    if (!(x)) __builtin_trap()

#include <stdlib.h>

// Se ejecuta al cargar libmain.so, antes del main del juego.
// Le dice a SDL que ignore los avisos internos (assertions) en vez de abrir el cartel.
__attribute__((constructor))
static void nfs_android_init() {
    setenv("SDL_ASSERT", "always_ignore", 1);
}

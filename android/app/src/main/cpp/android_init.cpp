#include <stdlib.h>
#include <jni.h>
#include <SDL3/SDL.h>

// Se ejecuta al cargar libmain.so, antes del main del juego.
// Le dice a SDL que ignore los avisos internos (assertions) en vez de abrir el cartel.
__attribute__((constructor))
static void nfs_android_init() {
    setenv("SDL_ASSERT", "always_ignore", 1);
}

// Envia un caracter al juego como "texto escrito" (WM_CHAR), para los trucos.
extern "C" JNIEXPORT void JNICALL
Java_com_nfs_recompiled_NfsActivity_nativeTypeChar(JNIEnv*, jclass, jint c) {
    char s[2] = { (char)c, 0 };
    SDL_Event e;
    SDL_zero(e);
    e.type = SDL_EVENT_TEXT_INPUT;
    e.text.text = s;
    SDL_PushEvent(&e);
}

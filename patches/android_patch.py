import sys
import pathlib

root = pathlib.Path(sys.argv[1])


def edit(rel, fn):
    p = root / rel
    s = p.read_text()
    n = fn(s)
    if n == s:
        print("AVISO: sin cambios en", rel)
    p.write_text(n)


# 1) Shaders: GLSL 400 (escritorio) -> GLSL ES 3.00
def shaders(s):
    s = s.replace('#version 400\\n',
                  '#version 300 es\\nprecision highp float;\\nprecision highp int;\\nprecision highp sampler2D;\\n')
    s = s.replace('texture2D(', 'texture(')
    s = s.replace(', 0, v_atlasInfo.t-1)', ', 0.0, v_atlasInfo.t-1.0)')
    s = s.replace(', 0, v_atlasInfo.t)', ', 0.0, v_atlasInfo.t)')
    return s


edit('src/lib/gliderenderer.cpp', shaders)


# 2) Ventana: pedir contexto OpenGL ES 3.0
def window(s):
    old = 'm_window(SDL_CreateWindow(title, w, h, SDL_WINDOW_RESIZABLE|SDL_WINDOW_OPENGL))'
    new = ('m_window((SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_ES),'
           ' SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 3),'
           ' SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 0),'
           ' SDL_CreateWindow(title, w, h, SDL_WINDOW_RESIZABLE|SDL_WINDOW_OPENGL)))')
    return s.replace(old, new)


edit('src/lib/window.cpp', window)


# 3) Inicializar gl4es tras crear el contexto (si el simbolo existe)
def renderer(s):
    s = s.replace('    setCurrent();\n    SDL_GL_SetSwapInterval(1);',
                  '    setCurrent();\n    if (initialize_gl4es) initialize_gl4es();\n    SDL_GL_SetSwapInterval(1);', 1)
    return '#include <SDL3/SDL.h>\nextern "C" void initialize_gl4es() __attribute__((weak));\n' + s


edit('src/lib/renderer.cpp', renderer)

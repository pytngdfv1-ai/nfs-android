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


def must_replace(s, old, new, count=-1):
    if old not in s:
        print("AVISO: no se encontro:", old[:60].replace("\n", " "))
    return s.replace(old, new, count)


# ---------------------------------------------------------------- gliderenderer.cpp
def glide(s):
    # Shaders: GLSL 400 (escritorio) -> GLSL ES 3.00
    s = must_replace(s, '#version 400\\n',
                     '#version 300 es\\nprecision highp float;\\nprecision highp int;\\nprecision highp sampler2D;\\n')
    s = must_replace(s, 'texture2D(', 'texture(')
    s = must_replace(s, ', 0, v_atlasInfo.t-1)', ', 0.0, v_atlasInfo.t-1.0)')
    s = must_replace(s, ', 0, v_atlasInfo.t)', ', 0.0, v_atlasInfo.t)')

    # glClearDepth no existe en GLES
    s = must_replace(s, 'glClearDepth(1.0);', 'glClearDepthf(1.0f);')

    # Matriz de proyeccion calculada a mano (sin glOrtho / GL_PROJECTION_MATRIX)
    old = ('        glMatrixMode(GL_PROJECTION);\n'
           '        glLoadIdentity();\n'
           '        glOrtho(0, m_renderer->m_width, 0, m_renderer->m_height, 0, -65536.0f);\n'
           '        glMatrixMode(GL_MODELVIEW);\n'
           '        glLoadIdentity();\n'
           '        glUseProgram(m_shaderProgram);\n'
           '        float matrix[16];\n'
           '        glGetFloatv(GL_PROJECTION_MATRIX, matrix);\n')
    new = ('        glUseProgram(m_shaderProgram);\n'
           '        float matrix[16] = {0};\n'
           '        matrix[0]  = 2.0f / float(m_renderer->m_width);\n'
           '        matrix[5]  = 2.0f / float(m_renderer->m_height);\n'
           '        matrix[10] = 2.0f / 65536.0f;\n'
           '        matrix[12] = -1.0f;\n'
           '        matrix[13] = -1.0f;\n'
           '        matrix[14] = -1.0f;\n'
           '        matrix[15] = 1.0f;\n')
    s = must_replace(s, old, new)

    # GL_UNSIGNED_INT_8_8_8_8 no existe en GLES: intercambiar bytes y usar GL_UNSIGNED_BYTE
    old = ('        glTexSubImage2D(GL_TEXTURE_2D, lod, x, y, largeMipmapSize, largeMipmapSize, '
           'GL_RGBA, GL_UNSIGNED_INT_8_8_8_8, textureData);\n')
    new = ('        for (x86::reg32 k = 0; k < largeMipmapSize*largeMipmapSize; ++k)\n'
           '            textureData[k] = __builtin_bswap32(textureData[k]);\n'
           '        glTexSubImage2D(GL_TEXTURE_2D, lod, x, y, largeMipmapSize, largeMipmapSize, '
           'GL_RGBA, GL_UNSIGNED_BYTE, textureData);\n')
    s = must_replace(s, old, new)

    return 'extern "C" void glClearDepthf(float);\n' + s


edit('src/lib/gliderenderer.cpp', glide)


# ---------------------------------------------------------------- window.cpp
def window(s):
    old = 'm_window(SDL_CreateWindow(title, w, h, SDL_WINDOW_RESIZABLE|SDL_WINDOW_OPENGL))'
    new = ('m_window((SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_ES),'
           ' SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 3),'
           ' SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 0),'
           ' SDL_CreateWindow(title, w, h, SDL_WINDOW_RESIZABLE|SDL_WINDOW_OPENGL)))')
    return must_replace(s, old, new)


edit('src/lib/window.cpp', window)


# ---------------------------------------------------------------- renderer.cpp
BLIT_HELPER = r'''
// ---- Android/GLES: dibujar la textura del juego con un shader (sin OpenGL antiguo) ----
extern "C" void android_set_viewport(int vpX, int vpY, int vpW, int vpH,
                                     int winW, int winH, int gameW, int gameH);
// El juego usa varios contextos GL (menu y carrera): cada uno necesita su propio programa.
struct AndroidBlitProg
{
    SDL_GLContext ctx;
    GLuint program;
    GLint texLoc;
};
static AndroidBlitProg s_blitProgs[8];
static int s_blitProgCount = 0;

static AndroidBlitProg* androidBlitGet()
{
    SDL_GLContext cur = SDL_GL_GetCurrentContext();
    for (int i = 0; i < s_blitProgCount; ++i)
    {
        if (s_blitProgs[i].ctx == cur)
            return &s_blitProgs[i];
    }

    PFNGLCREATESHADERPROC pCreateShader = (PFNGLCREATESHADERPROC)SDL_GL_GetProcAddress("glCreateShader");
    PFNGLSHADERSOURCEPROC pShaderSource = (PFNGLSHADERSOURCEPROC)SDL_GL_GetProcAddress("glShaderSource");
    PFNGLCOMPILESHADERPROC pCompileShader = (PFNGLCOMPILESHADERPROC)SDL_GL_GetProcAddress("glCompileShader");
    PFNGLCREATEPROGRAMPROC pCreateProgram = (PFNGLCREATEPROGRAMPROC)SDL_GL_GetProcAddress("glCreateProgram");
    PFNGLATTACHSHADERPROC pAttachShader = (PFNGLATTACHSHADERPROC)SDL_GL_GetProcAddress("glAttachShader");
    PFNGLLINKPROGRAMPROC pLinkProgram = (PFNGLLINKPROGRAMPROC)SDL_GL_GetProcAddress("glLinkProgram");
    PFNGLGETUNIFORMLOCATIONPROC pGetUniformLocation = (PFNGLGETUNIFORMLOCATIONPROC)SDL_GL_GetProcAddress("glGetUniformLocation");

    static const char vs[] =
        "#version 300 es\n"
        "out vec2 v_uv;\n"
        "void main() {\n"
        "    vec2 p = vec2(float(gl_VertexID & 1), float((gl_VertexID >> 1) & 1));\n"
        "    v_uv = vec2(p.x, 1.0 - p.y);\n"
        "    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);\n"
        "}\n";
    static const char fs[] =
        "#version 300 es\n"
        "precision mediump float;\n"
        "in vec2 v_uv;\n"
        "uniform sampler2D u_tex;\n"
        "out vec4 o_color;\n"
        "void main() { o_color = vec4(texture(u_tex, v_uv).rgb, 1.0); }\n";

    GLuint v = pCreateShader(GL_VERTEX_SHADER);
    const GLchar* vsrc = vs;
    pShaderSource(v, 1, &vsrc, nullptr);
    pCompileShader(v);
    GLuint f = pCreateShader(GL_FRAGMENT_SHADER);
    const GLchar* fsrc = fs;
    pShaderSource(f, 1, &fsrc, nullptr);
    pCompileShader(f);
    GLuint prog = pCreateProgram();
    pAttachShader(prog, v);
    pAttachShader(prog, f);
    pLinkProgram(prog);

    int slot = s_blitProgCount < 8 ? s_blitProgCount++ : 7;
    s_blitProgs[slot].ctx = cur;
    s_blitProgs[slot].program = prog;
    s_blitProgs[slot].texLoc = pGetUniformLocation(prog, "u_tex");
    return &s_blitProgs[slot];
}

static void androidBlit()
{
    AndroidBlitProg* bp = androidBlitGet();
    PFNGLUSEPROGRAMPROC pUseProgram = (PFNGLUSEPROGRAMPROC)SDL_GL_GetProcAddress("glUseProgram");
    PFNGLUNIFORM1IPROC pUniform1i = (PFNGLUNIFORM1IPROC)SDL_GL_GetProcAddress("glUniform1i");
    PFNGLBINDVERTEXARRAYPROC pBindVertexArray = (PFNGLBINDVERTEXARRAYPROC)SDL_GL_GetProcAddress("glBindVertexArray");

    GLint oldVao = 0;
    glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &oldVao);
    pBindVertexArray(0);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glActiveTexture(GL_TEXTURE0);
    pUseProgram(bp->program);
    pUniform1i(bp->texLoc, 0);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    pUseProgram(0);
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    pBindVertexArray((GLuint)oldVao);
}

'''


def renderer(s):
    # GL_TEXTURE_2D ya no se "habilita" en GLES
    s = must_replace(s, '    glEnable(GL_TEXTURE_2D);\n', '')

    # Bloque de OpenGL antiguo -> dibujo con shader
    start = s.find('    glMatrixMode(GL_PROJECTION);')
    end = s.find('    glEnd();\n', start)
    if start < 0 or end < 0:
        print("AVISO: no se encontro el bloque glMatrixMode...glEnd")
    else:
        s = s[:start] + '    androidBlit();\n' + s[end + len('    glEnd();\n'):]

    # Paleta 8 bits: GL_UNSIGNED_INT_8_8_8_8 no existe en GLES
    old = ('        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, m_width, m_height, 0, GL_RGBA, '
           'GL_UNSIGNED_INT_8_8_8_8, screenData);\n')
    new = ('        for (x86::reg32 k = 0; k < m_width*m_height; ++k)\n'
           '            screenData[k] = __builtin_bswap32(screenData[k]);\n'
           '        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, m_width, m_height, 0, GL_RGBA, '
           'GL_UNSIGNED_BYTE, screenData);\n')
    s = must_replace(s, old, new)

    # Informar al raton tactil de la posicion del juego en pantalla
    old = ('    glViewport(0, 0, w, h);\n'
           '    glClearColor(0.f, 0.f, 0.f, 1.f);')
    new = ('    android_set_viewport(vpX, vpY, vpW, vpH, w, h, int(m_width), int(m_height));\n'
           '    glViewport(0, 0, w, h);\n'
           '    glClearColor(0.f, 0.f, 0.f, 1.f);')
    s = must_replace(s, old, new, 1)

    # Insertar el helper antes de Renderer::present()
    marker = 'void Renderer::present()'
    if marker not in s:
        print("AVISO: no se encontro Renderer::present")
    s = s.replace(marker, BLIT_HELPER + marker, 1)
    return s


edit('src/lib/renderer.cpp', renderer)


# ---------------------------------------------------------------- idirectinputdevice.cpp
def dinput(s):
    s = must_replace(s, '#include <lib/gamepad.h>\n',
                     '#include <lib/gamepad.h>\n'
                     'extern "C" int android_mouse_fetch(unsigned* ofs, int* data, int maxCount, int peek);\n'
                     'extern "C" void android_mouse_state(int* dx, int* dy, int* button);\n', 1)

    # Modo inmediato (GetDeviceState)
    old = ('    app->lockContext(cpu);\n'
           '    return 0;\n'
           '}\n'
           '\n'
           'HRESULT IDirectInputDevice::GetDeviceData(')
    new = ('    else if (dynamic_cast<Mouse*>(m_resource) && cbData >= 16)\n'
           '    {\n'
           '        int mdx = 0, mdy = 0, mb = 0;\n'
           '        android_mouse_state(&mdx, &mdy, &mb);\n'
           '        int* mstate = reinterpret_cast<int*>(lpvData);\n'
           '        mstate[0] = mdx;\n'
           '        mstate[1] = mdy;\n'
           '        reinterpret_cast<unsigned char*>(lpvData)[12] = mb ? 0x80 : 0x00;\n'
           '    }\n'
           '    app->lockContext(cpu);\n'
           '    return 0;\n'
           '}\n'
           '\n'
           'HRESULT IDirectInputDevice::GetDeviceData(')
    s = must_replace(s, old, new, 1)

    # Modo con buffer (GetDeviceData)
    old = ('    NFS2_ASSERT(dynamic_cast<Mouse*>(m_resource));\n'
           '    *pdwInOut = 0;\n'
           '    return 0;\n')
    new = ('    NFS2_ASSERT(dynamic_cast<Mouse*>(m_resource));\n'
           '    {\n'
           '        static unsigned s_sequence = 0;\n'
           '        unsigned mofs[64];\n'
           '        int mdata[64];\n'
           '        unsigned cap = *pdwInOut;\n'
           '        if (cap > 64) cap = 64;\n'
           '        int n = android_mouse_fetch(mofs, mdata, int(cap), (dwFlags & 1) ? 1 : 0);\n'
           '        for (int i = 0; i < n; ++i)\n'
           '        {\n'
           '            DIDEVICEOBJECTDATA* d = reinterpret_cast<DIDEVICEOBJECTDATA*>(\n'
           '                reinterpret_cast<char*>(rgdod) + i * cbObjectData);\n'
           '            d->dwOfs = mofs[i];\n'
           '            d->dwData = DWORD(mdata[i]);\n'
           '            d->dwTimeStamp = 0;\n'
           '            d->dwSequence = ++s_sequence;\n'
           '        }\n'
           '        *pdwInOut = DWORD(n);\n'
           '    }\n'
           '    return 0;\n')
    return must_replace(s, old, new, 1)


edit('src/lib/winapi/dinput/idirectinputdevice.cpp', dinput)

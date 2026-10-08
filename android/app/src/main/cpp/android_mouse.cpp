// Mouse tactil para el juego (DirectInput "SysMouse" no esta implementado en el proyecto original).
// El dedo se traduce a movimiento relativo + boton izquierdo del raton.
#include <SDL3/SDL.h>
#include <mutex>
#include <deque>
#include <cmath>

// Si el cursor del juego cae desplazado respecto al dedo, ajustar este factor.
static const float MOUSE_SCALE = 1.0f;

namespace
{
struct Ev { unsigned ofs; int data; };

std::mutex          s_mutex;
std::deque<Ev>      s_queue;
bool                s_fingerActive = false;
SDL_FingerID        s_finger = 0;
float               s_lastGX = 0, s_lastGY = 0;
float               s_remX = 0, s_remY = 0;
bool                s_buttonDown = false;
bool                s_pressedSincePoll = false;

int s_vpX = 0, s_vpY = 0, s_vpW = 1, s_vpH = 1;
int s_winW = 1, s_winH = 1, s_gameW = 640, s_gameH = 480;

enum { OFS_X = 0, OFS_Y = 4, OFS_BUTTON0 = 12 };

void push(unsigned ofs, int data)
{
    if (s_queue.size() > 512)
        s_queue.pop_front();
    Ev e = { ofs, data };
    s_queue.push_back(e);
}

void toGame(float nx, float ny, float& gx, float& gy)
{
    float px = nx * float(s_winW);
    float py = ny * float(s_winH);
    gx = (px - float(s_vpX)) * float(s_gameW) / float(s_vpW);
    gy = (py - float(s_vpY)) * float(s_gameH) / float(s_vpH);
    if (gx < 0) gx = 0;
    if (gy < 0) gy = 0;
    if (gx > s_gameW - 1) gx = float(s_gameW - 1);
    if (gy > s_gameH - 1) gy = float(s_gameH - 1);
}

bool SDLCALL onEvent(void*, SDL_Event* e)
{
    switch (e->type)
    {
    case SDL_EVENT_FINGER_DOWN:
        {
            std::lock_guard<std::mutex> lock(s_mutex);
            if (s_fingerActive)
                break;
            float gx, gy;
            toGame(e->tfinger.x, e->tfinger.y, gx, gy);
            s_fingerActive = true;
            s_finger = e->tfinger.fingerID;
            // Llevar el cursor a la esquina (0,0) y de ahi al punto tocado
            push(OFS_X, -4000);
            push(OFS_Y, -4000);
            push(OFS_X, int(std::lround(gx * MOUSE_SCALE)));
            push(OFS_Y, int(std::lround(gy * MOUSE_SCALE)));
            push(OFS_BUTTON0, 0x80);
            s_lastGX = gx;
            s_lastGY = gy;
            s_remX = s_remY = 0;
            s_buttonDown = true;
            s_pressedSincePoll = true;
        }
        break;
    case SDL_EVENT_FINGER_MOTION:
        {
            std::lock_guard<std::mutex> lock(s_mutex);
            if (!s_fingerActive || e->tfinger.fingerID != s_finger)
                break;
            float gx, gy;
            toGame(e->tfinger.x, e->tfinger.y, gx, gy);
            float dx = (gx - s_lastGX) * MOUSE_SCALE + s_remX;
            float dy = (gy - s_lastGY) * MOUSE_SCALE + s_remY;
            int ix = int(std::lround(dx));
            int iy = int(std::lround(dy));
            s_remX = dx - float(ix);
            s_remY = dy - float(iy);
            if (ix) push(OFS_X, ix);
            if (iy) push(OFS_Y, iy);
            s_lastGX = gx;
            s_lastGY = gy;
        }
        break;
    case SDL_EVENT_FINGER_UP:
    case SDL_EVENT_FINGER_CANCELED:
        {
            std::lock_guard<std::mutex> lock(s_mutex);
            if (!s_fingerActive || e->tfinger.fingerID != s_finger)
                break;
            push(OFS_BUTTON0, 0);
            s_fingerActive = false;
            s_buttonDown = false;
        }
        break;
    default:
        break;
    }
    return true;
}

void install()
{
    static std::once_flag once;
    std::call_once(once, []() { SDL_AddEventWatch(onEvent, nullptr); });
}
}

// Lo llama Renderer::present() en cada fotograma
extern "C" void android_set_viewport(int vpX, int vpY, int vpW, int vpH,
                                     int winW, int winH, int gameW, int gameH)
{
    std::lock_guard<std::mutex> lock(s_mutex);
    s_vpX = vpX; s_vpY = vpY;
    s_vpW = vpW > 0 ? vpW : 1;
    s_vpH = vpH > 0 ? vpH : 1;
    s_winW = winW > 0 ? winW : 1;
    s_winH = winH > 0 ? winH : 1;
    s_gameW = gameW > 0 ? gameW : 640;
    s_gameH = gameH > 0 ? gameH : 480;
}

// Modo "buffered" de DirectInput (GetDeviceData)
extern "C" int android_mouse_fetch(unsigned* ofs, int* data, int maxCount, int peek)
{
    install();
    std::lock_guard<std::mutex> lock(s_mutex);
    int n = 0;
    while (n < maxCount && n < int(s_queue.size()))
    {
        ofs[n] = s_queue[n].ofs;
        data[n] = s_queue[n].data;
        ++n;
    }
    if (!peek)
    {
        for (int i = 0; i < n; ++i)
            s_queue.pop_front();
    }
    return n;
}

// Modo "inmediato" de DirectInput (GetDeviceState): desplazamiento acumulado y boton
extern "C" void android_mouse_state(int* dx, int* dy, int* button)
{
    install();
    std::lock_guard<std::mutex> lock(s_mutex);
    int sx = 0, sy = 0;
    while (!s_queue.empty())
    {
        Ev e = s_queue.front();
        s_queue.pop_front();
        if (e.ofs == OFS_X) sx += e.data;
        else if (e.ofs == OFS_Y) sy += e.data;
    }
    *dx = sx;
    *dy = sy;
    *button = (s_buttonDown || s_pressedSincePoll) ? 1 : 0;
    s_pressedSincePoll = false;
}

package com.nfs.recompiled;

import android.os.Bundle;
import java.io.File;
import org.libsdl.app.SDLActivity;

public class NfsActivity extends SDLActivity {

    private String gameDir() {
        File base = getExternalFilesDir(null);
        File dir = new File(base, "game");
        dir.mkdirs();
        return dir.getAbsolutePath();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        gameDir(); // crea la carpeta antes de arrancar
        super.onCreate(savedInstanceState);
    }

    // Se pasa como argv[1] al main() del juego (datos y "CD" en la misma carpeta)
    @Override
    protected String[] getArguments() {
        return new String[] { gameDir() };
    }
}

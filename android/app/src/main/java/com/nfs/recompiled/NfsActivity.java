package com.nfs.recompiled;

import android.os.Bundle;
import android.os.Environment;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import org.libsdl.app.SDLActivity;

public class NfsActivity extends SDLActivity {

    // Carpeta de los archivos del juego: <almacenamiento interno>/NFS3
    private String gameDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), "NFS3");
        dir.mkdirs();
        return dir.getAbsolutePath();
    }

    // Guarda el log de la app en <almacenamiento interno>/nfs_log.txt
    private void startLogger() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File f = new File(Environment.getExternalStorageDirectory(), "nfs_log.txt");
                    java.lang.Process p = Runtime.getRuntime().exec(new String[] { "logcat", "-v", "time" });
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    FileWriter w = new FileWriter(f, false);
                    String line;
                    while ((line = r.readLine()) != null) {
                        w.write(line + "\n");
                        w.flush();
                    }
                } catch (Exception e) {
                    // sin log, no pasa nada
                }
            }
        }).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        gameDir(); // crea la carpeta NFS3 si no existe
        startLogger();
        super.onCreate(savedInstanceState);
    }

    // Se pasa como argv[1] al main() del juego
    @Override
    protected String[] getArguments() {
        return new String[] { gameDir() };
    }

    // Matar el proceso al cerrar: así cada apertura empieza limpia
    @Override
    protected void onDestroy() {
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}

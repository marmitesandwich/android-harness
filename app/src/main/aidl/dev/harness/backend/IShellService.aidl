package dev.harness.backend;

import android.os.Bundle;

interface IShellService {

    // Destroy method defined by Shizuku server.
    void destroy() = 16777114;

    // Runs a command and returns {exitCode:int, stdout:String, stderr:String}.
    Bundle exec(String command);
}

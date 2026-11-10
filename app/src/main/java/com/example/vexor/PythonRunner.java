package com.example.vexor;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public class PythonRunner {

    public interface OutputCallback {
        void onOutput(String text);
    }

    public interface InputCallback {
        String readInput();
    }

    private final Context context;

    public PythonRunner(Context context) {
        this.context = context;
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context));
        }
    }

    public void runInteractive(String code, InputCallback inputCallback, OutputCallback outputCallback) {
        try {
            Python py = Python.getInstance();
            PyObject module = py.getModule("vexor_runner");
            String extraPath = context.getFilesDir().getAbsolutePath();
            module.callAttr("execute_interactive", code, inputCallback, outputCallback, extraPath);
        } catch (Exception e) {
            outputCallback.onOutput("\nExecution error: " + e.getMessage() + "\n");
        }
    }

    public void installLibrary(String packageName, OutputCallback outputCallback) {
        try {
            Python py = Python.getInstance();
            PyObject module = py.getModule("vexor_runner");
            String extraPath = context.getFilesDir().getAbsolutePath();
            module.callAttr("install_lib_interactive", packageName, outputCallback, extraPath);
        } catch (Exception e) {
            outputCallback.onOutput("\nInstallation error: " + e.getMessage() + "\n");
        }
    }
}

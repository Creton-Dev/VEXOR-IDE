package com.example.vexor;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.concurrent.LinkedBlockingQueue;

public class TerminalActivity extends AppCompatActivity {

    private TextView terminalOutput;
    private ScrollView terminalScroll;
    private EditText inputField;
    private final LinkedBlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_terminal);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.terminal_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        WindowInsetsControllerCompat wic = WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());
        wic.hide(WindowInsetsCompat.Type.navigationBars());
        wic.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE);




        terminalOutput = findViewById(R.id.terminal_output);
        terminalScroll = findViewById(R.id.terminal_scroll);
        inputField = findViewById(R.id.input_field);

        Button btnClear = findViewById(R.id.btn_clear_term);
        Button btnClose = findViewById(R.id.btn_close_term);
        Button btnSend = findViewById(R.id.btn_send_input);

        btnClear.setOnClickListener(v -> terminalOutput.setText(""));
        btnClose.setOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> sendUserInput());
        inputField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendUserInput();
                return true;
            }
            return false;
        });

        String mode = getIntent().getStringExtra("mode");
        if ("pip".equals(mode)) {
            String pkgName = getIntent().getStringExtra("package_name");
            appendTerminal(">>> Installing " + pkgName + " via Pip...\n\n");
            new Thread(() -> {
                PythonRunner runner = new PythonRunner(TerminalActivity.this);
                runner.installLibrary(pkgName, this::appendTerminal);
            }).start();
        } else if ("maven".equals(mode)) {
            String pkgName = getIntent().getStringExtra("package_name");
            appendTerminal(">>> Installing " + pkgName + " via Maven...\n\n");
            new Thread(() -> {
                MavenInstaller.installDependency(TerminalActivity.this, pkgName, this::appendTerminal);
            }).start();
        } else if ("java".equals(mode)) {
            String code = getIntent().getStringExtra("code");
            appendTerminal(">>> Running Java Code...\n\n");
            new Thread(() -> {
                JavaRunner runner = new JavaRunner(TerminalActivity.this);
                runner.runInteractive(
                        code,
                        () -> {
                            runOnUiThread(() -> {
                                inputField.requestFocus();
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (imm != null) {
                                    imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT);
                                }
                            });
                            try {
                                return inputQueue.take();
                            } catch (InterruptedException e) {
                                return "";
                            }
                        },
                        this::appendTerminal
                );
            }).start();
        } else {
            String code = getIntent().getStringExtra("code");
            appendTerminal(">>> Running Python Script...\n\n");
            new Thread(() -> {
                PythonRunner runner = new PythonRunner(TerminalActivity.this);
                runner.runInteractive(
                        code,
                        () -> {
                            // Focus input field and open keyboard when Python asks for input
                            runOnUiThread(() -> {
                                inputField.requestFocus();
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (imm != null) {
                                    imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT);
                                }
                            });
                            try {
                                return inputQueue.take();
                            } catch (InterruptedException e) {
                                return "";
                            }
                        },
                        this::appendTerminal
                );
            }).start();
        }
    }

    private void sendUserInput() {
        String text = inputField.getText().toString();
        inputField.setText("");
        inputQueue.offer(text);
    }

    private void appendTerminal(String text) {
        runOnUiThread(() -> {
            terminalOutput.append(text);
            terminalScroll.post(() -> terminalScroll.fullScroll(View.FOCUS_DOWN));
        });
    }
}

package com.example.vexor;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;

public class Python_activity extends AppCompatActivity {

    private CodeEditor codeEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_python);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        WindowInsetsControllerCompat wic = WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());
        wic.hide(WindowInsetsCompat.Type.navigationBars());
        wic.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE);



        codeEditor = findViewById(R.id.codeeditor);
        Button btnRun = findViewById(R.id.run);
        Button btnPip = findViewById(R.id.btn_pip);

        // Setup Code Editor
        codeEditor.setTypefaceText(Typeface.MONOSPACE);
        codeEditor.setColorScheme(new io.github.rosemoe.sora.widget.schemes.EditorColorScheme() {{
            setColor(WHOLE_BACKGROUND, Color.rgb(20, 20, 24));
            setColor(TEXT_NORMAL, Color.rgb(230, 230, 230));
            setColor(LINE_NUMBER, Color.rgb(120, 120, 130));
            setColor(LINE_NUMBER_BACKGROUND, Color.rgb(20, 20, 24));
            setColor(SELECTION_INSERT, Color.rgb(100, 160, 255));
            setColor(UNDERLINE, Color.rgb(255, 80, 80));
        }});
        codeEditor.setEditorLanguage(new JavaLanguage());
        codeEditor.setText("\nname = input(\"Enter your name: \")\nprint(f\"Hello, {name}!\")\n\nfor i in range(1, 4):\n    print(f\"Counting: {i}\")");

        // Run Python Script in Terminal Activity
        btnRun.setOnClickListener(v -> {
            Intent intent = new Intent(Python_activity.this, TerminalActivity.class);
            intent.putExtra("mode", "run");
            intent.putExtra("code", codeEditor.getText().toString());
            startActivity(intent);
        });

        // Install Libraries via UI Dialog
        btnPip.setOnClickListener(v -> showInstallPackageDialog());
    }

    private void showInstallPackageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Install Python Library (Pip)");

        final EditText input = new EditText(this);
        input.setHint("e.g. requests, numpy, colorama");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Install", (dialog, which) -> {
            String pkgName = input.getText().toString().trim();
            if (!pkgName.isEmpty()) {
                Intent intent = new Intent(Python_activity.this, TerminalActivity.class);
                intent.putExtra("mode", "pip");
                intent.putExtra("package_name", pkgName);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}

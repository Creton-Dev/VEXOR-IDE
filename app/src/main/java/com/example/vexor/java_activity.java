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
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

public class java_activity extends AppCompatActivity {

    private CodeEditor codeEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_java);

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
        Button btnMaven = findViewById(R.id.btn_maven);

        // Setup Sora Code Editor
        codeEditor.setTypefaceText(Typeface.MONOSPACE);
        codeEditor.setColorScheme(new EditorColorScheme() {{
            setColor(WHOLE_BACKGROUND, Color.rgb(20, 20, 24));
            setColor(TEXT_NORMAL, Color.rgb(230, 230, 230));
            setColor(LINE_NUMBER, Color.rgb(120, 120, 130));
            setColor(LINE_NUMBER_BACKGROUND, Color.rgb(20, 20, 24));
            setColor(SELECTION_INSERT, Color.rgb(100, 160, 255));
            setColor(UNDERLINE, Color.rgb(255, 80, 80));
        }});
        codeEditor.setEditorLanguage(new JavaLanguage());
        codeEditor.setText("\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"Hello from Java!\");\n" +
                "        \n" +
                "        List items = new ArrayList();\n" +
                "        items.add(\"Java\");\n" +
                "        items.add(\"Maven\");\n" +
                "        items.add(\"Vexor\");\n" +
                "        \n" +
                "        for (Object item : items) {\n" +
                "            System.out.println(\"Item: \" + item);\n" +
                "        }\n" +
                "    }\n" +
                "}\n");

        // Run Java Code in Terminal Activity
        btnRun.setOnClickListener(v -> {
            Intent intent = new Intent(java_activity.this, TerminalActivity.class);
            intent.putExtra("mode", "java");
            intent.putExtra("code", codeEditor.getText().toString());
            startActivity(intent);
        });

        // Install Maven Libraries via Dialog
        btnMaven.setOnClickListener(v -> showInstallMavenDialog());
    }

    private void showInstallMavenDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Install Java Library (Maven)");

        final EditText input = new EditText(this);
        input.setHint("e.g. com.google.code.gson:gson:2.10.1");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Install", (dialog, which) -> {
            String depName = input.getText().toString().trim();
            if (!depName.isEmpty()) {
                Intent intent = new Intent(java_activity.this, TerminalActivity.class);
                intent.putExtra("mode", "maven");
                intent.putExtra("package_name", depName);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}

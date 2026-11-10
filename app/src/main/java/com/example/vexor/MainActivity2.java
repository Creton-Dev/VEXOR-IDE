package com.example.vexor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class MainActivity2 extends AppCompatActivity {

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main2);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        WindowInsetsControllerCompat wic = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        wic.hide(WindowInsetsCompat.Type.navigationBars());
        wic.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        TextView txt = findViewById(R.id.textView5);
        SharedPreferences sp = getSharedPreferences("user_info", Context.MODE_PRIVATE);
        String user_n = sp.getString("USER_NAME", "User");

        txt.setText("Welcome, " + user_n + "!");

        Button bu = findViewById(R.id.button5);
        bu.setOnClickListener(v -> {
            Uri uri = Uri.parse("https://www.instagram.com/creton_dev?dlrf=cTg0NGdrdDVpcW5r");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        });

        Button pyb = findViewById(R.id.button2);
        pyb.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity2.this, Python_activity.class);
            startActivity(intent);
        });

        Button javab = findViewById(R.id.button3);
        javab.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity2.this, java_activity.class);
            startActivity(intent);
        });

        Button onec = findViewById(R.id.button4);
        onec.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity2.this, online_compiler_activity.class);
            startActivity(intent);
        });
    }
}

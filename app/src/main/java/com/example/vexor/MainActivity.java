package com.example.vexor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        WindowInsetsControllerCompat wic = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        wic.hide(WindowInsetsCompat.Type.navigationBars());
        wic.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);


        SharedPreferences sp = getSharedPreferences("user_info",Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        String data = sp.getString("USER_NAME","");

        if(data.trim().isEmpty()){

            EditText te = findViewById(R.id.editTextText2);
            Button submit = findViewById(R.id.button);
            submit.setOnClickListener(v -> {
                String data1 = te.getText().toString();
                editor.putString("USER_NAME",""+data1).apply();
                Intent intent = new Intent(MainActivity.this, MainActivity2.class);
                intent.putExtra("user_n", data1);
                startActivity(intent);
                finish();
            });

        }else{
            Intent intent = new Intent(MainActivity.this,MainActivity2.class);
            startActivity(intent);
            finish();
        }




    }
}

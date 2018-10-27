package com.ankit.demomapapp;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.ankit.demomapapp.positioning.BasicPositioningActivity;

public class HomeActivity extends AppCompatActivity {

    Button simpleMap, currentPos, aroundMe, navigation;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        simpleMap = findViewById(R.id.button);
        currentPos = findViewById(R.id.button2);
        aroundMe = findViewById(R.id.button3);
        navigation = findViewById(R.id.button4);



        simpleMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this,MainActivity.class ));
            }
        });

        currentPos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this,BasicPositioningActivity.class ));
            }
        });

        aroundMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, com.ankit.demomapapp.search.MainActivity.class ));
            }
        });

        navigation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, com.ankit.demomapapp.navigation.MainActivity.class ));
            }
        });

    }
}

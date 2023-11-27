package com.jwlilly.accessibilityinspector;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class ServiceActivity extends AppCompatActivity implements View.OnClickListener{
    Button buttonStart, buttonStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonStart = findViewById(R.id.buttonStart);
        buttonStop = findViewById(R.id.buttonStop);

        buttonStart.setOnClickListener(this);
        buttonStop.setOnClickListener(this);
        startForegroundService(new Intent(this, SocketService.class));

    }
    public void onClick(View src) {
        switch (src.getId()) {
            case R.id.buttonStart:
                startForegroundService(new Intent(this, SocketService.class));
                break;
            case R.id.buttonStop:
                stopService(new Intent(this, SocketService.class));
                break;
        }
    }
}

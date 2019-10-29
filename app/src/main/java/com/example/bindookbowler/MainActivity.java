package com.example.bindookbowler;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;


public class MainActivity extends AppCompatActivity {

    private Button buttonSwitchToBluetooth, buttonSwitchToViewData, buttonSwitchToDirView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonSwitchToBluetooth = (Button) findViewById(R.id.buttonBlueTooth);
        buttonSwitchToViewData = (Button) findViewById(R.id.buttonViewData);
        buttonSwitchToDirView = (Button) findViewById(R.id.buttonDirData);

        buttonSwitchToBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context context = getApplicationContext();

                Intent blueToothManager = new Intent(context, BlueToothManager.class);
                startActivity(blueToothManager);
            }
        });

        buttonSwitchToViewData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context context = getApplicationContext();

                Intent dataView = new Intent(context, DataView.class);
                startActivity(dataView);
            }


        });

        buttonSwitchToDirView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context context = getApplicationContext();

                Intent dataDir = new Intent(context, DataDir.class);
                startActivity(dataDir);
            }


        });



    }
}

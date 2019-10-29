package com.example.bindookbowler;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Date;

public class DataView extends AppCompatActivity {

    private DataBuffer dataBuffer;
    private DataBuffer lastStored;

    private static int MSG_CONSTANT = 3;
    private static String ACCEL = "Acceleration:";
    private static String GYRO = "Gyroscope:";
    private static int RECORDLENGTH = 10000;

    private GraphView graphAcc, graphGyro;

    private TextView txtStatus, txtTime;
    private TextView txtAx, txtAy, txtAz, txtGx, txtGy, txtGz;

    private TextView txtDebug;

    private EditText recordLength, fileName;

    private Button record;

    private BTConnection btConnection;
    private Handler mHandler; // Our main handler that will receive callback notifications

    private Boolean recording;
    private int timeStartRecord, finishRecording;
    private int recordedData;

    private Button btnMenu, btnBT, btnDataDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_view);

        recording = false;

        txtAx = (TextView) findViewById(R.id.txtAx);
        txtAy = (TextView) findViewById(R.id.txtAy);
        txtAz = (TextView) findViewById(R.id.txtAz);

        txtGx = (TextView) findViewById(R.id.txtGx);
        txtGy = (TextView) findViewById(R.id.txtGy);
        txtGz = (TextView) findViewById(R.id.txtGz);

        txtTime = (TextView) findViewById(R.id.txtTime);
        txtStatus = (TextView) findViewById(R.id.txtStatus);

        txtDebug = (TextView) findViewById(R.id.txtDebug);

        recordLength = (EditText) findViewById(R.id.edtRecordingLength);
        fileName = (EditText) findViewById(R.id.edtSaveFileName);
        record = (Button) findViewById(R.id.btnRecord);

        graphAcc = (GraphView) findViewById(R.id.graphAcc);
        graphGyro = (GraphView) findViewById(R.id.graphGyro);

        btnMenu = (Button) findViewById(R.id.btnMenu);
        btnBT = (Button) findViewById(R.id.btnBT);
        btnDataDir = (Button) findViewById(R.id.btnDataDir);
        initialize_buttons();

        record.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!recording) {
                    timeStartRecord = dataBuffer.most_recent().time;
                    recording = true;
                    recordedData = 0;

                    String tEdt = recordLength.getText().toString();

                    int tEdit;
                    try {
                        tEdit = Integer.valueOf(tEdt);
                        if(tEdit > RECORDLENGTH) {
                            recordLength.setText(String.valueOf(RECORDLENGTH));
                            tEdit = RECORDLENGTH;
                        }
                    } catch (NumberFormatException e) {
                        recordLength.setText(String.valueOf(RECORDLENGTH));
                        tEdit = RECORDLENGTH;
                    }

                    finishRecording = timeStartRecord + tEdit;
                    dataBuffer.reset();
                    lastStored.reset();
                } else {
                    Toast toast = Toast.makeText(getBaseContext(), "Still Recording", Toast.LENGTH_LONG);
                    toast.show();
                }
            }

        }));

        // Should result in 10-15[s] of buffer
        dataBuffer = new DataBuffer(15000);
        lastStored = new DataBuffer(15000);

        mHandler = new Handler() {

            public void handleMessage(android.os.Message msg){
                if(msg.what == BTConnection.MESSAGE_READ){
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    parseString(readMessage);
                    if(recording) {
                        txtDebug.setText("recent time: " +  String.valueOf(dataBuffer.most_recent().time) +
                                        "finish recording time: " + String.valueOf(finishRecording));
                        if(dataBuffer.most_recent().time > finishRecording) {

                            int iDP = 0;
                            DataPoint[] dataListAx = new DataPoint[recordedData];
                            DataPoint[] dataListAy = new DataPoint[recordedData];
                            DataPoint[] dataListAz = new DataPoint[recordedData];

                            DataPoint[] dataListGx = new DataPoint[recordedData];
                            DataPoint[] dataListGy = new DataPoint[recordedData];
                            DataPoint[] dataListGz = new DataPoint[recordedData];

                            DataPointBT d = dataBuffer.take();
                            int intTime = d.time;
                            while(d != null) {
                                int t2 = d.time-intTime;
                                lastStored.put(d);
                                dataListAx[iDP] = new DataPoint(t2, d.ax);
                                dataListAy[iDP] = new DataPoint(t2, d.ay);
                                dataListAz[iDP] = new DataPoint(t2, d.az);
                                dataListGx[iDP] = new DataPoint(t2, d.gx);
                                dataListGy[iDP] = new DataPoint(t2, d.gy);
                                dataListGz[iDP] = new DataPoint(t2, d.gz);


                                d = dataBuffer.take();
                                iDP++;
                            }
                            recording = false;

                            if(save()) {
                                Toast toast = Toast.makeText(getBaseContext(), "Recording Finished, Save Successful", Toast.LENGTH_LONG);
                                toast.show();
                            } else {
                                Toast toast = Toast.makeText(getBaseContext(), "Recording Finished, Save Failed!!", Toast.LENGTH_LONG);
                                toast.show();
                            }

                            lastStored.reset();

                            LineGraphSeries<DataPoint> seriesX = new LineGraphSeries<DataPoint>(dataListAx);
                            LineGraphSeries<DataPoint> seriesY = new LineGraphSeries<DataPoint>(dataListAy);
                            LineGraphSeries<DataPoint> seriesZ = new LineGraphSeries<DataPoint>(dataListAz);
                            seriesX.setTitle("aX");
                            seriesY.setTitle("aY");
                            seriesZ.setTitle("aZ");
                            seriesX.setColor(Color.GREEN);
                            seriesY.setColor(Color.RED);
                            seriesZ.setColor(Color.BLUE);

                            // activate horizontal zooming and scrolling
                            graphAcc.getViewport().setScalable(true);
                            // activate vertical scrolling
                            graphAcc.getViewport().setScrollableY(true);
                            // set manual X bounds
                            graphAcc.getViewport().setXAxisBoundsManual(true);
                            graphAcc.getViewport().setMinX(dataListAx[0].getX());
                            graphAcc.getViewport().setMaxX(dataListAx[iDP-1].getX());
                            graphAcc.addSeries(seriesX);
                            graphAcc.addSeries(seriesY);
                            graphAcc.addSeries(seriesZ);
                            // Display the legend.
                            graphAcc.getLegendRenderer().setVisible(true);

                            LineGraphSeries<DataPoint> seriesGx = new LineGraphSeries<DataPoint>(dataListGx);
                            LineGraphSeries<DataPoint> seriesGy = new LineGraphSeries<DataPoint>(dataListGy);
                            LineGraphSeries<DataPoint> seriesGz = new LineGraphSeries<DataPoint>(dataListGz);

                            seriesGx.setTitle("gX");
                            seriesGy.setTitle("gY");
                            seriesGz.setTitle("gZ");
                            seriesGx.setColor(Color.GREEN);
                            seriesGy.setColor(Color.RED);
                            seriesGz.setColor(Color.BLUE);

                            // set manual X bounds
                            graphGyro.getViewport().setXAxisBoundsManual(true);
                            graphGyro.getViewport().setMinX(dataListGx[0].getX());
                            graphGyro.getViewport().setMaxX(dataListGx[iDP-1].getX());
                            // activate vertical scrolling
                            graphGyro.getViewport().setScrollableY(true);
                            // activate horizontal zooming and scrolling
                            graphGyro.getViewport().setScalable(true);
                            graphGyro.addSeries(seriesGx);
                            graphGyro.addSeries(seriesGy);
                            graphGyro.addSeries(seriesGz);
                            graphGyro.getLegendRenderer().setVisible(true);
                        }
                    }
                }

                if(msg.what == BTConnection.CONNECTING_STATUS){
                    if(msg.arg1 == 1)
                        txtStatus.setText("Connected to Device: " + (String)(msg.obj));
                    else
                        txtStatus.setText("Connection Failed");
                }
            }
        };

        btConnection = BTConnection.getInstance();
        btConnection.setHandler(mHandler);

    }

    private void initialize_buttons() {

        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context context = getApplicationContext();

                Intent mainActivity = new Intent(context, MainActivity.class);
                startActivity(mainActivity);
            }
        });

        btnBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context context = getApplicationContext();

                Intent blueToothManager = new Intent(context, BlueToothManager.class);
                startActivity(blueToothManager);
            }


        });

        btnDataDir.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context context = getApplicationContext();
                Intent dataDir = new Intent(context, DataDir.class);
                startActivity(dataDir);
            }
        });

    }

    private String parseData(String msg, String type) {
        int tIndex = msg.indexOf(type);
        String tempAcc = msg.substring(tIndex + type.length(),  msg.length());
        String[] temAcc = tempAcc.split("[;]");

        String res = "";
        res += temAcc[0].split("[=]")[1] + ";";
        res += temAcc[1].split("[=]")[1] + ";";
        res += temAcc[2].split("[=]")[1] + ";";
        return res;
    }

    private void parseString(String msg) {
        String t = "";
        String acc = "";
        String gyr = "";
        String[] accData, gyrData;
        accData = new String[3];
        gyrData = new String[3];

        String lines[] = msg.split("\\r?\\n");
        int lengthLines = lines.length;

        for(int i = 0; i < lengthLines; i++) {
            if(lines[i].contains("Acceleration:")) {

                try{
                    t = lines[i-1];
                    acc = parseData(lines[i], ACCEL);
                    accData[0] = acc.split(";")[0];
                    accData[1] = acc.split(";")[1];
                    accData[2] = acc.split(";")[2];

                    gyr = parseData(lines[i+1], GYRO);
                    gyrData[0] = gyr.split(";")[0];
                    gyrData[1] = gyr.split(";")[1];
                    gyrData[2] = gyr.split(";")[2];
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }


                i++;
                try {
                    DataPointBT d = new DataPointBT(Integer.valueOf(t), Double.valueOf(accData[0]),
                            Double.valueOf(accData[1]), Double.valueOf(accData[2]),
                            Double.valueOf(gyrData[0]), Double.valueOf(gyrData[1]),
                            Double.valueOf(gyrData[2]));
                    dataBuffer.put(d);
                    recordedData++;

                } catch (NumberFormatException e) {
                    t = "";
                }
            }
        }

        if(t.length() > 0){
            txtTime.setText(t);

            txtAx.setText(accData[0]);
            txtAy.setText(accData[1]);
            txtAz.setText(accData[2]);

            txtGx.setText(gyrData[0]);
            txtGy.setText(gyrData[1]);
            txtGz.setText(gyrData[2]);
        }
    }

    private boolean save() {
        int counter = 0;
        String header = "time,ax,ay,az,gx,gy,gz";

        String fN = "";
        if(fileName.getText().length() > 0) {
            fN = fileName.getText().toString();
        } else {
            Date currentTime = Calendar.getInstance().getTime();
            fN = "data_" + currentTime.toString();
            fileName.setText(fN);
        }
        String fileName = fN + ".csv";
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File file = new File(dir, fileName);

        try(FileWriter fileWriter = new FileWriter(file )) {
            fileWriter.append(header);
            DataPointBT d = lastStored.take();
            while(d != null) {
                fileWriter.append(d.toFile());
                counter++;
                d = lastStored.take();
                if(counter % 5 == 0 && d != null){
                    txtDebug.setText(d.toFile());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if(counter < 5) {
            return false;
        } else {
            return true;
        }
    }

    public void makeGraph() {

    }
}

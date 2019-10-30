package com.example.bindookbowler;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;



import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
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

    private Boolean recording, saveRecording;
    private int timeStartRecord, finishRecording;
    private int recordedData;

    private Button btnMenu, btnBT, btnDataDir;

    private DataPoint[] dataListAx, dataListAy, dataListAz;
    private DataPoint[] dataListGx, dataListGy, dataListGz;

    private static int RECORD_DATA_BUF = 15000;

    private int curTime;

    private String fN;
    private int fileIndex;

    private static String DATATAG = "{ data: [";
    private static String ENDTAG = "] }";
    private static String TIMETAG = "{ time:";
    private static String ENDTIMETAG = "}},";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_view);

        recording = false;
        saveRecording = false;

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
        curTime = 0;
        recordedData = 0;

        fN = "";
        fileIndex = 0;

        record.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(!recording) {
                    timeStartRecord = curTime;
                    recording = true;
                    saveRecording = false;
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

                    graphAcc.removeAllSeries();
                    graphGyro.removeAllSeries();
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

            // https://github.com/google/gson/blob/master/UserGuide.md#TOC-Object-Examples
            public void handleMessage(android.os.Message msg){
                handle_message(msg);
            }
        };

        btConnection = BTConnection.getInstance();
        btConnection.setHandler(mHandler);

    }

    private void handle_message(android.os.Message msg) {
        //Read message

        curTime = read_incoming_data(msg);

        if(curTime == -1) {
            return;
        }

        if(recording) {
            if(curTime > finishRecording) {
                saveRecording = true;
                recording = false;
            }
        }

        if(saveRecording) {
            // save data
            int maxDataPoints = save_data();
            // create graph
            create_graphs(maxDataPoints);
            lastStored.reset();
        }
    }

    private int read_incoming_data(android.os.Message msg) {
        String t = "";

        String ax ="";
        String ay = "";
        String az = "";

        String gx = "";
        String gy = "";
        String gz = "";

        if(msg.what == BTConnection.MESSAGE_READ) {
            String readMessage = null;
            try {
                readMessage = new String((byte[]) msg.obj, "UTF-8");
                //Log.d("data", readMessage);

                int start = readMessage.indexOf(DATATAG);
                int end = readMessage.indexOf(ENDTIMETAG + ENDTAG);

                //Log.d("test", "start: " + String.valueOf(start) + " end: " + String.valueOf(end) + " time: " + String.valueOf(firstIndexOfTime));

                if(end < 0) {
                    return -1;
                } else if(start < end) {
                    readMessage = readMessage.substring(start, end + 6);
                    Log.d("Good DATA", "data was good");
                } else {
                    int timeStart = readMessage.indexOf(TIMETAG);
                    int endOfDataPoint = readMessage.indexOf(ENDTIMETAG);

                    // Missing data
                    if(end < timeStart) {
                        readMessage = readMessage.substring(end + 6);
                        end = readMessage.indexOf(ENDTIMETAG + ENDTAG);
                        start = readMessage.indexOf(DATATAG);
                        if(start < end) {
                            readMessage = readMessage.substring(start, end + 6);
                            Log.d("Truncated DATA", "One data point went missing: ");
                        } else {

                            int finish = readMessage.lastIndexOf(ENDTIMETAG);
                            readMessage = readMessage.substring(start, + finish + 3) + ENDTAG;
                            Log.d("Truncated Data + ending missing", "Two data points went missing");

                            // end data is missing
                        }
                    } else if(timeStart < end) {
                        // Remove first bit of garabage data
                        readMessage = readMessage.substring(timeStart);
                        timeStart = readMessage.indexOf(TIMETAG);
                        endOfDataPoint = readMessage.indexOf(ENDTIMETAG);

                        start = readMessage.indexOf(DATATAG);
                        end = readMessage.indexOf(ENDTIMETAG + ENDTAG);

                        String data = "";
                        while(end < start) {
                            data += readMessage.substring(timeStart, endOfDataPoint + 3);
                            readMessage = readMessage.substring(endOfDataPoint + 3);
                            timeStart = readMessage.indexOf(TIMETAG);
                            endOfDataPoint = readMessage.indexOf(ENDTIMETAG);

                            end = readMessage.indexOf(ENDTAG);
                            start = readMessage.indexOf(DATATAG);

                            // All extra data points have been gathered
                            if(end < timeStart) {
                                readMessage = readMessage.substring(start);
                                end = readMessage.indexOf(ENDTIMETAG + ENDTAG);
                                start = readMessage.indexOf(DATATAG);
                                if(start < end) {
                                    String res = DATATAG + "\n\r" + data;
                                    int s = readMessage.indexOf(DATATAG);
                                    readMessage = res + readMessage.substring(s + 10);

                                    start = readMessage.indexOf(DATATAG);
                                    end = readMessage.indexOf(ENDTIMETAG + ENDTAG);

                                    readMessage = readMessage.substring(start, end + 6);
                                    Log.d("RESTORING DATA", "data restored");
                                    break;
                                } else {
                                    String res = DATATAG  + "\n\r" + data;
                                    timeStart = readMessage.indexOf(TIMETAG);
                                    endOfDataPoint = readMessage.indexOf(ENDTIMETAG);

                                    while(endOfDataPoint > 0) {
                                        res += readMessage.substring(timeStart, endOfDataPoint + 3);
                                        readMessage = readMessage.substring(endOfDataPoint + 3);
                                        timeStart = readMessage.indexOf(TIMETAG);
                                        endOfDataPoint = readMessage.indexOf(ENDTIMETAG);
                                    }

                                    res += ENDTAG;
                                    readMessage = res;
                                    end = 5;
                                    start = 0;

                                    Log.d("Broken JSON", "data restored, one data point missing");
                                }
                            }
                        }

                    }
                }


//                Gson gson = new Gson();
//
//                Type dataListType = new TypeToken<ArrayList<DataPointJSON>>(){}.getType();
//
//                ArrayList<DataPointJSON> dataPoints = gson.fromJson(readMessage, dataListType);
//
//                for(DataPointJSON dataP : dataPoints){
//                    Log.d("Trying gson", dataP.toString());
//                }

                try {
                    JSONObject data = new JSONObject(readMessage);
                    JSONArray dataFields = data.getJSONArray("data");

                    for (int i = 0; i < dataFields.length() - 1; i++) {
                        if (dataFields.get(i) != null) {
                            JSONObject data2 = dataFields.getJSONObject(i);
                            t = data2.get("time").toString();
                            JSONObject accelJSON = (data2.getJSONObject("acceleration"));
                            JSONObject gryoJSON = (data2.getJSONObject("gyroscope"));

                            ax = accelJSON.get("x").toString();
                            ay = accelJSON.get("y").toString();
                            az = accelJSON.get("z").toString();

                            gx = gryoJSON.get("x").toString();
                            gy = gryoJSON.get("y").toString();
                            gz = gryoJSON.get("z").toString();

                            if(recording) {
                                recordedData++;
                                txtDebug.setText("recent time: " + t + "finish recording time: " + String.valueOf(finishRecording));
                                DataPointBT d = new DataPointBT(Integer.valueOf(t), Double.valueOf(ax),
                                        Double.valueOf(ay), Double.valueOf(az),
                                        Double.valueOf(gx), Double.valueOf(gy),
                                        Double.valueOf(gz));
                                dataBuffer.put(d);
                            }
                        }
                    }
                    txtTime.setText(t);

                    txtAx.setText(ax);
                    txtAy.setText(ay);
                    txtAz.setText(az);

                    txtGx.setText(gx);
                    txtGy.setText(gy);
                    txtGz.setText(gz);
                    Log.d("DATA", "data added");
                } catch (JSONException e) {
                    Log.d("ERROR", "Packet dropped due to JSON error");
                    //e.printStackTrace();
                }

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        if(t != "" && Integer.valueOf(t) > curTime) {
            return Integer.valueOf(t);
        } else {
            return -1;
        }
    }

    private int save_data() {
        int iDP = 0;

        dataListAx = new DataPoint[recordedData];
        dataListAy = new DataPoint[recordedData];
        dataListAz = new DataPoint[recordedData];

        dataListGx = new DataPoint[recordedData];
        dataListGy = new DataPoint[recordedData];
        dataListGz = new DataPoint[recordedData];

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
        saveRecording = false;

        if(save()) {
            Toast toast = Toast.makeText(getBaseContext(), "Recording Finished, Save Successful", Toast.LENGTH_LONG);
            toast.show();
        } else {
            Toast toast = Toast.makeText(getBaseContext(), "Recording Finished, Save Failed!!", Toast.LENGTH_LONG);
            toast.show();
        }

        lastStored.reset();
        return iDP;
    }

    private void create_graphs(int iDP) {

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

    private boolean save() {
        int counter = 0;
        String header = "time,ax,ay,az,gx,gy,gz\n";

        if(fileName.getText().length() > 0) {
            String f = fileName.getText().toString();
            if(fN != f) {
                fN = f;
            }
        } else {
            Date currentTime = Calendar.getInstance().getTime();
            fN = "data_" + currentTime.toString();
            fileName.setText(fN);
        }

        String fileName = fN + "_" + String.valueOf(fileIndex) + ".csv";
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
            fileIndex++;
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

}

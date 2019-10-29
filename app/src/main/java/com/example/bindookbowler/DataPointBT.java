package com.example.bindookbowler;

public class DataPointBT {

    public int time;
    public double ax, ay, az;
    public double gx, gy, gz;

    private String rawData;

    public DataPointBT(int t, double ax, double ay, double az, double gx, double gy, double gz) {
        this.time = t;
        this.ax = ax;
        this.ay = ay;
        this.az = az;

        this.gx = gx;
        this.gy = gy;
        this.gz = gz;
    }


    public String toFile() {
        String res = String.valueOf(this.time) + ",";
        res +=  String.valueOf(this.ax) + ",";
        res +=  String.valueOf(this.ay) + ",";
        res +=  String.valueOf(this.az) + ",";
        res +=  String.valueOf(this.gx) + ",";
        res +=  String.valueOf(this.gy) + ",";
        res +=  String.valueOf(this.gz) + "\n\r";
        return res;
    }



}

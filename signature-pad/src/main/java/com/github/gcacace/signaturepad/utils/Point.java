package com.github.gcacace.signaturepad.utils;

/**
 * Created by rb911 on 4/20/2017.
 */


public class Point {
    public float val;
    public int grid_level;
    public String strTime;
    public String strAF;
    public Point(){
        this(0);
    }
    public Point(float v) {
        this(v, 0);
    }

    public Point(float v, int g) {
        this(v, g, "");
    }

    public Point(float v, int g, String dis) {
        this(v,g,dis,"");
    }
    public Point(float v, int g, String dis, String af) {
        this.val = v;
        this.grid_level = g;
        this.strTime = dis;
        this.strAF = af;
    }

    public Point set(float val){
        this.val = val;
        this.grid_level = 0;
        this.strTime = "";
        return this;
    }

    public Point set(float val, int grid_level) {
        this.val = val;
        this.grid_level = grid_level;
        this.strTime = "";
        return this;
    }
    public Point set(float val, int grid_level,String strTime) {
        this.val = val;
        this.grid_level = grid_level;
        this.strTime = strTime;

        return this;
    }

}
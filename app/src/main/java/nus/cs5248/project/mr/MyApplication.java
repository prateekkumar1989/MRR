package nus.cs5248.project.mr;

import android.app.Application;

public class MyApplication extends Application {

    private  int filenumber = 1;

    public int getFilenumber() {
        return filenumber;
    }

    public void setFilenumber(int filenumber) {
        this.filenumber = filenumber;
    }
}
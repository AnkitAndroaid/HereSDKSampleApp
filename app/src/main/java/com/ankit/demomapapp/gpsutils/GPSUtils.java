package com.ankit.demomapapp.gpsutils;

import android.content.Context;
import android.location.LocationManager;

import com.here.android.mpa.common.GeoCoordinate;

public class GPSUtils {

    double lat, longi;
    private LocationManager locationManager;

    public GeoCoordinate getCoordinate(Context context){
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if(locationManager!=null){
        }


        return new GeoCoordinate(lat, longi);
    }
}

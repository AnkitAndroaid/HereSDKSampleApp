/*
 * Copyright (c) 2011-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ankit.demomapapp.search;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import com.ankit.demomapapp.R;
import com.ankit.demomapapp.positionwithnearby.PositioningNearByActivity;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.Image;
import com.here.android.mpa.common.LocationDataSourceHERE;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.common.ViewObject;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapFragment;
import com.here.android.mpa.mapping.MapGesture;
import com.here.android.mpa.mapping.MapMarker;
import com.here.android.mpa.mapping.MapObject;
import com.here.android.mpa.mapping.MapOverlay;
import com.here.android.mpa.mapping.MapState;
import com.here.android.mpa.search.AroundRequest;
import com.here.android.mpa.search.Category;
import com.here.android.mpa.search.CategoryFilter;
import com.here.android.mpa.search.DiscoveryResult;
import com.here.android.mpa.search.DiscoveryResultPage;
import com.here.android.mpa.search.ErrorCode;
import com.here.android.mpa.search.ExploreRequest;
import com.here.android.mpa.search.HereRequest;
import com.here.android.mpa.search.Location;
import com.here.android.mpa.search.PlaceLink;
import com.here.android.mpa.search.ResultListener;
import com.here.android.mpa.search.ReverseGeocodeRequest2;
import com.here.android.mpa.search.SearchRequest;
import com.here.android.positioning.StatusListener;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

/**
 * This class encapsulates the properties and functionality of the Map view.It also implements 4
 * types of discovery requests that HERE Android SDK provides as example.
 */
public class MapFragmentView implements  MapGesture.OnGestureListener, Map.OnTransformListener,PositioningManager.OnPositionChangedListener {
    public static List<DiscoveryResult> s_ResultList;
    private MapFragment m_mapFragment;
    private Activity m_activity;
    private Context mContext;
    private Map m_map;
    private Button m_placeDetailButton;
    private List<MapObject> m_mapObjectList = new ArrayList<>();

    double lati, longi;
    private boolean moved = false;
    private MapMarker m_positionIndicatorFixed;

    Image icon;
    List<String> searchArrayList= new ArrayList<>();
    // flag that indicates whether maps is being transformed
    private boolean mTransforming;

    // callback that is called when transforming ends
    private Runnable mPendingUpdate;

    // positioning manager instance
    private PositioningManager mPositioningManager;

    // HERE location data source instance
    private LocationDataSourceHERE mHereLocation;
    private Handler mHandler;


    public MapFragmentView(Activity activity, Context context) {
        m_activity = activity;
        mContext = context;

        /*
         * The map fragment is not required for executing search requests. However in this example,
         * we will put some markers on the map to visualize the location of the search results.
         */
        initMapFragment();
        initSearchControlButtons();
        /* We use a list view to present the search results */
        initResultListButton();
    }

    // Google has deprecated android.app.Fragment class. It is used in current SDK implementation.
    // Will be fixed in future SDK version.
    @SuppressWarnings("deprecation")
    private MapFragment getMapFragment() {
        return (MapFragment) m_activity.getFragmentManager().findFragmentById(R.id.search_mapfragment);
    }

    private void initMapFragment() {
        /* Locate the mapFragment UI element */
        m_mapFragment = getMapFragment();


        // Set path of isolated disk cache
        String diskCacheRoot = Environment.getExternalStorageDirectory().getPath()
                + File.separator + ".isolated-here-maps";
        // Retrieve intent name from manifest
        String intentName = "";
        try {
            ApplicationInfo ai = m_activity.getPackageManager().getApplicationInfo(m_activity.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            intentName = bundle.getString("com.demo.here.maps.service");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(this.getClass().toString(), "Failed to find intent name, NameNotFound: " + e.getMessage());
        }

        boolean success = com.here.android.mpa.common.MapSettings.setIsolatedDiskCacheRootPath(diskCacheRoot, intentName);
        if (!success) {
            // Setting the isolated disk cache was not successful, please check if the path is valid and
            // ensure that it does not match the default location
            // (getExternalStorageDirectory()/.here-maps).
            // Also, ensure the provided intent name does not match the default intent name.
        } else {
            if (m_mapFragment != null) {
                /* Initialize the MapFragment, results will be given via the called back. */
                m_mapFragment.init(new OnEngineInitListener() {
                    @Override
                    public void onEngineInitializationCompleted(Error error) {
                        if (error == Error.NONE) {
                            icon = new Image();
                            m_positionIndicatorFixed = new MapMarker();
                            setUpMap();
                        } else {
                            Toast.makeText(m_activity,
                                    "ERROR: Cannot initialize Map with error " + error,
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    private void setUpMap() {

                        m_map = m_mapFragment.getMap();
                        m_map.setCenter(new GeoCoordinate(28.6077159025 ,77.224249103), Map.Animation.NONE);
                        mHandler = new Handler(Looper.getMainLooper()) {
                            @Override
                            public void handleMessage(Message message) {
                                // This is where you do your work in the UI thread.
                                // Your worker tells you in the message what to do.
                                if (message.what ==1){
                                   final MapMarker mapMarker =  (MapMarker)message.obj;
                                    Toast.makeText(m_activity, ""+mapMarker.getTitle(), Toast.LENGTH_LONG).show();


                                    ReverseGeocodeRequest2 revGecodeRequest = new ReverseGeocodeRequest2(mapMarker.getCoordinate());
                                    revGecodeRequest.execute(new ResultListener<Location>() {
                                        @Override
                                        public void onCompleted(Location location, ErrorCode errorCode) {
                                            if (errorCode == ErrorCode.NONE) {
                                                /*
                                                 * From the location object, we retrieve the address and display to the screen.
                                                 * Please refer to HERE Android SDK doc for other supported APIs.
                                                 */
                                                generateDialog(location.getAddress().toString(), mapMarker);
                                            } else {
                                                generateDialog("ERROR:RevGeocode Request returned error code:" + errorCode, mapMarker);
                                            }
                                        }
                                    });
                                }
                            }
                        };

                        m_map.addTransformListener(MapFragmentView.this);
                        m_mapFragment.getMapGesture().addOnGestureListener(MapFragmentView.this, 1, true);
                        setUpPositionManager();

                    }

                    private void setUpPositionManager() {
                        mPositioningManager = PositioningManager.getInstance();
                        mHereLocation = LocationDataSourceHERE.getInstance(
                                new StatusListener() {
                                    @Override
                                    public void onOfflineModeChanged(boolean offline) {
                                        // called when offline mode changes
                                    }

                                    @Override
                                    public void onAirplaneModeEnabled() {
                                        // called when airplane mode is enabled
                                    }

                                    @Override
                                    public void onWifiScansDisabled() {
                                        // called when Wi-Fi scans are disabled
                                    }

                                    @Override
                                    public void onBluetoothDisabled() {
                                        // called when Bluetooth is disabled
                                    }

                                    @Override
                                    public void onCellDisabled() {
                                        // called when Cell radios are switch off
                                    }

                                    @Override
                                    public void onGnssLocationDisabled() {
                                        // called when GPS positioning is disabled
                                    }

                                    @Override
                                    public void onNetworkLocationDisabled() {
                                        // called when network positioning is disabled
                                    }

                                    @Override
                                    public void onServiceError(ServiceError serviceError) {
                                        // called on HERE service error
                                    }

                                    @Override
                                    public void onPositioningError(PositioningError positioningError) {
                                        // called when positioning fails
                                    }

                                    @Override
                                    public void onWifiIndoorPositioningNotAvailable() {
                                        // called when running on Android 9.0 (Pie) or newer
                                    }
                                });
                        if (mHereLocation == null) {
                            Toast.makeText(m_activity, "LocationDataSourceHERE.getInstance(): failed, exiting", Toast.LENGTH_LONG).show();
                            m_activity.finish();
                        }
                        mPositioningManager.setDataSource(mHereLocation);
                        mPositioningManager.addListener(new WeakReference<PositioningManager.OnPositionChangedListener>(
                                MapFragmentView.this));

                        // start position updates, accepting GPS, network or indoor positions
                        if (mPositioningManager.start(PositioningManager.LocationMethod.GPS_NETWORK_INDOOR)) {
                            m_mapFragment.getPositionIndicator().setVisible(true);
                        } else {
                            Toast.makeText(m_activity, "PositioningManager.start: failed, exiting", Toast.LENGTH_LONG).show();
                            m_activity.finish();
                        }
                    }
                });
            }
        }
    }

    private void generateDialog(String s, MapMarker mapMarker) {

        if(!TextUtils.isEmpty(s) && mapMarker!=null){
            final AlertDialog.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder = new AlertDialog.Builder(mContext, android.R.style.Theme_Material_Dialog_Alert);
            } else {
                builder = new AlertDialog.Builder(mContext);
            }
            builder.setTitle(mapMarker.getTitle())
                    .setMessage(s)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // continue with delete

                            dialog.dismiss();
                        }
                    })

                    .setIcon(android.R.drawable.ic_dialog_alert).show();


        }

    }

    private void initResultListButton() {
        m_placeDetailButton = (Button) m_activity.findViewById(R.id.resultListBtn);
        m_placeDetailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /* Open the ResultListActivity */
                Intent intent = new Intent(m_activity, ResultListActivity.class);
                m_activity.startActivity(intent);
            }
        });
    }

    private void initSearchControlButtons() {

        SearchView searchView = m_activity.findViewById(R.id.searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                searchNow(s);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        });

        Button aroundRequestButton = (Button) m_activity.findViewById(R.id.aroundRequestBtn);
        aroundRequestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Trigger an AroundRequest based on the current map center and the filter for
                 * Eat&Drink category.Please refer to HERE Android SDK API doc for other supported
                 * location parameters and categories.
                 */
                cleanMap();
                if (m_map == null) {
                    m_map = m_mapFragment.getMap();
                }
                AroundRequest aroundRequest = new AroundRequest();
                aroundRequest.setSearchCenter(m_map.getCenter());
                CategoryFilter filter = new CategoryFilter();
                filter.add(Category.Global.EAT_DRINK);
                aroundRequest.setCategoryFilter(filter);
                aroundRequest.execute(discoveryResultPageListener);
            }
        });

        Button exploreRequestButton = (Button) m_activity.findViewById(R.id.exploreRequestBtn);
        exploreRequestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Trigger an ExploreRequest based on the bounding box of the current map and the
                 * filter for Shopping category.Please refer to HERE Android SDK API doc for other
                 * supported location parameters and categories.
                 */
                cleanMap();
                if (m_map == null) {
                    m_map = m_mapFragment.getMap();
                }
                ExploreRequest exploreRequest = new ExploreRequest();
                exploreRequest.setSearchArea(m_map.getBoundingBox());
                CategoryFilter filter = new CategoryFilter();
                filter.add(Category.Global.SHOPPING);
                exploreRequest.setCategoryFilter(filter);
                exploreRequest.execute(discoveryResultPageListener);
            }
        });

        Button hereRequestButton = (Button) m_activity.findViewById(R.id.hereRequestBtn);
        hereRequestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Trigger a HereRequest based on the current map center.Please refer to HERE
                 * Android SDK API doc for other supported location parameters and categories.
                 */
                cleanMap();
                moved = false;
                setCenter(new GeoCoordinate(lati, longi),Map.Animation.LINEAR );
//                if (m_map == null) {
//                    m_map = m_mapFragment.getMap();
//                }
//                HereRequest hereRequest = new HereRequest();
//                hereRequest.setSearchCenter(m_map.getCenter());
//                hereRequest.execute(discoveryResultPageListener);
            }
        });

        Button searchRequestButton = (Button) m_activity.findViewById(R.id.searchRequestBtn);
        searchRequestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Trigger a SearchRequest based on the current map center and search query
                 * "Hotel".Please refer to HERE Android SDK API doc for other supported location
                 * parameters and categories.
                 */
                cleanMap();
                if (m_map == null) {
                    m_map = m_mapFragment.getMap();
                }
                SearchRequest searchRequest = new SearchRequest("Hotel");
                searchRequest.setSearchCenter(m_map.getCenter());
                searchRequest.execute(discoveryResultPageListener);
            }
        });
    }

    private ResultListener<DiscoveryResultPage> discoveryResultPageListener = new ResultListener<DiscoveryResultPage>() {
        @Override
        public void onCompleted(DiscoveryResultPage discoveryResultPage, ErrorCode errorCode) {
            if (errorCode == ErrorCode.NONE) {
                /* No error returned,let's handle the results */
                m_placeDetailButton.setVisibility(View.VISIBLE);

                /*
                 * The result is a DiscoveryResultPage object which represents a paginated
                 * collection of items.The items can be either a PlaceLink or DiscoveryLink.The
                 * PlaceLink can be used to retrieve place details by firing another
                 * PlaceRequest,while the DiscoveryLink is designed to be used to fire another
                 * DiscoveryRequest to obtain more refined results.
                 */
                s_ResultList = discoveryResultPage.getItems();
                for (DiscoveryResult item : s_ResultList) {
                    /*
                     * Add a marker for each result of PlaceLink type.For best usability, map can be
                     * also adjusted to display all markers.This can be done by merging the bounding
                     * box of each result and then zoom the map to the merged one.
                     */
                    if (item.getResultType() == DiscoveryResult.ResultType.PLACE) {
                        PlaceLink placeLink = (PlaceLink) item;
                        addMarkerAtPlace(placeLink);
                    }
                }
                m_map.setZoomLevel(13.1);
            } else {
                Toast.makeText(m_activity,
                        "ERROR:Discovery search request returned return error code+ " + errorCode,
                        Toast.LENGTH_SHORT).show();
            }
        }
    };

    private void addMarkerAtPlace(PlaceLink placeLink) {
        Image img = new Image();
        try {
            img.setImageResource(R.drawable.marker);
        } catch (IOException e) {
            e.printStackTrace();
        }

        MapMarker mapMarker = new MapMarker();
        mapMarker.setIcon(img);
        mapMarker.setTitle(placeLink.getTitle());
        mapMarker.setDescription(placeLink.getIconUrl());
        mapMarker.setCoordinate(new GeoCoordinate(placeLink.getPosition()));
        m_map.addMapObject(mapMarker);
        m_mapObjectList.add(mapMarker);


    }

    private void cleanMap() {
        if (!m_mapObjectList.isEmpty()) {
            m_map.removeMapObjects(m_mapObjectList);
            m_mapObjectList.clear();
        }
        m_placeDetailButton.setVisibility(View.GONE);
    }


    private void setCurrentPosMarker() {
        try {
            icon.setImageResource(R.drawable.gps_position);
            m_positionIndicatorFixed.setIcon(icon);
        } catch (IOException e) {
            e.printStackTrace();
        }

        m_positionIndicatorFixed.setVisible(true);
        m_positionIndicatorFixed.setCoordinate(m_map.getCenter());
        m_map.addMapObject(m_positionIndicatorFixed);
        m_map.setZoomLevel(m_map.getMaxZoomLevel()-10);
    }



    @Override
    public void onPanStart() {

        moved = true;
    }

    @Override
    public void onPanEnd() {
//        moved = true;
    }

    @Override
    public void onMultiFingerManipulationStart() {
        moved = true;

    }

    @Override
    public void onMultiFingerManipulationEnd() {

    }

    @Override
    public boolean onMapObjectsSelected(List<ViewObject> list) {
        return false;
    }

    @Override
    public boolean onTapEvent(PointF pointF) {

        double level = m_map.getMinZoomLevel() + m_map.getMaxZoomLevel() / 2;
//       setCenter(new GeoCoordinate(49.196261, -123.004773),
//                Map.Animation.NONE);

        for (ViewObject viewObject : m_map.getSelectedObjects(pointF)) {
            if (viewObject.getBaseType() == ViewObject.Type.USER_OBJECT) {
                MapObject mapObject = (MapObject) viewObject;

                if (mapObject.getType() == MapObject.Type.MARKER) {
                    MapMarker window_marker = ((MapMarker) mapObject);
                    System.out.println("Title is................." + window_marker.getTitle());

                    Message message = mHandler.obtainMessage(1, window_marker);
                    message.sendToTarget();

                    return false;
                }
            }
        }
//        m_map.setZoomLevel(level);
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(PointF pointF) {
        return false;
    }

    @Override
    public void onPinchLocked() {

    }

    @Override
    public boolean onPinchZoomEvent(float v, PointF pointF) {
        return false;
    }

    @Override
    public void onRotateLocked() {

    }

    @Override
    public boolean onRotateEvent(float v) {
        return false;
    }

    @Override
    public boolean onTiltEvent(float v) {
        return false;
    }

    @Override
    public boolean onLongPressEvent(PointF pointF) {

        return false;
    }

    @Override
    public void onLongPressRelease() {

    }

    @Override
    public boolean onTwoFingerTapEvent(PointF pointF) {
        return false;
    }

    public void onDestroy() {
        if(m_mapFragment!=null) {
            m_mapFragment.getMapGesture().removeOnGestureListener(MapFragmentView.this);
            if (mPositioningManager != null) {
                mPositioningManager.stop();
            }
        }
    }

    @Override
    public void onMapTransformStart() {

        mTransforming = true;
    }

    @Override
    public void onMapTransformEnd(MapState mapState) {

        mTransforming = false;
        if (mPendingUpdate != null) {
            mPendingUpdate.run();
            mPendingUpdate = null;
        }

    }

    @Override
    public void onPositionUpdated(final PositioningManager.LocationMethod locationMethod, final GeoPosition geoPosition, final boolean mapMatched) {
        lati = geoPosition.getLatitudeAccuracy();
        longi = geoPosition.getLongitudeAccuracy();
        final GeoCoordinate coordinate = geoPosition.getCoordinate();
        if (mTransforming) {
            mPendingUpdate = new Runnable() {

                @Override
                public void run() {
                    onPositionUpdated(locationMethod, geoPosition, mapMatched);
                }
            };
        } else {
            setCenter(coordinate, Map.Animation.BOW);
        }
    }

    @Override
    public void onPositionFixChanged(PositioningManager.LocationMethod locationMethod, PositioningManager.LocationStatus locationStatus) {

    }

    public void onPause(){

    }

    public void onResume(){

    }

    public void setCenter(GeoCoordinate geoCoordinate, Map.Animation animation){
        if(!moved) {
            m_map.setCenter(geoCoordinate, animation);
            m_map.setZoomLevel(m_map.getMaxZoomLevel()-1);
            setCurrentPosMarker();
        }
    }


    public void searchNow(String searchText){

        moved = true;

        Category.Global category = null;
        if(!TextUtils.isEmpty(searchText)) {

            if (searchText.contains("guest") || searchText.contains("guest house") || searchText.contains("hotel") || searchText.contains("hostel")) {
                category = Category.Global.ACCOMMODATION;
            }

            if (searchText.contains("government") || searchText.contains("administrative")) {
                category = Category.Global.ADMINISTRATIVE_AREAS_BUILDINGS;
            }


            if (searchText.contains("business") || searchText.contains("bussiness") || searchText.contains("services")) {
                category = Category.Global.BUSINESS_SERVICES;
            }

            if (searchText.contains("restaurant") || searchText.contains("restorant") || searchText.contains("cafe") || searchText.contains("eat")  || searchText.contains("food") || searchText.contains("drink")) {
                category = Category.Global.EAT_DRINK;
            }


            if (searchText.contains("going out") || searchText.contains("outdoor") || searchText.contains("fun") || searchText.contains("activities")) {
                category = Category.Global.GOING_OUT;
            }


            if (searchText.contains("nature") || searchText.contains("park") ) {
                category = Category.Global.NATURAL_GEOGRAPHICAL;
            }
            if (searchText.contains("shop") || searchText.contains("shopkeeper") || searchText.contains("mall") ) {
                category = Category.Global.SHOPPING;
            }

            if (searchText.contains("histor") || searchText.contains("museum") ) {
                category = Category.Global.SIGHTS_MUSEUMS;
            }


            if (searchText.contains("bus") || searchText.contains("train") || searchText.contains("truck") || searchText.contains("air")  || searchText.contains("aero") || searchText.contains("car") || searchText.contains("bike")|| searchText.contains("taxi")) {
                category = Category.Global.TRANSPORT;
            }



        }


        cleanMap();
        if (m_map == null) {
            m_map = m_mapFragment.getMap();

        }

        ExploreRequest exploreRequest = new ExploreRequest();
        exploreRequest.setSearchArea(m_map.getBoundingBox());
        CategoryFilter filter = new CategoryFilter();
        filter.add(category);
        exploreRequest.setCategoryFilter(filter);
        exploreRequest.execute(discoveryResultPageListener);

    }


    public void setDataToArray(){
        searchArrayList.add("bus");
        searchArrayList.add("train");
        searchArrayList.add("truck");
        searchArrayList.add("air");
        searchArrayList.add("aero");
        searchArrayList.add("car");
        searchArrayList.add("bike");
        searchArrayList.add("taxi");
        searchArrayList.add("histor");
        searchArrayList.add("museum");
        searchArrayList.add("shop");
        searchArrayList.add("shopkeeper");
        searchArrayList.add("mall");
        searchArrayList.add("nature");
        searchArrayList.add("park");
        searchArrayList.add("going out");
        searchArrayList.add("outdoor");
        searchArrayList.add("fun");
        searchArrayList.add("activities");
        searchArrayList.add("restaurant");
        searchArrayList.add("restorant");
        searchArrayList.add("cafe");
        searchArrayList.add("eat");
        searchArrayList.add("food");
        searchArrayList.add("drink");
        searchArrayList.add("business");
        searchArrayList.add("bussiness");
        searchArrayList.add("services");
        searchArrayList.add("government");
        searchArrayList.add("administrative");
        searchArrayList.add("guest house");
        searchArrayList.add("guest");
        searchArrayList.add("hostel");
        searchArrayList.add("hotel");





    }
}

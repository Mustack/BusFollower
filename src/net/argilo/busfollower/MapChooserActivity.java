/*
 * Copyright 2012 Clayton Smith
 *
 * This file is part of Ottawa Bus Follower.
 *
 * Ottawa Bus Follower is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3, or (at
 * your option) any later version.
 *
 * Ottawa Bus Follower is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Ottawa Bus Follower; see the file COPYING.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package net.argilo.busfollower;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import net.argilo.busfollower.ocdata.DatabaseHelper;
import net.argilo.busfollower.ocdata.Stop;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapChooserActivity extends Activity implements OnMapReadyCallback, OnCameraChangeListener {
    private static final String TAG = "MapChooserActivity";
    private static final float MIN_ZOOM_LEVEL = 15; // The minimum zoom level at which stops will be displayed.

    private GoogleMap mMap = null;
    private Bundle mSavedInstanceState = null;
    
    private SQLiteDatabase db;
    private static FetchRoutesTask task = null;

    // Values taken from stops.txt.
    private static int globalMinLatitude = 45130104;
    private static int globalMaxLatitude = 45519650;
    private static int globalMinLongitude = -76040543;
    private static int globalMaxLongitude = -75342690;

    // We maintain this Hashmap in order to deal with clicking on markers
    private HashMap<Marker, Stop> markers;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mapchooser);

        db = ((BusFollowerApplication) getApplication()).getDatabase();

        markers = new HashMap<Marker, Stop>();

        Util.setDisplayHomeAsUpEnabled(this, true);

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);

        mSavedInstanceState = savedInstanceState;

        mapFragment.getMapAsync(this);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        if (mMap != null) {
            SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();

            editor.putLong("mapCenterLatitude", Double.doubleToRawLongBits(mMap.getCameraPosition().target.latitude));
            editor.putLong("mapCenterLongitude", Double.doubleToRawLongBits(mMap.getCameraPosition().target.longitude));
            editor.putFloat("mapZoom", mMap.getCameraPosition().zoom);
            editor.commit();
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // app icon in action bar clicked; go home
                Intent intent = new Intent(this, StopChooserActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        if (task != null) {
            // Let the AsyncTask know we're gone.
            task.setActivityContext(null);
        }

        if (mMap != null) {
            outState.putDouble("mapCenterLatitude", mMap.getCameraPosition().target.latitude);
            outState.putDouble("mapCenterLongitude", mMap.getCameraPosition().target.longitude);
            outState.putFloat("mapZoom", mMap.getCameraPosition().zoom);
        }
    }
    
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setMyLocationEnabled(true);

        if (mSavedInstanceState != null) {
            if (task != null) {
                // Let the AsyncTask know we're back.
                task.setActivityContext(this);
            }

            LatLng position = new LatLng(mSavedInstanceState.getDouble("mapCenterLatitude"), mSavedInstanceState.getDouble("mapCenterLongitude"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, mSavedInstanceState.getFloat("mapZoom")));
        } else {
            SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
            float mapZoom = settings.getFloat("mapZoom", -1);
            if (mapZoom != -1) {
                double lat = Double.longBitsToDouble(settings.getLong("mapCenterLatitude", 0));
                double lng = Double.longBitsToDouble(settings.getLong("mapCenterLongitude", 0));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), mapZoom));
            } else {
                // If it's our first time running, initially show OC Transpo's service area.
                LatLngBounds bounds = new LatLngBounds (new LatLng (globalMinLatitude, globalMinLongitude), new LatLng (globalMaxLatitude, globalMaxLongitude));
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
            }
        }

        mMap.setOnCameraChangeListener(this);

        new DisplayStopsTask().execute();
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        new DisplayStopsTask().execute();
    }

    private class DisplayStopsTask extends AsyncTask<Void, Void, ArrayList<Stop>> {
        String minLatitude, maxLatitude, minLongitude, maxLongitude = "";

        @Override
        protected void onPreExecute() {
            if (mMap.getCameraPosition().zoom < MIN_ZOOM_LEVEL) {
                cancel(true);
                return;
            }

            LatLngBounds proj = mMap.getProjection().getVisibleRegion().latLngBounds;

            double minLat = Math.min(proj.southwest.latitude, proj.northeast.latitude);
            double maxLat = Math.max(proj.southwest.latitude, proj.northeast.latitude);
            double minLng = Math.min(proj.southwest.longitude, proj.northeast.longitude);
            double maxLng = Math.max(proj.southwest.longitude, proj.northeast.longitude);

            minLatitude = String.valueOf(minLat);
            maxLatitude = String.valueOf(maxLat);
            minLongitude = String.valueOf(minLng);
            maxLongitude = String.valueOf(maxLng);

            // Remove markers no longer on the screen
            Iterator it = markers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry)it.next();
                LatLng pos = ((Stop) pair.getValue()).getLocation();
                if (pos.latitude < minLat || pos.latitude > maxLat ||
                    pos.longitude < minLng || pos.longitude > maxLng) {
                    // Remove marker from GoogleMap and HashMap
                    ((Marker) pair.getKey()).remove();
                    it.remove();
                }
            }
        }

        @Override
        protected ArrayList<Stop> doInBackground(Void... params) {
            if (isCancelled()) {
                return null;
            }

            ArrayList<Stop> stops = new ArrayList<Stop>();

            Log.d(TAG, "Before rawQuery");
            long startTime = System.currentTimeMillis();
            Cursor cursor = db.rawQuery("SELECT stop_code, stop_name, stop_lat, stop_lon FROM stops " +
                    "WHERE stop_lat > ? AND stop_lat < ? AND stop_lon > ? AND stop_lon < ? " +
                    "ORDER BY total_departures DESC",
                    new String[] { minLatitude, maxLatitude, minLongitude, maxLongitude });
            Log.d(TAG, "After rawQuery " + (System.currentTimeMillis() - startTime));
            if (cursor != null) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    String stopCode = cursor.getString(0);
                    String stopName = cursor.getString(1);
                    double stopLat = cursor.getDouble(2);
                    double stopLon = cursor.getDouble(3);
                    
                    if (stopCode != null) {
                        stops.add(new Stop(stopCode, stopName, stopLat, stopLon));
                    }
                    
                    cursor.moveToNext();
                }
                cursor.close();
                Log.d(TAG, "After cursor.close() " + (System.currentTimeMillis() - startTime));
            }

            return stops;
        }

        @Override
        protected void onPostExecute(ArrayList<Stop> stops) {
            if (stops.isEmpty()) {
                return;
            }

            for (Stop stop : stops) {
                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(stop.getLocation())
                        .title(stop.getName()));

                markers.put(marker, stop);
            }
        }
    }
}

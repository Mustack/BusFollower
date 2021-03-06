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
import java.util.Calendar;
import java.util.Date;

import net.argilo.busfollower.ocdata.GetNextTripsForStopResult;
import net.argilo.busfollower.ocdata.Route;
import net.argilo.busfollower.ocdata.RouteDirection;
import net.argilo.busfollower.ocdata.Trip;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class BusFollowerActivity extends Activity implements OnMapReadyCallback {
    private static final String TAG = "BusFollowerActivity";
    // The zoom level to use when there's only one point to display.
    private static final int MIN_ZOOM = 10000;
    private boolean isZoomAndCenterRequired = true;
    
    private SQLiteDatabase db;
    private static FetchTripsTask task;
    private GetNextTripsForStopResult result = null;
    private Route route;

    private ListView tripList = null;
    private GoogleMap mMap = null;

    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.busfollower);
        
        db = ((BusFollowerApplication) getApplication()).getDatabase();
        
        Util.setDisplayHomeAsUpEnabled(this, true);

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);

        tripList = (ListView) findViewById(R.id.tripList);
        tripList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Trip trip = (Trip) tripList.getAdapter().getItem(position);
                RouteDirection rd = trip.getRouteDirection();
                AlertDialog.Builder dialog = new AlertDialog.Builder(BusFollowerActivity.this);
                dialog.setTitle(rd.getRouteNumber() + " " + rd.getRouteLabel());
                dialog.setMessage(Util.getBusInformationString(BusFollowerActivity.this, rd, trip));
                dialog.setNegativeButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                dialog.show();
            }
        });
        
        result = (GetNextTripsForStopResult) getIntent().getSerializableExtra("result");
        route = (Route) getIntent().getSerializableExtra("route");
        if (savedInstanceState != null) {
            if (task != null) {
                // Let the AsyncTask know we're back.
                task.setActivityContext(this);
            }
            result = (GetNextTripsForStopResult) savedInstanceState.getSerializable("result");
            route = (Route) savedInstanceState.getSerializable("route");

            if (result != null) {
                // A configuration change has occurred. Don't reset zoom & center.
                isZoomAndCenterRequired = false;
            }
        } else {
            RecentQueryList.addOrUpdateRecent(this, result.getStop(), route);
            // We're arriving from another activity, so set zoom & center.
            isZoomAndCenterRequired = true;
        }
        
        setTitle(getString(R.string.stop_number) + " " + result.getStop().getNumber() +
                ", " + getString(R.string.route_number) + " " + route.getNumber() + " " + route.getHeading());

        mapFragment.getMapAsync(this);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        if (task != null) {
            // Let the AsyncTask know we're gone.
            task.setActivityContext(null);
        }
        outState.putSerializable("result", result);
        outState.putSerializable("route", route);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.busfollower_menu, menu);
        return true;
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
            case R.id.menu_refresh:
                task = new FetchTripsTask(this, db);
                task.execute(new RecentQuery(result.getStop(), route));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    private void displayGetNextTripsForStopResult() {
        double minLat = Double.MAX_VALUE;
        double maxLat = Double.MIN_VALUE;
        double minLong = Double.MAX_VALUE;
        double maxLong = Double.MIN_VALUE;
        
        LatLng stopLocation = result.getStop().getLocation();
        if (stopLocation != null) {
            mMap.addMarker(new MarkerOptions()
                    .position(stopLocation)
                    .title(result.getStopLabel()));

            minLat = maxLat = stopLocation.latitude;
            minLong = maxLong = stopLocation.longitude;
        }

        for (RouteDirection rd : result.getRouteDirections()) {
            if (rd.getDirection().equals(route.getDirection())) {
                tripList.setAdapter(new TripAdapter(BusFollowerActivity.this, R.layout.tripitem, rd.getTrips()));

                int number = 0;
                for (Trip trip : rd.getTrips()) {
                    number++;
                    LatLng point = trip.getLatLng();
                    if (point != null) {
                        minLat = Math.min(minLat, point.latitude);
                        maxLat = Math.max(maxLat, point.latitude);
                        minLong = Math.min(minLong, point.longitude);
                        maxLong = Math.max(maxLong, point.longitude);

                        mMap.addMarker(new MarkerOptions().position(stopLocation));
                    }
                }
            }
        }

        if (isZoomAndCenterRequired) {
            LatLngBounds bounds = new LatLngBounds (new LatLng (minLat ,minLong),new LatLng (maxLat ,maxLong ));
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
        }
    }
    
    public void setResult(GetNextTripsForStopResult result) {
        this.result = result;
        // The user requested a refresh. Don't reset zoom & center.
        isZoomAndCenterRequired = false;
        displayGetNextTripsForStopResult();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.mMap = googleMap;
        displayGetNextTripsForStopResult();
    }

    private class TripAdapter extends ArrayAdapter<Trip> {
        private Context context;
        private int resourceId;
        private ArrayList<Trip> trips;
        
        public TripAdapter(Context context, int resourceId, ArrayList<Trip> trips) {
            super(context, resourceId, trips);
            this.context = context;
            this.resourceId = resourceId;
            this.trips = trips;
        }
        
        @Override
        public View getView(int position, View v, ViewGroup parent) {
            if (v == null) {
                LayoutInflater li = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = li.inflate(resourceId, null);
            }
            final Trip trip = trips.get(position);
            if (trip != null) {
                TextView text1 = (TextView) v.findViewById(android.R.id.text1);
                TextView text2 = (TextView) v.findViewById(android.R.id.text2);
                ImageView busPin = (ImageView) v.findViewById(R.id.busPin);
                text1.setText(getHumanReadableTime(trip.getAdjustedScheduleTime()) + " (" + context.getResources().getString(trip.isEstimated() ? R.string.estimated : R.string.scheduled) + ")");
                text2.setText("Destination: " + trip.getDestination());
                if (trip.getLatLng() == null) {
                    busPin.setImageDrawable(null);
                } else {
                    busPin.setImageDrawable(Util.getNumberedPin(BusFollowerActivity.this, position + 1));
                }
            }
            return v;
        }

        private String getHumanReadableTime(Date date) {
            StringBuffer result = new StringBuffer();
            
            // Relative time
            long difference = date.getTime() - Calendar.getInstance().getTimeInMillis();
            if (difference >= 0) {
                int differenceMinutes = (int) ((difference + 30000) / 60000);
                result.append(context.getResources().getQuantityString(R.plurals.inMinutes, differenceMinutes, differenceMinutes));
            } else {
                int differenceMinutes = (int) ((-difference + 30000) / 60000);
                result.append(context.getResources().getQuantityString(R.plurals.minutesAgo, differenceMinutes, differenceMinutes));
            }
            
            return result.toString();
        }
    }
}
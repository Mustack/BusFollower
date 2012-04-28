package net.argilo.busfollower;

import java.util.ArrayList;

import net.argilo.busfollower.ocdata.GetRouteSummaryForStopResult;
import net.argilo.busfollower.ocdata.Route;
import net.argilo.busfollower.ocdata.Stop;

import android.app.ListActivity;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class RouteChooserActivity extends ListActivity {
	private SQLiteDatabase db = null;

	private Stop stop;
	private ArrayList<Route> routes;
		
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.routechooser);

        db = ((BusFollowerApplication) getApplication()).getDatabase();

        Util.setDisplayHomeAsUpEnabled(this, true);

        stop = (Stop) getIntent().getSerializableExtra("stop");
        GetRouteSummaryForStopResult result = (GetRouteSummaryForStopResult) getIntent().getSerializableExtra("result");
        routes = result.getRoutes();
        
        setListAdapter(new ArrayAdapter<Route>(this, android.R.layout.simple_list_item_1, routes));
        setTitle(getString(R.string.stop_number) + " " + stop.getNumber() + 
        		(stop.getName() != null ? " " + stop.getName() : ""));
    }
    
    @Override
    public void onListItemClick(ListView parent, View v, int position, long id) {
    	// Here we just use RecentQuery as a convenience, since it can hold a stop and route.
    	new FetchTripsTask(this, db).execute(new RecentQuery(stop, routes.get(position)));
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
}
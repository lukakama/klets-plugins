/*******************************************************************************
 * Copyright (c) 2013 Luca De Petrillo.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Luca De Petrillo - initial API and implementation
 ******************************************************************************/
package com.voicecontrolapp.pluginpack.action.foursquare.service;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.annotations.SerializedName;
import com.voicecontrolapp.klets.api.KletsPluginApi;
import com.voicecontrolapp.pluginpack.R;
import com.voicecontrolapp.pluginpack.action.foursquare.FoursquarePluginAction;
import com.voicecontrolapp.pluginpack.geolocator.GeoLocatorHelper;
import com.voicecontrolapp.pluginpack.geolocator.GeoLocatorHelper.Errors;
import com.voicecontrolapp.pluginpack.geolocator.GeoLocatorHelper.GeoLocatorListener;
import com.voicecontrolapp.pluginpack.util.HttpUtils;
import com.voicecontrolapp.pluginpack.util.HttpUtils.Method;

public class FoursquareService extends IntentService {
	private static final String LOG_TAG = "foursquare-service";
	
	private static final String FOURSQUARE_VERIFIED_DATE = "20130907";
	private static final String FOURSQUARE_SEARCH_URL = "https://api.foursquare.com/v2/venues/search";
	private static final String FOURSQUARE_CHECKIN_URL = "https://api.foursquare.com/v2/checkins/add";
	
	private GeoLocatorHelper geoLocatorHelper = null;
	private BroadcastReceiver receiver;

	public FoursquareService() {
		super("FoursquareService");
	}
	
	@Override
	@SuppressWarnings("deprecation")
	public void onCreate() {
		super.onCreate();
		
		// Ensure that Android doesn't kill this service, otherwise KLets will hang waiting for an intent that nobody 
		// will ever send. 
		Notification notification = new Notification(R.drawable.ic_notification, getText(R.string.app_name), 
				System.currentTimeMillis());
		notification.setLatestEventInfo(getApplicationContext(), getString(R.string.app_name), getText(R.string.app_name),
				null);
		startForeground(1, notification);
		
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				stopSelf();
			}
		};
		registerReceiver(receiver, new IntentFilter(KletsPluginApi.INTENT_SESSION_STOP));
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Bundle pluginExtras = intent.getBundleExtra("pluginExtras");
		
		Bundle parameters = pluginExtras.getBundle(KletsPluginApi.EXTRA_PARAMETERS);
		Bundle choices = pluginExtras.getBundle(KletsPluginApi.EXTRA_CHOICES);
		
		PendingIntent asyncPendingIntent = pluginExtras.getParcelable(KletsPluginApi.EXTRA_ASYNC_PENDING_INTENT);

		try {
			if ("FOURSQUARE_LOOKUP_VENUE".equals(intent.getAction())) {
				String venueName = parameters.getString(FoursquarePluginAction.PARAM_VENUE);
				
				performLookup(asyncPendingIntent, venueName);
				
			} else if ("FOURSQUARE_CHECKIN_VENUE".equals(intent.getAction())) {
				Bundle venue = choices.getBundle(FoursquarePluginAction.CHOICE_VENUE);
				String venueId = venue.getString("id");
				String venueName = venue.getString("name");
				
				performCheckIn(asyncPendingIntent, venueId, venueName);
			}
		} catch (Exception e) {
			Log.e(LOG_TAG, "Operation error", e);
			
			handleGenericAsyncException(asyncPendingIntent);
		}
	}
	
	@Override
	public void onDestroy() {
		// Release any pending location lookup.
		if (geoLocatorHelper != null) {
			geoLocatorHelper.abort();
			geoLocatorHelper = null;
		}
		
		if (receiver != null) {
			try {
				unregisterReceiver(receiver);
			} catch (Exception e) {
			}
			receiver = null;
		}
		
		super.onDestroy();
	}
	
	private void performLookup(PendingIntent asyncPendingIntent, String venueName) throws Exception {
		GeoLocatorListenerImpl listener = new GeoLocatorListenerImpl();
		
		synchronized (listener) {
			geoLocatorHelper = new GeoLocatorHelper(getApplicationContext(), listener, 100, 20000, true);
			geoLocatorHelper.start();

			// Wait for the listener to complete.
			try {
				listener.wait();
			} catch (InterruptedException e) {
				// If someone interrupted the wait, simply quit (it is likely to be a thread abort).
				return;
			}
			
			geoLocatorHelper = null;
		}

		// Handle listener result.
		if (listener.error != null) {
			Log.e(LOG_TAG, "Locator error: " + listener.error);
			
			handleGenericAsyncException(asyncPendingIntent);
			
			return;
		} else if (listener.location == null) {
			Bundle resultExtras = new Bundle();
			resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, getString(R.string.action_foursquare_not_location_found));
			sendResult(asyncPendingIntent, KletsPluginApi.ACTION_RESULT_OK, resultExtras);
			
			return;
		}
		
		Location location = listener.location;
		
		Bundle resultExtras = new Bundle();
		
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		String authToken = pref.getString("foursquareAuthToken", null);

		Builder builder = Uri.parse(FOURSQUARE_SEARCH_URL).buildUpon();
		builder.appendQueryParameter("ll", location.getLatitude() + "," + location.getLongitude());
		builder.appendQueryParameter("llAcc", location.getAccuracy() + "");
		builder.appendQueryParameter("query", venueName);
		builder.appendQueryParameter("limit", "5");
		builder.appendQueryParameter("intent", "checkin");
		builder.appendQueryParameter("radius", "1000");
		
		builder.appendQueryParameter("v", FOURSQUARE_VERIFIED_DATE);
		builder.appendQueryParameter("locale", getString(R.string.action_foursquare_language));
		builder.appendQueryParameter("oauth_token", authToken);
		
		URL url = new URL(builder.build().toString());
		
		SearchResponse searchResponse = HttpUtils.getJson(url, SearchResponse.class);
		if (handleResponseErrors(asyncPendingIntent, searchResponse)) {
			return;
		}
		
		List<VenueCompact> venues = searchResponse.response.venues;

		// More than one venue found. We need to ask for which one to use.
		if ((venues != null) && (venues.size() > 1)) {
			LinkedList<String> labelsList = new LinkedList<String>();
			LinkedList<Bundle> valuesList = new LinkedList<Bundle>();
			for (VenueCompact venue : venues) {
				labelsList.add(venue.name);
				
				Bundle value = new Bundle();
				value.putString("id", venue.id);
				value.putString("name", venue.name);
				valuesList.add(value);
			}

			// Setting choice data.
			resultExtras.putString(KletsPluginApi.EXTRA_CHOICE_ID, FoursquarePluginAction.CHOICE_VENUE);
			resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, 
					getString(R.string.action_foursquare_select_venue, venues.size()));
			
			String[] labels = labelsList.toArray(new String[labelsList.size()]);
			Bundle[] values = valuesList.toArray(new Bundle[valuesList.size()]);

			resultExtras.putStringArray(KletsPluginApi.EXTRA_CHOICE_LABELS, labels);
			resultExtras.putParcelableArray(KletsPluginApi.EXTRA_CHOICE_VALUES, values);

			// Notify async completion and wait for the next invocation.
			// Setting the result code in order to enable the choice mode of Voice Control.
			sendResult(asyncPendingIntent, KletsPluginApi.ACTION_RESULT_ASK_CHOICE, resultExtras);
			
		} else if ((venues != null) && (venues.size() == 1)) {
			// Found exacly one venue. Start the checkin.

			VenueCompact venue = venues.get(0);
			String selectedVenueId = venue.id;
			String selectedVenueName = venue.name;
			
			performCheckIn(asyncPendingIntent, selectedVenueId, selectedVenueName);
			
		} else {
			// No venue found so far. Warn the user and quit.
			resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, 
					getString(R.string.action_foursquare_no_venues, venueName));
			
			sendResult(asyncPendingIntent, KletsPluginApi.ACTION_RESULT_OK, resultExtras);
		}
	}
		
	/**
	 * Utility methods used to handle Foursquare errors. Returns <code>true</code> if an error has been handled and 
	 * notified, <code>false</code> otherwise.
	 * 
	 * @param asyncPendingIntent
	 * @param response
	 * @return <code>true</code> if an error has been notified, <code>false</code> otherwise.
	 */
	private boolean handleResponseErrors(PendingIntent asyncPendingIntent, FoursquareBaseResponse response) {
		int code = response.meta.code;
		
		if (code == 200) {
			return false;
		}
		
		Log.e(LOG_TAG, String.format("Foursquare API error: %1$s", code));
		
		String errorMessage;
		if (code == 401) {
			errorMessage = getString(R.string.action_foursquare_api_access_error);
			
		} else {
			errorMessage = getString(R.string.action_foursquare_api_error);
		}
		
		Bundle resultExtras = new Bundle();
		resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, errorMessage);
		
		sendResult(asyncPendingIntent, KletsPluginApi.ACTION_RESULT_OK, resultExtras);

		return true;
	}

	private void performCheckIn(PendingIntent asyncPendingIntent, String venueId, String venueName) throws Exception {
		Bundle resultExtras = new Bundle();
		
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		String authToken = pref.getString("foursquareAuthToken", null);
		
		// Checkin to the venue.
		Builder builder = Uri.parse(FOURSQUARE_CHECKIN_URL).buildUpon();
		builder.appendQueryParameter("venueId", venueId);
		
		builder.appendQueryParameter("v", FOURSQUARE_VERIFIED_DATE);
		builder.appendQueryParameter("locale", getString(R.string.action_foursquare_language));
		builder.appendQueryParameter("oauth_token", authToken);
		
		URL url = new URL(builder.build().toString());
		
		FoursquareBaseResponse response = HttpUtils.getJson(Method.POST, url, FoursquareBaseResponse.class);
		if (handleResponseErrors(asyncPendingIntent, response)) {
			return;
		}
		
		// if no errors, then tell the user the the checkin has been placed.
		resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, getString(R.string.action_foursquare_complete, venueName));
		
		sendResult(asyncPendingIntent, KletsPluginApi.ACTION_RESULT_OK, resultExtras);
	}
	
	private void handleGenericAsyncException(final PendingIntent asyncPendingIntent) {
		Bundle resultExtras = new Bundle();
		
		// Handling error and quit.
		resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, getString(R.string.action_foursquare_generic_error));
		
		sendResult(asyncPendingIntent, KletsPluginApi.ACTION_RESULT_OK, resultExtras);
	}

	private final class GeoLocatorListenerImpl implements GeoLocatorListener {
		private Location location = null;
		private Errors error = null;

		@Override
		public void locationStart() {
		}

		@Override
		public synchronized void locationInitError(Errors error) {
			this.notify();

			this.error = error;
		}

		@Override
		public synchronized void locationComplete(Location location) {
			this.notify();

			this.location  = location;
		}

		@Override
		public void locationAbort() {
		}
	}
	
	public static class FoursquareBaseResponse {
		@SerializedName("meta")
		public Meta meta;
		
		public static class Meta {
			@SerializedName("code")
			public int code;
			
			@SerializedName("errorType")
			public String errorType;
			
			@SerializedName("errorDetail")
			public String errorDetail;
		}
	}
	
	/**
	 * Stub class used to handle Foursquare search responses. 
	 * 
	 * @author Luca De Petrillo
	 */
	public static class SearchResponse extends FoursquareBaseResponse {
		@SerializedName("response")
		public InnerResponse response;
		
		public static class InnerResponse {
			@SerializedName("venues")
			public List<VenueCompact> venues;
		}
	}
	
	public static class VenueCompact {
		@SerializedName("id")
		public String id;
		
		@SerializedName("name")
		public String name;
	}
	
	private void sendResult(PendingIntent asyncPendingIntent, int resultCode, Bundle resultExtras) {
		try {
			asyncPendingIntent.send(getApplicationContext(), resultCode, (new Intent()).putExtras(resultExtras));
		} catch (CanceledException cancelEx) {
			// This pending intent has been canceled. We don't need to notify the error to KLets anymore.
		}
	}
}

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
package com.voicecontrolapp.pluginpack.action.weather.service;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.util.Log;

import com.google.gson.annotations.SerializedName;
import com.voicecontrolapp.klets.api.KletsPluginApi;
import com.voicecontrolapp.pluginpack.BuildConfig;
import com.voicecontrolapp.pluginpack.R;
import com.voicecontrolapp.pluginpack.action.weather.WeatherPluginAction;
import com.voicecontrolapp.pluginpack.action.weather.service.WeatherService.PlacesList.Place;
import com.voicecontrolapp.pluginpack.geolocator.GeoLocatorHelper;
import com.voicecontrolapp.pluginpack.geolocator.GeoLocatorHelper.Errors;
import com.voicecontrolapp.pluginpack.geolocator.GeoLocatorHelper.GeoLocatorListener;
import com.voicecontrolapp.pluginpack.util.DateUtils;
import com.voicecontrolapp.pluginpack.util.HttpUtils;

public class WeatherService extends IntentService {
	private static final String LOG_TAG = "weather-service";
	
	private static final String WEATHER_URL = "http://api.worldweatheronline.com/free/v1/weather.ashx";
	private static final String PLACES_SEARCH_URL = "https://maps.googleapis.com/maps/api/place/textsearch/json";
	
	private GeoLocatorHelper geoLocatorHelper = null;
	private BroadcastReceiver receiver;

	public WeatherService() {
		super("WeatherService");
	}
	
	@Override
	@SuppressWarnings("deprecation")
	public void onCreate() {
		super.onCreate();
		
		// Ensure that Android doesn't kill this service, otherwise KLets will hang waiting for an intent that nobody 
		// will ever send. 
		Notification notification = new Notification(R.drawable.ic_notification_main, getText(R.string.app_name), 
				System.currentTimeMillis());
		notification.setLatestEventInfo(getApplicationContext(), getString(R.string.app_name), getText(R.string.app_name),
				null);
		startForeground(2, notification);
		
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
			Date date = null;
			if (parameters.containsKey(WeatherPluginAction.PARAM_DATE)) {
				date = (Date) parameters.getSerializable(WeatherPluginAction.PARAM_DATE);
			}
			
			if ("GET_CURRENT_PLACE".equals(intent.getAction())) {
				performLookup(asyncPendingIntent, null, date);
			
			} else if ("LOOKUP_PLACE".equals(intent.getAction())) {
				String placeToLookup = parameters.getString(WeatherPluginAction.PARAM_PLACE);
				
				performLookup(asyncPendingIntent, placeToLookup, date);
				
			} else if ("GET_CHOICE_PLACE_WEATHER".equals(intent.getAction())) {
				Bundle placeData = choices.getBundle(WeatherPluginAction.CHOICE_PLACE);
				
				lookupWeather(asyncPendingIntent, placeData.getDouble("lat"), placeData.getDouble("lng"), 
						placeData.getString("placeName"), date);
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
	
	private void performLookup(PendingIntent asyncPendingIntent, String placeToLookup, Date date) throws Exception {
		double latitude = 0;
		double longitude = 0;
		String placeName = null;

		if (placeToLookup == null) {
			GeoLocatorListenerImpl listener = new GeoLocatorListenerImpl();
			
			synchronized (listener) {
				geoLocatorHelper = new GeoLocatorHelper(getApplicationContext(), listener, 1500, 20000, true);
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
			latitude = location.getLatitude();
			longitude = location.getLongitude();
			
			Geocoder geocoder = new Geocoder(this);
			List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
			if (addresses.size() == 0) {
				Bundle resultExtras = new Bundle();
				resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, getString(R.string.action_foursquare_not_location_found));
				sendResult(asyncPendingIntent, KletsPluginApi.ACTION_RESULT_OK, resultExtras);
				
				return;
			}
			
			placeName = addresses.get(0).getLocality();
					
		} else {
			Builder builder = Uri.parse(PLACES_SEARCH_URL).buildUpon();
			builder.appendQueryParameter("sensor", "true");
			builder.appendQueryParameter("query", placeToLookup);
			builder.appendQueryParameter("key", BuildConfig.GOOGLE_PLACES_KEY);
			builder.appendQueryParameter("language", getString(R.string.placesLanguage));
			builder.appendQueryParameter("types", "locality");
			
			URL url = new URL(builder.build().toString());

			PlacesList locations;
			try {
				locations = HttpUtils.getJson(url, PlacesList.class);
			} catch (Exception e) {
				Log.e(LOG_TAG, "Places API error: " + e.getMessage(), e);
				
				handleGenericAsyncException(asyncPendingIntent);
				return;
			}
			
			if ((locations == null) || (locations.status == null) || (locations.results == null) || 
					(!locations.status.equals("OK")) || (locations.results.size() == 0)) {
				
				handleGenericAsyncException(asyncPendingIntent);
				return;
				
			// Handle multiple results
			/* FIXME: Actually turned off, as the first result is precise enough...
			} else if (locations.results.size() > 1) {
				Bundle resultExtras = new Bundle();
				
				ArrayList<String> placesName = new ArrayList<String>(locations.results.size());
				ArrayList<Bundle> placesData = new ArrayList<Bundle>(locations.results.size());
				
				for (Place result : locations.results) {
					String resultName = result.formattedAddress;
					PlacesList.Place.Location resultLocation = result.geometry.location;
					
					Bundle placeData = new Bundle();
					placeData.putString("placeName", resultName);
					placeData.putDouble("lat", resultLocation.lat);
					placeData.putDouble("lng", resultLocation.lng);
					
					placesName.add(resultName);
					placesData.add(placeData);
					
					// Handle maximum 3 results.
					if (placesData.size() >= 3) {
						break;
					}
				}
				
				resultExtras.putString(KletsPluginApi.EXTRA_CHOICE_ID, WeatherPluginAction.CHOICE_PLACE);
				resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, getString(R.string.action_weather_select_place));
				resultExtras.putStringArray(KletsPluginApi.EXTRA_CHOICE_LABELS, 
						placesName.toArray(new String[placesName.size()]));
				resultExtras.putParcelableArray(KletsPluginApi.EXTRA_CHOICE_VALUES, 
						placesData.toArray(new Bundle[placesData.size()]));

				sendResult(asyncPendingIntent, KletsPluginApi.ACTION_RESULT_ASK_CHOICE, resultExtras);
				
				return;
			*/
			} else {
				Place result = locations.results.get(0);
				
				PlacesList.Place.Location location = result.geometry.location;
				
				placeName = result.formattedAddress;
				latitude = location.lat;
				longitude = location.lng;
			}
		}
		
		lookupWeather(asyncPendingIntent, latitude, longitude, placeName, date);
	}
		
	private void lookupWeather(PendingIntent asyncPendingIntent, double latitude, double longitude, String placeName,
			Date date) throws Exception {
		Builder builder = Uri.parse(WEATHER_URL).buildUpon();
		builder.appendQueryParameter("key", BuildConfig.WEATHER_KEY);
		builder.appendQueryParameter("q", latitude + "," + longitude);
		builder.appendQueryParameter("format", "json");
		builder.appendQueryParameter("fx", "yes");
		builder.appendQueryParameter("cc", "no");
		builder.appendQueryParameter("show_comments", "no");
		if (date == null) {
			builder.appendQueryParameter("date", "today");	
		} else {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
			builder.appendQueryParameter("date", sdf.format(date));
		}
		
		URL url = new URL(builder.build().toString());
		
		WeatherResponse weatherResponse;
		try {
			weatherResponse = HttpUtils.getJson(url, WeatherResponse.class);
		} catch (Exception e) {
			Log.e(LOG_TAG, "Weather API error: " + e.getMessage(), e);
			
			handleGenericAsyncException(asyncPendingIntent);
			return;
		}
		
		if (weatherResponse == null) {
			Log.e(LOG_TAG, "Null Weather API response");
			
			handleGenericAsyncException(asyncPendingIntent);
			return;
		}
		
		String weatherCode = weatherResponse.data.weather.get(0).weatherCode;
		String weatherDescription = getWeatherDescriptionMap().get(weatherCode);
		
		Date today = new Date();
		
		String weatherMessage;
		if ((date == null) || (DateUtils.daysBetween(today, date) == 0)) {
			weatherMessage = getString(R.string.action_weather_forecast_today, placeName, weatherDescription);
		} else if (today.before(date) &&  (DateUtils.daysBetween(today, date) == 1)) {
			weatherMessage = getString(R.string.action_weather_forecast_tomorrow, placeName, weatherDescription);
		} else {
			weatherMessage = getString(R.string.action_weather_forecast_date, date, placeName, weatherDescription);
		}
		
		Bundle resultExtras = new Bundle();
		resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, weatherMessage);
		
		sendResult(asyncPendingIntent, KletsPluginApi.ACTION_RESULT_OK, resultExtras);
	}

	private Map<String, String> getWeatherDescriptionMap() {
		String[] weatherDescriptions = getResources().getStringArray(R.array.action_weather_codes);
		
		HashMap<String, String> weatherDesciptionsMap = new HashMap<String, String>(weatherDescriptions.length);
		
		for (String weatherDescription : weatherDescriptions) {
			int pipeIdx = weatherDescription.indexOf('|');
			
			weatherDesciptionsMap.put(weatherDescription.substring(0, pipeIdx), 
					weatherDescription.substring(pipeIdx + 1));
		}
		
		return weatherDesciptionsMap;
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
	
	private void sendResult(PendingIntent asyncPendingIntent, int resultCode, Bundle resultExtras) {
		try {
			asyncPendingIntent.send(getApplicationContext(), resultCode, (new Intent()).putExtras(resultExtras));
		} catch (CanceledException cancelEx) {
			// This pending intent has been canceled. We don't need to notify the error to KLets anymore.
		}
	}
	
	/**
	 * Simple stub for Places List API response.
	 * 
	 * @author Luca De Petrillo
	 */
	public static class PlacesList {
		@SerializedName("html_attributions")
		public List<String> htmlAttributions;
		
		@SerializedName("status")
		public String status;
		
		@SerializedName("results")
		public List<Place> results;
		
		public static class Place {
			@SerializedName("id")
			public String id;
			
			@SerializedName("name")
			public String name;
			
			@SerializedName("reference")
			public String reference;
			
			@SerializedName("icon")
			public String icon;
			
			@SerializedName("vicinity")
			public String vicinity;
			
			@SerializedName("geometry")
			public Geometry geometry;
			
			@SerializedName("formatted_address")
			public String formattedAddress;
			
			@SerializedName("formatted_phone_number")
			public String formattedPhoneNumber;
			
			@SerializedName("types")
			public List<String> types;

			@Override
			public String toString() {
				return name + " - " + id + " - " + reference;
			}

			public static class Geometry {
				@SerializedName("location")
				public Location location;
			}

			public static class Location {
				@SerializedName("lat")
				public double lat;

				@SerializedName("lng")
				public double lng;
			}
		}
	}
	
	/**
	 * Simple stub for Weather API response.
	 * 
	 * @author Luca De Petrillo
	 */
	public static class WeatherResponse {
		@SerializedName("data")
		public Data data;
		
		public static class Data {
			@SerializedName("weather")
			public List<Weather> weather = new ArrayList<Weather>();

			public static class Weather {
				@SerializedName("date")
				public String date;
				
				@SerializedName("precipMM")
				public String precipMM;
				
				@SerializedName("tempMaxC")
				public String tempMaxC;
				
				@SerializedName("tempMaxF")
				public String tempMaxF;
				
				@SerializedName("tempMinC")
				public String tempMinC;
				
				@SerializedName("tempMinF")
				public String tempMinF;
				
				@SerializedName("weatherCode")
				public String weatherCode;
				
				@SerializedName("weatherDesc")
				public List<WeatherDesc> weatherDesc = new ArrayList<WeatherDesc>();
				
				@SerializedName("weatherIconUrl")
				public List<WeatherIconUrl> weatherIconUrl = new ArrayList<WeatherIconUrl>();
				
				@SerializedName("winddir16Point")
				public String winddir16Point;
				
				@SerializedName("winddirDegree")
				public String winddirDegree;
				
				@SerializedName("winddirection")
				public String winddirection;
				
				@SerializedName("windspeedKmph")
				public String windspeedKmph;
				
				@SerializedName("windspeedMiles")
				public String windspeedMiles;
				
				public static class WeatherDesc {
					@SerializedName("value")
					public String value;
				}
				
				public static class WeatherIconUrl {
					@SerializedName("value")
					public String value;
				}
			}
		}
	}
}

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
package com.voicecontrolapp.pluginpack.action.foursquare;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import com.voicecontrolapp.klets.api.KletsPluginApi;
import com.voicecontrolapp.pluginpack.R;
import com.voicecontrolapp.pluginpack.action.PluginAction;
import com.voicecontrolapp.pluginpack.action.foursquare.service.FoursquareService;
import com.voicecontrolapp.pluginpack.geolocator.GeoLocatorHelper;

/**
 * Action used to check-in into a Foursquare place. It will works like the navigation action bundled in KLets.
 * 
 * @author Luca De Petrillo
 */
public class FoursquarePluginAction implements PluginAction {
	public static final String PARAM_VENUE = "VENUE_NAME";
	public static final String CHOICE_VENUE = "CHOICE_VENUE";

	@Override
	public void handlePluginFire(BroadcastReceiver mainReceiver, Context context, Intent intent) throws Exception {
		Bundle resultExtras = mainReceiver.getResultExtras(true);

		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
		String authToken = pref.getString("foursquareAuthToken", null);
		
		// No token? The user needs to connect to Foursquare.
		if (authToken == null) {
			mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_OK);
			resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, context.getString(R.string.action_foursquare_error_connect));
			return;
		}

		// This action requires GPS to be enabled
		if (!GeoLocatorHelper.hasActiveProviders(context)) {
			mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_OK);
			resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, context.getString(R.string.action_foursquare_error_gps));
			return;
		}
		
		Bundle parameters = intent.getBundleExtra(KletsPluginApi.EXTRA_PARAMETERS);
		Bundle choices = intent.getBundleExtra(KletsPluginApi.EXTRA_CHOICES);

		// If the venue parameter is missing, ask for it.
		if (!parameters.containsKey(PARAM_VENUE)) {
			mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_ASK_PARAM);

			resultExtras.putString(KletsPluginApi.EXTRA_PARAMETER_ID, PARAM_VENUE);

			return;
		}
		
		String selectedVenueId = null;
		// Check if we previously asked to select a venue.
		if (choices.containsKey(CHOICE_VENUE)) {
			Bundle venue = choices.getBundle(CHOICE_VENUE);
			selectedVenueId = venue.getString("id");
		}
		
		// No venue already selected. We need to lookup it using Foursquare API.
		if (selectedVenueId == null) {
			// Lookup need asynchrounous execution (it waits for geo-location and performs network operations).
			
			Intent serviceIntent = new Intent(context, FoursquareService.class);
			serviceIntent.setAction("FOURSQUARE_LOOKUP_VENUE");
			serviceIntent.putExtra("pluginExtras", intent.getExtras());
			
			context.startService(serviceIntent);
			
			mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_ASYNC);
			
		} else {
			// Checkin need asynchrounous execution (it performs network operations).
			Intent serviceIntent = new Intent(context, FoursquareService.class);
			serviceIntent.setAction("FOURSQUARE_CHECKIN_VENUE");
			serviceIntent.putExtra("pluginExtras", intent.getExtras());
			context.startService(serviceIntent);

			mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_ASYNC);
		}
	}
}

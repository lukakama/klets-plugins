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
package com.voicecontrolapp.pluginpack.action.weather;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.voicecontrolapp.klets.api.KletsPluginApi;
import com.voicecontrolapp.pluginpack.R;
import com.voicecontrolapp.pluginpack.action.PluginAction;
import com.voicecontrolapp.pluginpack.action.weather.service.WeatherService;
import com.voicecontrolapp.pluginpack.geolocator.GeoLocatorHelper;

/**
 * Action used to check-in into a Weather place. It will works like the navigation action bundled in KLets.
 * 
 * @author Luca De Petrillo
 */
public class WeatherPluginAction implements PluginAction {
	public static final String PARAM_PLACE = "PLACE_NAME";
	public static final String PARAM_DATE = "DATE";
	public static final String CHOICE_PLACE = "CHOICE_PLACE";

	@Override
	public void handlePluginFire(BroadcastReceiver mainReceiver, Context context, Intent intent) throws Exception {
		Bundle resultExtras = mainReceiver.getResultExtras(true);

		Bundle parameters = intent.getBundleExtra(KletsPluginApi.EXTRA_PARAMETERS);
		Bundle choices = intent.getBundleExtra(KletsPluginApi.EXTRA_CHOICES);

		// If the place parameter is missing, get it from GPS.
		if (!parameters.containsKey(PARAM_PLACE)) {
			// In this case, this action requires GPS to be enabled
			if (!GeoLocatorHelper.hasActiveProviders(context)) {
				mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_OK);
				resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, context.getString(R.string.action_weather_error_gps));
				return;
			}
			
			Intent serviceIntent = new Intent(context, WeatherService.class);
			serviceIntent.setAction("GET_CURRENT_PLACE");
			serviceIntent.putExtra("pluginExtras", intent.getExtras());
			
			context.startService(serviceIntent);
			
			mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_ASYNC);
			
			return;
		

		// Check if we previously asked to select a place.
		} else if (choices.containsKey(CHOICE_PLACE)) {
			Intent serviceIntent = new Intent(context, WeatherService.class);
			serviceIntent.setAction("GET_CHOICE_PLACE_WEATHER");
			serviceIntent.putExtra("pluginExtras", intent.getExtras());

			context.startService(serviceIntent);
			
			mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_ASYNC);
		
		// No place already selected. We need to lookup it using Google Places API.
		} else {
			// Lookup need asynchrounous execution (it waits for geo-location and performs network operations).
			
			Intent serviceIntent = new Intent(context, WeatherService.class);
			serviceIntent.setAction("LOOKUP_PLACE");
			serviceIntent.putExtra("pluginExtras", intent.getExtras());
			
			context.startService(serviceIntent);
			
			mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_ASYNC);
		}
	}
}

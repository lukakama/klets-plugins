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
package com.voicecontrolapp.pluginpack;

import java.util.HashMap;
import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.voicecontrolapp.klets.api.KletsPluginApi;
import com.voicecontrolapp.pluginpack.action.PluginAction;
import com.voicecontrolapp.pluginpack.action.contact.ContactPluginAction;
import com.voicecontrolapp.pluginpack.action.foursquare.FoursquarePluginAction;

/**
 * This {@link BroadcastReceiver} act as a gateway redirecting calls to the right action handler.
 * 
 * @author Luca De Petrillo
 */
public class PluginActionReceiver extends BroadcastReceiver {
	private static final String LOG_TAG = "PluginActionReceiver";
	
	/**
	 * Dictionary with actions handled by this plug-in pack.
	 */
	private static final Map<String, PluginAction> pluginDictionary;
	static {
		pluginDictionary = new HashMap<String, PluginAction>();
		pluginDictionary.put("foursquareAction", new FoursquarePluginAction());
		pluginDictionary.put("contactAction", new ContactPluginAction());
	}

	@Override
	@SuppressWarnings("unused")
	public void onReceive(Context context, Intent intent) {
		// Always validate Voice Control permission if we are not in debug mode.
		if (!BuildConfig.DEBUG && 
				!KletsPluginApi.isPermissionAuthentic(context, KletsPluginApi.PERMISSION_EXECUTE_TASK)) {
			// Some application is hacking this app!!! This should really never happen...
			ApplicationInfo hackishAppInfo = 
					KletsPluginApi.getAppWithPermission(context, KletsPluginApi.PERMISSION_EXECUTE_TASK);
			CharSequence hackishAppName = hackishAppInfo.loadLabel(context.getPackageManager());
			Log.e(LOG_TAG, String.format("Hack attempt from '%s' using Voice Control plugin APIs. Ignoring request.", 
					hackishAppName));
			
			return;
		}
		
		String actionId = intent.getStringExtra(KletsPluginApi.EXTRA_ACTION_ID);
		
		// Executing the right implementation for requested action.
		PluginAction targetAction = pluginDictionary.get(actionId);
		if (targetAction != null) {
			try {
				targetAction.handlePluginFire(this, context, intent);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		
		} else {
			// Should never happen...
			throw new IllegalAccessError(String.format("Action id '%s' not handled by this plugin!", actionId));
		}
	}
}

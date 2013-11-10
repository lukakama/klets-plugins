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
package com.voicecontrolapp.pluginpack.action.contact;

import java.util.ArrayList;
import java.util.LinkedList;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;

import com.voicecontrolapp.klets.api.KletsPluginApi;
import com.voicecontrolapp.klets.api.data.ContactData;
import com.voicecontrolapp.pluginpack.R;
import com.voicecontrolapp.pluginpack.action.PluginAction;

/**
 * Action used to check-in a Foursquare place. It will works like the navigation action bundled in KLets.
 * 
 * @author Luca De Petrillo
 */
public class ContactPluginAction implements PluginAction {
	private static final String PARAM_CONTACT = "CONTACT";
	
	private static final String CHOICE_CONTACT = "SELECTED_CONTACT";
	
	@Override
	public void handlePluginFire(BroadcastReceiver mainReceiver, Context context, Intent intent) {
		Bundle resultExtras = mainReceiver.getResultExtras(true);

		Bundle parameters = intent.getBundleExtra(KletsPluginApi.EXTRA_PARAMETERS);
		Bundle choices = intent.getBundleExtra(KletsPluginApi.EXTRA_CHOICES);

		// If the contact parameter is missing, ask for it.
		if (!parameters.containsKey(PARAM_CONTACT)) {
			mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_ASK_PARAM);

			resultExtras.putString(KletsPluginApi.EXTRA_PARAMETER_ID, PARAM_CONTACT);

			return;
		}
		
		
		ArrayList<Bundle> contacts = parameters.getParcelableArrayList(PARAM_CONTACT);

		long contactId;
		
		// Use the contact on the parameter of it is only one.
		if (contacts.size() == 1) {
			contactId = contacts.get(0).getLong(ContactData.CONTACT_ID);

		// We have more than one result for the contact name. If we already asked the user to select a contact, we use the
		// selected one.
		} else if (choices.containsKey(CHOICE_CONTACT)) {
			contactId = choices.getBundle(CHOICE_CONTACT).getLong(ContactData.CONTACT_ID);

		// Otherwhise we need to ask which one to use.
		} else {
			// Prepare variables to ask to the user which contact to open.
			LinkedList<String> labelsList = new LinkedList<String>();
			LinkedList<Bundle> valuesList = new LinkedList<Bundle>();
			for (Bundle contactToCheck : contacts) {
				labelsList.add(contactToCheck.getString(ContactData.NAME));
				valuesList.add(contactToCheck);
			}

			// Setting the result code in order to enable the choice mode of Voice Control.
			mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_ASK_CHOICE);

			// Setting choice data.
			resultExtras.putString(KletsPluginApi.EXTRA_CHOICE_ID, CHOICE_CONTACT);
			resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE,
					context.getString(R.string.action_contact_choose_contact_name));
			String[] labels = labelsList.toArray(new String[labelsList.size()]);
			Bundle[] values = valuesList.toArray(new Bundle[valuesList.size()]);

			resultExtras.putStringArray(KletsPluginApi.EXTRA_CHOICE_LABELS, labels);
			resultExtras.putParcelableArray(KletsPluginApi.EXTRA_CHOICE_VALUES, values);

			// Quitting and waiting for the next invocation.
			return;
		}
		
		// Start the intent to open a contact detail from its id.
		Intent viewContactIntent = new Intent(Intent.ACTION_VIEW);
		Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
		viewContactIntent.setData(uri);
		viewContactIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(viewContactIntent);

		// Setting the result result code for this BroadcastReceiver execution.
		mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_OK);
	}
}

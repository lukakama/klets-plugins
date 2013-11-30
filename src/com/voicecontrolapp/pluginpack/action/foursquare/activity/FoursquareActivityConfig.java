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
package com.voicecontrolapp.pluginpack.action.foursquare.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.TextView;

import com.foursquare.android.nativeoauth.FoursquareCancelException;
import com.foursquare.android.nativeoauth.FoursquareInternalErrorException;
import com.foursquare.android.nativeoauth.FoursquareOAuth;
import com.foursquare.android.nativeoauth.FoursquareOAuthException;
import com.foursquare.android.nativeoauth.FoursquareUnsupportedVersionException;
import com.foursquare.android.nativeoauth.model.AccessTokenResponse;
import com.foursquare.android.nativeoauth.model.AuthCodeResponse;
import com.voicecontrolapp.pluginpack.BuildConfig;
import com.voicecontrolapp.pluginpack.R;

public class FoursquareActivityConfig extends FragmentActivity {
	private static final int REQUEST_CODE_FSQ_CONNECT = 1;
	private static final int REQUEST_CODE_FSQ_TOKEN_EXCHANGE = 2;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setTitle("Foursquare config");

		setContentView(R.layout.foursquare_activity_config);

		refreshFoursquareText();
	}

	private void refreshFoursquareText() {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		if (pref.contains("foursquareAuthToken")) {
			TextView textView = (TextView) findViewById(R.id.txtFoursquareAccount);
			textView.setText(R.string.action_foursquare_config_already_connected);
		}
	}

	public void doConnectFoursquare(View view) {
		Intent intent = FoursquareOAuth.getConnectIntent(this, BuildConfig.FOURSQUARE_OAUTH_APP_ID);
		startActivityForResult(intent, REQUEST_CODE_FSQ_CONNECT);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE_FSQ_CONNECT) {
			AuthCodeResponse codeResponse = FoursquareOAuth.getAuthCodeFromResult(resultCode, data);
			if (codeResponse.getException() == null) {
				Intent intent = FoursquareOAuth.getTokenExchangeIntent(this, BuildConfig.FOURSQUARE_OAUTH_APP_ID,
						BuildConfig.FOURSQUARE_OAUTH_APP_SECRET, codeResponse.getCode());
				startActivityForResult(intent, REQUEST_CODE_FSQ_TOKEN_EXCHANGE);

			} else {
				handleFoursquareError(codeResponse.getException());
			}
			
		} else if (requestCode == REQUEST_CODE_FSQ_TOKEN_EXCHANGE) {
			AccessTokenResponse tokenResponse = FoursquareOAuth.getTokenFromResult(resultCode, data);
			if (tokenResponse.getException() == null) {
				SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
				Editor editor = pref.edit();
				editor.putString("foursquareAuthToken", tokenResponse.getAccessToken());
				editor.commit();

				refreshFoursquareText();
			} else {
				handleFoursquareError(tokenResponse.getException());
			}
		}
	}

	private void handleFoursquareError(Exception e) {
		// Operation canceled by the user... nothing to do.
		if (e instanceof FoursquareCancelException) {
			return;
		}

		String message;
		if (e instanceof FoursquareUnsupportedVersionException) {
			message = getString(R.string.action_foursquare_config_incompatible_error);
		} else if (e instanceof FoursquareOAuthException) {
			FoursquareOAuthException foaEx = (FoursquareOAuthException) e;
			message = getString(R.string.action_foursquare_config_connection_error, foaEx.getErrorCode());
		} else if (e instanceof FoursquareInternalErrorException) {
			message = getString(R.string.action_foursquare_config_internal_error,  e.getCause());
		} else {
			message = getString(R.string.action_foursquare_config_generic_error, e.getCause());
		}

		// The DialogFragment is bogus: it throws an "IllegalStateException: Can not perform this action after 
		// onSaveInstanceState " exception when a it is shown in a stack handling a "onActivityResult" callback.
		// As a workaround, we use the obsolete but well-working AlertDialog system.
		new AlertDialog.Builder(this)
			.setTitle(R.string.title_user_support)
			.setMessage(message)
			.setCancelable(true)
			.setPositiveButton(android.R.string.ok, null)
			.show();
	}
}

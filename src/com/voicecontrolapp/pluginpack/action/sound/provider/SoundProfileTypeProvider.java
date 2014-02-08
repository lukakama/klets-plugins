/*******************************************************************************
 * Copyright 2013 Luca De Petrillo
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.voicecontrolapp.pluginpack.action.sound.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import com.voicecontrolapp.klets.api.KletsPluginApi;
import com.voicecontrolapp.pluginpack.BuildConfig;
import com.voicecontrolapp.pluginpack.R;

public class SoundProfileTypeProvider extends ContentProvider {

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new IllegalArgumentException("This provider is read-only");
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		throw new IllegalArgumentException("This provider is read-only");
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		throw new IllegalArgumentException("This provider is read-only");
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public String getType(Uri uri) {
		return null;
	}

	@Override
	@SuppressWarnings("unused")
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		// Check if someone is hacking Voice Control permission
		Context context = getContext();
		
		if (!BuildConfig.DEBUG &&
				!KletsPluginApi.isPermissionAuthentic(context, KletsPluginApi.PERMISSION_QUERY_TYPE)) {
			throw new IllegalArgumentException("Permission hacked! Access denied!");
		}
		
		MatrixCursor matrixCursor = new MatrixCursor(new String[] {"ID_FIELD", "TEXT_FIELD"});

		matrixCursor.addRow(new Object[] {"silent", context.getString(R.string.action_sound_profile_set_param_profile_val_silent)});
		matrixCursor.addRow(new Object[] {"vibrate", context.getString(R.string.action_sound_profile_set_param_profile_val_vibrate)});
		matrixCursor.addRow(new Object[] {"normal", context.getString(R.string.action_sound_profile_set_param_profile_val_normal)});
		
		return matrixCursor;
	}
}

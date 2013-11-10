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
package com.voicecontrolapp.pluginpack.provider;

import com.voicecontrolapp.klets.api.KletsPluginApi;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public class CustomTypeProvider extends ContentProvider {

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
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		// Check if someone is hacking Voice Control permission
		if (!KletsPluginApi.isPermissionAuthentic(getContext(), KletsPluginApi.PERMISSION_QUERY_TYPE)) {
			//throw new IllegalArgumentException("Permission hacked! Access denied!");
		}
		
		MatrixCursor matrixCursor = new MatrixCursor(new String[] {"ID_FIELD", "TEXT_FIELD"});
		
		// I'm italian, so I'm handling my language in order to check the sample :)
		if ("it".equalsIgnoreCase(getContext().getResources().getConfiguration().locale.getLanguage())) {
			matrixCursor.addRow(new Object[] {"ID_1", "valore 1"});
			matrixCursor.addRow(new Object[] {"ID_2", "valore 2"});
			matrixCursor.addRow(new Object[] {"ID_3", "valore 3"});
			matrixCursor.addRow(new Object[] {"ID_4", "valore 4"});
			matrixCursor.addRow(new Object[] {"ID_5", "valore 5"});
			
		} else {
			matrixCursor.addRow(new Object[] {"ID_1", "value 1"});
			matrixCursor.addRow(new Object[] {"ID_2", "value 2"});
			matrixCursor.addRow(new Object[] {"ID_3", "value 3"});
			matrixCursor.addRow(new Object[] {"ID_4", "value 4"});
			matrixCursor.addRow(new Object[] {"ID_5", "value 5"});
		}
		
		return matrixCursor;
	}
}

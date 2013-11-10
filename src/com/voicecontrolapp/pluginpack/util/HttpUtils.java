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
package com.voicecontrolapp.pluginpack.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;

public class HttpUtils {
	public static enum Method {
		POST, GET
	}
	
	public static String getCharset(String contentType) {
		String charset = null;
		
		if (contentType != null) {
			String[] values = contentType.split(";"); //The values.length must be equal to 2...
			for (String value : values) {
			    value = value.trim();
	
			    if (value.toLowerCase(Locale.US).startsWith("charset=")) {
			        charset = value.substring("charset=".length()).trim();
			    }
			}
		}

		return charset;
	}
	
	public static <T> T getJson(URL url, Class<T> classOfT) throws UnsupportedEncodingException, IOException  {
		return getJson(Method.GET, url, classOfT);
	}
	
	public static <T> T getJson(Method method, URL url, Class<T> classOfT) throws UnsupportedEncodingException, IOException  {
		HttpURLConnection urlConnection = null;
		BufferedReader reader  = null;
		try {
			Log.d(HttpUtils.class.getSimpleName(), String.format("Request URL: %s", url.toString()));
			
			urlConnection = (HttpURLConnection) url.openConnection();
			
			if (method == Method.GET) {
				urlConnection.setDoOutput(false);
			} else {
				urlConnection.setDoOutput(true);
			}
			urlConnection.setDoInput(true);
			
			Log.d(HttpUtils.class.getSimpleName(), String.format("Response code: %d", urlConnection.getResponseCode()));

			String charset = getCharset(urlConnection.getContentType());
			if ((charset == null) || (charset.trim().length() == 0)) {
				reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
			} else {
				reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), charset));
			}
			
			Gson gson = new Gson();
			return gson.fromJson(reader, classOfT);
		} finally {
			if (reader != null) {
				reader.close();
			}
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}
	}
	
	public static int getResponseCode(URL url) throws UnsupportedEncodingException, IOException  {
		return getResponseCode(Method.GET, url);
	}
	
	public static int getResponseCode(Method method, URL url) throws UnsupportedEncodingException, IOException  {
		HttpURLConnection urlConnection = null;
		try {
			Log.d(HttpUtils.class.getSimpleName(), String.format("Request URL: %s", url.toString()));
			
			urlConnection = (HttpURLConnection) url.openConnection();
			
			if (method == Method.GET) {
				urlConnection.setDoOutput(false);
			} else {
				urlConnection.setDoOutput(true);
			}
			urlConnection.setDoInput(true);
			
			Log.d(HttpUtils.class.getSimpleName(), String.format("Response code: %d", urlConnection.getResponseCode()));

			return urlConnection.getResponseCode();
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}
	}
	
	public static void disableConnectionReuseIfNecessary() {
	    // HTTP connection reuse was buggy pre-froyo
	    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
	        System.setProperty("http.keepAlive", "false");
	    }
	}
}

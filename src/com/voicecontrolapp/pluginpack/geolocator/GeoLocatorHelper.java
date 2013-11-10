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
package com.voicecontrolapp.pluginpack.geolocator;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

/**
 * Utility class that try to obtain a location using a progressive strategy.
 *   
 * @author Luca De Petrillo
 *
 */
public class GeoLocatorHelper {
	
	public enum Errors {
		GPS_DISABLED
	}
	
	private WakeLock wakeLock = null;
	private final Context context;
	private final GeoLocatorListener listener;
	private LocationListenerHandler locationHandler;

	/**
	 * Constuctor of the utility class.
	 * 
	 * @param context
	 * @param listener
	 * @param accuracyThreshold Stop locating if a retrieved location have less then this accuracy in meters. -1 to 
	 *   disable.
	 * @param maxTimeout Maximum timeout for location operation. Use the <code>completeOnMaxTimeout</code> parameter to 
	 *   configure the timeout behavior.
	 * @param completeOnMaxTimeout If <code>false</code>, when the maxTimout is reached and no location have been found,
	 *   the class will continue to perform the location in order to end as soon as a location is found (regardless its 
	 *   accuracy). If <code>true</code>, the class will complete the location operation also if no location has been 
	 *   found.
	 */
	public GeoLocatorHelper(Context context, GeoLocatorListener listener, long accuracyThreshold, long maxTimeout, 
			boolean completeOnMaxTimeout) {
		this.context = context;
		this.listener = listener;
		
		PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		
		locationHandler = new LocationListenerHandler(accuracyThreshold, maxTimeout, completeOnMaxTimeout);
		
		wakeLock = powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK
				| PowerManager.ON_AFTER_RELEASE,
				"locator_lock");
	}

	public boolean isLocating() {
		return wakeLock.isHeld();
	}

	public void abort() {
		if (locationHandler != null) {
			locationHandler.abort();
		}

		wakeLock.release();

		notifyAbort();
	}
	
	public static boolean hasActiveProviders(Context context) {
		LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
				|| locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			return true;
		}
		
		return false;
	}

	public void start() {
		// Acquire a reference to the system Location Manager
		LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

		// Register the listener with the Location Manager to receive location
		// updates
		List<String> providers = new LinkedList<String>();
		if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			providers.add(LocationManager.GPS_PROVIDER);
		}
		if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			providers.add(LocationManager.NETWORK_PROVIDER);
		}

		if (providers.size() == 0) {
			notifyInitError(Errors.GPS_DISABLED);
		} else {
			wakeLock.acquire();

			locationHandler.start(providers);

			notifyStart();
		}
	}

	public void handleComplete(Location location) {
		wakeLock.release();

		notifyComplete(location);
	}

	private final class LocationListenerHandler {
		private static final long TWO_MINUTES = 120000;
		
		private static final long MAX_LAST_KNOWN_MINUTES = 30 * 60 * 1000;
		
		private long maxTimeout;
		private boolean completeOnMaxTimeout;
		private long accuracyThreshold;

		private long initialTimeout = 2000;
		private long updateTimeout = 7000;
		
		private Timer timeoutTimer = new Timer();
		private CheckTask updateTimeoutTask = null;

		private boolean timedOut = false;
		private boolean canceled = false;

		private Location lastLocation = null;
		private List<LocationListener> listeners = new LinkedList<LocationListener>();
		private boolean completeOnNextUpdate = false;

		public LocationListenerHandler(long accuracyThreshold, long maxTimeout, boolean completeOnMaxTimeout) {
			this.accuracyThreshold = accuracyThreshold;
			this.maxTimeout = maxTimeout;
			this.completeOnMaxTimeout = completeOnMaxTimeout;
		}

		public synchronized void start(List<String> providers) {
			LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
			for (String provider : providers) {
				LocationListenerImpl listener = new LocationListenerImpl(this);
				listeners.add(listener);

				Location lastKnownLocation = locationManager.getLastKnownLocation(provider);
				if ((lastKnownLocation != null) && 
						((System.currentTimeMillis() - lastKnownLocation.getTime()) <= MAX_LAST_KNOWN_MINUTES) &&
						isBetterLocation(lastKnownLocation, lastLocation)) {
					lastLocation = lastKnownLocation;
				}
				
				locationManager.requestLocationUpdates(provider, 0, 0, listener);
			}
			
			completeOnNextUpdate = false;

			timeoutTimer.schedule(new CheckTask(), initialTimeout);
			timeoutTimer.schedule(new CompleteTask(), maxTimeout);
		}

		public synchronized void onLocationChanged(Location location) {
			if (canceled || timedOut) {
				return;
			}

			if (isBetterLocation(location, lastLocation)) {
				lastLocation = location;
	
				if (updateTimeoutTask != null) {
					updateTimeoutTask.cancel();
				}
				updateTimeoutTask = new CheckTask();
				timeoutTimer.schedule(updateTimeoutTask, updateTimeout);
				
				if (completeOnNextUpdate) {
					complete();
				}
			}
		}

		private synchronized void checkComplete() {
			if ((lastLocation != null) && lastLocation.hasAccuracy() && (lastLocation.getAccuracy() <= accuracyThreshold)) {
				complete();
			}
		}
		
		private synchronized void checkForcedComplete() {
			if (completeOnMaxTimeout || (lastLocation != null)) {
				complete();
			} else {
				completeOnNextUpdate  = true;
			}
		}
		
		public synchronized void complete() {
			if (canceled || timedOut) {
				return;
			}

			timedOut = true;

			release();

			if (!canceled) {
				handleComplete(lastLocation);
			}
		}

		public synchronized void abort() {
			if (canceled || timedOut) {
				return;
			}

			canceled = true;

			release();
		}

		private synchronized void release() {
			timeoutTimer.cancel();
			timeoutTimer.purge();

			LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
			for (LocationListener listener : listeners) {
				locationManager.removeUpdates(listener);
			}
		}

		private final class CompleteTask extends TimerTask {
			@Override
			public void run() {
				checkForcedComplete();
			}
		}

		private final class CheckTask extends TimerTask {
			@Override
			public void run() {
				checkComplete();
			}
		}
		
		
		/**
		 * Method obtained from <a href="http://developer.android.com/guide/topics/location/strategies.html">
		 * http://developer.android.com/guide/topics/location/strategies.html</a>.
		 * 
		 * Determines whether one Location reading is better than the current Location fix
		 * 
		 * @param location  The new Location that you want to evaluate
		 * @param currentBestLocation  The current Location fix, to which you want to compare the new one
		 * @return
		 */
		protected boolean isBetterLocation(Location location, Location currentBestLocation) {
		    if (currentBestLocation == null) {
		        // A new location is always better than no location
		        return true;
		    }

		    // Check whether the new location fix is newer or older
		    long timeDelta = location.getTime() - currentBestLocation.getTime();
		    boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
		    boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
		    boolean isNewer = timeDelta > 0;

		    // If it's been more than two minutes since the current location, use the new location
		    // because the user has likely moved
		    if (isSignificantlyNewer) {
		        return true;
		    // If the new location is more than two minutes older, it must be worse
		    } else if (isSignificantlyOlder) {
		        return false;
		    }

		    // Check whether the new location fix is more or less accurate
		    int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
		    boolean isLessAccurate = accuracyDelta > 0;
		    boolean isMoreAccurate = accuracyDelta < 0;
		    boolean isSignificantlyLessAccurate = accuracyDelta > 200;

		    // Check if the old and new location are from the same provider
		    boolean isFromSameProvider = isSameProvider(location.getProvider(),
		            currentBestLocation.getProvider());

		    // Determine location quality using a combination of timeliness and accuracy
		    if (isMoreAccurate) {
		        return true;
		    } else if (isNewer && !isLessAccurate) {
		        return true;
		    } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
		        return true;
		    }
		    return false;
		}

		/** Checks whether two providers are the same */
		private boolean isSameProvider(String provider1, String provider2) {
		    if (provider1 == null) {
		      return provider2 == null;
		    }
		    return provider1.equals(provider2);
		}
	}

	private final class LocationListenerImpl implements LocationListener {
		private final LocationListenerHandler handler;

		public LocationListenerImpl(LocationListenerHandler handler) {
			this.handler = handler;
		}

		@Override
		public void onLocationChanged(Location location) {
			this.handler.onLocationChanged(location);
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
			Log.d("GEO_LOCATOR", "onStatusChanged: " + provider);
		}

		public void onProviderEnabled(String provider) {
			Log.d("GEO_LOCATOR", "onProviderEnabled: " + provider);
		}

		public void onProviderDisabled(String provider) {
			Log.d("GEO_LOCATOR", "onProviderDisabled: " + provider);
		}
	}

	public interface GeoLocatorListener {
		void locationStart();

		void locationComplete(Location location);

		void locationAbort();

		void locationInitError(Errors error);
	}

	private void notifyStart() {
		try {
			listener.locationStart();
		} catch (Exception e) {
		}
	}

	private void notifyAbort() {
		try {
			listener.locationAbort();
		} catch (Exception e) {
		}
	}

	private void notifyInitError(Errors error) {
		try {
			listener.locationInitError(error);
		} catch (Exception e) {
		}
	}

	private void notifyComplete(Location location) {
		try {
			listener.locationComplete(location);
		} catch (Exception e) {
		}
	}
}

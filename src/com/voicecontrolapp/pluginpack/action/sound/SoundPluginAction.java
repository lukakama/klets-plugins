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
package com.voicecontrolapp.pluginpack.action.sound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;

import com.voicecontrolapp.klets.api.KletsPluginApi;
import com.voicecontrolapp.pluginpack.R;
import com.voicecontrolapp.pluginpack.action.PluginAction;

/**
 * Action handling all commands used to change phone volumes.
 * 
 * @author Luca De Petrillo
 */
public class SoundPluginAction implements PluginAction {
	private static final String PARAM_VOLUME = "VOLUME";
	private static final String PARAM_PROFILE = "PROFILE";

	@Override
	public void handlePluginFire(BroadcastReceiver mainReceiver, Context context, Intent intent) {
		Bundle resultExtras = mainReceiver.getResultExtras(true);

		String actionId = intent.getStringExtra(KletsPluginApi.EXTRA_ACTION_ID);

		AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

		Bundle parameters = intent.getBundleExtra(KletsPluginApi.EXTRA_PARAMETERS);

		int[] audioStreams = { AudioManager.STREAM_MUSIC, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_ALARM };

		if (actionId.equals("soundVolumeUp")) {
			for (int audioStream : audioStreams) {
				audioManager.adjustStreamVolume(audioStream, AudioManager.ADJUST_RAISE, 0);
			}

			resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, context.getString(R.string.action_sound_volume_confirm_message));
		} else if (actionId.equals("soundVolumeDown")) {
			for (int audioStream : audioStreams) {
				audioManager.adjustStreamVolume(audioStream, AudioManager.ADJUST_LOWER, 0);
			}

			resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, context.getString(R.string.action_sound_volume_confirm_message));
		} else if (actionId.equals("soundVolumeSet")) {
			if (!parameters.containsKey(PARAM_VOLUME)) {

				mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_ASK_PARAM);
				resultExtras.putString(KletsPluginApi.EXTRA_PARAMETER_ID, PARAM_VOLUME);

				return;
			}

			int volume = (int) Math.min(parameters.getLong(PARAM_VOLUME), 100);

			for (int audioStream : audioStreams) {
				int maxVolume = audioManager.getStreamMaxVolume(audioStream);
				int streamVolume = (volume * maxVolume) / 100;

				audioManager.setStreamVolume(audioStream, streamVolume, 0);
			}
			
			resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, context.getString(R.string.action_sound_volume_confirm_message));
		} else if (actionId.equals("soundVolumeMax")) {
			for (int audioStream : audioStreams) {
				audioManager.setStreamVolume(audioStream, audioManager.getStreamMaxVolume(audioStream), 0);
			}
			
			resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, context.getString(R.string.action_sound_volume_confirm_message));
		} else if (actionId.equals("soundProfileSet")) {
			if (!parameters.containsKey(PARAM_PROFILE)) {

				mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_ASK_PARAM);
				resultExtras.putString(KletsPluginApi.EXTRA_PARAMETER_ID, PARAM_PROFILE);

				return;
			}

			String profile = parameters.getStringArrayList(PARAM_PROFILE).get(0);
			
			if ("normal".equals(profile)) {
				audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
			} else if ("silent".equals(profile)) {
				audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
			} else if ("vibrate".equals(profile)) {
				audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
			}

			resultExtras.putString(KletsPluginApi.EXTRA_MESSAGE, context.getString(R.string.action_sound_profile_confirm_message));
		}

		// Setting the result result code for this BroadcastReceiver execution.
		mainReceiver.setResultCode(KletsPluginApi.ACTION_RESULT_OK);
	}
}

package com.voicecontrolapp.pluginpack;

import com.voicecontrolapp.pluginpack.util.HttpUtils;

import android.app.Application;

public class PluginPackApplication extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		
		HttpUtils.disableConnectionReuseIfNecessary();
	}
}

/*******************************************************************************
 * Copyright (c) 2014 Luca De Petrillo.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Luca De Petrillo - initial API and implementation
 ******************************************************************************/
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

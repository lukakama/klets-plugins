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
package com.voicecontrolapp.pluginpack.action;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Interface used to specialize implementations for actions handled by this plug-in pack. 
 *  
 * @author Luca De Petrillo
 */
public interface PluginAction {
	void handlePluginFire(BroadcastReceiver mainReceiver, Context context, Intent intent) throws Exception;
}

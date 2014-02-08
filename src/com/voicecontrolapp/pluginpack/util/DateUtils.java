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
package com.voicecontrolapp.pluginpack.util;

import java.util.Calendar;
import java.util.Date;

public class DateUtils {
	public static Calendar getDatePart(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);

		return cal;
	}

	public static long daysBetween(Date startDate, Date endDate) {
		Calendar sDate = getDatePart(startDate.before(endDate) ? startDate :endDate);
		Calendar eDate = getDatePart(startDate.before(endDate) ? endDate :startDate);

		long daysBetween = 0;
		while (sDate.before(eDate)) {
			sDate.add(Calendar.DAY_OF_MONTH, 1);
			daysBetween++;
		}
		return daysBetween;
	}
}

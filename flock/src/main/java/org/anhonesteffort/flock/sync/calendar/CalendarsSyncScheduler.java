/*
 * *
 *  Copyright (C) 2014 Open Whisper Systems
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 * /
 */

package org.anhonesteffort.flock.sync.calendar;

import android.content.Context;
import android.net.Uri;
import android.provider.CalendarContract;

import org.anhonesteffort.flock.sync.AbstractSyncScheduler;

/**
 * Programmer: rhodey
 */
public class CalendarsSyncScheduler extends AbstractSyncScheduler {

  private static final String TAG               = "org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler";
  public  static final String CONTENT_AUTHORITY = CalendarContract.AUTHORITY;

  public CalendarsSyncScheduler(Context context) {
    super(context);
  }

  @Override
  protected String getTAG() {
    return TAG;
  }

  @Override
  public String getAuthority() {
    return CONTENT_AUTHORITY;
  }

  @Override
  protected Uri getUri() {
    return CalendarContract.Calendars.CONTENT_URI;
  }

}

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calendar;

import com.android.calendar.CalendarController.ViewType;

import android.content.Context;
import android.database.DataSetObserver;
import android.os.Handler;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Iterator;
import java.util.Locale;


/*
 * The MenuSpinnerAdapter defines the look of the ActionBar's pull down menu
 * for small screen layouts. The pull down menu replaces the tabs uses for big screen layouts
 *
 * The MenuSpinnerAdapter responsible for creating the views used for in the pull down menu.
 */

public class CalendarViewAdapter implements SpinnerAdapter {

    private static final String TAG = "MenuSpinnerAdapter";

    private String mButtonNames [];           // Text on buttons
    private ArrayList<DataSetObserver> mObservers;

    // Used to define the look of the menu button according to the current view:
    // Day view: show day of the week + full date underneath
    // Week view: show the month + year
    // Month view: show the month + year
    // Agenda view: show day of the week + full date underneath
    private int mCurrentMainView;

    private final LayoutInflater mInflater;

    // Defines the types of view returned by this spinner
    private static final int BUTTON_VIEW_TYPE = 0;
    static final int VIEW_TYPE_NUM = 1;  // Increase this if you add more view types

    public static final int DAY_BUTTON_INDEX = 0;
    public static final int WEEK_BUTTON_INDEX = 1;
    public static final int MONTH_BUTTON_INDEX = 2;
    public static final int AGENDA_BUTTON_INDEX = 3;

    // The current selected event's time, used to calculate the date and day of the week
    // for the buttons.
    long mMilliTime;
    String mTimeZone;
    private Time mTmpTime;
    long mTodayJulianDay;

    Context mContext;
    private Formatter mFormatter;
    private StringBuilder mStringBuilder;
    private Handler mMidnightHandler = null; // Used to run a time update every midnight

    // Updates time specific variables (time-zone, today's Julian day).
    private Runnable mTimeUpdater = new Runnable() {
        @Override
        public void run() {
            setTimeVars(mContext);
        }
    };

    public CalendarViewAdapter(Context context, int viewType) {
        super();

        mCurrentMainView = viewType;
        mContext = context;

        // Initialize
        mButtonNames = context.getResources().getStringArray(R.array.buttons_list);
        mObservers = new ArrayList<DataSetObserver>();
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mStringBuilder = new StringBuilder(50);
        mFormatter = new Formatter(mStringBuilder, Locale.getDefault());

        // Sets time specific variables and starts a thread for midnight updates
        setTimeVars(context);
    }


    // Sets the time zone and today's Julian day to be used by the adapter.
    // Also, notify listener on the change and resets the midnight update thread.
    private void setTimeVars(Context context) {
        mTimeZone = Utils.getTimeZone(context, mTimeUpdater);
        mTmpTime = new Time(mTimeZone);
        Time time = new Time(mTimeZone);
        long now = System.currentTimeMillis();
        time.set(now);
        mTodayJulianDay = Time.getJulianDay(now, time.gmtoff);
        notifyDataChange();
        setMidnightHandler();
    }

    // Sets a thread to run 1 second after midnight and update the current date
    // This is used to display correctly the date of yesterday/today/tomorrow
    public void setMidnightHandler() {
        if (mMidnightHandler == null) {
            mMidnightHandler = new Handler();
        } else {
            mMidnightHandler.removeCallbacks(mTimeUpdater);
        }
        // Set the time updater to run at 1 second after midnight
        long now = System.currentTimeMillis();
        Time time = new Time(mTimeZone);
        time.set(now);
        long runInMillis = (24 * 3600 - time.hour * 3600 - time.minute * 60 -
                time.second + 1) * 1000;
        mMidnightHandler.postDelayed(mTimeUpdater, runInMillis);
    }

    // Stops the midnight update thread, called by the activity when it is paused.
    public void resetMidnightHandler() {
        if (mMidnightHandler != null) {
            mMidnightHandler.removeCallbacks(mTimeUpdater);
        }
    }

    // Add a data observer that will be called if there is a change in the data set (buttons list)
    public void registerDataSetObserver(DataSetObserver observer) {
        if (observer != null) {
            synchronized(mObservers) {
                mObservers.add(observer);
            }
        }
    }

    // Remove an existing data observer
    public void unregisterDataSetObserver(DataSetObserver observer) {
        if (observer != null) {
            synchronized(mObservers) {
                mObservers.remove(observer);
            }
        }
    }

    // Returns the amount of buttons in the menu
    public int getCount() {
        return mButtonNames.length;
    }


    public Object getItem(int position) {
        if (position < mButtonNames.length) {
            return mButtonNames[position];
        }
        return null;
    }

    public long getItemId(int position) {
        // Item ID is its location in the list
        return position;
    }

    public boolean hasStableIds() {
        return false;
    }

    public View getView(int position, View convertView, ViewGroup parent) {

        View v;
        // Check if can recycle the view
        if (convertView == null ||
                ((Integer)convertView.getTag()).intValue() !=
                    R.layout.actionbar_pulldown_menu_top_button) {
            v = mInflater.inflate(R.layout.actionbar_pulldown_menu_top_button, parent, false);
            // Set the tag to make sure you can recycle it when you get it as a convert view
            v.setTag(new Integer(R.layout.actionbar_pulldown_menu_top_button));
        } else {
            v = convertView;
        }
        TextView weekDay = (TextView)v.findViewById(R.id.top_button_weekday);
        TextView date = (TextView)v.findViewById(R.id.top_button_date);

        switch (mCurrentMainView) {
            case ViewType.DAY:
                weekDay.setVisibility(View.VISIBLE);
                weekDay.setText(buildDayOfWeek());
                date.setText(buildFullDate());
                break;
            case ViewType.WEEK:
                weekDay.setVisibility(View.GONE);
                date.setText(buildMonthYearDate());
                break;
            case ViewType.MONTH:
                weekDay.setVisibility(View.GONE);
                date.setText(buildMonthYearDate());
                break;
            case ViewType.AGENDA:
                weekDay.setVisibility(View.VISIBLE);
                weekDay.setText(buildDayOfWeek());
                date.setText(buildFullDate());
                break;
            default:
                v = null;
                break;
        }
        return v;
    }

    public int getItemViewType(int position) {
        // Only one kind of view is used
        return BUTTON_VIEW_TYPE;
    }

    public int getViewTypeCount() {
        return VIEW_TYPE_NUM;
    }

    public boolean isEmpty() {
        return (mButtonNames.length == 0);
    }

    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        View v = mInflater.inflate(R.layout.actionbar_pulldown_menu_button, parent, false);
        TextView viewType = (TextView)v.findViewById(R.id.button_view);
        TextView date = (TextView)v.findViewById(R.id.button_date);
        switch (position) {
            case DAY_BUTTON_INDEX:
                viewType.setText(mButtonNames [DAY_BUTTON_INDEX]);
                date.setText(buildMonthDayDate());
                break;
            case WEEK_BUTTON_INDEX:
                viewType.setText(mButtonNames [WEEK_BUTTON_INDEX]);
                date.setText(buildWeekDate());
                break;
            case MONTH_BUTTON_INDEX:
                viewType.setText(mButtonNames [MONTH_BUTTON_INDEX]);
                date.setText(buildMonthDate());
                break;
            case AGENDA_BUTTON_INDEX:
                viewType.setText(mButtonNames [AGENDA_BUTTON_INDEX]);
                date.setText(buildMonthDayDate());
                break;
            default:
                v = convertView;
                break;
        }
        return v;
    }

    // Updates the current viewType
    // Used to match the label on the menu button with the calendar view
    public void setMainView(int viewType) {
        mCurrentMainView = viewType;
        notifyDataChange();
    }

    // Update the date that is displayed on buttons
    // Used when the user selects a new day/week/month to watch
    public void setTime(long time) {
        mMilliTime = time;
        notifyDataChange();
    }

    private void notifyDataChange() {
        synchronized(mObservers) {
            if (!mObservers.isEmpty()) {
                Iterator<DataSetObserver> i = mObservers.iterator();
                while (i.hasNext()) {
                    i.next().onChanged();
                }
            }
        }
    }

    // Builds a string with the day of the week and the word yesterday/today/tomorrow
    // before it if applicable.
    private String buildDayOfWeek() {

        Time t = new Time(mTimeZone);
        t.set(mMilliTime);
        long julianDay = Time.getJulianDay(mMilliTime,t.gmtoff);
        String dayOfWeek = null;
        mStringBuilder.setLength(0);

        if (julianDay == mTodayJulianDay) {
            dayOfWeek = mContext.getString(R.string.agenda_today,
                    DateUtils.formatDateRange(mContext, mFormatter, mMilliTime, mMilliTime,
                            DateUtils.FORMAT_SHOW_WEEKDAY, mTimeZone).toString());
        } else if (julianDay == mTodayJulianDay - 1) {
            dayOfWeek = mContext.getString(R.string.agenda_yesterday,
                    DateUtils.formatDateRange(mContext, mFormatter, mMilliTime, mMilliTime,
                            DateUtils.FORMAT_SHOW_WEEKDAY, mTimeZone).toString());
        } else if (julianDay == mTodayJulianDay + 1) {
            dayOfWeek = mContext.getString(R.string.agenda_tomorrow,
                    DateUtils.formatDateRange(mContext, mFormatter, mMilliTime, mMilliTime,
                            DateUtils.FORMAT_SHOW_WEEKDAY, mTimeZone).toString());
        } else {
            dayOfWeek = DateUtils.formatDateRange(mContext, mFormatter, mMilliTime, mMilliTime,
                    DateUtils.FORMAT_SHOW_WEEKDAY, mTimeZone).toString();
        }
        return dayOfWeek.toUpperCase();
    }

    // Builds strings with different formats:
    // Full date: Month,day Year
    // Month year
    // Month day
    // Month
    // Week:  month day-day or month day - month day
    private String buildFullDate() {
        mStringBuilder.setLength(0);
        String date = DateUtils.formatDateRange(mContext, mFormatter, mMilliTime, mMilliTime,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR, mTimeZone).toString();
         return date;
    }
    private String buildMonthYearDate() {
        mStringBuilder.setLength(0);
        String date = DateUtils.formatDateRange(mContext, mFormatter, mMilliTime, mMilliTime,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_MONTH_DAY |
                DateUtils.FORMAT_SHOW_YEAR, mTimeZone).toString();
         return date;
    }
    private String buildMonthDayDate() {
        mStringBuilder.setLength(0);
        String date = DateUtils.formatDateRange(mContext, mFormatter, mMilliTime, mMilliTime,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR, mTimeZone).toString();
         return date;
    }
    private String buildMonthDate() {
        mStringBuilder.setLength(0);
        String date = DateUtils.formatDateRange(mContext, mFormatter, mMilliTime, mMilliTime,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR |
                DateUtils.FORMAT_NO_MONTH_DAY, mTimeZone).toString();
         return date;
    }
    private String buildWeekDate() {


        // Calculate the start of the week, taking into account the "first day of the week"
        // setting.

        Time t = new Time(mTimeZone);
        t.set(mMilliTime);
        int firstDayOfWeek = Utils.getFirstDayOfWeek(mContext);
        int dayOfWeek = t.weekDay;
        int diff = dayOfWeek - firstDayOfWeek;
        if (diff != 0) {
            if (diff < 0) {
                diff += 7;
            }
            t.monthDay -= diff;
            t.normalize(true /* ignore isDst */);
        }

        long weekStartTime = t.toMillis(true);
        long weekEndTime = weekStartTime + DateUtils.WEEK_IN_MILLIS;

        // If week start and end is in 2 different months, use short months names
        Time t1 = new Time(mTimeZone);
        t.set(weekEndTime);
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR;
        if (t.month != t1.month) {
            flags |= DateUtils.FORMAT_ABBREV_MONTH;
        }

        mStringBuilder.setLength(0);
        String date = DateUtils.formatDateRange(mContext, mFormatter, weekStartTime,
                weekEndTime, flags, mTimeZone).toString();
         return date;
    }

}

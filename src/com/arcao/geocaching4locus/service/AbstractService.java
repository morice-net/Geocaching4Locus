package com.arcao.geocaching4locus.service;

import geocaching.api.exception.InvalidCredentialsException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.arcao.geocaching4locus.ErrorActivity;
import com.arcao.geocaching4locus.R;

public abstract class AbstractService extends IntentService {
	protected String TAG;
	
	public static final String ACTION_PROGRESS_UPDATE = "com.arcao.geocaching4locus.intent.action.PROGRESS_UPDATE";
	public static final String ACTION_PROGRESS_COMPLETE = "com.arcao.geocaching4locus.intent.action.PROGRESS_COMPLETE";
	public static final String ACTION_ERROR = ErrorActivity.ACTION_ERROR;
	
	public static final String PARAM_COUNT = "COUNT";
	public static final String PARAM_CURRENT = "CURRENT";
	public static final String PARAM_RESOURCE_ID = ErrorActivity.PARAM_RESOURCE_ID;
	public static final String PARAM_ADDITIONAL_MESSAGE = ErrorActivity.PARAM_ADDITIONAL_MESSAGE;
	public static final String PARAM_OPEN_PREFERENCE = ErrorActivity.PARAM_OPEN_PREFERENCE;

	private boolean canceled;
	
	protected NotificationManager notificationManager;
	protected RemoteViews contentViews;
	protected Notification progressNotification;

	private Method startForegroundMethod;
	private Method setForegroundMethod;
	private Method stopForegroundMethod;
	
	protected int notificationId;
	protected int actionTextId;
	
	public AbstractService(String tag, int notificationId, int actionTextId) {
		super(tag);
		this.TAG = tag;
		this.notificationId = notificationId;
		this.actionTextId = actionTextId;
	}
	
	protected abstract void setInstance();
	protected abstract void removeInstance();
	
	protected abstract Intent createOngoingEventIntent();
	protected abstract void run(Intent intent) throws Exception;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		setInstance();
		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		
		prepareCompatMethods();
		
		canceled = false;

		progressNotification = createProgressNotification(); 
		startForegroundCompat(notificationId, progressNotification);
	}

	protected Notification createProgressNotification() {
		// extract colors and text sizes for notification
		extractColors();
		
		Notification n = new Notification();
		
		n.icon = R.drawable.ic_launcher;
		n.tickerText = null;
		n.flags |= Notification.FLAG_ONGOING_EVENT;				
		n.contentView = new RemoteViews(getPackageName(), R.layout.notification_download);
		n.contentView.setTextViewText(R.id.progress_title, getText(actionTextId));
		
		// correct size and color
		n.contentView.setTextColor(R.id.progress_title, notification_title_color);
		n.contentView.setFloat(R.id.progress_title, "setTextSize", notification_title_size);
		
		n.contentView.setTextColor(R.id.progress_text, notification_text_color);
		
		Intent intent = createOngoingEventIntent();
		if (intent != null)
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		
		n.contentIntent = PendingIntent.getActivity(getBaseContext(), 0, intent, 0);
		return n;
	}
	
	protected Notification createErrorNotification(int resErrorId, String errorText, boolean openPreference) {
		Notification n = new Notification();
		
		n.icon = R.drawable.ic_launcher;
		n.tickerText = getText(R.string.error_title);
		n.when = new Date().getTime(); 
		
		Intent intent = new Intent(this, ErrorActivity.class);
		intent.setAction(ACTION_ERROR);
		intent.putExtra(PARAM_RESOURCE_ID, resErrorId);
		intent.putExtra(PARAM_ADDITIONAL_MESSAGE, errorText);
		intent.putExtra(PARAM_OPEN_PREFERENCE, openPreference);
		
		n.setLatestEventInfo(this, getText(R.string.error_title), Html.fromHtml(getString(resErrorId, errorText)), PendingIntent.getActivity(getBaseContext(), 0, intent, 0));
		
		return n;
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		loadConfiguration(PreferenceManager.getDefaultSharedPreferences(this));
					
		try {
			run(intent);
		} catch (InvalidCredentialsException e) {
			Log.e(TAG, e.getMessage(), e);
			sendError(R.string.error_credentials, null, true);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			String message = e.getMessage();
			if (message == null)
				message = "";
			
			sendError(R.string.error, String.format("<br>%s<br> <br>Exception: %s<br>File: %s<br>Line: %d", message, e.getClass().getSimpleName(), e.getStackTrace()[0].getFileName(), e.getStackTrace()[0].getLineNumber()));
		}
	}
	
	protected void storeSession(String session) {
		Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
		edit.putString("session", session);
		edit.commit();
	}
	
	protected void removeSession() {
		Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
		edit.remove("session");
		edit.commit();
	}
	
	@Override
	public void onDestroy() {
		canceled = true;
		removeInstance();
		stopForegroundCompat(notificationId);
		super.onDestroy();
	}
	
	public boolean isCanceled() {
		return canceled;
	}

	protected abstract void loadConfiguration(SharedPreferences prefs);
		
	public void sendProgressUpdate(int current, int count) {
		if (canceled)
			return;
		
		int percent = 100;
		if (count > 0)
			percent = ((current * 100) / count);
		
		progressNotification.contentView.setProgressBar(R.id.progress_bar, count, current, false);
		progressNotification.contentView.setTextViewText(R.id.progress_text, percent + "%");
		notificationManager.notify(notificationId, progressNotification);
		
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(ACTION_PROGRESS_UPDATE);
		//broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
		broadcastIntent.putExtra(PARAM_COUNT, count);
		broadcastIntent.putExtra(PARAM_CURRENT, current);
		sendBroadcast(broadcastIntent);
	}
	
	protected void sendProgressComplete(int count) {
		if (!canceled) {
			progressNotification.contentView.setProgressBar(R.id.progress_bar, count, count, false);
			progressNotification.contentView.setTextViewText(R.id.progress_text, "100%");
			notificationManager.notify(notificationId, progressNotification);
		}
		
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(ACTION_PROGRESS_COMPLETE);
		//broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
		broadcastIntent.putExtra(PARAM_COUNT, count);
		broadcastIntent.putExtra(PARAM_CURRENT, count);
		sendBroadcast(broadcastIntent);
	}
	
	protected void sendError(int error, String additionalMessage) {
		sendError(error, additionalMessage, false);
	}
	
	protected void sendError(int error, String additionalMessage, boolean openPreference) {
		// error notification
		notificationManager.notify(error, createErrorNotification(error, additionalMessage, openPreference));
		
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(ACTION_ERROR);
		//broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
		broadcastIntent.putExtra(PARAM_RESOURCE_ID, error);
		if (additionalMessage != null)
			broadcastIntent.putExtra(PARAM_ADDITIONAL_MESSAGE, additionalMessage);
		
		broadcastIntent.putExtra(PARAM_OPEN_PREFERENCE, openPreference);
		sendBroadcast(broadcastIntent);
	}

	// ----------------  Compatible methods for start / stop foreground service --------------------
	
	protected void prepareCompatMethods() {
		try {
			startForegroundMethod = getClass().getMethod("startForeground", new Class[] {int.class, Notification.class});
			stopForegroundMethod = getClass().getMethod("stopForeground", new Class[] {boolean.class});
			return;
		} catch (NoSuchMethodException e) {
			// Running on an older platform.
			startForegroundMethod = stopForegroundMethod = null;
		}
		try {
			setForegroundMethod = getClass().getMethod("setForeground", new Class[] {boolean.class});
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("OS doesn't have Service.startForeground OR Service.setForeground!");
		}
	}

	/**
	 * This is a wrapper around the new startForeground method, using the older
	 * APIs if it is not available.
	 */
	protected void startForegroundCompat(int id, Notification notification) {
	    // If we have the new startForeground API, then use it.
	    if (startForegroundMethod != null) {
	        invokeMethod(startForegroundMethod, new Object[] { id, notification});
	        return;
	    }

	    // Fall back on the old API.
	    invokeMethod(setForegroundMethod, new Object[] {true});
	    notificationManager.notify(id, notification);
	}

	/**
	 * This is a wrapper around the new stopForeground method, using the older
	 * APIs if it is not available.
	 */
	protected void stopForegroundCompat(int id) {
	    // If we have the new stopForeground API, then use it.
	    if (stopForegroundMethod != null) {
	        invokeMethod(stopForegroundMethod, new Object[] { true });
	        return;
	    }

	    // Fall back on the old API.  Note to cancel BEFORE changing the
	    // foreground state, since we could be killed at that point.
	    notificationManager.cancel(id);
	    invokeMethod(setForegroundMethod, new Object[] { false });
	}
	
	protected void invokeMethod(Method method, Object[] args) {
    try {
        method.invoke(this, args);
    } catch (InvocationTargetException e) {
        // Should not happen.
        Log.w(TAG, "Unable to invoke method", e);
    } catch (IllegalAccessException e) {
        // Should not happen.
        Log.w(TAG, "Unable to invoke method", e);
    }
	}
	
	// --------------------- Methods to get right color and text size for title and text for notification ------------------
	protected Integer notification_title_color = null;
	protected float notification_title_size = 11;
	protected Integer notification_text_color = null;
	protected float notification_text_size = 11;
	private final String COLOR_SEARCH_RECURSE_TITLE_TIP = "SOME_SAMPLE_TITLE";
	private final String COLOR_SEARCH_RECURSE_TEXT_TIP = "SOME_SAMPLE_TEXT";

	protected boolean recurseGroup(ViewGroup gp) {
		final int count = gp.getChildCount();

		for (int i = 0; i < count; ++i) {
			if (gp.getChildAt(i) instanceof TextView) {
				final TextView text = (TextView) gp.getChildAt(i);
				final String szText = text.getText().toString();

				if (COLOR_SEARCH_RECURSE_TITLE_TIP.equals(szText)) {
					notification_title_color = text.getTextColors().getDefaultColor();
					notification_title_size = text.getTextSize();
					DisplayMetrics metrics = new DisplayMetrics();
					WindowManager systemWM = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
					systemWM.getDefaultDisplay().getMetrics(metrics);
					notification_title_size /= metrics.scaledDensity;

					if (notification_title_color != null && notification_text_color != null)
						return true;

				} else if (COLOR_SEARCH_RECURSE_TEXT_TIP.equals(szText)) {
					notification_text_color = text.getTextColors().getDefaultColor();
					notification_text_size = text.getTextSize();
					DisplayMetrics metrics = new DisplayMetrics();
					WindowManager systemWM = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
					systemWM.getDefaultDisplay().getMetrics(metrics);
					notification_text_size /= metrics.scaledDensity;

					if (notification_title_color != null && notification_text_color != null)
						return true;
				}
			} else if (gp.getChildAt(i) instanceof ViewGroup) {
				if (recurseGroup((ViewGroup) gp.getChildAt(i)))
					return true;
			}
		}
		return false;
	}

	protected void extractColors() {
		if (notification_title_color != null && notification_text_color != null)
			return;

		try {
			Notification ntf = new Notification();
			ntf.setLatestEventInfo(this, COLOR_SEARCH_RECURSE_TITLE_TIP, COLOR_SEARCH_RECURSE_TEXT_TIP, null);
			LinearLayout group = new LinearLayout(this);
			ViewGroup event = (ViewGroup) ntf.contentView.apply(this, group);
			recurseGroup(event);
			group.removeAllViews();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
		
		if (notification_title_color == null)
			notification_title_color = android.R.color.black;
		if (notification_text_color == null)
			notification_text_color = android.R.color.black;
	}

}

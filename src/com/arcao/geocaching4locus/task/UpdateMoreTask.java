package com.arcao.geocaching4locus.task;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import locus.api.android.ActionTools;
import locus.api.android.utils.RequiredVersionMissingException;
import locus.api.mapper.LocusDataMapper;
import locus.api.objects.extra.Waypoint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.arcao.geocaching.api.GeocachingApi;
import com.arcao.geocaching.api.data.Geocache;
import com.arcao.geocaching.api.exception.GeocachingApiException;
import com.arcao.geocaching.api.exception.InvalidCredentialsException;
import com.arcao.geocaching.api.exception.InvalidSessionException;
import com.arcao.geocaching.api.impl.LiveGeocachingApiFactory;
import com.arcao.geocaching.api.impl.live_geocaching_api.filter.CacheCodeFilter;
import com.arcao.geocaching.api.impl.live_geocaching_api.filter.Filter;
import com.arcao.geocaching4locus.Geocaching4LocusApplication;
import com.arcao.geocaching4locus.constants.AppConstants;
import com.arcao.geocaching4locus.constants.PrefConstants;
import com.arcao.geocaching4locus.exception.ExceptionHandler;
import com.arcao.geocaching4locus.util.UserTask;

public class UpdateMoreTask extends UserTask<long[], Integer, Boolean> {
	private static final String TAG = UpdateMoreTask.class.getName();

	private int logCount;

	public interface OnTaskFinishedListener {
		void onTaskFinished(boolean success);
		void onProgressUpdate(int count);
	}

	private WeakReference<OnTaskFinishedListener> onTaskFinishedListenerRef;

	public void setOnTaskUpdateListener(OnTaskFinishedListener onTaskUpdateInterface) {
		this.onTaskFinishedListenerRef = new WeakReference<OnTaskFinishedListener>(onTaskUpdateInterface);
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Geocaching4LocusApplication.getAppContext());

		logCount = prefs.getInt(PrefConstants.DOWNLOADING_COUNT_OF_LOGS, 5);
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		OnTaskFinishedListener listener = onTaskFinishedListenerRef.get();
		if (listener != null)
			listener.onTaskFinished(result);
	}

	@Override
	protected void onProgressUpdate(Integer... values) {
		OnTaskFinishedListener listener = onTaskFinishedListenerRef.get();
		if (listener != null)
			listener.onProgressUpdate(values[0]);
	}

	@Override
	protected Boolean doInBackground(long[]... params) throws Exception {
		Context context = Geocaching4LocusApplication.getAppContext();
		long[] pointIndexes = params[0];

		if (!Geocaching4LocusApplication.getAuthenticatorHelper().hasAccount())
			throw new InvalidCredentialsException("Account not found.");

		GeocachingApi api = LiveGeocachingApiFactory.create();

		int current = 0;
		int count = pointIndexes.length;

		try {
			login(api);

			current = 0;
			while (current < count) {
				// prepare old cache data
				List<Waypoint> oldPoints = prepareOldWaypointsFromIndexes(context, pointIndexes, current, AppConstants.CACHES_PER_REQUEST);

				if (oldPoints.size() == 0) {
					// all are Waypoints without geocaching data
					current = current + Math.min(pointIndexes.length - current, AppConstants.CACHES_PER_REQUEST);
					publishProgress(current);
					continue;
				}

				@SuppressWarnings({ "unchecked", "rawtypes" })
				List<Geocache> cachesToAdd = (List) api.searchForGeocaches(false, AppConstants.CACHES_PER_REQUEST, logCount, 0, new Filter[] {
						new CacheCodeFilter(getCachesIds(oldPoints))
				});

				Geocaching4LocusApplication.getAuthenticatorHelper().getRestrictions().updateLimits(api.getLastCacheLimits());

				if (isCancelled())
					return false;

				if (cachesToAdd.size() == 0)
					break;

				List<Waypoint> points = LocusDataMapper.toLocusPoints(context, cachesToAdd);

				for (Waypoint p : points) {
					if (p == null || p.gcData == null)
						continue;

					// Geocaching API can return caches in a different order
					Waypoint oldPoint = searchOldPointByGCCode(oldPoints, p.gcData.getCacheID());

					p = LocusDataMapper.mergePoints(Geocaching4LocusApplication.getAppContext(), p, oldPoint);

					// update new point data in Locus
					ActionTools.updateLocusWaypoint(context, p, false);

				}

				current = current + Math.min(pointIndexes.length - current, AppConstants.CACHES_PER_REQUEST);
				publishProgress(current);

				// force memory clean
				oldPoints = null;
				cachesToAdd = null;
				points = null;
			}
			publishProgress(current);

			Log.i(TAG, "updated caches: " + current);

			if (current > 0) {
				return true;
			} else {
				return false;
			}

		} catch (InvalidSessionException e) {
			Log.e(TAG, e.getMessage(), e);
			Geocaching4LocusApplication.getAuthenticatorHelper().invalidateAuthToken();

			throw e;
		}
	}

	private Waypoint searchOldPointByGCCode(List<Waypoint> oldPoints, String gcCode) {
		if (gcCode == null || gcCode.length() == 0)
			return null;

		for (Waypoint oldPoint : oldPoints) {
			if (oldPoint.gcData != null && gcCode.equals(oldPoint.gcData.getCacheID())) {
				return oldPoint;
			}
		}

		return null;
	}

	private List<Waypoint> prepareOldWaypointsFromIndexes(Context context, long[] pointIndexes, int current, int cachesPerRequest) {
		List<Waypoint> waypoints = new ArrayList<Waypoint>();

		int count = Math.min(pointIndexes.length - current, cachesPerRequest);

		for (int i = 0; i < count; i++) {
			try {
				// get old waypoint from Locus
				Waypoint wpt = ActionTools.getLocusWaypoint(context, pointIndexes[current + i]);
				if (wpt.gcData == null) {
					Log.w(TAG, "Waypoint " + (current + i) + " with id " + pointIndexes[current + i] + " isn't cache. Skipped...");
					continue;
				}

				waypoints.add(wpt);
			} catch (RequiredVersionMissingException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}


		return waypoints;
	}

	protected String[] getCachesIds(List<Waypoint> caches) {
		int count = caches.size();

		String[] ret = new String[count];

		for (int i = 0; i < count; i++) {
			ret[i] = caches.get(i).gcData.getCacheID();
		}

		return ret;
	}

	@Override
	protected void onCancelled() {
		super.onCancelled();

		OnTaskFinishedListener listener = onTaskFinishedListenerRef.get();
		if (listener != null)
			listener.onTaskFinished(false);
	}

	@Override
	protected void onException(Throwable t) {
		super.onException(t);

		if (isCancelled())
			return;

		Log.e(TAG, t.getMessage(), t);

		Context mContext = Geocaching4LocusApplication.getAppContext();

		Intent intent = new ExceptionHandler(mContext).handle(t);
		intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NEW_TASK);

		OnTaskFinishedListener listener = onTaskFinishedListenerRef.get();
		if (listener != null)
			listener.onTaskFinished(false);

		mContext.startActivity(intent);
	}

	private void login(GeocachingApi api) throws GeocachingApiException {
		String token = Geocaching4LocusApplication.getAuthenticatorHelper().getAuthToken();
		if (token == null) {
			Geocaching4LocusApplication.getAuthenticatorHelper().removeAccount();
			throw new InvalidCredentialsException("Account not found.");
		}

		api.openSession(token);
	}
}

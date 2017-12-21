package com.ebc.fuelmonitoring.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.PersistableBundle;

import com.ebc.fuelmonitoring.util.Util;

public class DistanceService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        PersistableBundle pb = params.getExtras();
        Intent schedulerIntent = new Intent(Util.INTENT_ACTION_SCHEDULER);
        schedulerIntent.putExtra(Util.DISTANCE_EXTRA_KEY, pb.getString(Util.DISTANCE_EXTRA_KEY));
        sendBroadcast(schedulerIntent);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}

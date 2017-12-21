package com.ebc.fuelmonitoring;

import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.ebc.fuelmonitoring.util.Util;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;
import java.lang.reflect.Array;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.TimeZone;

import static java.lang.Double.*;

public class MainActivity extends AppCompatActivity {

    Date currentTime = Calendar.getInstance().getTime();
    Scanner sc = new Scanner(System.in);

    private static final String ECHO_PIN_NAME = "BCM20";
    private static final String TRIGGER_PIN_NAME = "BCM21";

    private static final String TAG = "ultrasonicsensor";

    private Handler mCallbackHandler;
    private Handler ultrasonicTriggerHandler;

//    private static final int INTERVAL_BETWEEN_TRIGGERS = 3 * 1000; //3 detik
    private static final int INTERVAL_BETWEEN_TRIGGERS = 1 * 1000;
    private TextView tvDistance;

    private static final int INTERVAL_TIME = 1 * 60 * 1000;
    private int COUNT_TIME = 0;

    private List<Integer> listDistance = new ArrayList<>();

    private Runnable triggerRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                readDistanceAsnyc();
                ultrasonicTriggerHandler.postDelayed(triggerRunnable, INTERVAL_BETWEEN_TRIGGERS);
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    private Gpio mEcho;
    private Gpio mTrigger;

    private JobScheduler jobScheduler;

    protected BroadcastReceiver schedulerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String gson = intent.getStringExtra(Util.DISTANCE_EXTRA_KEY);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn1 = (Button) findViewById(R.id.btn1);
        btn1.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                startActivity(intent);
            }
        });

        tvDistance = findViewById(R.id.distance);

        // Prepare handler for GPIO callback
        HandlerThread handlerThread = new HandlerThread("callbackHandlerThread");
        handlerThread.start();
        mCallbackHandler = new Handler(handlerThread.getLooper());

        // Prepare handler to send triggers
        HandlerThread triggerHandlerThread = new HandlerThread("triggerHandlerThread");
        triggerHandlerThread.start();
        ultrasonicTriggerHandler = new Handler(triggerHandlerThread.getLooper());

        //Initialize PeripheralManagerService
        PeripheralManagerService service = new PeripheralManagerService();

        //List all available GPIOs
        Log.d(TAG, "Available GPIOs: " + service.getGpioList());

        try {
            // Step 1. Create GPIO connection.
            mEcho = service.openGpio(ECHO_PIN_NAME);
            // Step 2. Configure as an input.
            mEcho.setDirection(Gpio.DIRECTION_IN);
            // Step 3. Enable edge trigger events.
            mEcho.setEdgeTriggerType(Gpio.EDGE_BOTH);
            // Step 4. Set Active type to HIGH, then it will trigger TRUE (HIGH, active) events
            mEcho.setActiveType(Gpio.ACTIVE_HIGH);
            // Step 5. Register an event callback.
            mEcho.registerGpioCallback(mCallback, mCallbackHandler);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }

        try {
            // Step 1. Create GPIO connection.
            mTrigger = service.openGpio(TRIGGER_PIN_NAME);

            // Step 2. Configure as an output with default LOW (false) value.
            mTrigger.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }

        /*
        new Thread(){
            @Override
            public void run() {
                try {
                    while (true) {
                        readDistanceAsnyc();
                        Thread.sleep(300);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        */

        ultrasonicTriggerHandler.post(triggerRunnable);
    }

    long time1, time2;

    protected void readDistanceAsnyc() throws IOException, InterruptedException {
        // Just to be sure, set the trigger first to false
        mTrigger.setValue(false);
        Thread.sleep(0, 2000);

        // Hold the trigger pin high for at least 10 us
        mTrigger.setValue(true);
        Thread.sleep(0, 10000); //10 microsec

        // Reset the trigger pin
        mTrigger.setValue(false);

    }


    // Step 5. Register an event callback.
    private GpioCallback mCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            try {
                final int distance;
                if (gpio.getValue() == false) {
                    // The end of the pulse on the ECHO pin

                    time2 = System.nanoTime();

                    long pluseWidth = time2 - time1;
                    //Log.d(TAG, "pluseWidth: " + pluseWidth);
                    double d = (pluseWidth / 1000000000.0) * 340.0 / 2.0 * 100.0;
                    distance = (int) d;
                    //double distance = (pluseWidth / 1000.0 ) / 58.23 ; //cm
                    //double distance = (pluseWidth / 10000000000.0);
                    //double distance = (pluseWidth / 10000000000.0) * 340.0 /2.0 * 100.0 ;
                    Log.i(TAG, "distance: " + distance + " cm");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvDistance.setText("Now Distance = " + String.valueOf(distance) + " cm");
                        }
                    });
                    COUNT_TIME += INTERVAL_BETWEEN_TRIGGERS;
                    listDistance.add(distance);
                    if (COUNT_TIME >= INTERVAL_TIME) {
                        COUNT_TIME = 0;
                        int max = Collections.max(listDistance);
                        Log.d(TAG, "max = " + max);
                        int min = Collections.min(listDistance);
                        Log.d(TAG, "min = " + min);
                        listDistance = new ArrayList<>();
                    }

                } else {
                    // The pulse arrived on ECHO pin
                    time1 = System.nanoTime();

                    //2nd try
                    //mHandler.sendEmptyMessage(1);
                    //Log.i(TAG, "Echo ARRIVED!");

                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Step 6. Return true to keep callback active.
            return true;
        }


        @Override
        public void onGpioError(Gpio gpio, int error) {
            Log.e(TAG, "error: " + error);
            super.onGpioError(gpio, error);
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Step 7. Close the resource
        if (mEcho != null) {
            // unregister from the callback
            mEcho.unregisterGpioCallback(mCallback);
            try {
                mEcho.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }

        // Step 7. Close the resource
        if (mTrigger != null) {
            try {
                mTrigger.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }
        this.finish();

    }
}
package com.qoeapps.qoactivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.mobiqoe.enforce.R;


@SuppressWarnings("ALL")
public class ExperienceSplashActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);
        TextView capView = (TextView) findViewById(R.id.orginal_cap);
        double caoa = batteryCapacity(this);
        capView.setText("ORIGINAL CAPACITY\n"+caoa+"MAH");
        //thread for splash screen running
        Thread logoTimer = new Thread() {
            public void run() {
                try {
                    sleep(2000);
                } catch (InterruptedException e) {
                    Log.d("Exception", "Exception" + e);
                } finally {
                    startActivity(
                            new Intent(ExperienceSplashActivity.this,
                                    QoExperienceWelcome.class));
                }
                finish();
            }
        };
        logoTimer.start();

        //Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);

    }

    private double batteryCapacity(Context context) {
        Object mPowerProfile_ = null;
        double newbattery = 0;

        final String POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile";

        try {
            mPowerProfile_ = Class.forName(POWER_PROFILE_CLASS)
                    .getConstructor(Context.class).newInstance(context);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            newbattery = (double) Class.forName(POWER_PROFILE_CLASS).getMethod("getAveragePower", String.class).invoke(mPowerProfile_, "battery.capacity");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return newbattery;
    }
}
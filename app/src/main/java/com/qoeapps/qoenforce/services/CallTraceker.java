package com.qoeapps.qoenforce.services;

import android.content.Context;

import java.util.Date;

/**
 * Created by mohoque on 06/03/2018.
 */

public class CallTraceker extends TelephoneBoradcast {


    @Override
    protected void onOutgoingCallStarted(Context ctx, String number, Date start) {
    }

    @Override
    protected void onIncomingCallEnded(Context ctx, String number, Date start, Date end) {
    }

    @Override
    protected void onOutgoingCallEnded(Context ctx, String number, Date start, Date end) {
    }

    @Override
    protected void onMissedCall(Context ctx, String number, Date start) {
    }
    @Override
    protected void onIncomingCallReceived(Context ctx, String number, Date start) {
    }
    @Override
    protected void onIncomingCallAnswered(Context ctx, String number, Date start) {
    }

}
package com.qoeapps.qoactivity;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by Bipin on 5/6/2016.
 */

@SuppressWarnings("ALL")
public class SenseScreenFragment extends Fragment {

    final static String LAYOUT_ID = "layoutId";

    public static com.qoeapps.qoactivity.SenseScreenFragment newInstance(int layoutId) {
        com.qoeapps.qoactivity.SenseScreenFragment pane = new com.qoeapps.qoactivity.SenseScreenFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(LAYOUT_ID, layoutId);
        pane.setArguments(bundle);
        return pane;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(getArguments().getInt(LAYOUT_ID, -1), container, false);
        return root;
    }
}

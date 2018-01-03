package com.qoeapps.qoenforce.datacontrol;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by mohoque on 22/02/2017.
 */

public class MetaMineConstants {

    public static final Map<Integer, String> IdAppNameMaps = new HashMap<>();

    private static final Map<String, Integer> DIFF_SERV_NAMES
            = new HashMap<String, Integer>();
    /** Common names used for Differentiated Services values. */


    static {

        DIFF_SERV_NAMES.put("CS0", 0);
        DIFF_SERV_NAMES.put("CS1", 8);
        DIFF_SERV_NAMES.put("CS2", 16);
        DIFF_SERV_NAMES.put("CS3", 24);
        DIFF_SERV_NAMES.put("CS4", 32);
        DIFF_SERV_NAMES.put("CS5", 40);
        DIFF_SERV_NAMES.put("CS6", 48);
        DIFF_SERV_NAMES.put("CS7", 56);
        DIFF_SERV_NAMES.put("AF11", 10);
        DIFF_SERV_NAMES.put("AF12", 12);
        DIFF_SERV_NAMES.put("AF13", 14);
        DIFF_SERV_NAMES.put("AF21", 18);
        DIFF_SERV_NAMES.put("AF22", 20);
        DIFF_SERV_NAMES.put("AF23", 22);
        DIFF_SERV_NAMES.put("AF31", 26);
        DIFF_SERV_NAMES.put("AF32", 28);
        DIFF_SERV_NAMES.put("AF33", 30);
        DIFF_SERV_NAMES.put("AF41", 34);
        DIFF_SERV_NAMES.put("AF42", 36);
        DIFF_SERV_NAMES.put("AF43", 38);
        DIFF_SERV_NAMES.put("EF", 46);
    }
}

package de.flashheart.rlg.commander.misc;


import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONObject;

import java.awt.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;

@Log4j2
public class Tools {

    public static final String[] fignums = new String[]{
            "  __\n" +
                    " /  \\\n" +
                    "| () |\n" +
                    " \\__/\n",
            " _\n" +
                    "/ |\n" +
                    "| |\n" +
                    "|_|\n",
            " ___\n" +
                    "|_  )\n" +
                    " / /\n" +
                    "/___|\n",
            " ____\n" +
                    "|__ /\n" +
                    " |_ \\\n" +
                    "|___/\n",
            " _ _\n" +
                    "| | |\n" +
                    "|_  _|\n" +
                    "  |_|\n",
            " ___\n" +
                    "| __|\n" +
                    "|__ \\\n" +
                    "|___/\n",
            "  __\n" +
                    " / /\n" +
                    "/ _ \\\n" +
                    "\\___/\n",
            " ____\n" +
                    "|__  |\n" +
                    "  / /\n" +
                    " /_/\n",
            " ___\n" +
                    "( _ )\n" +
                    "/ _ \\\n" +
                    "\\___/\n",
            " ___\n" +
                    "/ _ \\\n" +
                    "\\_, /\n" +
                    " /_/\n"
    };


    // todo: this should be in the rlgagent
    public static JSONObject getProgressTickingScheme(String deviceid, int time_period_in_millis) {
        if (time_period_in_millis <= 30000L) {
            JSONObject signal = new JSONObject().put(deviceid, new JSONObject("""
                    {
                        "repeat": 1,
                        "scheme": [2000,-5000,100,-5000,100,-5000,100,-5000,100,-5000,100,-5000,100,-5000]
                    }
                    """));
            log.debug("Progress Ticking Scheme {} with Period {}", signal.toString(4), time_period_in_millis);
            return signal;
        }

        // increasing siren signals during bomb time. repeated beeps signals the quarter were in
        int segment_time_in_millis = time_period_in_millis / 4;
        ArrayList<Integer> scheme = new ArrayList<>(Arrays.asList(2000, -10000));
        ArrayList<Integer> siren_tick = new ArrayList<>(Arrays.asList(100, -100));

        //String siren_tick = "on,100;off,100;";
        //String signal = "1:on,2000;off,10000;";
        //int period_intro = 12000;
        int q1_tick = 10200;
        int q2_tick = 10400;
        int q3_tick = 10600;
        int q4_tick = 10800;

        //int period_q1 = segment_time_in_millis - period_intro;
        // the rest will be added up with 10200ms chunks ticks
        int q1_repeat = segment_time_in_millis / q1_tick;
        int q2_repeat = segment_time_in_millis / q2_tick;
        int q3_repeat = segment_time_in_millis / q3_tick;
        int q4_repeat = segment_time_in_millis / q4_tick;

        for (int rep = 0; rep < q1_repeat; rep++) {
            scheme.addAll(siren_tick);
            scheme.add(-10000);
        }
        scheme.add(-(segment_time_in_millis - q1_repeat * q1_tick));
        for (int rep = 0; rep < q2_repeat; rep++) {
            scheme.addAll(siren_tick);
            scheme.addAll(siren_tick);
            scheme.add(-10000);
        }
        scheme.add(-(segment_time_in_millis - q2_repeat * q2_tick));
        for (int rep = 0; rep < q3_repeat; rep++) {
            scheme.addAll(siren_tick);
            scheme.addAll(siren_tick);
            scheme.addAll(siren_tick);
            scheme.add(-10000);
        }
        scheme.add(-(segment_time_in_millis - q3_repeat * q3_tick));
        for (int rep = 0; rep < q4_repeat; rep++) {
            scheme.addAll(siren_tick);
            scheme.addAll(siren_tick);
            scheme.addAll(siren_tick);
            scheme.addAll(siren_tick);
            scheme.add(-10000);
        }
        scheme.add(-(segment_time_in_millis - q4_repeat * q4_tick));


//        signal += StringUtils.repeat(siren_tick + "off,10000;", q1_repeat) + String.format("off,%s;", segment_time_in_millis - q1_repeat * q1_tick);
//        signal += StringUtils.repeat(siren_tick + siren_tick + "off,10000;", q2_repeat) + String.format("off,%s;", segment_time_in_millis - q2_repeat * q2_tick);
//        signal += StringUtils.repeat(siren_tick + siren_tick + siren_tick + "off,10000;", q3_repeat) + String.format("off,%s;", segment_time_in_millis - q3_repeat * q3_tick);
//        signal += StringUtils.repeat(siren_tick + siren_tick + siren_tick + siren_tick + "off,10000;", q4_repeat) + String.format("off,%s;", segment_time_in_millis - q4_repeat * q4_tick);
//
        JSONObject signal = new JSONObject().put(deviceid, new JSONObject().put("repeat",1).put("scheme", scheme));

        log.debug("Progress Ticking Scheme {} with Period {}", signal.toString(4), time_period_in_millis);

        return signal;
    }


    /**
     * https://stackoverflow.com/a/43381186
     *
     * @param remain
     * @param total
     */
    public static String get_progress_bar(int remain, int total) {
        if (remain > total) {
            throw new IllegalArgumentException();
        }
        int maxBareSize = 10; // 10unit for 100%
        int remainProcent = ((100 * remain) / total) / maxBareSize;
        char defaultChar = '-';
        String icon = "*";
        String bare = new String(new char[maxBareSize]).replace('\0', defaultChar) + "]";
        StringBuilder bareDone = new StringBuilder();
        bareDone.append("[");
        for (int i = 0; i < remainProcent; i++) {
            bareDone.append(icon);
        }
        String bareRemain = bare.substring(remainProcent);
        return bareDone + bareRemain + " " + remainProcent * 10 + "%";
    }


    /**
     * http://stackoverflow.com/questions/8741479/automatically-determine-optimal-fontcolor-by-backgroundcolor
     *
     * @param background
     * @return
     */
    public static Color getForeground(Color background) {
        int red = 0;
        int green = 0;
        int blue = 0;

        if (background.getRed() + background.getGreen() + background.getBlue() < 383) {
            red = 255;
            green = 255;
            blue = 255;
        }
        return new Color(red, green, blue);
    }
}

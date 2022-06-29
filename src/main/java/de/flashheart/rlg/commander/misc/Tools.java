package de.flashheart.rlg.commander.misc;


import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;

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
    public static String getProgressTickingScheme(int time_period_in_millis) {
        if (time_period_in_millis <= 30000l) return "1:on,2000;off,5000;on,100;off,5000;on,100;off,5000;on,100;off,5000;on,100;off,5000;on,100;off,5000;on,100;off,5000";
        // increasing siren signals during bomb time. repeated beeps signals the quarter were in
        int segment_time_in_millis = time_period_in_millis / 4;
        String siren_tick = "on,100;off,100;";
        String signal = "1:on,2000;off,10000;";
        int period_intro = 12000;
        int q1_tick = 10200;
        int q2_tick = 10400;
        int q3_tick = 10600;
        int q4_tick = 10800;

        int period_q1 = segment_time_in_millis - period_intro;
        // the rest will be added up with 10200ms chunks ticks
        int q1_repeat = segment_time_in_millis / q1_tick;
        int q2_repeat = segment_time_in_millis / q2_tick;
        int q3_repeat = segment_time_in_millis / q3_tick;
        int q4_repeat = segment_time_in_millis / q4_tick;
        signal += StringUtils.repeat(siren_tick + "off,10000;", q1_repeat) + String.format("off,%s;", segment_time_in_millis - q1_repeat * q1_tick);
        signal += StringUtils.repeat(siren_tick + siren_tick + "off,10000;", q2_repeat) + String.format("off,%s;", segment_time_in_millis - q2_repeat * q2_tick);
        signal += StringUtils.repeat(siren_tick + siren_tick + siren_tick + "off,10000;", q3_repeat) + String.format("off,%s;", segment_time_in_millis - q3_repeat * q3_tick);
        signal += StringUtils.repeat(siren_tick + siren_tick + siren_tick + siren_tick + "off,10000;", q4_repeat) + String.format("off,%s;", segment_time_in_millis - q4_repeat * q4_tick);

        log.debug("Progress Ticking Scheme {} with Period {}", signal, time_period_in_millis);

        return signal;
    }

}

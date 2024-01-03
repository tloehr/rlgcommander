package de.flashheart.rlg.commander.games.traits;

import org.quartz.JobDataMap;

/**
 * needed for classes that have a deferred audio job.
 * mainly used for the intro voiceover which is delayed
 * for 6080 milliseconds after the music starts. Then
 * it will perfectly fit with the siren signal for the
 * game_start
 *
 * MQTT.toJSON("channel", map.getString("channel"), "subpath",
 *                         map.getString("subpath"), "soundfile",
 *                         map.getString("soundfile")),
 *
 */
public interface HasDelayedAudio {
    /**
     * QUARTZ method
     * @param map contains the details about what to play.
     *            <b>channel</b>, <b>subpath</b> and <b>soundfile</b>
     */
    void play_later(JobDataMap map);
}

package de.flashheart.rlg.commander.misc;

import de.flashheart.rlg.commander.persistence.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class AppStartupRunner implements ApplicationRunner {
    private final UsersRepository usersRepository;
    private final PlayedGamesService playedGamesService;

    public AppStartupRunner(UsersRepository usersRepository, PlayedGamesService playedGamesService) {
        this.usersRepository = usersRepository;
        this.playedGamesService = playedGamesService;
    }

    @Override
    public void run(ApplicationArguments args) {
        usersRepository.findAll().forEach(log::debug);
        Users owner = usersRepository.findByUsername("torsten");
//        GamesHistory gamesHistory = gamesHistoryService.createNew(owner,
//        new JSONObject("{\n" +
//                "  \"comment\": \"Center Flags x3\",\n" +
//                "  \"class\": \"de.flashheart.rlg.commander.games.CenterFlags\",\n" +
//                "  \"game_time\": 1800,\n" +
//                "  \"game_mode\": \"center_flags\",\n" +
//                "  \"resume_countdown\": 0,\n" +
//                "  \"silent_game\": false,\n" +
//                "  \"agents\": {\n" +
//                "    \"capture_points\": [\n" +
//                "      \"ag01\",\n" +
//                "      \"ag02\",\n" +
//                "      \"ag03\"\n" +
//                "    ],\n" +
//                "    \"sirens\": [\n" +
//                "      \"ag50\"\n" +
//                "    ]\n" +
//                "  },\n" +
//                "  \"spawns\": {\n" +
//                "    \"count_respawns\": true,\n" +
//                "    \"game_lobby\": true,\n" +
//                "    \"intro_mp3\": \"<none>\",\n" +
//                "    \"intro_voice\": \"sharon30s\",\n" +
//                "    \"starter_countdown\": 0,\n" +
//                "    \"announce_sprees\": true,\n" +
//                "    \"respawn_time\": 0,\n" +
//                "    \"teams\": [\n" +
//                "      {\n" +
//                "        \"role\": \"red_spawn\",\n" +
//                "        \"led\": \"red\",\n" +
//                "        \"name\": \"RedFor\",\n" +
//                "        \"agents\": [\n" +
//                "          [\n" +
//                "            \"ag30\"\n" +
//                "          ]\n" +
//                "        ]\n" +
//                "      },\n" +
//                "      {\n" +
//                "        \"role\": \"blue_spawn\",\n" +
//                "        \"led\": \"blu\",\n" +
//                "        \"name\": \"BlueFor\",\n" +
//                "        \"agents\": [\n" +
//                "          [\n" +
//                "            \"ag31\"\n" +
//                "          ]\n" +
//                "        ]\n" +
//                "      }\n" +
//                "    ]\n" +
//                "  }\n" +
//                "}\n"));
//        gamesHistory.setMode("center_flags");
//        gamesHistory.setPit(LocalDateTime.now());
        //gamesHistoryService.save(gamesHistory);
    }
}

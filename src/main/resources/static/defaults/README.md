JSON Game Parameters
=============
In this folder, You will find a default setup file for every game mode. These templates are used to prefill the web interface when setting up a new game. Within the game class hierarchy those entries are passed down via the constructor argument "game_parameters".

# Entries
Even though every file is different from each other, there are many entries which lie in the intersection.

## Common

comment
: the value is shown on the LCDs of the agents

class
: the fully qualified class name of the game class to load (see below) 

game_mode
: short name for every game mode (see below)

resume_countdown
: countdown length in seconds before the game resumes after a pause

silent_game
: when **true**, no siren or buzzer will be activated during the game

### Agents

The role of every agent is assigned by adding it to the appropriate list

capture_points
: when the game mode uses **capture points** - which they usually do - they are listed here

sirens
: list of all siren agents

audio
: list of all agents to play sounds. There are exceptions about this group. Announcements like "enemy double kill" are specific for one team, because the other side hears "double kill". In that case the WithRespawns class handles it's own ist for each side. 

## Spawns


## Conquest

respawn_tickets
: number of starting spawn tickets for each team

not_bleeding_before_cps
: minimum number of capture points for a team to hold, before the bleeding starts

game_mode
: conquest

start_bleed_interval
: 5


end_bleed_interval
: 0.5

ticket_price_for_respawn
: the number of tickets to be lost, when a player presses the spawn button


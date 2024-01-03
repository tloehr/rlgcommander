Game Class Hierarchy
=============
When a **load_game** command is sent to the commander, the GameService loads the needed class into memory and hands over the parameters to the constructor.

- game_parameters contains all values that describe the game setup as a json object.
- scheduler and mqttOutbound objects are passed down because games are no spring beans, so there cannot simply get those via dependency injection.

Depending on the type of game mode we need, the hierarchy from top to bottom differs. But in the end we always reach the base class named **Game**.

On the way down, every parent class picks from the game parameters their specific part and handles it.

# Loadable Games
## Conquest
## Center Flags
## FarCry
## Hardpoint
## MagSpeed
## Meshed
## Signal
## Stronghold
## Timed Only

# Parent Classes
# Scheduled
# Pausable
# WithRespawns
# Timed
# Game (BASE CLASS)

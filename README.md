RLG Commander
=============

---
# Preface
The purpose of the **RLGS** (Real Life Gaming System) is to realize games for **tactical sports** like paintball, airsoft, Nerf or Laser Tag. RLGS adapts well known multiplayer modes from games like Battlefield, Call of Duty, FarCry, Planetside 2 oder Counterstrike to be played in real life.

The RLGS concept consists of two basic elements: the commander (this project) and one or more [agents](https://github.com/tloehr/rlgagent)

Agents can produce optical and acoustical signals and detect events (currently only the press of a button). They do **not know anything** about why they are flashing LEDs, sounding sirens or why somebody presses their buttons. They completely rely on the commander to tell them what to do. The commander is the only one who keeps track about the game situation.

# Controlling the commander
## Web Interface
The commander communicates with the agent via the MQTT protocol. You *- as the game master -* can control the system via a webinterface port 8090.

![web-desktop](src/main/resources/docs/web-desktop.png)

We are using the Bootstrap CSS framework, so the web client is fully responsive and can be easily accessed via a mobile device.

![web-desktop](src/main/resources/docs/web-mobile.png)

## REST Interface

# Main Releases
* pre 1.1 - development versions never used in RL
* 1.1 First version to run on a real paintball field. BF Conquest only.
* 1.2 Added **FarCry Assault**.
* 1.3 Redesign of spawn agent concept
* 1.4 Added **Signal** and **Center Flags**
* 1.5 Added **Stronghold**
* 1.6 Added **Timed Only**
* 1.7 Added a webinterface to replace the old **rlgrc** software.
* 1.8 Added **Hardpoint**




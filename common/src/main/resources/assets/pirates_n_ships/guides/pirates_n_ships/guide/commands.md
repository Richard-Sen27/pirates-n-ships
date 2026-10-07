---
navigation:
  title: "Commands"
  parent: index.md
  position: 120
  icon: minecraft:command_block
---

# Commands

All commands need operator rights (permission level 2). They exist so that features can be tried before the NPCs,
ports and screens that will normally drive them exist.

| Command | What it does |
|---|---|
| `/pirates wind get` | Shows the wind. |
| `/pirates wind set <fromDegrees> <strength>` | Fixes the wind: the compass bearing it blows from, and blocks per second. |
| `/pirates wind clear` | Returns to the natural wind. |
| `/pirates ship forces` | For the ship you stand on: speed, heading, wind angle, how deep it sits, rudder and anchor, and every force on it. |
| `/pirates crew spawn` | Spawns a crew member. |
| `/pirates crew assign <crew> <station>` | Puts a crew member at a station. |
| `/pirates crew release <crew>` | Releases crew from their stations. |
| `/pirates crew order <hoist\|reef\|furl> [crew]` | Gives a sail order. |
| `/pirates flag get\|strike\|raise <pos>`, `/pirates flag set <pos> <kind>` | Reads or changes a flagpole without the delay. |
| `/pirates provisions show <crew> [pos]` | What the pantry you look at holds, and how many days it feeds that crew. |
| `/pirates provisions advance <days> <crew> [prisoners] [rum] [pos]` | Lets that crew live off the pantry for some days and prints what happened. |
| `/pirates ship templates` / `/pirates ship place <template> [force] [assemble]` | Lists the prebuilt ships; puts one on the water in front of you, bow away from you, optionally assembled (operators). |
| `/pirates trade port <name> <kind> <climate>` | Creates a test port. |
| `/pirates trade open\|goods\|buy\|sell …` | Opens a market, lists prices, buys and sells with real coins and items. |
| `/pirates trade contracts <from> <to>`, `/pirates trade contract list\|accept\|deliver` | Delivery contracts between two test ports. |
| `/pirates trade weight` | Cargo weight of the container you look at. |
| `/pirates trade plunder` | Marks the stack in your hand as plundered. |
| `/pirates trade coins` | Gives doubloons. |
| `/pirates law score get\|set\|add <target> …` | Reads or changes a criminal score. |
| `/pirates law crime <target> <crime>` | Reports a crime. |
| `/pirates law last <target>`, `/pirates law hostile <target>` | The last reported crime, and whether the navy would attack. |
| `/pirates law fine <target> <doubloons>` | Pays a fine. |
| `/pirates law bounty list\|place\|claim\|clear …` | Bounties. `claim proof` uses the Bounty Proof in your hand. |
| `/pirates brig list\|capture\|deliver\|ransom\|pressgang\|release …` | Prisoners. |

The playtest checklists in `playtests/` show each command in use.

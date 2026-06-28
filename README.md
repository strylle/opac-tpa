# OPAC-TPA (Fabric, Minecraft 1.21.1)
A small server-side mod coded by me to solve a simple problem! Everyone wants TPA but I wanted to preserve some of the exploration of MC, so I have made it gated to being in the same OPAC party as others & add a lengthy cooldown.

This is a **server-only** mod, only the server needs Fabric Loader + Fabric API + OPAC + OPAC-TPA

Some basic features:
Teleport requests
A basic anti-combat logging mechanic, you cannot use the command after x seconds of being attacked by another player.

## Commands (anyone can run these)

- `/tpa <player>` - sends a teleport request to another player that expires in 60 seconds. Requires being in the same OPAC party.
- `/tpa status` - shows your own free-teleport count, cooldown, and combat tag status.
- `/tpaccept` - accepts a teleport request and teleports them to you
- `/tpadeny` - self explanatory

## Operator commands

- `/partytpa cooldown <minutes>` - sets the cooldown applied once a player's free teleports run out.
- `/partytpa freeteleports <count>` - sets the lifetime number of free teleports per player (uuid based)
- `/partytpa combattag <seconds>` - how long a player must be out of combat to be able to teleport.
- `/partytpa reset cooldown all` - resets everyone's cooldown timer
- `/partytpa reset cooldown <player>` - resets one player's cooldown timer
- `/partytpa reset teleports all` - resets everyone's free teleport count
- `/partytpa reset teleports <player>` - resets one player's free teleport count
- `/partytpa status <player>` - shows a player's tp count, cooldowns, and combat status
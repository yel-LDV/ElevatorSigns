# ElevatorSigns

This is a plugin that allows users to create signs that act as elevators when clicked.

### Spigot Page
https://www.spigotmc.org/resources/elevator-signs.64108/

### Building
This plugin is built using maven. It only has a single dependency, spigot, so it should be fairly easy to clone and build.

### Elevator lives
Turn on `lives.enabled` in the config and every elevator sign starts with a number of lives. Each hit on the sign takes one life off, and the player who hit it is told how many hits are left.

- `lives.amount`: how many hits the elevator can take.
- `lives.break-on-empty: true`: when the last life is gone, the sign breaks and drops on the ground.
- `lives.break-on-empty: false`: the sign stays, but can't be used for `lives.cooldown-seconds` (10 by default). It gets all its lives back afterwards.
- `lives.regenerate-seconds`: after the first hit, the elevator regains one life every this many seconds until it's back to full.

Each sign keeps track of its own lives, and they're only stored in memory, so elevators are at full lives again after a server restart. With `lives.enabled: false` the plugin works exactly like it always has.

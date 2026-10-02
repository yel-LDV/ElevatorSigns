package com.golfing8.elevatorsigns.signhandler;

import com.golfing8.elevatorsigns.ElevatorSignsRevamped;
import com.golfing8.elevatorsigns.Version;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.bukkit.block.Sign;
import org.bukkit.Location;
import org.bukkit.material.Openable;
import org.bukkit.Material;
import org.bukkit.block.Block;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Collection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import com.golfing8.elevatorsigns.Color;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import com.golfing8.elevatorsigns.config.annotation.Configurable;
import com.golfing8.elevatorsigns.config.ConfigManager;
import java.util.UUID;
import java.util.Set;
import org.bukkit.plugin.Plugin;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.Listener;

public abstract class SignHandler implements Listener, CommandExecutor
{
    private Plugin plugin;
    private Set<UUID> recentTries;
    //Keyed by world,x,y,z of the sign block. Only lives in memory, cleared on restart.
    private final Map<String, ElevatorLife> elevatorLives = new HashMap<String, ElevatorLife>();
    private ConfigManager configManager;
    @Configurable(path = "messages.", name = "use")
    protected String useMessage;
    @Configurable(path = "messages.", name = "create")
    protected String createMessage;
    @Configurable(path = "sounds.", name = "use")
    protected String onUseSound;
    @Configurable(path = "sounds.", name = "create")
    protected String onCreateSound;
    @Configurable(path = "messages.", name = "invalid-location")
    protected String invalidLocationMessage;
    @Configurable(path = "messages.", name = "no-permission-use")
    protected String noPermissionUseMessage;
    @Configurable(path = "messages.", name = "no-permission-create")
    protected String noPermissionCreateMessage;
    @Configurable(path = "messages.", name = "invalid-sign")
    protected String invalidSignMessage;
    @Configurable(path = "permissions.", name = "create")
    protected String createPermission;
    @Configurable(path = "permissions.", name = "use")
    protected String usePermission;
    @Configurable(path = "permissions.", name = "reload")
    protected String reloadPermission;
    @Configurable(name = "elevator-line-format")
    protected String elevatorLineFormat;
    @Configurable(name = "up-keyword")
    protected String elevatorUpFormat;
    @Configurable(name = "down-keyword")
    protected String elevatorDownFormat;
    @Configurable(name = "match-updown-lowercase")
    protected boolean matchLowercase;
    @Configurable(name = "floors-enabled")
    protected boolean floorsEnabled;
    @Configurable(name = "max-distance-away")
    protected double maxDistance;
    @Configurable(path = "lives.", name = "enabled")
    protected boolean livesEnabled;
    @Configurable(path = "lives.", name = "amount")
    protected int livesAmount;
    @Configurable(path = "lives.", name = "break-on-empty")
    protected boolean livesBreakOnEmpty;
    @Configurable(path = "lives.", name = "cooldown-seconds")
    protected int livesCooldownSeconds;
    @Configurable(path = "lives.", name = "regenerate-seconds")
    protected int livesRegenerateSeconds;
    @Configurable(path = "messages.", name = "lives-remaining")
    protected String livesRemainingMessage;
    @Configurable(path = "messages.", name = "lives-broken")
    protected String livesBrokenMessage;
    @Configurable(path = "messages.", name = "lives-cooldown")
    protected String livesCooldownMessage;
    
    protected void sendSound(Player player, String sound) {
        if (sound == null || sound.isEmpty()) {
            return;
        }
        String[] split = sound.split(":");
        try {
            player.playSound(player.getLocation(), Sound.valueOf(split[0]), Float.parseFloat(split[1]), Float.parseFloat(split[2]));
        }
        catch (IllegalArgumentException ex) {}
    }
    
    protected boolean checkPermission(Player player, String toCheck) {
        return toCheck == null || toCheck.isEmpty() || player.hasPermission(toCheck);
    }
    
    protected void msg(Player player, String toInform) {
        if (toInform == null || toInform.isEmpty()) {
            return;
        }
        player.sendMessage(Color.c(toInform));
    }

    //Clears the cooldown set every full second.
    private void runTask() {
        new BukkitRunnable() {
            public void run() {
                SignHandler.this.recentTries.clear();
            }
        }.runTaskTimer(this.plugin, 0L, 20L);
    }

    //Regenerates the lives of the elevators that have been hit, once every full second.
    private void runLivesTask() {
        new BukkitRunnable() {
            public void run() {
                SignHandler.this.regenerateElevatorLives();
            }
        }.runTaskTimer(this.plugin, 0L, 20L);
    }
    
    public SignHandler(Plugin plugin) {
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;
        this.runTask();
        this.runLivesTask();
        this.configManager = new ConfigManager(this, plugin);
        this.recentTries = new HashSet<UUID>();
        this.configManager.reloadConfig();
    }

    /**
     * We use this method instead of the isPassable method in newer versions so that we can retain backwards compat.
     * @param block the block to check
     * @return true if the block is passable, false if not.
     */
    protected boolean blockIsPassable(Block block) {
        Material material = block.getType();
        if (material == Material.AIR) {
            return true;
        }
        if (block.isLiquid()) {
            return true;
        }
        if (!material.isBlock()) {
            return true;
        }
        if (material.toString().equals("GRASS_BLOCK") || material.toString().equals("GRASS_PATH")) {
            return false;
        }
        //Works for 1.18 servers.
        if (material.toString().contains("VOID_AIR") || material.toString().contains("GRASS")) {
            return true;
        }
        String name = material.name();
        if (block.getState() instanceof Openable) {
            Openable openable = (Openable)block.getState();
            return openable.isOpen();
        }
        return name.contains("BANNER") || name.contains("SIGN") || name.contains("FLOWER") || name.contains("DOOR");
    }
    
    protected SignInfo fromSign(Location from, UUID uuid, Block block) {
        //Not a sign?
        if (!block.getType().toString().contains("SIGN")) {
            return null;
        }
        //Clicking too fast?
        if (this.recentTries.contains(uuid)) {
            return null;
        }

        Vector signLoc = block.getLocation().add(0.5, 0.5, 0.5).toVector();

        Vector playerLoc = from.toVector();

        double distance = signLoc.distance(playerLoc);

        //Too far away?
        if (distance > this.maxDistance) {
            return null;
        }

        //Add them to the cooldown set
        this.recentTries.add(uuid);
        Sign sign = (Sign)block.getState();
        SignInfo signInfo;
        try {
            //Valid sign?
            signInfo = new SignInfo(sign, this.floorsEnabled);
        }
        catch (IllegalStateException | IllegalArgumentException ex2) {
            return null;
        }
        return signInfo;
    }

    /**
     * Gets the max world height of the world provided by the location.
     * @param location the location to get the world from
     * @return the max world height
     */
    private int getMaxWorldHeight(Location location)
    {
        return location.getWorld().getMaxHeight();
    }

    /**
     * Gets the min world height of the world provided by the location.
     * If we're on or after 1.18, we use the getMinHeight method, if not, we know it's 1.
     * @param location the location of the world we're in.
     * @return the min height.
     */
    private int getMinWorldHeight(Location location)
    {
        return ElevatorSignsRevamped.getRunningVersion().isAtOrAfter(Version.v1_18) ? location.getWorld().getMinHeight() : 1;
    }


    protected Location getLocation(SignInfo signInfo) {
        switch (signInfo.getDirection()) {
            case UP: {
                return this.getUp(signInfo);
            }
            case DOWN: {
                return this.getDown(signInfo);
            }
            default: {
                return null;
            }
        }
    }

    /**
     * Gets the next valid location from a sign's information going up.
     * @param signInfo the sign information.
     * @return the next valid location. (Null if not valid)
     */
    private Location getUp(SignInfo signInfo) {
        Location mainLoc = signInfo.getSignLocation();
        Block lastFloor = null;
        int sinceLastFloor = 0;
        Location toReturn = null;
        for (int z = 1; z <= signInfo.getSpaces(); ++z) {
            toReturn = null;
            for (int y = mainLoc.getBlockY(); y <= getMaxWorldHeight(mainLoc); ++y) {
                Block blockAt = mainLoc.getWorld().getBlockAt(mainLoc.getBlockX(), y, mainLoc.getBlockZ());

                boolean b = this.blockIsPassable(blockAt);
                if (!b) {
                    sinceLastFloor = 0;
                    lastFloor = blockAt;
                }
                else if (lastFloor != null) {
                    if (++sinceLastFloor >= 2) {
                        sinceLastFloor = 0;
                        lastFloor = null;
                        mainLoc = blockAt.getLocation();
                        toReturn = blockAt.getLocation().add(0.5, -1.0, 0.5);
                        break;
                    }
                }
            }
        }
        return toReturn;
    }

    /**
     * Gets the next valid location from a sign's information going down.
     * @param signInfo the sign information.
     * @return the next valid location. (Null if not valid)
     */
    private Location getDown(SignInfo signInfo) {
        Location mainLoc = signInfo.getSignLocation();
        Block lastCeiling = null;
        int sinceLastCeiling = 0;
        Location toReturn = null;
        for (int z = 1; z <= signInfo.getSpaces(); ++z) {
            toReturn = null;
            for (int y = mainLoc.getBlockY(); y > getMinWorldHeight(mainLoc); --y) {
                Block blockAt = mainLoc.getWorld().getBlockAt(mainLoc.getBlockX(), y, mainLoc.getBlockZ());
                boolean b = this.blockIsPassable(blockAt);
                if (!b) {
                    sinceLastCeiling = 0;
                    lastCeiling = blockAt;
                }
                else if (lastCeiling != null) {
                    if (++sinceLastCeiling >= 2 && !this.blockIsPassable(blockAt.getRelative(BlockFace.DOWN))) {
                        sinceLastCeiling = 0;
                        lastCeiling = null;
                        toReturn = blockAt.getLocation().add(0.5, 0.0, 0.5);
                        mainLoc = blockAt.getLocation();
                        break;
                    }
                }
            }
        }
        return toReturn;
    }

    /**
     * Checks if the block provided is an elevator sign.
     * @param block the block to check.
     * @return true if the block is an elevator sign, false if not.
     */
    protected boolean isElevatorSign(Block block) {
        if (block == null || !block.getType().toString().contains("SIGN")) {
            return false;
        }
        Sign sign;
        try {
            sign = (Sign) block.getState();
        }
        catch (ClassCastException ex) {
            return false;
        }
        try {
            new SignInfo(sign, this.floorsEnabled);
            return true;
        }
        catch (IllegalStateException | IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Formats the 'lives-remaining' message, telling the player how many hits are left.
     * @param lives the lives currently left on the elevator.
     * @return the formatted message.
     */
    protected String livesRemainingMessage(int lives) {
        return this.livesRemainingMessage
            .replace("%lives", String.valueOf(lives))
            .replace("%max", String.valueOf(this.getMaxLives()));
    }

    /**
     * Formats the 'lives-cooldown' message, telling the player how long the elevator is out.
     * @param seconds the seconds the elevator is out of commission.
     * @return the formatted message.
     */
    protected String livesCooldownMessage(double seconds) {
        return this.livesCooldownMessage
            .replace("%seconds", String.valueOf((int) Math.ceil(seconds)));
    }

    /**
     * Gets the maximum amount of lives an elevator can have.
     * @return the maximum lives.
     */
    protected int getMaxLives() {
        return Math.max(1, this.livesAmount);
    }

    /**
     * Gets the key we store an elevator's life information under.
     * @param block the sign block.
     * @return the unique key for the sign block's location.
     */
    private String lifeKey(Block block) {
        return block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    /**
     * Gets the life information for the sign, creating it if it doesn't exist yet.
     * @param block the sign block.
     * @return the (never null) life information of the sign.
     */
    private ElevatorLife getOrCreateLife(Block block) {
        return this.elevatorLives.computeIfAbsent(this.lifeKey(block), key -> new ElevatorLife());
    }

    /**
     * Handles a hit on an elevator sign, taking one life off of it.
     * @param player the player that hit the sign.
     * @param block the sign that was hit.
     * @return true if this hit was counted, so the sign should not break normally.
     */
    protected boolean hitElevator(Player player, Block block) {
        if (!this.isElevatorSign(block)) {
            return false;
        }
        ElevatorLife life = this.getOrCreateLife(block);
        long now = System.currentTimeMillis();
        //The elevator is on a cooldown right now, it can't be hit again until it's back.
        if (life.blockedUntil > now) {
            this.msg(player, this.livesCooldownMessage((life.blockedUntil - now) / 1000.0));
            return true;
        }
        --life.lives;
        life.lastHit = now;
        //Still has lives left.
        if (life.lives > 0) {
            this.msg(player, this.livesRemainingMessage(life.lives));
            return true;
        }
        //Out of lives. If we don't break, the elevator goes on a cooldown instead.
        if (!this.livesBreakOnEmpty) {
            life.blockedUntil = now + (this.livesCooldownSeconds * 1000L);
            this.msg(player, this.livesCooldownMessage(this.livesCooldownSeconds));
            return true;
        }
        this.msg(player, this.livesBrokenMessage);
        this.elevatorLives.remove(this.lifeKey(block));
        this.breakElevator(block);
        return true;
    }

    /**
     * Stops an elevator sign from being broken normally while it still has lives left.
     * @param player the player trying to break the sign, null if it wasn't a player.
     * @param block the sign being broken.
     * @return true if the break should be cancelled, false if it should go through.
     */
    protected boolean protectElevator(Player player, Block block) {
        if (!this.isElevatorSign(block)) {
            return false;
        }
        ElevatorLife life = this.elevatorLives.get(this.lifeKey(block));
        int lives = (life == null ? this.getMaxLives() : life.lives);
        //Out of lives, the sign is fair game to break.
        if (lives <= 0) {
            return false;
        }
        if (player != null) {
            this.msg(player, this.livesRemainingMessage(lives));
        }
        return true;
    }

    /**
     * Gets the seconds left on an elevator's cooldown.
     * @param block the sign to check.
     * @return the seconds left, or 0 if the elevator isn't on a cooldown.
     */
    protected double getElevatorCooldownLeft(Block block) {
        ElevatorLife life = this.elevatorLives.get(this.lifeKey(block));
        if (life == null) {
            return 0.0;
        }
        return Math.max(0.0, (life.blockedUntil - System.currentTimeMillis()) / 1000.0);
    }

    /**
     * Breaks the elevator sign and drops it on the ground.
     * @param block the sign to break.
     */
    private void breakElevator(Block block) {
        for (ItemStack drop : block.getDrops()) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
        }
        block.setType(Material.AIR);
    }

    /**
     * Gives lives back to the elevators that have been hit, and brings back the ones on a cooldown.
     * Called once every second.
     */
    private void regenerateElevatorLives() {
        if (!this.livesEnabled || this.elevatorLives.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int maxLives = this.getMaxLives();
        Iterator<Map.Entry<String, ElevatorLife>> iterator = this.elevatorLives.entrySet().iterator();
        while (iterator.hasNext()) {
            ElevatorLife life = iterator.next().getValue();
            //On a cooldown. It gets all of its lives back once the cooldown is over.
            if (life.blockedUntil > 0L) {
                if (now >= life.blockedUntil) {
                    life.blockedUntil = 0L;
                    life.lives = maxLives;
                    life.lastHit = now;
                }
                continue;
            }
            //At full lives, nothing left to track.
            if (life.lives >= maxLives) {
                iterator.remove();
                continue;
            }
            if (now - life.lastHit >= (this.livesRegenerateSeconds * 1000L)) {
                ++life.lives;
                life.lastHit = now;
            }
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        //No permission to reload
        if (!sender.hasPermission(this.reloadPermission)) {
            sender.sendMessage(Color.c("&cYou don't have permission to use this!"));
            return true;
        }
        //Not using a valid command
        if (args.length == 0) {
            return false;
        }
        //Are they trying to reload?
        if (args[0].equalsIgnoreCase("reload")) {
            long timeNow = System.currentTimeMillis();
            this.configManager.reloadConfig();
            long timeThen = System.currentTimeMillis();
            sender.sendMessage(Color.c("&aReloaded the config in &e" + (timeThen - timeNow) + "ms &a!"));
            return true;
        }
        return false;
    }
    
    protected class SignInfo
    {
        private final int spaces;
        private final Direction direction;
        private final Location signLocation;
        
        public SignInfo(Sign sign, boolean floorsEnabled) {
            String line1 = sign.getLine(0);
            if (line1 == null || !line1.equals(Color.c(elevatorLineFormat))) {
                throw new IllegalStateException();
            }
            String line2 = sign.getLine(2);
            this.spaces = ((floorsEnabled && line2 != null && !line2.equals("")) ? Integer.parseInt(line2) : 1);
            this.direction = getDirectionFromString(sign.getLine(1));
            this.signLocation = sign.getLocation();
        }
        
        public Location getSignLocation() {
            return this.signLocation;
        }
        
        public Direction getDirection() {
            return this.direction;
        }
        
        public int getSpaces() {
            return this.spaces;
        }
    }

    protected final Direction getDirectionFromString(String str){
        if(str.equals(Color.c(elevatorUpFormat)))
            return Direction.UP;
        else if(str.equals(Color.c(elevatorDownFormat)))
            return Direction.DOWN;
        else
            throw new IllegalArgumentException();
    }
    
    protected enum Direction
    {
        UP, 
        DOWN;
    }

    /**
     * Holds the lives of a single elevator sign. This is only kept in memory, so every elevator
     * goes back to full lives when the server restarts.
     */
    protected class ElevatorLife
    {
        private int lives;
        private long lastHit;
        private long blockedUntil;

        public ElevatorLife() {
            this.lives = getMaxLives();
        }
    }
}
 
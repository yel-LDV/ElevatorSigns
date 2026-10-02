package com.golfing8.elevatorsigns.signhandler;

import com.golfing8.elevatorsigns.Color;
import com.golfing8.elevatorsigns.ElevatorSignsRevamped;
import com.golfing8.elevatorsigns.Version;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

public class HandlerV1_7_V1_18 extends SignHandler
{
    public HandlerV1_7_V1_18(Plugin plugin) {
        super(plugin);
    }
    
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        //Not clicking a block?
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (e.getClickedBlock() == null) {
            return;
        }
        Block clickedBlock = e.getClickedBlock();
        SignInfo signInfo = this.fromSign(player.getEyeLocation(), player.getUniqueId(), clickedBlock);
        if (signInfo == null) {
            return;
        }

        if (!this.checkPermission(player, this.usePermission)) {
            this.msg(e.getPlayer(), this.noPermissionUseMessage);
            return;
        }

        //The elevator is out of lives and on a cooldown, it can't be used for now.
        if (this.livesEnabled) {
            double cooldownLeft = this.getElevatorCooldownLeft(clickedBlock);
            if (cooldownLeft > 0.0) {
                this.msg(player, this.livesCooldownMessage(cooldownLeft));
                return;
            }
        }

        Location toTeleportTo = this.getLocation(signInfo);
        if (toTeleportTo == null) {
            this.msg(player, this.invalidLocationMessage);
            return;
        }

        toTeleportTo.setYaw(player.getLocation().getYaw());
        toTeleportTo.setPitch(player.getLocation().getPitch());
        player.teleport(toTeleportTo);
        this.msg(player, this.useMessage);
        this.sendSound(player, this.onUseSound);
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent e) {
        //Each hit takes one life off of the elevator. As long as it has lives, it can't break normally.
        if (this.livesEnabled && this.hitElevator(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        //Only stop players from breaking an elevator that still has lives. Explosions and the like go through.
        if (this.livesEnabled && e.getPlayer() != null && this.protectElevator(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        if (e.getLine(0) == null) {
            return;
        }
        if (!e.getLine(0).equalsIgnoreCase("[Elevator]")) {
            return;
        }
        if (!this.checkPermission(e.getPlayer(), this.createPermission)) {
            this.msg(e.getPlayer(), this.noPermissionCreateMessage);
            e.setLine(0, ChatColor.RED + "[ERROR]");
            return;
        }
        if (e.getLine(1) == null) {
            e.setLine(0, ChatColor.RED + "[ERROR]");
            this.msg(e.getPlayer(), this.invalidSignMessage);
            return;
        }
        //
        String compare = e.getLine(1);

        String upFormatFormatted = ChatColor.stripColor(Color.c(elevatorUpFormat));
        String downFormatFormatted = ChatColor.stripColor(Color.c(elevatorDownFormat));

        if(matchLowercase ? upFormatFormatted.equalsIgnoreCase(compare) : upFormatFormatted.equals(compare)){
            e.setLine(1, Color.c(elevatorUpFormat));
        }else if(matchLowercase ? downFormatFormatted.equalsIgnoreCase(compare) : downFormatFormatted.equals(compare)){
            e.setLine(1, Color.c(elevatorDownFormat));
        }else{
            e.setLine(0, ChatColor.RED + "[ERROR]");
            this.msg(e.getPlayer(), this.invalidSignMessage);
            return;
        }

        e.setLine(0, Color.c(this.elevatorLineFormat));
        if (!this.floorsEnabled || e.getLine(2) == null || e.getLine(2).isEmpty()) {
            this.sendSound(e.getPlayer(), this.onCreateSound);
            this.msg(e.getPlayer(), this.createMessage);
            if (ElevatorSignsRevamped.getRunningVersion().isAtOrAfter(Version.v1_20))
                ((Sign) e.getBlock().getState()).setWaxed(true);
            return;
        }
        try {
            int a = Integer.parseInt(e.getLine(2));
            if (a <= 0) {
                throw new NumberFormatException();
            }
            this.sendSound(e.getPlayer(), this.onCreateSound);
            this.msg(e.getPlayer(), this.createMessage);
            if (ElevatorSignsRevamped.getRunningVersion().isAtOrAfter(Version.v1_20))
                ((Sign) e.getBlock().getState()).setWaxed(true);
        }
        catch (NumberFormatException ignored) {
            e.setLine(0, ChatColor.RED + "[ERROR]");
            this.msg(e.getPlayer(), this.invalidSignMessage);
        }
    }
}
 
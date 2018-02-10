/*
 * Copyright (C) 2017 The MoonLake Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package com.minecraft.moonlake.newchesttitle;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.Set;
import java.util.logging.Level;

public class NewChestTitle extends JavaPlugin implements Listener {

    private File dataDir;

    public NewChestTitle() {
    }

    @Override
    public void onEnable() {
        this.initFolder();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getLogger().info("新的箱子标题 NewChestTitle 插件 v" + getDescription().getVersion() + " 成功加载.");
    }

    @Override
    public void onDisable() {
        // 释放变量以及其他操作
        this.dataDir = null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        // 处理物品栏打开事件
        Inventory oldChest = event.getInventory(); // 获取箱子物品栏
        if(oldChest.getType() != InventoryType.CHEST) return; // 不是箱子类型则返回
        InventoryHolder oldChestHolder = oldChest.getHolder(); // 获取此物品栏的拥有者
        if(oldChestHolder == null || !(oldChestHolder instanceof Chest)) return; // 如果拥有者为 null 或者不是箱子方块则返回
        // 否则获取该物品栏的箱子方块对象
        Chest chest = (Chest) oldChestHolder;
        String title = getChestTitle(chest); // 获取该箱子的标题
        if(title == null || title.isEmpty()) return; // 为 null 或为空说明没有, 或 IO 读取异常则返回
        // 否则创建新的物品栏并阻止事件最后打开
        event.setCancelled(true);
        Inventory newChest = Bukkit.getServer().createInventory(null, oldChest.getSize(), toColor(title));
        newChest.setContents(oldChest.getContents()); // 复制旧箱子的内容到此物品栏
        event.getPlayer().openInventory(newChest); // 最后打开给实体玩家
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 处理命令
        if(args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("help"))) {
            // 提示帮助
            sender.sendMessage(new String[] {
                    toColor("&b&l&m          &d NewChestTitle &7By &6Month_Light &b&l&m          "),
                    toColor("&6/newchesttitle help &7- 查看插件命令帮助."),
                    toColor("&6/newchesttitle del &7- 删除准星箱子方块的标题数据."),
                    toColor("&6/newchesttitle delall &7- 删除所有的箱子方块标题数据."),
                    toColor("&6/newchesttitle add &8<&atitle&8> &7- 添加准星箱子方块的标题数据."),
                    toColor("&6如标题需要空格请使用 &b^ &6符号代替, 例如: &bNew^Chest^Title")
            });
            return true;
        }
        if(!sender.hasPermission("moonlake.newchesttitle." + args[0])) {
            // 没有权限则发送消息并返回
            sender.sendMessage(getMessage("&c你没有使用这个命令的权限."));
            return true;
        }
        if(args.length == 1) {
            if(args[0].equalsIgnoreCase("del")) {
                // 删除准星箱子方块
                if(checkPlayer(sender)) return true; // 检查是否为玩家
                delChestTitle((Player) sender); // 然后进行删除
            } else if(args[0].equalsIgnoreCase("delall")) {
                // 删除所有箱子方块
                if(!delAllChestTitle()) sender.sendMessage(getMessage("&c错误: 删除箱子标题数据文件时未成功."));
                else sender.sendMessage(getMessage("&a成功删除所有的箱子标题数据."));
            }
        } else if(args.length == 2) {
            if(args[0].equalsIgnoreCase("add")) {
                // 添加准星箱子方块
                if(checkPlayer(sender)) return true; // 检查是否为玩家
                setChestTitle((Player) sender, args[1].replace("^", " "));
            }
        }
        return true;
    }

    private boolean checkPlayer(CommandSender sender) {
        // 检查是否为玩家
        if(!(sender instanceof Player)) {
            sender.sendMessage(getMessage("&c控制台不能使用这个命令参数."));
            return true;
        }
        return false;
    }

    private boolean delAllChestTitle() {
        // 删除所有的箱子方块标题数据
        File[] listFile = dataDir.listFiles();
        if(listFile != null && listFile.length > 0)
            for(File chestFile : listFile)
                if(chestFile.exists() && !chestFile.delete())
                    return false;

        return true;
    }

    private void delChestTitle(Player player) {
        // 删除玩家准星的箱子方块标题
        Block block = player.getTargetBlock((Set<Material>) null, 5);
        if(block == null || block.getType() == Material.AIR || !(block.getState() instanceof Chest)) {
            // 玩家准星的方块不为箱子
            player.sendMessage(getMessage("&c错误: 准星位置的方块不是箱子!!!"));
            return;
        }
        // 否则为箱子则删除箱子标题
        File chestFile = getChestDataFile((Chest) block.getState());
        boolean result = !chestFile.exists() || chestFile.delete();
        if(result) player.sendMessage(getMessage("&a成功删除准星箱子方块的标题数据!"));
        else player.sendMessage(getMessage("&c错误: 删除准星箱子方块时错误."));
    }

    private void setChestTitle(Player player, String title) {
        // 设置玩家准星的箱子方块标题
        Block block = player.getTargetBlock((Set<Material>) null, 5);
        if(block == null || block.getType() == Material.AIR || !(block.getState() instanceof Chest)) {
            // 玩家准星的方块不为箱子
            player.sendMessage(getMessage("&c错误: 准星位置的方块不是箱子!!!"));
            return;
        }
        // 否则为箱子则设置箱子标题
        if(setChestTitle((Chest) block.getState(), title)) player.sendMessage(getMessage("&a成功设置准星箱子方块的自定义标题: &r" + title));
        else player.sendMessage(getMessage("&c错误: 设置准星箱子方块时 IO 流错误, 详情查看控制台."));
    }

    private boolean setChestTitle(Chest chest, String title) {
        // 设置箱子标题保存到配置文件
        File chestFile = getChestDataFile(chest);
        BufferedWriter bw = null;

        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(chestFile), "utf-8"));
            bw.write(title);
            bw.newLine();
            bw.flush();
            bw.close();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "写出 '" + chestFile.getName() + "' 箱子标题数据时错误, 异常信息:", e);
        } finally {
            if(bw != null) try {
                bw.close();
            } catch (Exception e) {
            }
        }
        return false;
    }

    private String getChestTitle(Chest chest) {
        // 从配置文件读取指定方块箱子的自定义标题
        File chestFile = getChestDataFile(chest);
        if(!chestFile.exists()) return null; // 数据文件不存在则返回 null
        // 否则进行读取数据文件的自定义标题
        BufferedReader br = null;
        String title = null;

        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(chestFile), "utf-8"));
            title = br.readLine();
            br.close();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "读取 '" + chestFile.getName() + "' 箱子标题数据时错误, 异常信息:", e);
        } finally {
            if(br != null) try {
                br.close();
            } catch (Exception e) {
            }
        }
        return title;
    }

    private File getChestDataFile(Chest chest) {
        // 获取指定箱子的数据文件
        Location location = chest.getLocation();
        String fileName = chest.getWorld().getName() + "_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ() + ".dat";
        return new File(dataDir, File.separator + fileName);
    }

    private String getMessage(String message) {
        // 获取插件前缀消息
        return toColor("&f[&aNCT&f] " + message);
    }

    private String toColor(String text) {
        // 颜色转换
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void initFolder() {
        if(!getDataFolder().exists())
            getDataFolder().mkdirs();
        this.dataDir = new File(getDataFolder(), File.separator + "chests");
        if(!this.dataDir.exists())
            this.dataDir.mkdirs();
    }
}

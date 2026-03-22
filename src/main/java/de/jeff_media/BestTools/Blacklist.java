package de.jeff_media.BestTools;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class Blacklist {

    public List<Material> mats;

    public Blacklist(List<String> strings) {
        mats = new ArrayList<>();
        for(String s : strings) {
            Material mat = Material.getMaterial(s);
            if(mat!=null) mats.add(mat);
        }
    }

    public Blacklist() {
        mats = new ArrayList<>();
    }

    public void add(String string) {
        Material mat = Material.getMaterial(string);
        if(mat!=null && !mats.contains(mat)) mats.add(mat);
    }

    public void add(Material mat) {
        if(!mats.contains(mat)) mats.add(mat);
    }

    public boolean contains(Material mat) {
        return mats.contains(mat);
    }

    public void remove(Material mat) {
        if(mats.contains(mat)) mats.remove(mat);
    }

    List<String> toStringList() {
        ArrayList<String> list = new ArrayList<>();

        for(Material mat : mats) {
            list.add(mat.name());
        }
        return list;
    }

    void print(Player p,Main main) {

        if(mats.size()==0) {
            Messages.sendMessage(p,main.messages.BL_EMPTY);
            return;
        }

        p.sendMessage(ChatColor.translateAlternateColorCodes('&',main.getConfig().getString("blacklist-title")));

        /*p.sendMessage("This list will be nicer in the next version :P");
        p.sendMessage("Blacklist: ");
        StringBuilder slist = new StringBuilder();
        */
        for(Material mat : mats) {
            TextComponent text = new TextComponent("");
            TextComponent link = createLink("[X] ","/besttools blacklist remove "+mat.name());
            TextComponent name = new TextComponent(mat.name());
            name.setColor(net.md_5.bungee.api.ChatColor.GRAY);
            text.addExtra(link);
            text.addExtra(name);
            p.spigot().sendMessage(text);
        }
    }

    private TextComponent createLink(String text, String link) {
        TextComponent tc = new TextComponent(text);
        tc.setBold(true);
        // TODO: Make color configurable
        tc.setColor(net.md_5.bungee.api.ChatColor.DARK_RED);
        tc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, link));
        return tc;
    }

}

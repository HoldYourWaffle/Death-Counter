package me.ichun.mods.deathcounter.common.core;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Level;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

public class DeathHandler {
	public enum MESSAGE_TYPE { NONE, SHORT, LONG }
	
	public static File saveDir;
	
	public static HashMap<String, Integer> deathCounter = new HashMap<>();
	public static ArrayList<String> ranking = new ArrayList<>();
	
	public static MESSAGE_TYPE messageType;
	public static int leaderboardCount;
	public static boolean save;
	
	
	public DeathHandler(Configuration config) {
		switch(config.getInt("message", "deathcounter", 2, 0, 2, "Death Count Messages?\n0 = Disable\n1 = Short message\n2 = Long message")) {
			case 1:
				messageType = MESSAGE_TYPE.SHORT;
				break;
			case 2:
			default:
				messageType = MESSAGE_TYPE.LONG;
				break;
			case 0:
				messageType = MESSAGE_TYPE.NONE;
				break;
		}
		
		leaderboardCount = config.getInt("leaderboardCount", "deathcounter", 5, 0, 20, "Number of names to show in the leaderboards");
		save = config.getBoolean("singleSession", "deatchcounter", true, "Save deaths in the save folder? If false, your leaderboard is reset everytime deathcounter reloads");
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onDeath(LivingDeathEvent event) {
		if (event.getEntityLiving() instanceof EntityPlayer
				&& FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER
				&& FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUsername(event.getEntityLiving().getName()) != null) {
			EntityPlayer player = (EntityPlayer) event.getEntityLiving();
			addDeath(player);
			
			switch (messageType) {
				case LONG:
					player.sendStatusMessage(new TextComponentTranslation("dc.message.death", getDeathCount(player.getName())), false);
					player.sendStatusMessage(new TextComponentTranslation("dc.message.rank", getDisplayedRank(player.getName())), false);
					break;
				case SHORT:
					player.sendStatusMessage(new TextComponentTranslation("dc.message.deathAndRank", getDeathCount(player.getName()), getDisplayedRank(player.getName())), false);
					break;
				case NONE: break;
				default: throw new RuntimeException("Mod creator forgot to add a case for '"+messageType+"' in his switch");
			}
		}
	}
	
	@SubscribeEvent
	public void onChatEvent(ServerChatEvent event) {
		if (event.getMessage().toLowerCase().toLowerCase().startsWith("!dc") || event.getMessage().toLowerCase().toLowerCase().startsWith("!deathcounter")) {
			CommandDeathCounter.broadcastLeaderboard(event.getUsername());
			event.setCanceled(true);
		}
	}
	
	public void loadDeaths(WorldServer world) {
		File dir = new File(world.getChunkSaveLocation(), "deathCounter");
		if (!dir.exists()) dir.mkdirs();
		
		saveDir = dir;
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.getName().endsWith(".dat")) {
				String user = file.getName().substring(0, file.getName().length() - 4);
				try {
					NBTTagCompound tag = CompressedStreamTools.readCompressed(new FileInputStream(file));
					deathCounter.put(user, tag.getInteger("deaths"));
				} catch (EOFException e) {
					DeathCounter.console("File for " + user + " is corrupted. Flushing.", Level.WARN);
				} catch (IOException e) {
					DeathCounter.console("Failed to read file for " + user + ". Flushing.", Level.WARN);
				}
			}
		}
	}
	
	public void sortRanking() {
		for (Map.Entry<String, Integer> e : deathCounter.entrySet()) {
			ranking.remove(e.getKey());
			if (e.getValue() > 0) {
				for (int i = 0; i < ranking.size(); i++) {
					if (getDeathCount(ranking.get(i)) <= e.getValue()) {
						ranking.add(i, e.getKey());
						break;
					}
				}
				if (!ranking.contains(e.getKey())) ranking.add(e.getKey());
			}
		}
		
		if (save) {
			Properties s = new Properties();
			File text = new File(saveDir, "deaths.txt");
			
			if (text == null || text.isDirectory()) return;
			
			for (Map.Entry<String, Integer> e : deathCounter.entrySet()) {
				s.setProperty(e.getKey(), Integer.toString(e.getValue()));
			}
			
			try (FileOutputStream fos = new FileOutputStream(text)) {
				if (!text.exists()) text.createNewFile();
				s.store(fos, null);
			} catch (IOException e) {
				DeathCounter.console("Error writing deaths.txt", Level.WARN);
			}
		}
	}
	
	public void addDeath(EntityPlayer player) {
		File file = new File(saveDir, player.getName() + ".dat");
		NBTTagCompound tag = new NBTTagCompound();

		int deaths = getDeathCount(player.getName()) + 1;
		tag.setInteger("deaths", deaths);
		
		deathCounter.put(player.getName(), deaths);
		sortRanking();
		
		try (FileOutputStream fos = new FileOutputStream(file)) {
			CompressedStreamTools.writeCompressed(tag, fos);
		} catch (IOException ioexception) {
			DeathCounter.console("Failed to save death count for " + player.getName(), Level.WARN);
		}
		
	}
	
	public boolean clearDeath(String s) {
		if (s == null) {
			File[] files = saveDir.listFiles();
			for (File file : files) {
				file.delete();
			}
			deathCounter.clear();
			ranking.clear();
			return true;
		} else {
			File file = new File(saveDir, s + ".dat");
			if (file.exists()) {
				file.delete();
				deathCounter.remove(s);
				sortRanking();
				return true;
			} else return false;
		}
	}
	
	public int getDeathCount(String s) {
		try {
			return deathCounter.get(s);
		} catch (NullPointerException e) {
			return 0;
		}
	}
	
	public int getDisplayedRank(String s) {
		if (ranking.contains(s)) {
			for (int i = 0; i < ranking.size(); i++) {
				if (ranking.get(i).equals(s)) {
					int rank = i;
					int deaths = getDeathCount(s);
					while (i > 0 && getDeathCount(ranking.get(--i)) == deaths) rank--;
					return rank + 1;
				}
			}
		}
		return ranking.size() + 1;
	}

	public void reset() {
		deathCounter.clear();
		ranking.clear();
		
		loadDeaths(DimensionManager.getWorld(0));
		sortRanking();
	}
}

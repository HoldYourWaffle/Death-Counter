package me.ichun.mods.deathcounter.common.core;

import org.apache.logging.log4j.Level;

import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommandManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = "deathcounter", name = "DeathCounter", version = DeathCounter.version, acceptableRemoteVersions = "*", dependencies = "required-after:forge@[13.19.0.2141,)", acceptedMinecraftVersions = "[1.12,1.13)")
public class DeathCounter {
	
	public static final String version = "1.0.0";
	
	@Instance("deathcounter")
	public static DeathCounter instance;
	public static DeathHandler handler;
	
	public enum MESSAGE_TYPE { NONE, SHORT, LONG }
	
	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		
		Configuration config = new Configuration(event.getSuggestedConfigurationFile());
		config.load();

		handler = new DeathHandler(config);
		
		if (config.hasChanged()) config.save();
		MinecraftForge.EVENT_BUS.register(handler);
	}
	
	@EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		ICommandManager manager = event.getServer().getCommandManager();
		if (manager instanceof CommandHandler) ((CommandHandler) manager).registerCommand(new CommandDeathCounter());
	}
	
	@EventHandler
	public void serverStarted(FMLServerStartedEvent event) {
		handler.reset();
	}
	
	@SuppressWarnings("deprecation")
	public static void console(String s, Level logLevel) { 
		FMLLog.log("DeathCounter", logLevel, "%s", s);
	}
}

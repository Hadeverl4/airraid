package ace.actually.airraid;

import ace.actually.airraid.config.IAAddonConfig;
import ace.actually.airraid.util.WorldTickHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class AirRaidMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // Load Config
        IAAddonConfig.load();

        // Register Command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AirRaidCommand.register(dispatcher);
        });

        // Register Tick Handler for Natural Spawning
        ServerTickEvents.END_SERVER_TICK.register(new WorldTickHandler());
    }
}
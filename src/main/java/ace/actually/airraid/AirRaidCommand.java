package ace.actually.airraid;

import ace.actually.airraid.config.IAAddonConfig;
import ace.actually.airraid.util.RaidSpawner;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class AirRaidCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("airraid")
                .requires(source -> source.hasPermissionLevel(2))

                // --- RELOAD COMMAND ---
                .then(CommandManager.literal("reload")
                        .executes(ctx -> {
                            IAAddonConfig.load();
                            ctx.getSource().sendFeedback(() -> Text.literal("IA Pillager Addon config reloaded!"), true);
                            return 1;
                        })
                )

                // --- SPAWN COMMAND ---
                .then(CommandManager.literal("spawn")
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 20))
                                .executes(ctx -> executeSpawn(ctx, IntegerArgumentType.getInteger(ctx, "count"), null, null))
                                .then(CommandManager.argument("vehicle", IdentifierArgumentType.identifier()).suggests(SUGGEST_AIRCRAFT)
                                        .executes(ctx -> executeSpawn(ctx, IntegerArgumentType.getInteger(ctx, "count"), IdentifierArgumentType.getIdentifier(ctx, "vehicle"), null))
                                        .then(CommandManager.argument("weapon", IdentifierArgumentType.identifier()).suggests(SUGGEST_WEAPON)
                                                .executes(ctx -> executeSpawn(ctx, IntegerArgumentType.getInteger(ctx, "count"), IdentifierArgumentType.getIdentifier(ctx, "vehicle"), IdentifierArgumentType.getIdentifier(ctx, "weapon")))
                                        )
                                )
                        )
                )
        );
    }

    private static int executeSpawn(CommandContext<ServerCommandSource> context, int count, Identifier vehicleId, Identifier weaponId) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();

        // Create a temporary "Manual" faction config for this command
        IAAddonConfig.Faction manualFaction = new IAAddonConfig.Faction();
        manualFaction.minSquadSize = count;
        manualFaction.maxSquadSize = count;

        // Override vehicle weights if specified
        if (vehicleId != null) {
            manualFaction.vehicles.clear();
            manualFaction.vehicles.put(vehicleId.toString(), 100);
        }

        // Override weapon booleans based on specific input
        if (weaponId != null) {
            manualFaction.enableBombBay = weaponId.toString().contains("bomb");
            manualFaction.enableRotaryCannons = weaponId.toString().contains("rotary");
            manualFaction.enableCrossbows = weaponId.toString().contains("bow");
        }

        float yaw = source.getWorld().random.nextFloat() * 360.0f;

        // Use the utility class
        RaidSpawner.spawnSquadronAt(
                source.getWorld(),
                source.getPosition().x,
                source.getPosition().y + 50,
                source.getPosition().z,
                yaw,
                manualFaction
        );

        return count;
    }

    public static final SuggestionProvider<ServerCommandSource> SUGGEST_AIRCRAFT = (context, builder) -> {
        return CommandSource.suggestIdentifiers(
                Registries.ENTITY_TYPE.getIds().stream()
                        .filter(id -> id.getNamespace().equals("immersive_aircraft") && !id.getPath().contains("bullet") && !id.getPath().contains("tnt")),
                builder
        );
    };

    public static final SuggestionProvider<ServerCommandSource> SUGGEST_WEAPON = (context, builder) -> {
        return CommandSource.suggestIdentifiers(
                Registries.ITEM.getIds().stream()
                        .filter(id -> id.getNamespace().equals("immersive_aircraft") &&
                                (id.getPath().contains("cannon") || id.getPath().contains("bow") || id.getPath().contains("bomb"))),
                builder
        );
    };
}
package ch.skyfy.singlebackpack;

import ch.skyfy.singlebackpack.client.screen.BackpackScreenHandler;
import ch.skyfy.singlebackpack.feature.PlayerTimeMeter;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SingleBackpack implements ModInitializer {

    public static final String MODID = "single_backpack";

    public static final Logger LOGGER = LogManager.getLogger("SingleBackpack");

    public static final Item BACKPACK = new BackpackItem(new Item.Settings().group(ItemGroup.MISC).maxCount(1));//creates your backpack

    public static final ScreenHandlerType<BackpackScreenHandler> BACKPACK_SCREEN_HANDLER;

    static {
        BACKPACK_SCREEN_HANDLER = ScreenHandlerRegistry.registerSimple(new Identifier(MODID, "backpack_screen"), BackpackScreenHandler::new); //registers your screen handler
    }

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            if (Configurator.initialize()) return;
            PlayerTimeMeter.initialize();
            registerEvents();
        }
        BackpacksManager.initialize();
        registerItem();
    }

    private void registerItem() {
        Registry.register(Registry.ITEM, new Identifier(MODID, "backpack"), BACKPACK);
    }

    private void registerEvents() {
        ServerEntityEvents.ENTITY_LOAD.register(this::givePlayerBackpack);
        // TODO If player right click -> open backpack, but stop placing the block in the off hand
    }

    @SuppressWarnings("ConstantConditions")
    private void givePlayerBackpack(Entity entity, ServerWorld world) {
        if (!Configurator.getInstance().config.givePlayerBackpack) return;
        if (entity instanceof ServerPlayerEntity player) {
            var hasBackpack = false;
            for (var slot = 0; slot < player.getInventory().size(); slot++)
                if (player.getInventory().getStack(slot).getTranslationKey().equalsIgnoreCase("item.single_backpack.backpack"))
                    hasBackpack = true;
            if (!hasBackpack) {
                player.dropItem(new ItemStack(BACKPACK), false);
                player.sendMessage(Text.of("Your backpack has been dropped, be sure to get it back"), false);
            }
        }
    }

    public static JsonElement createBackpackRecipe() {
        return createShapedRecipeJson(
                Lists.newArrayList('L', 'P', 'S', 'C', 'W'),
                Lists.newArrayList(new Identifier("leather"), new Identifier("lead"), new Identifier("string"), new Identifier("chest"), new Identifier("wool")),
                Lists.newArrayList("item", "item", "item", "item", "tag"),
                Lists.newArrayList(
                        "LPL",
                        "SCS",
                        "WWW"
                ),
                new Identifier("single_backpack:backpack")
        );
    }

    // see https://fabricmc.net/wiki/tutorial:dynamic_recipe_generation
    private static JsonObject createShapedRecipeJson(ArrayList<Character> keys, ArrayList<Identifier> items, ArrayList<String> type, ArrayList<String> pattern, Identifier output) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");

        JsonArray jsonArray = new JsonArray();
        jsonArray.add(pattern.get(0));
        jsonArray.add(pattern.get(1));
        jsonArray.add(pattern.get(2));
        json.add("pattern", jsonArray);

        JsonObject individualKey;
        JsonObject keyList = new JsonObject();

        for (int i = 0; i < keys.size(); ++i) {
            individualKey = new JsonObject();
            individualKey.addProperty(type.get(i), items.get(i).toString()); //This will create a key in the form "type": "input", where type is either "item" or "tag", and input is our input item.
            keyList.add(keys.get(i) + "", individualKey); //Then we add this key to the main key object.
        }
        json.add("key", keyList);
        JsonObject result = new JsonObject();
        result.addProperty("item", output.toString());
        result.addProperty("count", 1);
        json.add("result", result);
        return json;
    }

}

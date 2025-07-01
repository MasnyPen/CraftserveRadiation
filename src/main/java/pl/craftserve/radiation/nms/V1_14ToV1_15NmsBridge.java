/*
 * Copyright 2019 Aleksander Jagiełło <themolkapl@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.craftserve.radiation.nms;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import pl.craftserve.radiation.LugolsIodinePotion;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class V1_14ToV1_15NmsBridge implements RadiationNmsBridge {
    static final Logger logger = Logger.getLogger(V1_14ToV1_15NmsBridge.class.getName());

    private final Class<?> itemClass;
    private final Class<?> iRegistryClass;
    private final Class<?> mobEffectClass;
    private final Class<?> potionRegistryClass;
    private final Class<?> potionBrewerClass;

    private final Method getItem;
    private final Method newMinecraftKey;
    private final Method getPotion;

    private final Object potionRegistry;

    public V1_14ToV1_15NmsBridge(String version) {
        Objects.requireNonNull(version, "version");

        try {
            this.itemClass = this.getNmsClass("Item", version);
            this.iRegistryClass = this.getNmsClass("IRegistry", version);
            this.mobEffectClass = this.getNmsClass("MobEffect", version);
            this.potionRegistryClass = this.getNmsClass("PotionRegistry", version);
            this.potionBrewerClass = this.getNmsClass("PotionBrewer", version);

            Class<?> craftMagicNumbers = this.getObcClass("util.CraftMagicNumbers", version);
            this.getItem = craftMagicNumbers.getMethod("getItem", Material.class);

            Class<?> iRegistry = this.getNmsClass("IRegistry", version);
            Class<?> minecraftKey = this.getNmsClass("MinecraftKey", version);
            this.newMinecraftKey = minecraftKey.getMethod("a", String.class);
            this.potionRegistry = iRegistry.getDeclaredField("POTION").get(null);
            this.getPotion = this.potionRegistry.getClass().getMethod("get", minecraftKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize 1.14-1.15 bridge", e);
        }
    }

    private Class<?> getNmsClass(String clazz, String version) throws ClassNotFoundException {
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(version, "version");

        return Class.forName(MessageFormat.format("net.minecraft.server.{1}.{0}", clazz, version));
    }

    private Class<?> getObcClass(String clazz, String version) throws ClassNotFoundException {
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(version, "version");

        return Class.forName(MessageFormat.format("org.bukkit.craftbukkit.{1}.{0}", clazz, version));
    }

    @Override
    public void registerLugolsIodinePotion(NamespacedKey potionKey, LugolsIodinePotion.Config.Recipe config) {
        Objects.requireNonNull(potionKey, "potionKey");
        Objects.requireNonNull(config, "config");

        try {
            String basePotionName = config.basePotion().name().toLowerCase(Locale.ROOT);
            Object basePotion = this.getPotion.invoke(this.potionRegistry, this.newMinecraftKey.invoke(null, basePotionName));
            Objects.requireNonNull(basePotion, "basePotion not found");

            Object ingredient = this.getItem.invoke(null, config.ingredient());
            Objects.requireNonNull(ingredient, "ingredient not found");

            Object registryType = this.iRegistryClass.getDeclaredField("POTION").get(null);
            Object mobEffectArray = Array.newInstance(this.mobEffectClass, 0);
            Object newPotion = this.potionRegistryClass.getConstructor(mobEffectArray.getClass()).newInstance(mobEffectArray);

            Method registerMethod = this.iRegistryClass.getDeclaredMethod("a", this.iRegistryClass, String.class, Object.class);
            Object potion = registerMethod.invoke(null, registryType, potionKey.getKey(), newPotion);

            Method registerBrewingRecipe = this.potionBrewerClass.getDeclaredMethod("a", this.potionRegistryClass, this.itemClass, this.potionRegistryClass);
            registerBrewingRecipe.setAccessible(true);
            registerBrewingRecipe.invoke(null, basePotion, ingredient, potion);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not handle reflective operation.", e);
        }
    }


    @Override
    public int getMinWorldHeight(World bukkitWorld) {
        return 0;
    }
}

package vg.civcraft.mc.civmodcore.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextComponent;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bedrock players can't render inline sprites or heads, so they get the message without them
 */
public final class Bedrock {

    private Bedrock() {
    }

    public static boolean isBedrock(@NotNull final Audience audience) {
        final UUID uuid = audience.get(Identity.UUID).orElse(null);
        if (uuid == null || !Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            return false;
        }
        return Floodgate.isFloodgatePlayer(uuid);
    }

    /** The message as this viewer should see it. */
    @NotNull
    public static Component adapt(@NotNull final Audience viewer, @NotNull final Component message) {
        return isBedrock(viewer) ? strip(message) : message;
    }

    /** The message with every sprite and head removed. */
    @NotNull
    public static Component strip(@NotNull final Component message) {
        final Component stripped = stripOrNull(message);
        return stripped == null ? Component.empty() : stripped;
    }

    /** A copy of the item with the sprites and heads removed from its names and lore, or the item itself if it had none. */
    @Nullable
    public static ItemStack strip(@Nullable final ItemStack item) {
        if (item == null || item.isEmpty()) {
            return item;
        }
        ItemStack result = item;
        for (final DataComponentType.Valued<Component> type : List.of(DataComponentTypes.CUSTOM_NAME, DataComponentTypes.ITEM_NAME)) {
            final Component name = result.getData(type);
            if (name != null) {
                final Component stripped = strip(name);
                if (stripped != name) {
                    result = result == item ? item.clone() : result;
                    result.setData(type, stripped);
                }
            }
        }
        final ItemLore lore = result.getData(DataComponentTypes.LORE);
        if (lore != null) {
            final List<Component> lines = lore.lines().stream().map(Bedrock::strip).toList();
            if (!lines.equals(lore.lines())) {
                result = result == item ? item.clone() : result;
                result.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
            }
        }
        return result;
    }

    /** Null when the component was a sprite with nothing left to show. */
    @Nullable
    private static Component stripOrNull(@NotNull final Component component) {
        if (component instanceof final ObjectComponent object) {
            final List<Component> children = stripChildren(object.children());

            final Component fallback = object.fallback();
            final boolean nothingLeft = (fallback == null || isBlank(fallback)) && children.stream().allMatch(Bedrock::isBlank);
            if (nothingLeft) {
                return null;
            }
            final TextComponent.Builder builder = Component.text().style(object.style());
            if (fallback != null) {
                builder.append(fallback);
            }
            return builder.append(children).build();
        }
        final List<Component> children = stripChildren(component.children());
        return children == component.children() ? component : component.children(children);
    }

    /** Returns the same list when nothing changed, so untouched subtrees aren't rebuilt. */
    @NotNull
    private static List<Component> stripChildren(@NotNull final List<Component> children) {
        if (children.isEmpty()) {
            return children;
        }
        final List<Component> result = new ArrayList<>(children.size());
        boolean changed = false;
        boolean dropNextSpace = false;
        for (final Component child : children) {
            if (dropNextSpace) {
                dropNextSpace = false;
                // The space that separated a dropped sprite from the word after it
                if (isSpace(child)) {
                    changed = true;
                    continue;
                }
            }
            final Component stripped = stripOrNull(child);
            if (stripped == null) {
                changed = true;
                dropNextSpace = true;
                continue;
            }
            changed |= stripped != child;
            result.add(stripped);
        }
        return changed ? result : children;
    }

    private static boolean isSpace(@NotNull final Component component) {
        return component instanceof final TextComponent text
            && text.content().equals(" ")
            && text.children().isEmpty();
    }

    private static boolean isBlank(@NotNull final Component component) {
        return component instanceof final TextComponent text
            && text.content().isBlank()
            && text.children().stream().allMatch(Bedrock::isBlank);
    }

    /** Kept apart so the Floodgate classes only load when Floodgate is installed. */
    private static final class Floodgate {

        private static boolean isFloodgatePlayer(@NotNull final UUID uuid) {
            return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        }
    }
}

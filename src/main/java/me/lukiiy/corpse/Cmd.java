package me.lukiiy.corpse;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Mannequin;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class Cmd implements BasicCommand {
    private static final Component USAGE = Component.text("Usage: /corpse <reload/clear>").color(NamedTextColor.RED);

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] strings) {
        Corpse corpse = Corpse.getInstance();
        CommandSender sender = commandSourceStack.getSender();

        if (strings.length == 0) {
            sender.sendMessage(USAGE);
            return;
        }

        switch (strings[0].toLowerCase()) {
            case "reload" -> {
                corpse.reloadConfig();
                corpse.updateCommons();
                sender.sendMessage(Component.text("Corpse Reload complete!").color(NamedTextColor.GREEN));
            }

            case "list" -> {
                if (corpse.tracked.isEmpty()) {
                    sender.sendMessage(Component.text("There are no corpses to list").color(NamedTextColor.RED));
                    return;
                }

                sender.sendMessage(Component.text("Tracked corpses (" + corpse.tracked.size() + "):").color(NamedTextColor.YELLOW));

                corpse.tracked.forEach(it -> {
                    String[] data = it.getPersistentDataContainer().get(Corpse.getKey(), PersistentDataType.STRING).split(";");
                    UUID uuid = UUID.fromString(data[2]);
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    Location loc = it.getLocation();
                    String coords = String.format("%s, %.1f, %.1f, %.1f", loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
                    long elapsed = (loc.getWorld().getGameTime() - Long.parseLong(data[0])) / 20;
                    long left = Math.max(0, Long.parseLong(data[1]) - elapsed);

                    Component sep = Component.text(';');
                    Component entry = Component.text("- ").color(NamedTextColor.WHITE)
                            .append(Component.text(player.getName() != null ? player.getName() : uuid.toString(), NamedTextColor.GREEN))
                            .append(sep)
                            .append(Component.text(coords).color(NamedTextColor.YELLOW).clickEvent(ClickEvent.suggestCommand(String.format("/tp %.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ()))).hoverEvent(HoverEvent.showText(Component.translatable("chat.coordinates.tooltip"))))
                            .append(sep)
                            .append(Component.text(left + "s", NamedTextColor.AQUA));

                    sender.sendMessage(entry);
                });
            }

            case "clear" -> {
                if (corpse.tracked.isEmpty()) {
                    sender.sendMessage(Component.text("There are no corpses to clear.").color(NamedTextColor.RED));
                    return;
                }

                int count = 0;
                for (Mannequin it : new ArrayList<>(corpse.tracked)) {
                    if (!it.isValid() || it.isDead()) {
                        corpse.tracked.remove(it);
                        continue;
                    }

                    corpse.popCorpseData(it);
                    it.remove();
                    corpse.tracked.remove(it);
                    count++;
                }

                sender.sendMessage(Component.text("Cleared " + count + " corpses!").color(NamedTextColor.GREEN));
            }

            default -> sender.sendMessage(USAGE);
        }
    }

    @Override
    public @Nullable String permission() {
        return "corpse.cmd";
    }

    @Override
    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        if (args.length == 1) return Stream.of("reload", "clear").filter(s -> s.startsWith(args[0].toLowerCase())).toList();

        return List.of();
    }
}

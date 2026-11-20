package me.lukiiy.corpse;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Mannequin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
}

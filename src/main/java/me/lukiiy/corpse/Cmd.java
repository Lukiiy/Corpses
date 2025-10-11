package me.lukiiy.corpse;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.Nullable;

public class Cmd implements BasicCommand {
    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] strings) {
        Corpse.getInstance().setupConfig();
        commandSourceStack.getSender().sendMessage(Component.text("Corpse Reload complete!").color(NamedTextColor.GREEN));
    }

    @Override
    public @Nullable String permission() {
        return "corpse.cmd";
    }
}

package world.edenmc.broadcastvelocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class BroadcastCommand implements SimpleCommand {

    private final ProxyServer server;
    private final Component prefix;

    public BroadcastCommand(ProxyServer server, Component prefix) {
        this.server = server;
        this.prefix = prefix;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            invocation.source().sendPlainMessage("Usage: /" + invocation.alias() + " <message>");
            return;
        }

        Component message = prefix.append(MiniMessage.miniMessage().deserialize(String.join(" ", args)));
        server.sendMessage(message);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxybroadcast.use");
    }
}

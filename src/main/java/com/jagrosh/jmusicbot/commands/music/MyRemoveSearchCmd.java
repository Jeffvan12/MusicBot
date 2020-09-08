package com.jagrosh.jmusicbot.commands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.jagrosh.jmusicbot.utils.OtherUtil.*;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.queue.FairQueue;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.User;

import javax.print.attribute.standard.MediaSize;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MyRemoveSearchCmd extends MusicCommand {
    public MyRemoveSearchCmd(Bot bot) {
        super(bot);
        this.name = "myremovesearch";
        this.help = "Removes songs in your queue that contain a specified string";
        this.arguments = "";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler.getQueue().isEmpty()) {
            event.replyError("There is nothing in the queue!");
            return;
        }
        if (event.getArgs().equalsIgnoreCase("all")) {
            int count = handler.getQueue().removeAll(event.getAuthor().getIdLong());
            if (count == 0)
                event.replyWarning("You don't have any songs in the queue!");
            else
                event.replySuccess("Successfully removed your " + count + " entries.");
            return;
        }


        User u;
        try {
            u = event.getJDA().getUserById(event.getAuthor().getIdLong());
        } catch (Exception e) {
            u = null;
        }



        String[] tracksToRemove = event.getArgs().trim().split(",");

        List<QueuedTrack> qts = new ArrayList<>();
        for (String toRemove : tracksToRemove){

            long identifier = event.getAuthor().getIdLong();
            FairQueue<QueuedTrack> queue = handler.getQueue();

            String finalToRemove = toRemove.trim().toLowerCase();
            Predicate<QueuedTrack> pred = track -> track.getTrack().getInfo().title.toLowerCase().contains(finalToRemove);

            qts.addAll(queue.removeIf(identifier, pred));
        }


        if (qts.size() > 10) {
            event.reply("Removed " + qts.size() + " songs");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = qts.size() - 1; i >= 0; i--) {
            sb.append(event.getClient().getSuccess()).append(" ").append("Removed **").append(" ").append(qts.get(i).getTrack().getInfo().title).append("**\n");
        }

        event.reply(sb.toString());

    }
}

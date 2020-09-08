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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MyQueueRemoveCmd extends MusicCommand {
    public MyQueueRemoveCmd(Bot bot) {
        super(bot);
        this.name = "myremove";
        this.help = "removes a song from the queue";
        this.arguments = "<position|ALL>";
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

        String input = event.getArgs().trim().replace(" ", "");

        User u;
        try {
            u = event.getJDA().getUserById(event.getAuthor().getIdLong());
        } catch (Exception e) {
            u = null;
        }

        long identifier = event.getAuthor().getIdLong();
        FairQueue<QueuedTrack> queue = handler.getQueue();

        if (handler.getQueue().getList(identifier).size() == 0){
            event.replyError("You don't have a queue");
        }

        if (input.contains("-")){
            String[] range = input.split("-");

            int start;
            int end;
            try {
                start = range[0].equals("") ? 0 : Integer.parseInt(range[0]) - 1;
                end = (range.length == 1) ? queue.getList(event.getAuthor().getIdLong()).size() - 1 : Integer.parseInt(range[1]) - 1;
            } catch (NumberFormatException e){
                event.reply("Invalid start or end number");
                return;
            }

            if(start > end) {
                event.replyError("Removing range of positions must be in format x-y where y >= x!");
                return;
            }

            start = OtherUtil.clamp(start, 0, queue.getList(event.getAuthor().getIdLong()).size() - 1);
            end = OtherUtil.clamp(end, 0, queue.getList(event.getAuthor().getIdLong()).size() - 1);

            List<Integer> toRemove = IntStream.rangeClosed(start, end).boxed().collect(Collectors.toList());
            List<QueuedTrack> qts = queue.specificQueueRemove(toRemove, event.getAuthor().getIdLong());

            if (qts.size() > 10) {
                event.reply("Removed " + qts.size() + " songs");
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = qts.size() - 1; i >= 0; i--) {
                sb.append(event.getClient().getSuccess()).append(" ").append("Removed **").append(" ").append(qts.get(i).getTrack().getInfo().title).append("**\n");
            }

            event.reply(sb.toString());

        } else if(input.contains(",")){
            List<Integer> toRemove = new ArrayList<>();
            for (String indexToRemove : input.split(",")){
                try {
                    int num = Integer.parseInt(indexToRemove);
                    if (num > 0 && num <= queue.getList(event.getAuthor().getIdLong()).size()){
                        toRemove.add(num - 1);
                    } else{
                        event.reply(indexToRemove + " is not a valid index");
                    }
                } catch (NumberFormatException e){
                    event.reply(indexToRemove + " is not a valid index");
                }
            }

            List<QueuedTrack> qts = handler.getQueue().specificQueueRemove(toRemove, event.getAuthor().getIdLong());

            if (qts.size() > 10){
                event.reply("Removed " + qts.size() + " songs");
                return;
            }
            StringBuilder sb = new StringBuilder();

            for (int i = qts.size() - 1; i >= 0; i--) {
                sb.append(event.getClient().getSuccess()).append(" ").append("Removed **").append(" ").append(qts.get(i).getTrack().getInfo().title).append("**\n");
            }

            event.reply(sb.toString());

        } else {
            int pos;
            try {
                pos = Integer.parseInt(event.getArgs());
            } catch(NumberFormatException e){
                pos = 0;
            }

            if (pos < 1 || pos > handler.getQueue().size()) {
                event.replyError("Position must be a valid integer between 1 and " + handler.getQueue().size() + "!");
                return;
            }
            Settings settings = event.getClient().getSettingsFor(event.getGuild());
            boolean isDJ = event.getMember().hasPermission(Permission.MANAGE_SERVER);
            if (!isDJ)
                isDJ = event.getMember().getRoles().contains(settings.getRole(event.getGuild()));
            QueuedTrack qt = handler.getQueue().get(pos - 1);
            if (qt.getIdentifier() == event.getAuthor().getIdLong()) {
                handler.getQueue().specificQueueRemove(pos - 1, event.getAuthor().getIdLong());
                event.replySuccess("Removed **" + qt.getTrack().getInfo().title + "** from the queue");
            } else if (isDJ) {
                handler.getQueue().specificQueueRemove(pos - 1, event.getAuthor().getIdLong());
                event.replySuccess("Removed **" + qt.getTrack().getInfo().title
                        + "** from the queue (requested by " + (u == null ? "someone" : "**" + u.getName() + "**") + ")");
            } else {
                event.replyError("You cannot remove **" + qt.getTrack().getInfo().title + "** because you didn't add it!");
            }
        }

    }
}
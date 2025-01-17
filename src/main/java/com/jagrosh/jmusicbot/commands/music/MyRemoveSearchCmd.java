package com.jagrosh.jmusicbot.commands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.queue.FairQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class MyRemoveSearchCmd extends MusicCommand {
    public MyRemoveSearchCmd(Bot bot) {
        super(bot);
        this.name = "myremovesearch";
        this.help = "Removes songs in your queue that contain a specified string";
        this.arguments = "";
        this.aliases = new String[]{"mrs"};
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

        String[] tracksToRemove = event.getArgs().trim().split(",");

        long identifier = event.getAuthor().getIdLong();
        FairQueue<QueuedTrack> queue = handler.getQueue();

        List<QueuedTrack> qts = new ArrayList<>();
        for (String toRemove : tracksToRemove){
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

package com.jagrosh.jmusicbot.commands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.queue.FairQueue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MyRemoveSearchInverseCmd extends MusicCommand {
    public MyRemoveSearchInverseCmd(Bot bot) {
        super(bot);
        this.name = "myremovesearchinverse";
        this.help = "Removes songs in your queue that contain a specified string";
        this.arguments = "";
        this.aliases = new String[] {"mk"};
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

        List<String> tracksToKeep = Arrays.stream(event.getArgs().trim().split(",")).map(String::trim).collect(Collectors.toList());

        long identifier = event.getAuthor().getIdLong();
        FairQueue<QueuedTrack> queue = handler.getQueue();

        Predicate<QueuedTrack> pred = track -> listContains(tracksToKeep, track.getTrack().getInfo().title);
        List<QueuedTrack> qts = (queue.removeIf(identifier, pred));

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

    private boolean listContains(List<String> words, String title){
        for (String word : words) {
            if (title.toLowerCase().contains(word.toLowerCase())){
                return false;
            }
        }
        return true;
    }
}

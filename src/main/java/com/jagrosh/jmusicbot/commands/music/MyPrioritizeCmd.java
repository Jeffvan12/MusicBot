package com.jagrosh.jmusicbot.commands.music;

import java.util.List;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.queue.FairQueue;
import com.jagrosh.jmusicbot.selectors.ParseException;
import com.jagrosh.jmusicbot.selectors.Parser;
import com.jagrosh.jmusicbot.selectors.Selector;

public class MyPrioritizeCmd extends MusicCommand {
    public MyPrioritizeCmd(Bot bot) {
        super(bot);
        this.name = "myprioritize";
        this.help = "Moves songs to the front of the queue";
        this.arguments = "<position|x-y|comma separated nums i.e. 1,3,7>";
        this.aliases = new String[]{"mp"};
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

        long identifier = event.getAuthor().getIdLong();
        FairQueue<QueuedTrack> queue = handler.getQueue();

        Selector<QueuedTrack> selector;
        try {
            selector = new Parser().parse(event.getArgs());
        } catch (ParseException e) {
            event.replyError("Invalid selector expression!");
            return;
        }

        List<QueuedTrack> qts = queue.moveToFrontIf(identifier, selector);

        if (qts.size() > 10 || qts.isEmpty()) {
            event.reply("Prioritised " + qts.size() + " songs");
        } else {
            StringBuilder sb = new StringBuilder();
            for (QueuedTrack qt : qts) {
                sb.append(event.getClient().getSuccess()).append(" ").append("Prioritised **").append(" ")
                        .append(qt.getTrack().getInfo().title).append("**\n");
            }

            event.reply(sb.toString());
        }
    }
}

/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.commands.music;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.menu.Paginator;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.queue.FairQueue;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.PermissionException;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class QueueCmd extends MusicCommand {
    private final static String REPEAT = "\uD83D\uDD01"; // 🔁

    private final Paginator.Builder builder;

    public QueueCmd(Bot bot) {
        super(bot);
        this.name = "queue";
        this.help = "shows the current queue";
        this.arguments = "[pagenum]";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
        this.botPermissions = new Permission[] { Permission.MESSAGE_ADD_REACTION,
                Permission.MESSAGE_EMBED_LINKS };
        builder = new Paginator.Builder().setColumns(1).setFinalAction(m -> {
            try {
                m.clearReactions().queue();
            } catch (PermissionException ignore) {
            }
        }).setItemsPerPage(10).waitOnSinglePage(false).useNumberedItems(true).showPageNumbers(true)
                .wrapPageEnds(true).setEventWaiter(bot.getWaiter()).setTimeout(1, TimeUnit.MINUTES);
    }

    @Override
    public void doCommand(CommandEvent event) {
        int pagenum;
        try {
            pagenum = Integer.parseInt(event.getArgs());
        } catch (NumberFormatException ignore) {
            pagenum = 1;
        }
        AudioHandler ah = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        FairQueue<QueuedTrack> queue = ah.getQueue();

        List<QueuedTrack> list = queue.getList();
        int nonRepeatLength = list.size() - queue.getList(FairQueue.REPEAT_SENTINEL).size();

        if (list.isEmpty()) {
            Message nowp = ah.getNowPlaying(event.getJDA());
            Message nonowp = ah.getNoMusicPlaying(event.getJDA());
            Message built = new MessageBuilder()
                    .setContent(event.getClient().getWarning() + " There is no music in the queue!")
                    .setEmbed((nowp == null ? nonowp : nowp).getEmbeds().get(0)).build();
            event.reply(built, m -> {
                if (nowp != null)
                    bot.getNowplayingHandler().setLastNPMessage(m);
            });
            return;
        }
        String[] songs = new String[list.size()];
        long total = 0;
        long repeat_total = 0;
        for (int i = 0; i < list.size(); i++) {
            if (i < nonRepeatLength) {
                total += list.get(i).getTrack().getDuration();
                songs[i] = list.get(i).toString();
            } else {
                repeat_total += list.get(i).getTrack().getDuration();
                AudioTrack track = list.get(i).getTrack();
                songs[i] = "`[" + FormatUtil.formatTime(track.getDuration()) + "]` ** " + REPEAT
                        + " " + track.getInfo().title + "** - <@" + track.getUserData(Long.class)
                        + ">";
            }
        }

        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        long fintotal = total;
        long finrepeattotal = repeat_total;
        builder.setText((i1, i2) -> getQueueTitle(ah, queue, event.getGuild(),
                event.getClient().getSuccess(), nonRepeatLength, fintotal, settings.getRepeatMode(),
                finrepeattotal, list.size() - nonRepeatLength)).setItems(songs)
                .setUsers(event.getAuthor()).setColor(event.getSelfMember().getColor());
        builder.build().paginate(event.getChannel(), pagenum);

    }

    private String getQueueTitle(AudioHandler ah, FairQueue<QueuedTrack> queue, Guild guild,
            String success, int songslength, long total, boolean repeatmode, long repeattotal,
            int repeatlength) {
        StringBuilder sb = new StringBuilder();
        if (ah.getPlayer().getPlayingTrack() != null) {
            sb.append(ah.getPlayer().isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI)
                    .append(" **").append(ah.getPlayer().getPlayingTrack().getInfo().title)
                    .append("**\n");
        }

        sb.append(success).append(" Current Queue | ").append(songslength).append(" (")
                .append(repeatlength).append(")").append(" entries | `")
                .append(FormatUtil.formatTime(total)).append(" (")
                .append(FormatUtil.formatTime(repeattotal)).append(")").append("` ")
                .append(repeatmode ? "| " + REPEAT : "");

        List<Long> userIds = queue.getUsers();
        long shortest = queue.getTime(userIds.get(0));
        for (Long identifier : userIds) {
            User user = guild.getJDA().getUserById(identifier);
            if (user == null) {
                continue;
            }
            sb.append("\n**").append(user.getName()).append(":** `+");
            long time = queue.getTime(identifier) - shortest;
            boolean timeStarted = false;
            if (time > 60 * 60 * 1000) {
                long hours = time / 60 / 60 / 1000;
                sb.append(hours).append(':');
                timeStarted = true;
                time %= 60 * 60 * 1000;
            }
            long minutes = time / 60 / 1000;
            if (timeStarted) {
                sb.append(String.format("%02d", minutes));
            } else {
                sb.append(minutes);
            }
            sb.append(':');
            time %= 60 * 1000;
            long seconds = time / 1000;
            sb.append(String.format("%02d", seconds));
            sb.append('`');
        }

        return FormatUtil.filter(sb.toString());
    }
}

/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
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

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SeekCmd extends MusicCommand {
    public SeekCmd(Bot bot) {
        super(bot);
        this.name = "seek";
        this.help = "changes the time in the current song";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager()
                .getSendingHandler();

        AudioTrack track = handler.getPlayer().getPlayingTrack();

        if (!track.isSeekable()) {
            event.replyError("Track is not seekable!");
            return;
        }

        long newPosition;
        try {
            newPosition = 1000 * seekBy(track.getPosition() / 1000, event.getArgs());
        } catch (NumberFormatException e) {
            event.replyError("Invalid time format!");
            return;
        }
        if (newPosition < 0 || newPosition >= track.getDuration()) {
            event.replyError("Time is not within the song bounds!");
            return;
        }

        if (handler.seekTo(newPosition)) {
            event.replySuccess("Successfully seeked.");
        } else {
            event.replyError("Failed to seek.");
        }
    }

    long seekBy(long currentSeconds, String seekBy) throws NumberFormatException {
        seekBy = seekBy.strip();

        boolean relative = false;
        boolean negative = false;
        if (seekBy.startsWith("+")) {
            seekBy = seekBy.substring(1).strip();
            relative = true;
        } else if (seekBy.startsWith("-")) {
            seekBy = seekBy.substring(1).strip();
            relative = true;
            negative = true;
        }

        String[] parts = seekBy.split(":");
        if (parts.length == 0 || parts.length > 3) {
            throw new NumberFormatException();
        }

        long seconds = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if ((part.length() != 2 && i != 0) || !part.chars().allMatch(Character::isDigit)) {
                throw new NumberFormatException();
            }
            long value = Long.parseLong(part);
            if (value < 0 || (value >= 60 && i != 0)) {
                throw new NumberFormatException();
            }
            seconds = seconds * 60 + value;
        }

        if (negative) {
            seconds *= -1;
        }
        if (relative) {
            seconds += currentSeconds;
        }

        return seconds;
    }
}

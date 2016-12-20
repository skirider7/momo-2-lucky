package io.ph.bot.audio;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.eclipsesource.json.JsonObject;

import io.ph.bot.Bot;
import io.ph.bot.exception.NoAPIKeyException;
import io.ph.bot.model.Guild.GuildMusic;
import io.ph.util.MessageUtils;
import io.ph.util.Util;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.util.EmbedBuilder;

/**
 * Download a URL (youtube, *.webm, or standard .mp3) then check if
 * true playlist size is < 2. If not, wait for TrackFinishEvent to poll
 * then convert it to an AudioInputStream
 * @author Paul
 *
 */
public class GetAudio implements Runnable {

	private File preparedFile;
	private String url;
	private String userId;
	private String title;
	private IChannel channel;
	private GuildMusic guildMusic;
	public GetAudio(String userId, String title, String url, IChannel channel, GuildMusic m) {
		this.url = url;
		this.userId = userId;
		this.title = title;
		this.guildMusic = m;
		this.channel = channel;
	}

	private void process() {
		int rand = new Random().nextInt(100000);
		EmbedBuilder em = new EmbedBuilder();


		if(url.contains("youtu.be") || url.contains("youtube")) {
			JsonObject jo;
			try {
				jo = Util.jsonFromUrl("https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails&id="
						+ Util.extractYoutubeId(this.url) + "&key=" + Bot.getInstance().getApiKeys().get("youtube"), true).asObject();
				Duration d = Duration.parse(jo.get("items").asArray().get(0).asObject()
						.get("contentDetails").asObject().getString("duration", "PT11M"));
				if((d.get(ChronoUnit.SECONDS) / 60) > 9) {
					em.withColor(Color.RED);
					em.withTitle("Error");
					em.withDesc("Please keep song length to 10 minutes or below");
					MessageUtils.sendMessage(channel, em.build());
					return;
				}
				this.title = jo.get("items").asArray().get(0).asObject().get("snippet").asObject().get("title").asString();
				em.withTitle("Queued: " + this.title)
				.withDesc(Bot.getInstance().getBot().getUserByID(this.userId).getDisplayName(channel.getGuild())
						+ " queued a track\n"
						+ this.url)
				.withFooterText("Place in queue: " + (this.guildMusic.getQueueSize() + 1))
				.withColor(Color.GREEN);
				MessageUtils.sendMessage(channel, em.build());
			} catch (NoAPIKeyException e) {
				em.withColor(Color.RED).withTitle("Error").withDesc("Looks like the owner doesn't have a Youtube API key setup yet");
				e.printStackTrace();
				MessageUtils.sendMessage(this.channel, em.build());
				return;
			} catch(IOException e1) {
				e1.printStackTrace();
				return;
			}
			String command = "youtube-dl --external-downloader ffmpeg -o resources/tempdownloads/"
					+ rand + ".mp4 -f mp4 --extract-audio --audio-format mp3 " + url;
			try {
				Process p = Runtime.getRuntime().exec(command);
				File f = new File("resources/tempdownloads/" + rand + ".mp3");
				if(!p.waitFor(3, TimeUnit.MINUTES)) {
					p.destroy();
					f.delete();
					return;
				}
				this.preparedFile = f;
			} catch (IOException e) {
				e.printStackTrace();
				return;
			} catch (InterruptedException e) {
				e.printStackTrace();
				return;
			}
		} else if(url.contains(".webm")) {
			FFmpeg ffmpeg;
			try {
				URL link = new URL(url);
				if((getFileSize(link) / (1024*1024) > 25)
						|| getFileSize(link) == -1) {
					em.withColor(Color.RED);
					em.withTitle("Error");
					em.withDesc("Please keep file size below 25 megabytes");
					System.out.println(getFileSize(link));
					MessageUtils.sendMessage(channel, em.build());
					return;
				}
				ffmpeg = new FFmpeg();
				FFmpegBuilder build = new FFmpegBuilder();
				File before = new File("resources/tempdownloads/" + rand);
				em.withTitle("Music queued")
				.withDesc(Bot.getInstance().getBot().getUserByID(this.userId).getDisplayName(channel.getGuild())
						+ " queued a track")
				.withFooterText("Place in queue: " + (this.guildMusic.getQueueSize() + 1))
				.withColor(Color.GREEN);
				MessageUtils.sendMessage(channel, em.build());
				Util.saveFile(link, before);

				build.setInput("resources/tempdownloads/" + rand);
				build.addOutput("resources/tempdownloads/" + rand + "out.mp3").setFormat("mp3").disableVideo().setAudioChannels(1);
				FFmpegExecutor exe = new FFmpegExecutor(ffmpeg, new FFprobe());
				exe.createJob(build).run();

				File after = new File("resources/tempdownloads/"+rand+"out.mp3");
				this.preparedFile = after;

			} catch (IOException e) {
				e.printStackTrace();
				return;
			}

		} else if(url.endsWith(".mp3") || url.endsWith(".flac")) {
			this.preparedFile = new File("resources/tempdownloads/" + rand + url.substring(url.lastIndexOf(".")));
			try {
				URL link = new URL(url);
				if((getFileSize(link) / (1024*1024) > 25)
						|| getFileSize(link) == -1) {
					em.withColor(Color.RED);
					em.withTitle("Error");
					em.withDesc("Please keep file size below 25 megabytes");
					System.out.println(getFileSize(link));
					MessageUtils.sendMessage(channel, em.build());
					return;
				}
				em.withTitle("Music queued")
				.withDesc(Bot.getInstance().getBot().getUserByID(this.userId).getDisplayName(channel.getGuild())
						+ " queued a track")
				.withFooterText("Place in queue: " + (this.guildMusic.getQueueSize() + 1))
				.withColor(Color.GREEN);
				MessageUtils.sendMessage(channel, em.build());
				Util.saveFile(link, preparedFile);
			} catch (MalformedURLException e) {
				em.withTitle("Error").withColor(Color.RED).withDesc("Your URL is invalid!");
				MessageUtils.sendMessage(channel, em.build());
				e.printStackTrace();
				return;
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}
		//this.preparedFile.deleteOnExit();
		String songLength;
		try {
			songLength = Util.getMp3Duration(this.preparedFile);
		} catch (UnsupportedAudioFileException | IOException e) {
			e.printStackTrace();
			songLength = "null";
		}
		this.guildMusic.queueMusicMeta(this.userId, this.title, this.url, songLength, this);
	}

	/**
	 * Return file size of URL based on its content-length header
	 * @param url URL to connect to
	 * @return Bytes of content
	 */
	private int getFileSize(URL url) {
		HttpsURLConnection conn = null;
		try {
			conn = (HttpsURLConnection) url.openConnection();
			conn.setRequestMethod("HEAD");
			conn.setRequestProperty("User-Agent",
					"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36");
			conn.getInputStream();
			return conn.getContentLength();
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
		} finally {
			conn.disconnect();
		}
	}

	public File getPreparedFile() {
		return preparedFile;
	}

	@Override
	public void run() {
		process();
	}
}
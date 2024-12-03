package org.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class BotListener extends ListenerAdapter {
    private static final String API_URL = "https://vsong.art/api/search"; // vsong.art API 엔드포인트
    private final OkHttpClient httpClient;
    private final GuildManager guildManager;

    public BotListener(GuildManager guildManager) {
        this.httpClient = new OkHttpClient();
        this.guildManager = guildManager;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println("봇이 성공적으로 로그인했습니다!");

        // 저장된 모든 길드에 대해 슬래시 명령어 등록
        for (String guildId : guildManager.getGuildIds()) {
            Guild guild = event.getJDA().getGuildById(guildId);
            if (guild != null) {
                registerCommands(guild);
            } else {
                System.err.println("저장된 길드 ID 중 유효하지 않은 ID가 있습니다: " + guildId);
            }
        }
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        String guildId = event.getGuild().getId();
        System.out.println("새로운 길드에 추가되었습니다: " + event.getGuild().getName() + " (ID: " + guildId + ")");

        // 슬래시 명령어 등록
        registerCommands(event.getGuild());

        // GuildManager에 길드 ID 저장
        guildManager.saveGuildId(guildId);
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "셋업":
                handleSetupCommand(event);
                break;
            case "search":
                handleSearchCommand(event);
                break;
            default:
                event.reply("알 수 없는 명령어입니다.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // 봇이 보낸 메시지라면 무시
        if (event.getAuthor().isBot()) return;

        String message = event.getMessage().getContentRaw();
        if (message.startsWith("!search")) {
            String[] parts = message.split(" ", 2);
            if (parts.length < 2) {
                event.getChannel().sendMessage("검색할 노래명을 입력해주세요. 사용법: `!search [노래명]`").queue();
                return;
            }

            String songName = parts[1];
            searchSong(event, songName);
        }
    }

    private void handleSetupCommand(SlashCommandInteractionEvent event) {
        // 명령어 권한 확인 (예: 관리자 권한)
        if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("이 명령어를 사용할 권한이 없습니다.").setEphemeral(true).queue();
            return;
        }

        // 이미 채널이 존재하는지 확인
        boolean channelExists = event.getGuild().getTextChannelsByName("vsong-음악채널", true).size() > 0;

        if (channelExists) {
            event.reply("`vsong-음악채널`이 이미 존재합니다.").setEphemeral(true).queue();
            return;
        }

        // 텍스트 채널 생성
        event.getGuild().createTextChannel("vsong-음악채널")
                .queue(
                        channel -> {
                            // 확인 메시지 전송 후, 5초 후 삭제
                            event.reply("텍스트 채널이 성공적으로 생성되었습니다: " + channel.getAsMention())
                                    .queue(hook -> {
                                        // 메시지 삭제를 위해 InteractionHook 사용
                                        hook.deleteOriginal().queueAfter(5, TimeUnit.SECONDS);
                                    });
                        },
                        error -> event.reply("채널 생성에 실패했습니다: " + error.getMessage()).setEphemeral(true).queue()
                );
    }

    private void handleSearchCommand(SlashCommandInteractionEvent event) {
        // 슬래시 명령어의 옵션에서 노래명 가져오기
        String songName = event.getOption("노래명").getAsString();

        // vsong.art API를 통해 노래 검색
        searchSong(event, songName);
    }

    private void searchSong(MessageReceivedEvent event, String songName) {
        HttpUrl url = HttpUrl.parse(API_URL).newBuilder()
                .addQueryParameter("query", songName)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        event.getChannel().sendTyping().queue();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                event.getChannel().sendMessage("API 요청에 실패했습니다. 나중에 다시 시도해주세요.").queue();
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    event.getChannel().sendMessage("API 응답이 올바르지 않습니다. 상태 코드: " + response.code()).queue();
                    return;
                }

                String responseBody = response.body().string();
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                JsonArray results = json.getAsJsonArray("results");
                if (results.size() == 0) {
                    event.getChannel().sendMessage("노래를 찾을 수 없습니다.").queue();
                    return;
                }

                JsonObject song = results.get(0).getAsJsonObject();
                String title = song.get("title").getAsString();
                String artist = song.get("artist").getAsString();
                String url = song.get("url").getAsString();

                String reply = String.format("**노래 제목**: %s\n**아티스트**: %s\n**URL**: %s", title, artist, url);
                event.getChannel().sendMessage(reply).queue();
            }
        });
    }

    private void searchSong(SlashCommandInteractionEvent event, String songName) {
        HttpUrl url = HttpUrl.parse(API_URL).newBuilder()
                .addQueryParameter("query", songName)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        // SlashCommandInteractionEvent에서는 deferReply()를 사용하여 응답 지연을 알림
        event.deferReply().queue();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                event.getHook().sendMessage("API 요청에 실패했습니다. 나중에 다시 시도해주세요.").setEphemeral(true).queue();
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    event.getHook().sendMessage("API 응답이 올바르지 않습니다. 상태 코드: " + response.code()).setEphemeral(true).queue();
                    return;
                }

                String responseBody = response.body().string();
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                JsonArray results = json.getAsJsonArray("results");
                if (results.size() == 0) {
                    event.getHook().sendMessage("노래를 찾을 수 없습니다.").setEphemeral(true).queue();
                    return;
                }

                JsonObject song = results.get(0).getAsJsonObject();
                String title = song.get("title").getAsString();
                String artist = song.get("artist").getAsString();
                String url = song.get("url").getAsString();

                String reply = String.format("**노래 제목**: %s\n**아티스트**: %s\n**URL**: %s", title, artist, url);
                event.getHook().sendMessage(reply).queue();
            }
        });
    }

    private void registerCommands(Guild guild) {
        guild.updateCommands()
                .addCommands(
                        Commands.slash("셋업", "vsong-음악채널을 생성합니다."),
                        Commands.slash("search", "노래를 검색합니다.")
                                .addOption(OptionType.STRING, "노래명", "검색할 노래명을 입력하세요", true)
                )
                .queue(
                        success -> System.out.println("명령어가 성공적으로 등록되었습니다: " + guild.getName()),
                        error -> System.err.println("명령어 등록 실패 (" + guild.getName() + "): " + error.getMessage())
                );
    }
}

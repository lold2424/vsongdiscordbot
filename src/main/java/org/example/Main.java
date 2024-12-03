package org.example;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;

public class Main {
    public static void main(String[] args) {
        // Dotenv를 사용하여 환경 변수 로드
        Dotenv dotenv = Dotenv.load();
        String token = dotenv.get("BOT_TOKEN"); // .env 파일에 BOT_TOKEN 변수 설정

        if (token == null || token.isEmpty()) {
            System.err.println("봇 토큰이 설정되지 않았습니다. .env 파일을 확인하세요.");
            return;
        }

        // GuildManager 초기화
        GuildManager guildManager = new GuildManager();

        // JDA 인스턴스 생성 및 시작
        JDABuilder builder = JDABuilder.createDefault(token)
                .setActivity(Activity.playing("vsong-음악봇"))
                .addEventListeners(new BotListener(guildManager));

        builder.build();
    }
}

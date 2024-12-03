package org.example;

import java.io.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class GuildManager {
    private static final String GUILD_FILE = "guilds.txt";
    private Set<String> guildIds;

    public GuildManager() {
        guildIds = new HashSet<>();
        loadGuildIds();
    }

    // 길드 ID를 파일에서 불러오기
    private void loadGuildIds() {
        File file = new File(GUILD_FILE);
        if (!file.exists()) {
            System.out.println("길드 저장 파일이 존재하지 않습니다. 새로 생성됩니다.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                guildIds.add(line.trim());
            }
            System.out.println("길드 ID 로드 완료: " + guildIds.size() + "개");
        } catch (IOException e) {
            System.err.println("길드 ID 로드 실패: " + e.getMessage());
        }
    }

    // 길드 ID를 파일에 저장하기
    public void saveGuildId(String guildId) {
        if (guildIds.contains(guildId)) {
            return; // 이미 저장된 길드 ID라면 무시
        }

        guildIds.add(guildId);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(GUILD_FILE, true))) {
            writer.write(guildId);
            writer.newLine();
            System.out.println("길드 ID 저장 완료: " + guildId);
        } catch (IOException e) {
            System.err.println("길드 ID 저장 실패: " + e.getMessage());
        }
    }

    // 저장된 모든 길드 ID 반환
    public Set<String> getGuildIds() {
        return Collections.unmodifiableSet(guildIds);
    }
}

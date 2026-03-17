package cn.jason31416.multiauth.handler;

import lombok.Getter;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Whitelist {
    @Getter
    private static final Whitelist instance = new Whitelist();

    // authMethod -> set of whitelisted player names (case-insensitive via lowercase keys)
    private final Map<String, Set<String>> whitelist = new ConcurrentHashMap<>();

    public void addPlayer(String authMethod, String playerName) {
        whitelist.computeIfAbsent(authMethod, k -> ConcurrentHashMap.newKeySet())
                .add(playerName.toLowerCase());
    }

    public boolean removePlayer(String authMethod, String playerName) {
        Set<String> players = whitelist.get(authMethod);
        if (players == null) return false;
        return players.remove(playerName.toLowerCase());
    }

    public boolean isWhitelisted(String authMethod, String playerName) {
        Set<String> players = whitelist.get(authMethod);
        if (players == null) return false;
        return players.contains(playerName.toLowerCase());
    }

    public Set<String> getPlayers(String authMethod) {
        Set<String> players = whitelist.get(authMethod);
        if (players == null) return Collections.emptySet();
        return Collections.unmodifiableSet(players);
    }
}

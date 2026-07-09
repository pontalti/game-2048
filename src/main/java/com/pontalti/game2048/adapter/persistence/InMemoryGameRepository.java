package com.pontalti.game2048.adapter.persistence;

import com.pontalti.game2048.domain.Game;
import com.pontalti.game2048.domain.port.out.GameRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link GameRepository}, backed by a
 * {@link ConcurrentHashMap}. Games live only for the lifetime of the running
 * server (they are lost on restart) — which is fine for demonstrating the
 * architecture. Swapping this for a Redis- or JPA-backed adapter would not
 * touch the domain or the REST layer, since both depend only on the port.
 */
@Repository
public class InMemoryGameRepository implements GameRepository {

    private final ConcurrentHashMap<String, Game> games = new ConcurrentHashMap<>();

    @Override
    public String create(Game game) {
        String id = UUID.randomUUID().toString();
        games.put(id, game);
        return id;
    }

    @Override
    public Optional<Game> findById(String id) {
        return Optional.ofNullable(games.get(id));
    }

    @Override
    public boolean deleteById(String id) {
        return games.remove(id) != null;
    }
}

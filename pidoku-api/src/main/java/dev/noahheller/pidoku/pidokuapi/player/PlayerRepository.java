package dev.noahheller.pidoku.pidokuapi.player;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByFirebaseUid(String firebaseUid);

    /**
     * Creates the player for a uid unless it exists. Safe when two requests from a new player
     * arrive together: the second waits for the first and then inserts nothing.
     */
    @Modifying
    @Query(value = "INSERT INTO players (firebase_uid) VALUES (:uid) ON CONFLICT (firebase_uid) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("uid") String firebaseUid);
}

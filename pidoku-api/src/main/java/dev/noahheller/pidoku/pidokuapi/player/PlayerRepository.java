package dev.noahheller.pidoku.pidokuapi.player;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByFirebaseUid(String firebaseUid);

    Optional<Player> findByFriendCode(String friendCode);

    boolean existsByFriendCode(String friendCode);

    /**
     * Gives a player a friend code unless they have one. Safe when two requests arrive together:
     * the second changes nothing, so a code never changes once handed out. Clears the persistence
     * context, so read the player again afterwards.
     *
     * @return 1 if the code was assigned, 0 if the player already had one
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE players SET friend_code = :code WHERE id = :id AND friend_code IS NULL", nativeQuery = true)
    int assignFriendCode(@Param("id") long playerId, @Param("code") String code);

    /**
     * Creates the player for a uid unless it exists. Safe when two requests from a new player
     * arrive together: the second waits for the first and then inserts nothing.
     */
    @Modifying
    @Query(value = "INSERT INTO players (firebase_uid) VALUES (:uid) ON CONFLICT (firebase_uid) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("uid") String firebaseUid);
}

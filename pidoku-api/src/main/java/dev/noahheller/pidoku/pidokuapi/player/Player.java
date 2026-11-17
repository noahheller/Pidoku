package dev.noahheller.pidoku.pidokuapi.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

/**
 * One Firebase account (table {@code players}, created in V3). Rows are inserted by
 * {@link PlayerRepository#insertIfAbsent}, never through JPA; the display name is the only thing
 * that changes afterwards.
 */
@Getter
@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The Firebase uid: the {@code sub} claim of the player's ID token. */
    @Column(name = "firebase_uid", nullable = false, updatable = false, unique = true, length = 128)
    private String firebaseUid;

    /** Null until the player picks one. Already checked by {@link DisplayName} when set. */
    @Column(name = "display_name", length = 40)
    private String displayName;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    /** For JPA. */
    protected Player() {
    }

    public void rename(DisplayName displayName) {
        this.displayName = displayName.value();
    }
}

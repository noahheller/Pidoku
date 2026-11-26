package dev.noahheller.pidoku.pidokuapi.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.Instant;

/**
 * One Firebase account (table {@code players}, created in V3). Rows are inserted by
 * {@link PlayerRepository#insertIfAbsent}, never through JPA; afterwards JPA only changes the
 * display name and profile visibility. Supporter status is only ever written by SupporterService.
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

    /**
     * What friends type in (or open as {@code /f/{code}}) to add this player. Null until first
     * asked for; assigned once by {@link PlayerRepository#assignFriendCode} and never changed.
     */
    @Column(name = "friend_code", insertable = false, updatable = false, unique = true, length = 8)
    private String friendCode;

    /** Who can see the player's profile, by {@link ProfileVisibility#id()}; 'friends' until changed (V11). */
    @Getter(AccessLevel.NONE)
    @Column(name = "profile_visibility", nullable = false, insertable = false, length = 10)
    private String profileVisibility;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    /** When they first supported Pidoku on Ko-fi; null if they haven't (V13). Set by SupporterService. */
    @Column(name = "supporter_since", insertable = false, updatable = false)
    private Instant supporterSince;

    /** When their supporter badge ends; null for good (one-off tips). For memberships later. */
    @Column(name = "supporter_until", insertable = false, updatable = false)
    private Instant supporterUntil;

    /** For JPA. */
    protected Player() {
    }

    /** Whether they have a supporter badge at {@code now}. Matches LeaderboardService's query. */
    public boolean isSupporter(Instant now) {
        return supporterSince != null && (supporterUntil == null || supporterUntil.isAfter(now));
    }

    public void rename(DisplayName displayName) {
        this.displayName = displayName.value();
    }

    public ProfileVisibility getProfileVisibility() {
        return ProfileVisibility.fromId(profileVisibility);
    }

    public void setProfileVisibility(ProfileVisibility visibility) {
        this.profileVisibility = visibility.id();
    }
}

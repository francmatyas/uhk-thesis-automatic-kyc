package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "user_preferences")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, exclude = {"user"})
@SQLDelete(sql = "UPDATE user_preferences SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class UserPreferences extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_userpreferences_user"))
    private User user;

    /**
     * Jazykový kód (např. "en", "cs", "de")
     */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String language = "en";

    /**
     * Režim zobrazení: LIGHT, DARK nebo SYSTEM
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DisplayMode displayMode = DisplayMode.SYSTEM;

    /**
     * Měnový kód (např. "USD", "EUR", "CZK")
     */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "USD";

    /**
     * Preference formátu data
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DateFormat dateFormat = DateFormat.Y_M_D;

    /**
     * Preference formátu čísel
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NumberFormat numberFormat = NumberFormat.COMMA_DOT;

    /**
     * Časová zóna (např. "UTC", "Europe/Prague", "America/New_York")
     */
    @Column(length = 50)
    @Builder.Default
    private String timezone = "UTC";

    /**
     * Zda použít 24hodinový formát času (true) nebo 12hodinový (false)
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean use24HourTime = true;

    // Pomocný setter pro synchronizaci obousměrné asociace
    public void setUser(User user) {
        if (this.user == user) {
            return;
        }
        // Odpojení původního uživatele
        if (this.user != null) {
            User old = this.user;
            this.user = null;
            if (old.getPreferences() == this) {
                old.setPreferences(null);
            }
        }
        // Připojení nového uživatele
        if (user != null) {
            this.user = user;
            if (user.getPreferences() != this) {
                user.setPreferences(this);
            }
        }
    }
}

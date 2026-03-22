package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.EncryptedInstantConverter;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Entity
@Table(name = "user_profiles")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, exclude = {"user"})
@SQLDelete(sql = "UPDATE user_profiles SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class UserProfile extends BaseEntity {
    @Convert(converter = EncryptedInstantConverter.class)
    @Column(name = "date_of_birth", columnDefinition = "text")
    private Instant dateOfBirth;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String gender;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String dialCode;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_number", columnDefinition = "text")
    private String phoneNumber;
    @Column
    private String avatarUrl;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_userprofile_user"))
    private User user;

    // Pomocný setter pro synchronizaci obousměrné asociace
    public void setUser(User user) {
        if (this.user == user) {
            return;
        }
        // Odpojení původního uživatele
        if (this.user != null) {
            User old = this.user;
            this.user = null;
            if (old.getProfile() == this) {
                old.setProfile(null);
            }
        }
        // Připojení nového uživatele
        if (user != null) {
            this.user = user;
            if (user.getProfile() != this) {
                user.setProfile(this);
            }
        }
    }
}
